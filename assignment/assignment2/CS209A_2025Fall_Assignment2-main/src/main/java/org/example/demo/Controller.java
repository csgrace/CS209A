package org.example.demo;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.Duration;
import java.io.*;
import java.net.Socket;
import org.example.demo.game.Game;

/**
 * Minimal JavaFX controller that mirrors last year's style while showing the new mechanics.
 * 2.2 Client & GUI Implementation - Core GUI Layout, Action Controls, Visual Feedback, Networking & Updates, Responsiveness
 */
public class Controller {

    // UI Components - 2.2 Core GUI Layout
    private GridPane gameBoard;
    private Label coinsLabel;
    private Label statusLabel;
    private Button plantButton;
    private Button harvestButton;
    private Button stealButton;
    private TextField friendField;
    private Button visitButton;
    private Button backButton;

    private Game game;
    private ToggleButton[][] cells;
    private Timeline refreshTimeline;
    private String statusMessage = "Ready.";

    private int selectedRow = -1;
    private int selectedCol = -1;

    // 任务4：网络 - 2.2 Networking & Updates
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenThread;
    // 在字段声明处直接初始化，避免null问题
    private String myPlayerName = "alice"; // 默认值
    private String viewingPlayerName = "alice"; // 默认值，避免null

    public void init(Game game) {
        this.game = game;
        // 确保viewingPlayerName有初始值
        if (this.viewingPlayerName == null) {
            this.viewingPlayerName = "alice";
        }
        if (this.myPlayerName == null) {
            this.myPlayerName = "alice";
        }
        // createBoard will be called after UI creation
        startRefreshTicker(); // 2.2 Responsiveness - keep UI interactive
    }

    /**
     * 2.2 Core GUI Layout - Create complete JavaFX UI without FXML
     * Display 4x4 grid, player info, and all required controls
     */
    public Parent createUI() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #F0F8FF;");

        // Header with player info - 2.2 Core GUI Layout
        VBox headerBox = new VBox(5);
        headerBox.setAlignment(Pos.CENTER);

        coinsLabel = new Label("Player: alice | Coins: 40 | Ready");
        coinsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2E8B57;");

        statusLabel = new Label("🌱 Welcome to QQ Farm! Select a plot and choose an action.");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #4169E1;");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(600);

        headerBox.getChildren().addAll(coinsLabel, statusLabel);

        // 🔥 1. 先创建所有按钮，避免null问题
        createAllButtons();

        // 2.2 Action Controls - Visit Friends functionality
        HBox friendBox = new HBox(10);
        friendBox.setAlignment(Pos.CENTER);
        friendBox.setPadding(new Insets(10));
        friendBox.setStyle("-fx-border-color: #DDA0DD; -fx-border-width: 2px; -fx-border-radius: 5px; -fx-background-color: #F8F8FF;");

        Label friendLabel = new Label("👥 Visit Friends:");
        friendLabel.setStyle("-fx-font-weight: bold;");

        friendField = new TextField();
        friendField.setPromptText("Enter friend's name (try 'bob')");
        friendField.setPrefWidth(150);
        friendField.setStyle("-fx-border-radius: 5px;");

        friendBox.getChildren().addAll(friendLabel, friendField, visitButton, backButton);

        // 2.2 Core GUI Layout - 4x4 grid display with clear visuals
        Label gridLabel = new Label("🚜 Your Farm (4x4 Grid)");
        gridLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #8B4513;");

        gameBoard = new GridPane();
        gameBoard.setHgap(3);
        gameBoard.setVgap(3);
        gameBoard.setAlignment(Pos.CENTER);
        gameBoard.setStyle("-fx-border-color: #8B4513; -fx-border-width: 3px; -fx-background-color: #F5DEB3; -fx-padding: 10px;");

        // 🔥 2. 现在安全调用 createBoard，因为按钮已经创建了
        createBoard(); // Initialize the 4x4 grid

        // 2.2 Action Controls - Plant, Harvest, Visit Friends, Steal with enable/disable
        HBox actionBox = new HBox(15);
        actionBox.setAlignment(Pos.CENTER);
        actionBox.setPadding(new Insets(10));
        actionBox.setStyle("-fx-border-color: #CD853F; -fx-border-width: 2px; -fx-border-radius: 5px; -fx-background-color: #FFF8DC;");

        actionBox.getChildren().addAll(plantButton, harvestButton, stealButton);

        // Instructions
        Label instructions = new Label("💡 Click a plot to select it, then choose an action. Visit friends to see their farms!");
        instructions.setStyle("-fx-font-size: 12px; -fx-text-fill: #696969; -fx-font-style: italic;");
        instructions.setWrapText(true);
        instructions.setMaxWidth(600);
        instructions.setAlignment(Pos.CENTER);

        // 组装根容器
        root.getChildren().addAll(headerBox, friendBox, gridLabel, gameBoard, actionBox, instructions);

        return root;
    }

    // 🔥 新增方法：统一创建所有按钮
    private void createAllButtons() {
        // Visit buttons
        visitButton = new Button("🚀 Visit");
        visitButton.setOnAction(e -> handleVisit());
        visitButton.setStyle("-fx-background-color: #87CEEB; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5px;");

        backButton = new Button("🏠 Back Home");
        backButton.setOnAction(e -> handleBack());
        backButton.setStyle("-fx-background-color: #98FB98; -fx-text-fill: #2F4F4F; -fx-font-weight: bold; -fx-border-radius: 5px;");

        // Action buttons
        plantButton = new Button("🌱 Plant (Cost: 5 coins)");
        plantButton.setOnAction(e -> handlePlant());
        plantButton.setStyle("-fx-background-color: #90EE90; -fx-text-fill: #006400; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-radius: 5px;");
        plantButton.setTooltip(new Tooltip("Plant a crop - costs 5 coins, grows automatically"));

        harvestButton = new Button("🌾 Harvest (+12 coins)");
        harvestButton.setOnAction(e -> handleHarvest());
        harvestButton.setStyle("-fx-background-color: #FFD700; -fx-text-fill: #B8860B; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-radius: 5px;");
        harvestButton.setTooltip(new Tooltip("Harvest ripe crops - gain 12 coins per crop"));

        stealButton = new Button("🏴‍☠️ Steal (+3 coins)");
        stealButton.setOnAction(e -> handleSteal());
        stealButton.setStyle("-fx-background-color: #FF6B6B; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-radius: 5px;");
        stealButton.setTooltip(new Tooltip("Steal from friend's farm when they're away"));
    }

    // 任务4：登录 + 建立网络连接 - 2.2 Networking & Updates
    public void connectAndLogin(String playerName) {
        try {
            // 🔥 确保在网络连接前就设置好名字
            this.myPlayerName = playerName;
            this.viewingPlayerName = playerName;

            socket = new Socket("127.0.0.1", 5050);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            // 启动异步读线程 - 2.2 Responsiveness
            listenThread = new Thread(this::listenLoop, "server-listener");
            listenThread.setDaemon(true);
            listenThread.start();

            out.println("LOGIN " + playerName); // 发送登录
            showStatus("🔄 Connecting to server...");
        } catch (IOException e) {
            showStatus("❌ Connect failed: " + e.getMessage());
            disableAllActions(); // 2.5 Exception Handling - graceful degradation
        }
    }

    // 2.2 Networking & Updates - listen for server push notifications, use Platform.runLater()
    private void listenLoop() {
        String line;
        try {
            while ((line = in.readLine()) != null) {
                final String msg = line;
                System.out.println("🔄 Received from server: " + msg); // 2.4 Concurrency logging

                if (msg.startsWith("UPDATE ")) {
                    // 格式：UPDATE <player> <json>
                    int firstSpace = msg.indexOf(' ');
                    int secondSpace = msg.indexOf(' ', firstSpace + 1);
                    if (secondSpace > 0) {
                        String player = msg.substring(firstSpace + 1, secondSpace);
                        String json = msg.substring(secondSpace + 1);

                        // 🔥 修复：只有正在查看的玩家才更新UI
                        if (player.equals(viewingPlayerName)) {
                            Platform.runLater(() -> {
                                updateGameFromSnapshot(json);
                                refreshBoardFromGameState();
                                System.out.println("✅ Updated UI for: " + player);
                            });
                        }

                        // 2.2 Visual Feedback - show toast/status messages
                        Platform.runLater(() -> {
                            if (player.equals(myPlayerName)) {
                                showStatus("✅ Your farm updated!");
                            } else {
                                showStatus("📢 Friend " + player + " updated");
                            }
                        });
                    }
                } else if (msg.startsWith("OK ") || msg.startsWith("ERR ")) {
                    // 2.2 Visual Feedback - show success/failure messages
                    Platform.runLater(() -> {
                        if (msg.startsWith("OK LOGGED_IN")) {
                            showStatus("✅ Successfully connected to farm server!");
                        } else if (msg.startsWith("ERR")) {
                            showStatus("❌ " + msg.substring(4));
                        } else {
                            showStatus("ℹ️ " + msg.substring(3));
                        }
                    });

                    // 🔥 修复：处理包含JSON数据的OK响应
                    if (msg.startsWith("OK {")) {
                        String json = msg.substring(3);
                        Platform.runLater(() -> {
                            updateGameFromSnapshot(json);
                            refreshBoardFromGameState();
                            System.out.println("✅ Direct snapshot updated");
                        });
                    }
                }
            }
        } catch (IOException e) {
            Platform.runLater(() -> {
                showStatus("❌ Disconnected from server"); // 2.5 Exception Handling
                disableAllActions();
            });
        }
    }

    // 🔥 新方法：专门用于更新游戏状态（修复JSON解析）
    private void updateGameFromSnapshot(String json) {
        try {
            System.out.println("📊 Parsing JSON: " + json);

            // 解析coins
            int coinsIdx = json.indexOf("\"coins\":");
            if (coinsIdx >= 0) {
                int colon = coinsIdx + 8;
                int comma = json.indexOf(",", colon);
                if (comma == -1) comma = json.indexOf("}", colon);

                if (comma > colon) {
                    String coinsStr = json.substring(colon, comma).trim();
                    int newCoins = Integer.parseInt(coinsStr);
                    game.setCoins(newCoins); // 需要添加这个方法到Game类
                    System.out.println("💰 Updated coins: " + newCoins);
                }
            }

            // 解析board
            int boardStart = json.indexOf("\"board\":[");
            if (boardStart >= 0) {
                int arrStart = json.indexOf("[", boardStart + 8);
                int arrEnd = json.lastIndexOf("]]") + 1;

                if (arrStart > 0 && arrEnd > arrStart) {
                    String boardContent = json.substring(arrStart, arrEnd + 1);
                    System.out.println("🎯 Board content: " + boardContent);

                    // 更精确的JSON解析
                    parseBoardData(boardContent);
                }
            }

        } catch (Exception e) {
            System.err.println("❌ JSON parsing error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 🔥 新方法：更精确的解析农场数据
    private void parseBoardData(String boardJson) {
        try {
            // 移除外层方括号
            String content = boardJson.substring(1, boardJson.length() - 1);

            // 分割行
            String[] rows = splitRows(content);

            for (int r = 0; r < rows.length && r < game.getRows(); r++) {
                String row = rows[r].trim();
                // 移除行的方括号
                if (row.startsWith("[")) row = row.substring(1);
                if (row.endsWith("]")) row = row.substring(0, row.length() - 1);

                // 分割单元格
                String[] cells = row.split(",");

                for (int c = 0; c < cells.length && c < game.getCols(); c++) {
                    String cell = cells[c].trim().replace("\"", "");
                    try {
                        Game.PlotState state = Game.PlotState.valueOf(cell);
                        game.setState(r, c, state); // 需要添加这个方法到Game类
                        System.out.println("🎲 Set (" + r + "," + c + ") = " + state);
                    } catch (IllegalArgumentException e) {
                        System.err.println("⚠️ Invalid state: " + cell);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Board parsing error: " + e.getMessage());
        }
    }

    // 辅助方法：分割JSON数组的行
    private String[] splitRows(String content) {
        // 简单的状态机解析器，处理嵌套的方括号
        java.util.List<String> rows = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '[') {
                depth++;
                current.append(c);
            } else if (c == ']') {
                depth--;
                current.append(c);
                if (depth == 0) {
                    rows.add(current.toString());
                    current = new StringBuilder();
                    // 跳过逗号
                    if (i + 1 < content.length() && content.charAt(i + 1) == ',') {
                        i++;
                    }
                }
            } else if (c == ',' && depth == 0) {
                // 顶级逗号，分割行
                if (current.length() > 0) {
                    rows.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            rows.add(current.toString());
        }

        return rows.toArray(new String[0]);
    }

    // 🔥 新方法：专门刷新界面显示
    private void refreshBoardFromGameState() {
        if (cells == null || game == null) {
            return;
        }

        System.out.println("🎨 Refreshing board from game state");

        for (int row = 0; row < game.getRows(); row++) {
            for (int col = 0; col < game.getCols(); col++) {
                ToggleButton cell = cells[row][col];
                if (cell != null) {
                    boolean isSelected = (row == selectedRow && col == selectedCol);
                    cell.setSelected(isSelected);
                    updateCellState(cell, row, col, isSelected);
                }
            }
        }

        updateCoins(statusMessage);
        updateButtonStates();
    }

    // 2.2 Core GUI Layout - Display 4x4 grid of plots with clear visuals for empty/growing/ripe states
    private void createBoard() {
        if (gameBoard == null) return;

        gameBoard.getChildren().clear();
        cells = new ToggleButton[game.getRows()][game.getCols()];

        for (int row = 0; row < game.getRows(); row++) {
            for (int col = 0; col < game.getCols(); col++) {
                ToggleButton cell = new ToggleButton();
                cell.setPrefSize(80, 80);
                cell.setMinSize(80, 80);
                cell.setMaxSize(80, 80);

                // 🔥 初始样式设置
                cell.setStyle("-fx-font-size: 11px; -fx-border-width: 2px; -fx-background-radius: 5px; -fx-border-radius: 5px;");

                // 2.2 Visual Feedback - Tooltip for plot information
                Tooltip tooltip = new Tooltip("Plot (" + row + "," + col + ") - Click to select");
                cell.setTooltip(tooltip);

                int r = row;
                int c = col;
                cell.setOnAction(event -> {
                    System.out.println("🖱️ Clicked plot (" + r + "," + c + ")");
                    selectedRow = r;
                    selectedCol = c;
                    refreshBoardFromGameState(); // 使用新的刷新方法
                    showStatus("📍 Selected plot (" + r + "," + c + ") - Choose an action below");
                });

                gameBoard.add(cell, col, row);
                cells[row][col] = cell;
            }
        }

        // 🔥 初始刷新
        Platform.runLater(this::refreshBoardFromGameState);
    }

    // 2.2 Visual Feedback - Highlight plot state changes with icons, colors, tooltips
    private void refreshBoard() {
        refreshBoardFromGameState();
    }

    // 2.2 Action Controls - enable/disable feedback based on game state
    private void updateButtonStates() {
        boolean onOwnFarm = viewingPlayerName != null && viewingPlayerName.equals(myPlayerName);
        boolean connected = socket != null && !socket.isClosed();

        // 🔥 添加null检查，防止按钮还未创建时调用
        if (plantButton != null) {
            plantButton.setDisable(!onOwnFarm || !connected);   // 2.2 Controls + 2.5 Disable on disconnect
        }
        if (harvestButton != null) {
            harvestButton.setDisable(!onOwnFarm || !connected); // 同上
        }
        if (stealButton != null) {
            stealButton.setDisable(onOwnFarm || !connected);    // 自己农场不能偷 2.3 Rule
        }

        if (backButton != null) {
            backButton.setDisable(onOwnFarm || !connected);
        }

        if (visitButton != null && friendField != null) {
            visitButton.setDisable(!connected || friendField.getText().trim().isEmpty());
        }
    }

    // 2.2 Visual Feedback - icons, colors, tooltips for different states
    private void updateCellState(ToggleButton cell, int row, int col, boolean isSelected) {
        Game.PlotState state = game.getState(row, col);

        // 🔥 清除所有之前的样式，避免样式冲突
        cell.getStyleClass().clear();

        // 基础文字和颜色设置
        String text = "";
        String backgroundColor = "";
        String textColor = "";

        switch (state) {
            case EMPTY -> {
                text = "Empty";
                backgroundColor = "#F5DEB3"; // 浅棕色
                textColor = "#8B4513"; // 深棕色
            }
            case GROWING -> {
                text = "Growing";
                backgroundColor = "#90EE90"; // 浅绿色
                textColor = "#006400"; // 深绿色
            }
            case RIPE -> {
                text = "RIPE!";
                backgroundColor = "#FFD700"; // 金色
                textColor = "#FF8C00"; // 橙色
            }
        }

        // 🔥 选中状态的边框处理
        String borderColor = isSelected ? "#FF0000" : "#8B4513";
        String borderWidth = isSelected ? "4px" : "2px";

        // 🔥 组合完整样式，确保每次都是全新的样式
        String completeStyle = String.format(
                "-fx-background-color: %s; " +
                        "-fx-text-fill: %s; " +
                        "-fx-border-color: %s; " +
                        "-fx-border-width: %s; " +
                        "-fx-font-size: 11px; " +
                        "-fx-border-radius: 5px; " +
                        "-fx-background-radius: 5px; " +
                        "-fx-font-weight: %s;",
                backgroundColor, textColor, borderColor, borderWidth,
                (state == Game.PlotState.RIPE ? "bold" : "normal")
        );

        cell.setText(text);
        cell.setStyle(completeStyle);

        // 🔥 强制刷新显示
        cell.applyCss();

        // 2.2 Visual Feedback - updated tooltip content with null safety
        if (cell.getTooltip() != null) {
            String currentPlayer = (myPlayerName != null) ? myPlayerName : "unknown";
            String viewingPlayer = (viewingPlayerName != null) ? viewingPlayerName : "unknown";

            String ownerInfo = viewingPlayer.equals(currentPlayer) ? "Your farm" : viewingPlayer + "'s farm";
            cell.getTooltip().setText("Plot (" + row + "," + col + ")\nState: " + state + "\n" + ownerInfo);
        }
    }

    // 2.2 Core GUI Layout - Surface player name, coin balance, and contextual status messages
    private void updateCoins(String message) {
        statusMessage = message;

        // 🔥 修复空指针问题：添加null检查
        String currentPlayer = (myPlayerName != null) ? myPlayerName : "Player";
        String viewingPlayer = (viewingPlayerName != null) ? viewingPlayerName : "Unknown";

        String farmOwner = viewingPlayer.equals(currentPlayer) ? "Your Farm" : viewingPlayer + "'s Farm";
        coinsLabel.setText("Player: " + currentPlayer + " | Viewing: " + farmOwner + " | Coins: " + game.getCoins());
    }

    // 2.2 Visual Feedback - contextual status messages
    private void showStatus(String msg) {
        statusMessage = msg;
        if (statusLabel != null) {
            statusLabel.setText(msg);
        }
        updateCoins(statusMessage);
    }

    // 🔥 修复：访问好友逻辑
    private void handleVisit() {
        if (friendField == null) {
            showStatus("❌ No friend field available");
            return;
        }
        String friend = friendField.getText().trim();
        if (friend.isEmpty()) {
            showStatus("⚠️ Please enter a friend's name to visit");
            return;
        }

        if (out != null) {
            viewingPlayerName = friend; // 更新当前查看对象
            out.println("VIEW " + friend); // 发送VIEW命令
            showStatus("🚀 Visiting " + friend + "'s farm...");
        } else {
            showStatus("❌ Not connected to server");
        }
    }

    private void handleBack() {
        if (out != null) {
            viewingPlayerName = myPlayerName; // 返回自己农场
            out.println("GET"); // 请求自家快照
            showStatus("🏠 Returning to your farm...");
        }
    }

    // 🔥 修复：种植逻辑
    private void handlePlant() {
        if (!ensureSelection()) {
            showStatus("⚠️ Please select a plot first");
            return;
        }

        if (!viewingPlayerName.equals(myPlayerName)) {
            showStatus("❌ You can only plant on your own farm!");
            return;
        }

        if (out != null) {
            out.println("PLANT " + selectedRow + " " + selectedCol);
            showStatus("🌱 Planting crop at (" + selectedRow + "," + selectedCol + ")...");
        } else {
            // 离线降级
            try {
                game.plant(selectedRow, selectedCol);
                refreshBoardFromGameState();
                showStatus("🌱 Local plant (offline mode)");
            } catch (Exception e) {
                showStatus("❌ " + e.getMessage());
            }
        }
    }

    // 🔥 修复：收获逻辑
    private void handleHarvest() {
        if (!ensureSelection()) {
            showStatus("⚠️ Please select a plot first");
            return;
        }

        if (!viewingPlayerName.equals(myPlayerName)) {
            showStatus("❌ You can only harvest your own crops!");
            return;
        }

        if (out != null) {
            out.println("HARVEST " + selectedRow + " " + selectedCol);
            showStatus("🌾 Harvesting crop at (" + selectedRow + "," + selectedCol + ")...");
        } else {
            try {
                game.harvest(selectedRow, selectedCol);
                refreshBoardFromGameState();
                showStatus("✅ Harvest successful!");
            } catch (Exception e) {
                showStatus("❌ " + e.getMessage());
            }
        }
    }

    // 🔥 修复：偷菜逻辑
    private void handleSteal() {
        if (viewingPlayerName == null || viewingPlayerName.equals(myPlayerName)) {
            showStatus("⚠️ Visit a friend's farm first to steal!");
            return;
        }

        if (out != null) {
            out.println("STEAL " + viewingPlayerName); // 偷当前查看玩家的菜
            showStatus("🏴‍☠️ Attempting to steal from " + viewingPlayerName + "...");
        } else {
            game.stealRandom();
            refreshBoardFromGameState();
            showStatus("🏴‍☠️ Local simulated steal (offline mode)");
        }
    }

    public void shutdown() {
        if (refreshTimeline != null) refreshTimeline.stop();
        if (game != null) game.shutdown();
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    private boolean ensureSelection() {
        return selectedRow >= 0 && selectedCol >= 0;
    }

    // 2.2 Responsiveness - keep UI interactive during network activity
    private void startRefreshTicker() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (cells != null) {
                refreshBoardFromGameState();
            }
        }));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    // 2.5 Exception Handling - graceful degradation
    private void disableAllActions() {
        if (plantButton != null) plantButton.setDisable(true);
        if (harvestButton != null) harvestButton.setDisable(true);
        if (stealButton != null) stealButton.setDisable(true);
        if (visitButton != null) visitButton.setDisable(true);
        if (backButton != null) backButton.setDisable(true);
    }
}