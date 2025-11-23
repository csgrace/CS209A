package org.example.demo.server;
/*
mvn compile exec:java "-Dexec.mainClass=org.example.demo.server.Server"
*/
import org.example.demo.game.Game;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Server {
    private final int port;
    private final ExecutorService pool = Executors.newCachedThreadPool((Runnable runnable) -> {
        Thread t = new Thread(runnable, "client-io");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService statsExecutor = Executors.newScheduledThreadPool(1, (Runnable runnable) -> {
        Thread t = new Thread(runnable, "stats-monitor");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, Game> farms = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<PrintWriter> allClients = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, String> currentView = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> playerActionStats = new ConcurrentHashMap<>();
    private long serverStartTime;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Boolean>> canStealThisCycle = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> sessionStealCounts = new ConcurrentHashMap<>();
    private static final String SAVE_FILE = "farms.txt";

    public Server(int port) {
        this.port = port;
    }

    private static void log(String tag, String msg) {
        long millis = System.currentTimeMillis(); // 当前时间戳（毫秒级）
        // 当前时间格式化为可读的 HH:mm:ss.SSS
        String humanReadableTime = new java.text.SimpleDateFormat("HH:mm:ss.SSS")
                .format(new java.util.Date(millis));
        // 纳秒级时间戳（系统单调时间，用于计算时间间隔）
        long nanoTime = System.nanoTime();
        System.out.printf(
                "[%s][thread=%s][time=%s][nano=%d] %s%n",
                tag, // 标签，例如 "STEAL", "CLIENT"
                Thread.currentThread().getName(), // 当前线程名
                humanReadableTime, // 人类时间
                nanoTime, // 纳秒级别精准时间
                msg // 日志消息内容
        );
    }

    public void start() throws IOException {
        serverStartTime = System.currentTimeMillis();

        loadFarmsFromDisk();

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("========================================");
            System.out.println("Server started on port: " + port);
            System.out.println("Waiting for clients...");
            System.out.println("========================================\n");

            startStatsMonitor();

            while (true) {
                Socket socket = server.accept();
                log("ACCEPT", "New socket accepted from " + socket.getInetAddress());
                pool.submit(new ClientHandler(socket));
            }
        } finally {
            pool.shutdown();
            statsExecutor.shutdown();
        }
    }

    private void startStatsMonitor() {
        statsExecutor.scheduleAtFixedRate(
                this::printServerStats,
                10,
                60,
                TimeUnit.SECONDS
        );
    }

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
        log("STATS", "Reported status: clients=" + allClients.size() + " farms=" + farms.size());
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
                log("CLIENT", "Connection established from " + socket.getInetAddress());

                out.println("OK WELCOME Use: LOGIN <name>|GET|VIEW <player>|PLANT r c|HARVEST r c|STEAL <victim>|PLAYERS");

                String line;
                while ((line = in.readLine()) != null) {
                    handle(line.trim());
                }
            } catch (Exception e) {
                System.out.println("[ERROR] Connection error: " + e.getMessage());
                log("CLIENT-ERROR", "player=" + player + " msg=" + e.getMessage());
            } finally {
                allClients.remove(out);
                if (player != null) {
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
                    log("DISCONNECT", "player=" + player);
                }
            }
        }

        private void handle(String line) {
            try {
                if (line.isEmpty()) return;
                String[] parts = line.split("\\s+");
                String cmd = parts[0].toUpperCase(Locale.ROOT);

                log("HANDLE", "player=" + (player == null ? "(unauth)" : player) + " raw=\"" + line + "\"");

                if (player != null) {
                    playerActionStats.merge(player, 1, (old, nev) -> old + nev);
                }

                switch (cmd) {
                    case "LOGIN": {
                        ensure(parts.length == 2, "Usage: LOGIN <name>");
                        String newPlayer = parts[1];
                        player = newPlayer;
                        farms.computeIfAbsent(player, k -> {
                            Game g = new Game();
                            g.setOnStateChange(game -> broadcastUpdate(k, game));
                            return g;
                        });
                        handleViewChange(player, player);
                        currentView.put(player, player);
                        playerActionStats.putIfAbsent(player, 0);
                        out.println("OK LOGGED_IN " + player);
                        System.out.println("[LOGIN] " + player + " logged in");
                        log("LOGIN", "player=" + player);
                        broadcastUpdate(player, farms.get(player));
                        saveFarmsToDisk();
                        break;
                    }
                    case "PLAYERS": {
                        out.println("OK " + String.join(",", farms.keySet()));
                        log("PLAYERS", "count=" + farms.keySet().size());
                        break;
                    }
                    case "GET": {
                        requireLogin();
                        handleViewChange(player, player);
                        currentView.put(player, player);
                        Game g = farms.get(player);
                        out.println("OK " + snapshot(g));
                        log("GET", "player=" + player);
                        break;
                    }
                    case "VIEW": {
                        requireLogin();
                        ensure(parts.length == 2, "Usage: VIEW <player>");
                        String target = parts[1];
                        Game g = farms.get(target);
                        if (g == null) {
                            out.println("ERR Player not found");
                            log("VIEW-ERR", "player=" + player + " target=" + target + " reason=not-found");
                        } else {
                            handleViewChange(player, target);
                            currentView.put(player, target);
                            out.println("OK " + snapshot(g));
                            System.out.println("[VIEW] " + player + " is viewing " + target + "'s farm");
                            log("VIEW", "viewer=" + player + " target=" + target);

                            ConcurrentHashMap<String, Boolean> thiefCanMap =
                                    canStealThisCycle.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
                            boolean canStart = thiefCanMap.getOrDefault(target, true);
                            boolean victimAtHome = target.equals(currentView.get(target));
                            if (canStart && !victimAtHome) {
                                sessionStealCounts.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(target, 0);
                                log("VIEW-SESSION", "start potential steal session viewer=" + player + " victim=" + target);
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
                        out.println("OK PLANTED at (" + r + "," + c + ") | Snapshot: " + snapshot(g));
                        System.out.println("[PLANT] " + player + " planted at (" + r + "," + c + ")");
                        log("PLANT", "player=" + player + " pos=(" + r + "," + c + ")");
                        for (String thief : farms.keySet()) {
                            if (!thief.equals(player)) {
                                canStealThisCycle.computeIfAbsent(thief, k -> new ConcurrentHashMap<>()).put(player, true);
                                sessionStealCounts.computeIfAbsent(thief, k -> new ConcurrentHashMap<>()).put(player, 0);
                            }
                        }
                        saveFarmsToDisk();
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
                        out.println("OK HARVESTED at (" + r + "," + c + ") | Snapshot: " + snapshot(g));
                        System.out.println("[HARVEST] " + player + " harvested at (" + r + "," + c + ")");
                        log("HARVEST", "player=" + player + " pos=(" + r + "," + c + ")");
                        saveFarmsToDisk();
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
                            log("STEAL-DENY", "thief=" + player + " victim=" + victimName + " reason=victim-at-home");
                            break;
                        }

                        String myView = currentView.get(player);
                        if (myView == null || !myView.equals(victimName)) {
                            out.println("ERR Must VIEW victim before stealing");
                            System.out.println("[STEAL] " + player + " failed - not viewing victim");
                            log("STEAL-DENY", "thief=" + player + " victim=" + victimName + " reason=not-viewing");
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

                        ConcurrentHashMap<String, Boolean> thiefCanSteal =
                                canStealThisCycle.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
                        boolean allowedToStart = thiefCanSteal.getOrDefault(victimName, true);
                        if (!allowedToStart) {
                            out.println("ERR Cannot steal this cycle");
                            System.out.println("[STEAL] " + player + " failed - cannot steal this cycle");
                            log("STEAL-DENY", "thief=" + player + " victim=" + victimName + " reason=cycle-blocked");
                            break;
                        }

                        log("STEAL-ENTER", "thief=" + player + " victim=" + victimName + " preRipe=" + victim.getRipeCount());

                        synchronized (victim) {
                            int currentRipe = victim.getRipeCount();
                            int maxSteal = (int) (currentRipe * 0.25);

                            if (maxSteal <= 0) {
                                out.println("ERR No allowed steals (maxSteal=0)");
                                System.out.println("[STEAL] " + player + " failed - maxSteal 0 (currentRipe=" + currentRipe + ")");
                                log("STEAL-DENY", "thief=" + player + " victim=" + victimName + " currentRipe=" + currentRipe + " maxSteal=0");
                                break;
                            }

                            System.out.println("[STEAL ATTEMPT] Player: " + player + " | Victim: " + victimName
                                    + " | currentRipe: " + currentRipe + " | maxSteal: " + maxSteal);
                            log("STEAL-CHECK", "thief=" + player + " victim=" + victimName + " currentRipe=" + currentRipe + " maxSteal=" + maxSteal);

                            ConcurrentHashMap<String, Integer> thiefSession =
                                    sessionStealCounts.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
                            int sessionCount = thiefSession.getOrDefault(victimName, 0);

                            if (sessionCount >= maxSteal) {
                                out.println("ERR Cannot steal more in this session");
                                System.out.println("[STEAL] " + player + " failed - session limit reached, current: "
                                        + sessionCount + ", max: " + maxSteal);
                                log("STEAL-DENY", "thief=" + player + " victim=" + victimName + " reason=session-limit sessionCount=" + sessionCount);
                                break;
                            }

                            boolean success = victim.stealOneRipe();
                            if (success) {
                                thief.addCoins(Game.STEAL_REWARD);
                                thiefSession.put(victimName, sessionCount + 1);
                                out.println("OK " + snapshot(thief));
                                System.out.println("[STEAL] " + player + " successfully stole from " + victimName
                                        + " | session count: " + (sessionCount + 1));
                                log("STEAL-SUCCESS", "thief=" + player + " victim=" + victimName + " newSessionCount=" + (sessionCount + 1) + " victimRipeNow=" + victim.getRipeCount());
                                if ((sessionCount + 1) >= maxSteal) {
                                    thiefCanSteal.put(victimName, false);
                                    System.out.println("[STEAL] " + player + " reached maxSteal for " + victimName + " in this session");
                                    log("STEAL-LIMIT", "thief=" + player + " victim=" + victimName + " reached maxSteal");
                                }
                            } else {
                                out.println("ERR No ripe crops to steal");
                                System.out.println("[STEAL] " + player + " failed - no ripe crops in " + victimName + "'s farm");
                                log("STEAL-FAIL", "thief=" + player + " victim=" + victimName + " reason=no-ripe");
                            }
                        }

                        log("STEAL-EXIT", "thief=" + player + " victim=" + victimName + " postRipe=" + farms.get(victimName).getRipeCount());
                        broadcastUpdate(player, farms.get(player));
                        broadcastUpdate(victimName, farms.get(victimName));
                        saveFarmsToDisk();
                        break;
                    }
                    default:
                        out.println("ERR Unknown command: " + cmd);
                        log("HANDLE-ERR", "player=" + player + " cmd=" + cmd + " reason=unknown");
                }

            } catch (Exception e) {
                out.println("ERR " + e.getMessage());
                log("HANDLE-EX", "player=" + player + " error=" + e.getMessage());
            }
        }

        private void handleViewChange(String playerName, String newView) {
            if (playerName == null) return;
            String prev = currentView.get(playerName);
            if (prev == null) {
                // no-op
            } else if (!prev.equals(newView)) {
                if (!prev.equals(playerName)) {
                    ConcurrentHashMap<String, Integer> sessions = sessionStealCounts.get(playerName);
                    if (sessions != null) {
                        Integer cnt = sessions.getOrDefault(prev, 0);
                        if (cnt != null && cnt > 0) {
                            canStealThisCycle.computeIfAbsent(playerName, k -> new ConcurrentHashMap<>()).put(prev, false);
                            System.out.println("[VIEW CHANGE] " + playerName + " left " + prev + "'s farm after stealing session -> block future steals until victim PLANT");
                            log("VIEW-CHANGE", "player=" + playerName + " leaving=" + prev + " stoleCnt=" + cnt);
                        }
                        sessions.remove(prev);
                    }
                }
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
        log("UPDATE-BROADCAST", "player=" + playerName + " sent to clients=" + allClients.size());
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


    private synchronized void saveFarmsToDisk() {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(SAVE_FILE), StandardCharsets.UTF_8))) {
            for (var entry : farms.entrySet()) {
                String name = entry.getKey();
                Game g = entry.getValue();
                pw.println(name + " " + g.toSaveString());
            }
            pw.flush();
            System.out.println("[PERSIST] Farms saved to " + SAVE_FILE);
            log("PERSIST", "saved file=" + SAVE_FILE + " farms=" + farms.size());
        } catch (IOException ex) {
            System.out.println("[PERSIST] Save failed: " + ex.getMessage());
            log("PERSIST-ERR", "error=" + ex.getMessage());
        }
    }

    private synchronized void loadFarmsFromDisk() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) {
            System.out.println("[PERSIST] No existing save file, starting with empty farms.");
            log("PERSIST-LOAD", "no-file-start-empty");
            return;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int spaceIdx = line.indexOf(' ');
                if (spaceIdx < 0) continue;
                String name = line.substring(0, spaceIdx);
                String saveData = line.substring(spaceIdx + 1);

                Game g = new Game();
                g.setOnStateChange(game -> broadcastUpdate(name, game));
                g.fromSaveString(saveData);
                farms.put(name, g);
            }
            System.out.println("[PERSIST] Farms loaded from " + SAVE_FILE + " farms.keySet())");
            log("PERSIST-LOAD", "loaded file=" + SAVE_FILE + " farms=" + farms.size());
        } catch (Exception ex) {
            System.out.println("[PERSIST] Load failed: " + ex.getMessage());
            log("PERSIST-ERR", "load-failed error=" + ex.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        new Server(5050).start();
    }
}