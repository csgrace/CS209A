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
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final ExecutorService pool = Executors.newCachedThreadPool((Runnable runnable) -> {
        Thread t = new Thread(runnable, "client-io");
        t.setDaemon(true);
        return t;
    });

    // 🔥 新增：统计监控线程池
    private final ScheduledExecutorService statsExecutor = Executors.newScheduledThreadPool(1, (Runnable runnable) -> {
        Thread t = new Thread(runnable, "stats-monitor");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Game> farms = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PrintWriter> allClients = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, String> currentView = new ConcurrentHashMap<>();

    // 🔥 新增：统计信息
    private final ConcurrentHashMap<String, Integer> playerActionStats = new ConcurrentHashMap<>();
    private long serverStartTime;

    // 🔥 新增：偷窃周期控制
    // canStealThisCycle: thief -> (victim -> can start a NEW stealing session?)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> canStealThisCycle = new ConcurrentHashMap<>();
    // sessionStealCounts: thief -> (victim -> times stolen in current session)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> sessionStealCounts = new ConcurrentHashMap<>();

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
                    // 当玩家断开时，若其正在某个偷窃会话中（sessionStealCounts>0），需要把 canStealThisCycle[player][victim] 置为 false
                    ConcurrentHashMap<String, Integer> sessions = sessionStealCounts.get(player);
                    if (sessions != null) {
                        for (var entry : sessions.entrySet()) {
                            String victim = entry.getKey();
                            int cnt = entry.getValue() == null ? 0 : entry.getValue();
                            if (cnt > 0) {
                                canStealThisCycle.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(victim, false);
                            }
                        }
                    }
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
                    playerActionStats.merge(player, 1, (old, nev) -> old + nev);
                }

                switch (cmd) {
                    case "LOGIN": {
                        ensure(parts.length == 2, "Usage: LOGIN <name>");
                        String newPlayer = parts[1];
                        String prev = player;
                        player = newPlayer;
                        farms.computeIfAbsent(player, k -> {
                            Game g = new Game();
                            g.setOnStateChange(game -> broadcastUpdate(k, game));
                            return g;
                        });
                        // 当登录时，把视图设置为自己（并处理从可能的 previous view 离开行为）
                        handleViewChange(player, player);
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
                        // 切换视图到自己——如果之前在看别人并且会话开始过，需要结束会话
                        handleViewChange(player, player);
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
                            // 在改变 currentView 之前，处理离开以前视图的逻辑
                            handleViewChange(player, target);
                            currentView.put(player, target);
                            out.println("OK " + snapshot(g));
                            System.out.println("[VIEW] " + player + " is viewing " + target + "'s farm");

                            // 只有当允许开始新会话（canStealThisCycle == true）并且 target 不在自己的农场时，才把 sessionStealCounts 重置为 0（标志一个新会话可以开始）
                            ConcurrentHashMap<String, Boolean> thiefCanMap = canStealThisCycle.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
                            boolean canStart = thiefCanMap.getOrDefault(target, true);
                            // 如果 victim 在家则不允许偷，也不需要重置会话
                            boolean victimAtHome = target.equals(currentView.get(target));
                            if (canStart && !victimAtHome) {
                                sessionStealCounts.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(target, 0);
                            }
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
                        // 🔥 重置所有小偷的 canStealThisCycle 为 true（owner plant 重置被偷周期）
                        for (String thief : farms.keySet()) {
                            if (!thief.equals(player)) {
                                canStealThisCycle.computeIfAbsent(thief, k -> new ConcurrentHashMap<>()).put(player, true);
                                // 同时重置其会话计数为 0（允许下次进入开始新的会话）
                                sessionStealCounts.computeIfAbsent(thief, k -> new ConcurrentHashMap<>()).put(player, 0);
                            }
                        }
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

                        // victim 在家（自己在看自己的农场）时不能被偷
                        if (victimName.equals(currentView.get(victimName))) {
                            out.println("ERR Victim at home (cannot steal)");
                            System.out.println("[STEAL] " + player + " failed - " + victimName + " is at home");
                            break;
                        }

                        // 要求小偷当前视图必须是 victim（只有在看 victim 的时候才可以发起偷）
                        String myView = currentView.get(player);
                        if (myView == null || !myView.equals(victimName)) {
                            out.println("ERR Must VIEW victim before stealing");
                            System.out.println("[STEAL] " + player + " failed - not viewing victim");
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

                        // 检查是否允许开始/继续当前会话
                        ConcurrentHashMap<String, Boolean> thiefCanSteal = canStealThisCycle.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
                        boolean allowedToStart = thiefCanSteal.getOrDefault(victimName, true);
                        if (!allowedToStart) {
                            out.println("ERR Cannot steal this cycle");
                            System.out.println("[STEAL] " + player + " failed - cannot steal this cycle");
                            break;
                        }

                        int currentRipe = victim.getRipeCount();
                        int maxSteal = (int) (currentRipe * 0.25); // floor
                        // If maxSteal == 0 -> cannot steal at all
                        if (maxSteal <= 0) {
                            out.println("ERR No allowed steals (maxSteal=0)");
                            System.out.println("[STEAL] " + player + " failed - maxSteal 0");
                            break;
                        }

                        System.out.println("[STEAL ATTEMPT] Player: " + player + " | Victim: " + victimName + " | currentRipe: " + currentRipe + " | maxSteal: " + maxSteal);
                        ConcurrentHashMap<String, Integer> thiefSession = sessionStealCounts.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
                        int sessionCount = thiefSession.getOrDefault(victimName, 0);

                        if (sessionCount >= maxSteal) {
                            out.println("ERR Cannot steal more in this session");
                            System.out.println("[STEAL] " + player + " failed - session limit reached, current: " + sessionCount + ", max: " + maxSteal);
                            break;
                        }

                        boolean success = stealAtomic(thief, victim);
                        broadcastUpdate(player, thief);
                        broadcastUpdate(victimName, victim);
                        if (success) {
                            // 增加当前会话计数
                            thiefSession.put(victimName, sessionCount + 1);
                            out.println("OK " + snapshot(thief));
                            System.out.println("[STEAL] " + player + " successfully stole from " + victimName + " | session count: " + (sessionCount + 1));
                            // 注意：不在第一次偷后立即把 canStealThisCycle 设为 false，
                            // 只有当离开视图（handleViewChange）或达到 session limit 才会阻止下次开启
                            if ((sessionCount + 1) >= maxSteal) {
                                // 达到该次会话上限，防止继续偷
                                thiefCanSteal.put(victimName, false);
                                System.out.println("[STEAL] " + player + " reached maxSteal for " + victimName + " in this session");
                            }
                        } else {
                            out.println("ERR No ripe crops to steal");
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

        /**
         * 统一处理“视图切换”引发的会话结束逻辑：
         * - prev = currentView.get(player)
         * - newView = newView
         * 如果 prev != null && !prev.equals(newView) 并且 prev 是某个 victim，
         *   如果 sessionStealCounts[player][prev] > 0 (说明会话已经开始过)，
         *   则把 canStealThisCycle[player][prev] = false —— 直到 victim 下次 PLANT 才能重置为 true。
         * 这个实现保证了：只要小偷在一次会话中离开（即便未达到 maxSteal），就不能再次偷，直到被偷者 PLANT。
         */
        private void handleViewChange(String playerName, String newView) {
            if (playerName == null) return;
            String prev = currentView.get(playerName);
            if (prev == null) {
                // no-op
            } else if (!prev.equals(newView)) {
                // 离开 prev
                if (!prev.equals(playerName)) { // 离开的是别人的农场（否则是回到自己农场）
                    ConcurrentHashMap<String, Integer> sessions = sessionStealCounts.get(playerName);
                    if (sessions != null) {
                        Integer cnt = sessions.getOrDefault(prev, 0);
                        if (cnt != null && cnt > 0) {
                            // 会话已经开始，离开时必须把 canStealThisCycle 置为 false
                            canStealThisCycle.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>()).put(prev, false);
                            System.out.println("[VIEW CHANGE] " + playerName + " left " + prev + "'s farm after stealing session -> block future steals until victim PLANT");
                        }
                        // 无论是否曾偷过，都清理该会话计数（离开视图就不保留会话计数）
                        sessions.remove(prev);
                    }
                }
            }
            // 不在这里 put newView 到 currentView —— 调用者负责写入 currentView
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
