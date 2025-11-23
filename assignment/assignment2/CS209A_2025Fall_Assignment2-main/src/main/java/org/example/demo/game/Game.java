package org.example.demo.game;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

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
            System.out.printf("[PLANT][thread=%s][ts=%d] (%d,%d) coins=%d%n",
                    Thread.currentThread().getName(), System.nanoTime(), row, col, coins);
            callback = onStateChange;
        }
        if (callback != null) callback.accept(this);
        scheduleRipen(row, col, GROW_TIME_MS);
    }

    private void scheduleRipen(int row, int col, long delayMs) {
        scheduler.schedule(() -> {
            boolean ripened = false;
            Consumer<Game> cb;
            synchronized (Game.this) {
                if (board[row][col] == PlotState.GROWING) {
                    board[row][col] = PlotState.RIPE;
                    plantTimestamps[row][col] = 0; // 成熟后清空时间戳
                    ripened = true;
                    System.out.printf("[RIPEN][thread=%s][ts=%d] (%d,%d) matured%n",
                            Thread.currentThread().getName(), System.nanoTime(), row, col);
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
            System.out.printf("[HARVEST][thread=%s][ts=%d] (%d,%d) coins=%d%n",
                    Thread.currentThread().getName(), System.nanoTime(), row, col, coins);
            callback = onStateChange;
        }
        if (callback != null) callback.accept(this);
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

}