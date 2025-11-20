package org.example.demo.server;
// mvn compile exec:java "-Dexec.mainClass=org.example.demo.server.Server"
import org.example.demo.game.Game;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server:
 * - 2.1 Connection / Game State / Crop Growth (调用 Game 内线程)
 * - 2.2 Networking push UPDATE
 * - 2.3 Gameplay Rules (偷菜限制 + 奖励调整)
 * - 2.4 Concurrency logs + atomic double-lock
 * - 2.5 Exception handling (I/O 捕获不中断整体, 输入校验)
 */
public class Server {
    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "client-io");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, Game> farms = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PrintWriter> allClients = new CopyOnWriteArrayList<>();

    // 记录每个玩家当前“正在查看的农场”用于偷菜限制 —— [Task 2.3 Gameplay Rules: owner away condition]
    private final ConcurrentHashMap<String, String> currentView = new ConcurrentHashMap<>(); // 新增

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port); // [Task 2.5 Log]
            while (true) {
                Socket socket = server.accept();
                pool.submit(new ClientHandler(socket));
            }
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String player;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (socket) {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                allClients.add(out);
                System.out.println(Thread.currentThread().getName() + " CONNECT"); // [Task 2.4 Concurrency Log]

                out.println("OK WELCOME Use: LOGIN <name>|GET|VIEW <player>|PLANT r c|HARVEST r c|STEAL <victim>|PLAYERS"); // [Task 2.2 Controls list]

                String line;
                while ((line = in.readLine()) != null) {
                    handle(line.trim());
                }
            } catch (Exception e) {
                // [Task 2.5 Exception Handling] I/O/运行异常不使服务器崩溃
            } finally {
                allClients.remove(out);
                if (player != null) currentView.remove(player); // [Task 2.5 Cleanup]
            }
        }

        private void handle(String line) {
            try {
                if (line.isEmpty()) return;
                String[] parts = line.split("\\s+");
                String cmd = parts[0].toUpperCase(Locale.ROOT);

                switch (cmd) {
                    case "LOGIN": {
                        ensure(parts.length == 2, "Usage: LOGIN <name>");
                        player = parts[1];
                        // 创建游戏并设置成熟回调推送 —— [Task 2.2 Push Update & 2.1 Game State]
                        farms.computeIfAbsent(player, k -> {
                            Game g = new Game();
                            g.setOnStateChange(game -> broadcastUpdate(k, game)); // 成熟/状态变化广播
                            return g;
                        });
                        currentView.put(player, player); // 初始查看自己的农场 [Task 2.3 Owner view tracking]
                        out.println("OK LOGGED_IN " + player);
                        broadcastUpdate(player, farms.get(player)); // 登录后立即推送当前状态 [Task 2.2]
                        break;
                    }
                    case "PLAYERS": {
                        out.println("OK " + String.join(",", farms.keySet()));
                        break;
                    }
                    case "GET": {
                        requireLogin();
                        currentView.put(player, player); // 返回自家农场视图 [Task 2.3 View tracking]
                        Game g = farms.get(player);
                        out.println("OK " + snapshot(g));
                        break;
                    }
                    case "VIEW": {
                        requireLogin();
                        ensure(parts.length == 2, "Usage: VIEW <player>");
                        String target = parts[1];
                        Game g = farms.get(target);
                        if (g == null) {
                            out.println("ERR Player not found");
                        } else {
                            currentView.put(player, target); // 更新当前查看对象 [Task 2.3 View tracking]
                            out.println("OK " + snapshot(g));
                        }
                        break;
                    }
                    case "PLANT": {
                        requireLogin();
                        ensure(parts.length == 3, "Usage: PLANT <row> <col>");
                        Game g = farms.get(player);
                        int r = parse(parts[1]);
                        int c = parse(parts[2]);
                        ensure(inBounds(g, r, c), "Index out of bounds");
                        g.plant(r, c); // Game 内部同步 [Task 2.1 Atomic]
                        broadcastUpdate(player, g); // 推送更新 [Task 2.2]
                        out.println("OK " + snapshot(g));
                        break;
                    }
                    case "HARVEST": {
                        requireLogin();
                        ensure(parts.length == 3, "Usage: HARVEST <row> <col>");
                        Game g = farms.get(player);
                        int r = parse(parts[1]);
                        int c = parse(parts[2]);
                        ensure(inBounds(g, r, c), "Index out of bounds");
                        g.harvest(r, c);
                        broadcastUpdate(player, g); // 推送更新 [Task 2.2]
                        out.println("OK " + snapshot(g));
                        break;
                    }
                    case "STEAL": {
                        requireLogin();
                        ensure(parts.length == 2, "Usage: STEAL <victim>");
                        String victimName = parts[1];
                        ensure(!victimName.equals(player), "Cannot steal yourself"); // [Task 2.3 Rule]

                        // 受害者是否“在自己农场”判定 —— [Task 2.3 Owner away condition]
                        if (victimName.equals(currentView.get(victimName))) {
                            out.println("ERR Victim at home (cannot steal)"); // [Task 2.3 Rule enforcement]
                            break;
                        }

                        Game thief = farms.computeIfAbsent(player, k -> {
                            Game g = new Game();
                            g.setOnStateChange(game -> broadcastUpdate(k, game));
                            return g;
                        });
                        Game victim = farms.computeIfAbsent(victimName, k -> {
                            Game g = new Game();
                            g.setOnStateChange(game -> broadcastUpdate(k, game));
                            return g;
                        });

                        boolean success = stealAtomic(thief, victim); // [Task 2.1 Atomic Operations + 2.4 Concurrency]
                        broadcastUpdate(player, thief); // 推送小偷状态 [Task 2.2]
                        broadcastUpdate(victimName, victim); // 推送受害者状态
                        if (success) {
                            out.println("OK " + snapshot(thief));
                        } else {
                            out.println("ERR Steal failed (no ripe crop)");
                        }
                        break;
                    }
                    default:
                        out.println("ERR Unknown command: " + cmd);
                }
            } catch (Exception e) {
                out.println("ERR " + e.getMessage()); // [Task 2.5 Exception Handling]
            }
        }

        private void requireLogin() {
            if (player == null) throw new IllegalStateException("Please LOGIN first.");
        }

        private void ensure(boolean cond, String msg) {
            if (!cond) throw new IllegalArgumentException(msg);
        }

        private int parse(String s) {
            return Integer.parseInt(s); // 简化转换 （内部异常由上层捕获）[Task 2.5]
        }
    }

    private static boolean inBounds(Game g, int r, int c) {
        return r >= 0 && r < g.getRows() && c >= 0 && c < g.getCols();
    }

    // 广播 UPDATE 行 —— [Task 2.2 Networking Push]
    private void broadcastUpdate(String playerName, Game g) {
        String line = "UPDATE " + playerName + " " + snapshot(g);
        for (PrintWriter w : allClients) {
            w.println(line);
        }
    }

    // 双重锁原子偷菜 —— [Task 2.1 Atomic Operations] + 并发日志 [Task 2.4]
    private static boolean stealAtomic(Game thief, Game victim) {
        Game first = System.identityHashCode(thief) < System.identityHashCode(victim) ? thief : victim;
        Game second = (first == thief) ? victim : thief;

        synchronized (first) {
            synchronized (second) {
                boolean ok = victim.stealOneRipe();
                if (ok) {
                    thief.addCoins(Game.STEAL_REWARD);
                    System.out.println(Thread.currentThread().getName() + " STEAL success"); // [Task 2.4 Log]
                } else {
                    System.out.println(Thread.currentThread().getName() + " STEAL fail"); // [Task 2.4 Log]
                }
                return ok;
            }
        }
    }

    private static String snapshot(Game g) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"coins\":").append(g.getCoins()).append(",\"board\":[");
        for (int r = 0; r < g.getRows(); r++) {
            if (r > 0) sb.append(",");
            sb.append("[");
            for (int c = 0; c < g.getCols(); c++) {
                if (c > 0) sb.append(",");
                sb.append("\"").append(g.getState(r, c).name()).append("\"");
            }
            sb.append("]");
        }
        sb.append("]}");
        return sb.toString();
    }

    public static void main(String[] args) throws IOException {
        new Server(5050).start();
    }
}