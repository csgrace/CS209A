package org.example.demo.game;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal game logic to demonstrate multithreading and synchronization.
 * Updated: Move onStateChange callbacks out of synchronized blocks to reduce lock hold time.
 */
public class Game {

    public enum PlotState {EMPTY, GROWING, RIPE}

    private static final int ROWS = 4;
    private static final int COLS = 4;
    private static final int PLANT_COST = 5;
    private static final int HARVEST_REWARD = 12;
    public static final int STEAL_REWARD = 3; // (≤25% 收获奖励 12)

    private final PlotState[][] board = new PlotState[ROWS][COLS];
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "crop-growth");
        thread.setDaemon(true);
        return thread;
    });
    private final Random random = new Random();
    private Consumer<Game> onStateChange; // 回调
    private int coins = 40;

    public Game() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = PlotState.EMPTY;
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

    /**
     * Plant a crop and schedule ripening.
     * Callback moved outside synchronized lock.
     */
    public void plant(int row, int col) {
        Consumer<Game> callback;
        synchronized (this) {
            if (board[row][col] != PlotState.EMPTY) {
                throw new IllegalStateException("Plot occupied");
            }
            if (coins < PLANT_COST) {
                throw new IllegalStateException("Not enough coins");
            }
            coins -= PLANT_COST;
            board[row][col] = PlotState.GROWING;
            System.out.println(Thread.currentThread().getName() + " PLANT (" + row + "," + col + ")");
            callback = onStateChange;
        }
        // 广播在锁外
        if (callback != null) callback.accept(this);

        // 成熟任务：内部修改后再在锁外调用回调
        scheduler.schedule(() -> {
            boolean ripened = false;
            Consumer<Game> cb;
            synchronized (Game.this) {
                if (board[row][col] == PlotState.GROWING) {
                    board[row][col] = PlotState.RIPE;
                    ripened = true;
                    System.out.println(Thread.currentThread().getName() + " RIPEN (" + row + "," + col + ")");
                }
                cb = onStateChange;
            }
            if (ripened && cb != null) cb.accept(Game.this);
        }, 10, TimeUnit.SECONDS);
    }

    /**
     * Harvest a ripe crop.
     * Callback moved outside synchronized lock.
     */
    public void harvest(int row, int col) {
        Consumer<Game> callback;
        synchronized (this) {
            if (board[row][col] != PlotState.RIPE) {
                throw new IllegalStateException("Crop not ripe");
            }
            board[row][col] = PlotState.EMPTY;
            coins += HARVEST_REWARD;
            System.out.println(Thread.currentThread().getName() + " HARVEST (" + row + "," + col + ")");
            callback = onStateChange;
        }
        if (callback != null) callback.accept(this);
    }

    /**
     * Local random steal (client-side simulation).
     * Callback moved outside synchronized lock.
     */
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

    /**
     * Steal one ripe crop (internal, no callback).
     */
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

    /**
     * Update game from snapshot JSON (best-effort, silent on errors).
     */
    public synchronized void updateFromSnapshot(String json) {
        try {
            int coinsIdx = json.indexOf("\"coins\":");
            if (coinsIdx >= 0) {
                int comma = json.indexOf(",", coinsIdx);
                String coinsStr = json.substring(coinsIdx + 8, comma).trim();
                this.coins = Integer.parseInt(coinsStr);
            }
            int boardStart = json.indexOf("\"board\":[");
            if (boardStart >= 0) {
                int arrStart = json.indexOf("[", boardStart + 8);
                int arrEnd = json.lastIndexOf("]");
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
                        board[r][c] = PlotState.valueOf(cell);
                    }
                }
            }
        } catch (Exception ignored) {
            // 保留旧状态
        }
    }

    private String[] splitTopLevel(String s) {
        return s.split("\\s*,\\s*");
    }
}