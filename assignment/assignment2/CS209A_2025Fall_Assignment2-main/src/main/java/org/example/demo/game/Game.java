package org.example.demo.game;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/**
 * Game logic with timestamp-based persistence for GROWING crops.
 * When server restarts, GROWING crops will resume their countdown based on elapsed time.
 */
public class Game {

    public enum PlotState {EMPTY, GROWING, RIPE}

    private static final int ROWS = 4;
    private static final int COLS = 4;
    private static final int PLANT_COST = 5;
    private static final int HARVEST_REWARD = 12;
    public static final int STEAL_REWARD = 3;
    private static final long GROW_TIME_MS = 10_000; // 10 seconds

    private final PlotState[][] board = new PlotState[ROWS][COLS];
    private final long[][] plantTimestamps = new long[ROWS][COLS]; // 种植时间戳（毫秒）
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "crop-growth");
        thread.setDaemon(true);
        return thread;
    });
    private final Random random = new Random();
    private Consumer<Game> onStateChange;
    private int coins = 40;

    public Game() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = PlotState.EMPTY;
                plantTimestamps[r][c] = 0;
            }
        }
    }

    public synchronized void setOnStateChange(Consumer<Game> listener) {
        this.onStateChange = listener;
    }

    public synchronized int getCoins() {
        return coins;
    }

    public synchronized PlotState getState(int row, int col) {
        return board[row][col];
    }

    public void plant(int row, int col) {
        Consumer<Game> callback;
        long plantTime = System.currentTimeMillis();
        synchronized (this) {
            if (board[row][col] != PlotState.EMPTY) {
                throw new IllegalStateException("Plot occupied");
            }
            if (coins < PLANT_COST) {
                throw new IllegalStateException("Not enough coins");
            }
            coins -= PLANT_COST;
            board[row][col] = PlotState.GROWING;
            plantTimestamps[row][col] = plantTime;
            System.out.println(Thread.currentThread().getName() + " PLANT (" + row + "," + col + ") at " + plantTime);
            callback = onStateChange;
        }
        if (callback != null) callback.accept(this);

        // 10秒后成熟
        scheduleRipen(row, col, GROW_TIME_MS);
    }

    /**
     * 启动定时任务让作物在 delayMs 后成熟。
     */
    private void scheduleRipen(int row, int col, long delayMs) {
        scheduler.schedule(() -> {
            boolean ripened = false;
            Consumer<Game> cb;
            synchronized (Game.this) {
                if (board[row][col] == PlotState.GROWING) {
                    board[row][col] = PlotState.RIPE;
                    plantTimestamps[row][col] = 0; // 成熟后清空时间戳
                    ripened = true;
                    System.out.println(Thread.currentThread().getName() + " RIPEN (" + row + "," + col + ")");
                }
                cb = onStateChange;
            }
            if (ripened && cb != null) cb.accept(Game.this);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public void harvest(int row, int col) {
        Consumer<Game> callback;
        synchronized (this) {
            if (board[row][col] != PlotState.RIPE) {
                throw new IllegalStateException("Crop not ripe");
            }
            board[row][col] = PlotState.EMPTY;
            plantTimestamps[row][col] = 0;
            coins += HARVEST_REWARD;
            System.out.println(Thread.currentThread().getName() + " HARVEST (" + row + "," + col + ")");
            callback = onStateChange;
        }
        if (callback != null) callback.accept(this);
    }

    public void stealRandom() {
        boolean success;
        Consumer<Game> callback;
        synchronized (this) {
            success = stealOneRipe();
            if (success) {
                coins += STEAL_REWARD;
                System.out.println(Thread.currentThread().getName() + " LOCAL STEAL");
            }
            callback = onStateChange;
        }
        if (success && callback != null) callback.accept(this);
    }

    public synchronized void addCoins(int delta) {
        this.coins = Math.max(0, this.coins + delta);
    }

    public synchronized boolean stealOneRipe() {
        List<int[]> ripePositions = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == PlotState.RIPE) {
                    ripePositions.add(new int[]{r, c});
                }
            }
        }
        if (ripePositions.isEmpty()) {
            return false;
        }
        int[] pos = ripePositions.get(random.nextInt(ripePositions.size()));
        board[pos[0]][pos[1]] = PlotState.EMPTY;
        plantTimestamps[pos[0]][pos[1]] = 0;
        return true;
    }

    public synchronized int getRipeCount() {
        int count = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == PlotState.RIPE) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getRows() {
        return ROWS;
    }

    public int getCols() {
        return COLS;
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    public synchronized void setCoins(int coins) {
        this.coins = coins;
    }

    public synchronized void setState(int row, int col, PlotState state) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            this.board[row][col] = state;
        }
    }

    // ========== 持久化方法（带时间戳） ==========

    /**
     * 生成持久化字符串：
     * coins|state0,timestamp0,state1,timestamp1,...,state15,timestamp15
     * 每个格子保存"状态名,时间戳"对。
     */
    public synchronized String toSaveString() {
        StringBuilder sb = new StringBuilder();
        sb.append(coins).append("|");
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                sb.append(board[r][c].name()).append(",").append(plantTimestamps[r][c]);
                if (r < ROWS - 1 || c < COLS - 1) {
                    sb.append(",");
                }
            }
        }
        return sb.toString();
    }

    /**
     * 从持久化字符串恢复，并对 GROWING 格子根据时间戳恢复定时任务。
     */
    public synchronized void fromSaveString(String saveStr) {
        if (saveStr == null || saveStr.isEmpty()) return;
        try {
            String[] parts = saveStr.split("\\|");
            if (parts.length < 2) return;

            this.coins = Integer.parseInt(parts[0].trim());
            String[] tokens = parts[1].split(",");

            long now = System.currentTimeMillis();
            int idx = 0;
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (idx + 1 < tokens.length) {
                        String stateName = tokens[idx].trim();
                        long timestamp = Long.parseLong(tokens[idx + 1].trim());
                        idx += 2;

                        PlotState state = PlotState.valueOf(stateName);
                        board[r][c] = state;
                        plantTimestamps[r][c] = timestamp;

                        // 如果是 GROWING，检查是否应该已经成熟 or 继续倒计时
                        if (state == PlotState.GROWING && timestamp > 0) {
                            long elapsed = now - timestamp;
                            if (elapsed >= GROW_TIME_MS) {
                                // 已经超过 10 秒，直接成熟
                                board[r][c] = PlotState.RIPE;
                                plantTimestamps[r][c] = 0;
                                System.out.println("[Game.fromSaveString] Plot (" + r + "," + c + ") was GROWING -> now RIPE (elapsed " + elapsed + "ms)");
                            } else {
                                // 还没到 10 秒，继续倒计时剩余时间
                                long remaining = GROW_TIME_MS - elapsed;
                                System.out.println("[Game.fromSaveString] Plot (" + r + "," + c + ") is GROWING, resuming countdown: " + remaining + "ms remaining");
                                scheduleRipen(r, c, remaining);
                            }
                        }
                    }
                }
            }
            System.out.println("[Game.fromSaveString] Loaded: coins=" + coins + ", board states & timestamps restored.");
        } catch (Exception ex) {
            System.out.println("[Game.fromSaveString] Parse failed: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ========== 网络协议用 JSON 解析（用于 UPDATE 消息） ==========

    /**
     * Update game from snapshot JSON (for network protocol).
     * Expected format: {"coins":X,"board":[["EMPTY","RIPE",...],...]]}
     * 注意：这个方法不处理时间戳，只处理实时状态同步。
     */
    public synchronized void updateFromSnapshot(String json) {
        try {
            if (json == null || json.isEmpty()) return;

            int coinsIdx = json.indexOf("\"coins\":");
            if (coinsIdx >= 0) {
                int comma = json.indexOf(",", coinsIdx);
                if (comma < 0) comma = json.indexOf("}", coinsIdx);
                if (comma > coinsIdx) {
                    String coinsStr = json.substring(coinsIdx + 8, comma).trim();
                    this.coins = Integer.parseInt(coinsStr);
                }
            }

            int boardStart = json.indexOf("\"board\":[");
            if (boardStart >= 0) {
                int arrStart = json.indexOf("[", boardStart + 8);
                int arrEnd = json.lastIndexOf("]");
                if (arrStart >= 0 && arrEnd > arrStart) {
                    String boardContent = json.substring(arrStart, arrEnd + 1);
                    String rowsStr = boardContent.substring(1, boardContent.length() - 1);
                    String[] rowParts = splitTopLevel(rowsStr);
                    for (int r = 0; r < rowParts.length && r < ROWS; r++) {
                        String row = rowParts[r].trim();
                        if (row.startsWith("[")) row = row.substring(1);
                        if (row.endsWith("]")) row = row.substring(0, row.length() - 1);
                        String[] cells = splitTopLevel(row);
                        for (int c = 0; c < cells.length && c < COLS; c++) {
                            String cell = cells[c].replace("\"", "").trim();
                            PlotState newState = PlotState.valueOf(cell);
                            // 如果状态变了（例如从 GROWING 变成 RIPE），更新并清空时间戳
                            if (board[r][c] != newState) {
                                board[r][c] = newState;
                                if (newState == PlotState.RIPE || newState == PlotState.EMPTY) {
                                    plantTimestamps[r][c] = 0;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("[Game.updateFromSnapshot] Failed: " + ex.getMessage());
        }
    }

    private String[] splitTopLevel(String s) {
        return s.split("\\s*,\\s*");
    }
}