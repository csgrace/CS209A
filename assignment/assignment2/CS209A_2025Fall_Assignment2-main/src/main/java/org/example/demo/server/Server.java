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
 * - 🔥 NEW: ScheduledExecutorService for server statistics monitoring (Multithreading)
 */
public class Server {
    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "client-io");
        t.setDaemon(true);
        return t;
    });

    // 🔥 新增：统计监控线程池
    private final ScheduledExecutorService statsExecutor = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "stats-monitor");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Game> farms = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PrintWriter> allClients = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, String> currentView = new ConcurrentHashMap<>();

    // 🔥 新增：统计信息
    private final ConcurrentHashMap<String, Integer> playerActionStats = new ConcurrentHashMap<>();
    private long serverStartTime;

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        serverStartTime = System.currentTimeMillis();

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("========================================");
            System.out.println("Server started on port: " + port);
            System.out.println("Waiting for clients...");
            System.out.println("========================================\n");

            // 🔥 启动统计监控线程
            startStatsMonitor();

            while (true) {
                Socket socket = server.accept();
                pool.submit(new ClientHandler(socket));
            }
        } finally {
            // 优雅关闭
            pool.shutdown();
            statsExecutor.shutdown();
        }
    }

    // 🔥 新增：启动统计监控线程
    private void startStatsMonitor() {
        statsExecutor.scheduleAtFixedRate(
                this::printServerStats,
                10,          // 初始延迟10秒
                60,          // 每60秒执行一次
                TimeUnit.SECONDS
        );
    }

    // 🔥 新增：打印服务器统计信息
    private void printServerStats() {
        long uptime = System.currentTimeMillis() - serverStartTime;
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        System.out.println("\n--- Server Status Report ---");
        System.out.println("Uptime: " + String.format("%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60));
        System.out.println("Connected Clients: " + allClients.size());
        System.out.println("Total Farms: " + farms.size());
        System.out.println("Active Players: " + currentView.size());

        if (!playerActionStats.isEmpty()) {
            System.out.println("Player Activity:");
            for (var entry : playerActionStats.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue() + " actions");
            }
        }

        if (!farms.isEmpty()) {
            System.out.println("Farms Info:");
            for (var entry : farms.entrySet()) {
                Game game = entry.getValue();
                System.out.println("  " + entry.getKey() + ": " + game.getCoins() + " coins");
            }
        }
        System.out.println("----------------------------\n");
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
                System.out.println("[CONNECT] New client connected: " + socket.getInetAddress());

                out.println("OK WELCOME Use: LOGIN <name>|GET|VIEW <player>|PLANT r c|HARVEST r c|STEAL <victim>|PLAYERS");

                String line;
                while ((line = in.readLine()) != null) {
                    handle(line.trim());
                }
            } catch (Exception e) {
                System.out.println("[ERROR] Connection error: " + e.getMessage());
            } finally {
                allClients.remove(out);
                if (player != null) {
                    currentView.remove(player);
                    System.out.println("[DISCONNECT] Player: " + player);
                }
            }
        }

        private void handle(String line) {
            try {
                if (line.isEmpty()) return;
                String[] parts = line.split("\\s+");
                String cmd = parts[0].toUpperCase(Locale.ROOT);

                // 🔥 记录玩家操作统计
                if (player != null) {
                    playerActionStats.merge(player, 1, Integer::sum);
                }

                switch (cmd) {
                    case "LOGIN": {
                        ensure(parts.length == 2, "Usage: LOGIN <name>");
                        player = parts[1];
                        farms.computeIfAbsent(player, k -> {
                            Game g = new Game();
                            g.setOnStateChange(game -> broadcastUpdate(k, game));
                            return g;
                        });
                        currentView.put(player, player);
                        playerActionStats.putIfAbsent(player, 0);
                        out.println("OK LOGGED_IN " + player);
                        System.out.println("[LOGIN] " + player + " logged in");
                        broadcastUpdate(player, farms.get(player));
                        break;
                    }
                    case "PLAYERS": {
                        out.println("OK " + String.join(",", farms.keySet()));
                        break;
                    }
                    case "GET": {
                        requireLogin();
                        currentView.put(player, player);
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
                            currentView.put(player, target);
                            out.println("OK " + snapshot(g));
                            System.out.println("[VIEW] " + player + " is viewing " + target + "'s farm");
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
                        g.plant(r, c);
                        broadcastUpdate(player, g);
                        out.println("OK " + snapshot(g));
                        System.out.println("[PLANT] " + player + " planted at (" + r + "," + c + ")");
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
                        broadcastUpdate(player, g);
                        out.println("OK " + snapshot(g));
                        System.out.println("[HARVEST] " + player + " harvested at (" + r + "," + c + ")");
                        break;
                    }
                    case "STEAL": {
                        requireLogin();
                        ensure(parts.length == 2, "Usage: STEAL <victim>");
                        String victimName = parts[1];
                        ensure(!victimName.equals(player), "Cannot steal yourself");

                        if (victimName.equals(currentView.get(victimName))) {
                            out.println("ERR Victim at home (cannot steal)");
                            System.out.println("[STEAL] " + player + " failed - " + victimName + " is at home");
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

                        boolean success = stealAtomic(thief, victim);
                        broadcastUpdate(player, thief);
                        broadcastUpdate(victimName, victim);
                        if (success) {
                            out.println("OK " + snapshot(thief));
                            System.out.println("[STEAL] " + player + " successfully stole from " + victimName);
                        } else {
                            out.println("ERR Steal failed (no ripe crop)");
                            System.out.println("[STEAL] " + player + " failed - no ripe crops in " + victimName + "'s farm");
                        }
                        break;
                    }
                    default:
                        out.println("ERR Unknown command: " + cmd);
                }
            } catch (Exception e) {
                out.println("ERR " + e.getMessage());
            }
        }

        private void requireLogin() {
            if (player == null) throw new IllegalStateException("Please LOGIN first.");
        }

        private void ensure(boolean cond, String msg) {
            if (!cond) throw new IllegalArgumentException(msg);
        }

        private int parse(String s) {
            return Integer.parseInt(s);
        }
    }

    private static boolean inBounds(Game g, int r, int c) {
        return r >= 0 && r < g.getRows() && c >= 0 && c < g.getCols();
    }

    private void broadcastUpdate(String playerName, Game g) {
        String line = "UPDATE " + playerName + " " + snapshot(g);
        for (PrintWriter w : allClients) {
            w.println(line);
        }
    }

    private static boolean stealAtomic(Game thief, Game victim) {
        Game first = System.identityHashCode(thief) < System.identityHashCode(victim) ? thief : victim;
        Game second = (first == thief) ? victim : thief;

        synchronized (first) {
            synchronized (second) {
                boolean ok = victim.stealOneRipe();
                if (ok) {
                    thief.addCoins(Game.STEAL_REWARD);
                } else {
                    System.out.println("[STEAL] Atomic steal failed - no crops");
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