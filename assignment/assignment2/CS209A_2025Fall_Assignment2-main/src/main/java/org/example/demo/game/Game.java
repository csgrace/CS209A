package org.example.demo.game;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal game logic to demonstrate multithreading and synchronization.
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
    private Consumer<Game> onStateChange; // 新增字段
    private int coins = 40;


    public Game() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                board[r][c] = PlotState.EMPTY;
            }
        }
    }
    public synchronized void setOnStateChange(Consumer<Game> listener) {
        this.onStateChange = listener; // [Task 2.2 Networking Push] 设置成熟回调
    }
    public synchronized int getCoins() {
        return coins;
    }
    public synchronized PlotState getState(int row, int col) {
        return board[row][col];
    }
    public synchronized void plant(int row, int col) {
        if (board[row][col] != PlotState.EMPTY) {
            throw new IllegalStateException("Plot occupied");
        }
        if (coins < PLANT_COST) {
            throw new IllegalStateException("Not enough coins");
        }
        coins -= PLANT_COST;
        board[row][col] = PlotState.GROWING;
        System.out.println(Thread.currentThread().getName() + " PLANT (" + row + "," + col + ")"); // [Task 2.4 Concurrency Log]
        // Simulate growth finishing after 5 seconds
        scheduler.schedule(() -> {
            synchronized (Game.this) {
                if (board[row][col] == PlotState.GROWING) {
                    board[row][col] = PlotState.RIPE;
                    System.out.println(Thread.currentThread().getName() + " RIPEN (" + row + "," + col + ")"); // [Task 2.4 Concurrency Log]
                    if (onStateChange != null) onStateChange.accept(Game.this); // [Task 2.2 Push Update on maturation]
                }
            }
        }, 5, TimeUnit.SECONDS); // e.g., mature in 10 seconds)
        if (onStateChange != null) onStateChange.accept(this); // 立即广播该玩家状态（显示 GROWING）[Task 2.2]
    }

    public synchronized void harvest(int row, int col) {
        if (board[row][col] != PlotState.RIPE) {
            throw new IllegalStateException("Crop not ripe");
        }
        board[row][col] = PlotState.EMPTY;
        coins += HARVEST_REWARD;
        System.out.println(Thread.currentThread().getName() + " HARVEST (" + row + "," + col + ")"); // [Task 2.4 Concurrency Log]
        if (onStateChange != null) onStateChange.accept(this); // [Task 2.2 Push Update harvest]
    }

    public synchronized void stealRandom() {
        if (stealOneRipe()) {
            coins = Math.max(0, coins - STEAL_REWARD);
            System.out.println(Thread.currentThread().getName() + " LOCAL STEAL_RANDOM"); // [Task 2.4 Concurrency Log]
            if (onStateChange != null) onStateChange.accept(this); // [Task 2.2 Push Update local steal]
        }
    }

    public synchronized void addCoins(int delta) {
        this.coins = Math.max(0, this.coins + delta); // 保底不小于 0
    }
    public synchronized boolean stealOneRipe() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (board[r][c] == PlotState.RIPE) {
                    board[r][c] = PlotState.EMPTY; // 清空一个成熟格子，表示产量被偷
                    return true;
                }
            }
        }
        return false; // 没有成熟作物可偷
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
    // 在Game.java中添加这两个方法
    public synchronized void setCoins(int coins) {
        this.coins = coins;
    }

    public synchronized void setState(int row, int col, PlotState state) {
        if (row >= 0 && row < ROWS && col >= 0 && col < COLS) {
            this.board[row][col] = state;
        }
    }
    // 任务4：客户端解析服务器快照更新本地镜像
    // 简易手写解析（不依赖第三方库），假设格式固定 {"coins":X,"board":[["EMPTY","GROWING",...], ...]}
    // 客户端快照更新 —— [Task 2.2 Networking & Updates]
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
            // [Task 2.5 Exception Handling] 出错时保留旧状态，避免崩溃
        }
    }

    // 简易分割：按逗号分，但不拆内部嵌套（这里结构简单可行）
    private String[] splitTopLevel(String s) {
        return s.split("\\s*,\\s*");
    }
}
