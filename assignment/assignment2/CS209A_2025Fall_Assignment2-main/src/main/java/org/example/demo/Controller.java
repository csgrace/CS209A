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
import java.nio.charset.StandardCharsets;

import org.example.demo.game.Game;

public class Controller {
    private GridPane gameBoard;
    private Label coinsLabel;
    private Label statusLabel;
    private Button plantButton;
    private Button harvestButton;
    private Button stealButton;
    private TextField friendField;
    private Button visitButton;
    private Button backButton;
    private Game myFarmGame;
    private Game viewingFarmGame;
    private ToggleButton[][] cells;
    private Timeline refreshTimeline;
    private String statusMessage = "Ready.";
    private int selectedRow = -1;
    private int selectedCol = -1;
    private String myPlayerName;
    private String viewingPlayerName;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenThread;
    private String lastCommand = "";
    private final java.util.concurrent.ConcurrentHashMap<String, Game> friendFarmsCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private volatile boolean connected = false; // 当前是否与服务器连接正常
    private volatile boolean reconnecting = false;  // 是否正在自动重连
    private int reconnectAttempts = 0;
    private static final int RECONNECT_DELAY_MS = 3000;

    private static final String VISIT_BASE_STYLE =
            "-fx-background-color: #87CEEB; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5px;";
    private static final String VISIT_FINAL_STYLE =
            "-fx-background-color: linear-gradient(#1E90FF,#0042C7); -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5px;";

    public void init(Game game, String playerName) {
        this.myFarmGame = game;
        this.viewingFarmGame = game;
        this.myPlayerName = playerName;
        this.viewingPlayerName = playerName;
        startRefreshTicker();
    }

    public Parent createUI() {
        VBox root = new VBox(15);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #F0F8FF;");

        VBox headerBox = new VBox(5);
        headerBox.setAlignment(Pos.CENTER);

        coinsLabel = new Label("Player: " + myPlayerName + " | Your Farm | Coins: 40");
        coinsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2E8B57;");

        statusLabel = new Label("Welcome to QQ Farm! Select a plot and choose an action.");
        statusLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #4169E1;");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(600);

        headerBox.getChildren().addAll(coinsLabel, statusLabel);

        createAllButtons();

        HBox friendBox = new HBox(10);
        friendBox.setAlignment(Pos.CENTER);
        friendBox.setPadding(new Insets(10));
        friendBox.setStyle("-fx-border-color: #DDA0DD; -fx-border-width: 2px; -fx-border-radius: 5px; -fx-background-color: #F8F8FF;");

        Label friendLabel = new Label("Visit Friends:");
        friendLabel.setStyle("-fx-font-weight: bold;");

        friendField = new TextField();
        friendField.setPromptText("Enter friend's name (try 'bob')");
        friendField.setPrefWidth(150);
        friendField.setStyle("-fx-border-radius: 5px;");

        friendBox.getChildren().addAll(friendLabel, friendField, visitButton, backButton);

        Label gridLabel = new Label("Farm (4x4 Grid)");
        gridLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #8B4513;");

        gameBoard = new GridPane();
        gameBoard.setHgap(3);
        gameBoard.setVgap(3);
        gameBoard.setAlignment(Pos.CENTER);
        gameBoard.setStyle("-fx-border-color: #8B4513; -fx-border-width: 3px; -fx-background-color: #F5DEB3; -fx-padding: 10px;");

        createBoard();

        HBox actionBox = new HBox(15);
        actionBox.setAlignment(Pos.CENTER);
        actionBox.setPadding(new Insets(10));
        actionBox.setStyle("-fx-border-color: #CD853F; -fx-border-width: 2px; -fx-border-radius: 5px; -fx-background-color: #FFF8DC;");
        actionBox.getChildren().addAll(plantButton, harvestButton, stealButton);

        Label instructions = new Label("Click a plot to select it, then choose an action. Visit friends to see their farms!");
        instructions.setStyle("-fx-font-size: 12px; -fx-text-fill: #696969; -fx-font-style: italic;");
        instructions.setWrapText(true);
        instructions.setMaxWidth(600);
        instructions.setAlignment(Pos.CENTER);

        root.getChildren().addAll(headerBox, friendBox, gridLabel, gameBoard, actionBox, instructions);
        return root;
    }

    private void createAllButtons() {
        visitButton = new Button("Visit");
        visitButton.setStyle(VISIT_BASE_STYLE);
        visitButton.setOnAction(e -> {
            if (!checkConnectedForAction("visit a friend")) return;
            animateVisitButton();
            handleVisit();
        });

        backButton = new Button("Back Home");
        backButton.setOnAction(e -> {
            if (!checkConnectedForAction("go back home")) return;
            handleBack();
            resetVisitButtonStyle();
        });
        backButton.setStyle("-fx-background-color: #98FB98; -fx-text-fill: #2F4F4F; -fx-font-weight: bold; -fx-border-radius: 5px;");

        plantButton = new Button("Plant (Cost: 5 coins)");
        plantButton.setOnAction(e -> {
            if (!checkConnectedForAction("plant")) return;
            handlePlant();
        });
        plantButton.setStyle("-fx-background-color: #90EE90; -fx-text-fill: #006400; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-radius: 5px;");

        harvestButton = new Button("Harvest (+12 coins)");
        harvestButton.setOnAction(e -> {
            if (!checkConnectedForAction("harvest")) return;
            handleHarvest();
        });
        harvestButton.setStyle("-fx-background-color: #FFD700; -fx-text-fill: #B8860B; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-radius: 5px;");

        stealButton = new Button("Steal (+3 coins)");
        stealButton.setOnAction(e -> {
            if (!checkConnectedForAction("steal")) return;
            handleSteal();
        });
        stealButton.setStyle("-fx-background-color: #FF6B6B; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-radius: 5px;");
    }

    private boolean checkConnectedForAction(String actionName) {
        if (!connected) {
            String msg = "Disconnected from server. Unable to " + actionName + ".";
            showStatus(msg);
            return false;
        }
        return true;
    }

    private void animateVisitButton() {
        String[] colors = {
                "#87CEEB", "#76B9E4", "#65A5DD", "#5490D6",
                "#437CCF", "#3267C8", "#2153C1", "#0F3EB9"
        };
        Timeline t = new Timeline();
        for (int i = 0; i < colors.length; i++) {
            String color = colors[i];
            t.getKeyFrames().add(new KeyFrame(Duration.millis(i * 90), ev -> {
                visitButton.setStyle("-fx-background-color: " + color +
                        "; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5px;");
            }));
        }
        t.getKeyFrames().add(new KeyFrame(Duration.millis(colors.length * 90 + 50),
                ev -> visitButton.setStyle(VISIT_FINAL_STYLE)));
        t.play();
    }

    private void resetVisitButtonStyle() {
        visitButton.setStyle(VISIT_BASE_STYLE);
    }

    public void connectAndLogin(String playerName) {
        try {
            socket = new Socket("127.0.0.1", 5050);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            connected = true;
            reconnecting = false;
            reconnectAttempts = 0;

            listenThread = new Thread(this::listenLoop, "server-listener");
            listenThread.setDaemon(true);
            listenThread.start();

            out.println("LOGIN " + playerName);
            showStatus("Connecting to server...");
        } catch (IOException e) {
            connected = false;
            showStatus("Connect failed: " + e.getMessage());
            updateButtonStates();
            startReconnectLoop(); // 初次连接失败也尝试重连
        }
    }

    private void listenLoop() {
        String line;
        try {
            while ((line = in.readLine()) != null) {
                final String msg = line;
                handleServerMsg(line);
                System.out.println("Received from server: " + msg);

                if (msg.startsWith("UPDATE ")) {
                    int firstSpace = msg.indexOf(' ');
                    int secondSpace = msg.indexOf(' ', firstSpace + 1);
                    if (secondSpace > 0) {
                        String player = msg.substring(firstSpace + 1, secondSpace);
                        String updateDetails = msg.substring(secondSpace + 1); // 更详细的内容
                        String updateType;
                        String coordinates;

                        if (updateDetails.startsWith("PLANT")) {
                            updateType = "PLANT";
                            int coordStart = updateDetails.indexOf('(');
                            int coordEnd = updateDetails.indexOf(')', coordStart);
                            if (coordStart >= 0 && coordEnd > coordStart) {
                                coordinates = updateDetails.substring(coordStart, coordEnd + 1);
                            } else {
                                coordinates = null;
                            }
                        } else if (updateDetails.startsWith("HARVEST")) {
                            updateType = "HARVEST";
                            int coordStart = updateDetails.indexOf('(');
                            int coordEnd = updateDetails.indexOf(')', coordStart);
                            if (coordStart >= 0 && coordEnd > coordStart) {
                                coordinates = updateDetails.substring(coordStart, coordEnd + 1);
                            } else {
                                coordinates = null;
                            }
                        } else {
                            coordinates = null;
                            updateType = null;
                        }

                        if (player.equals(myPlayerName)) {
                            Platform.runLater(() -> {
                                updateGameSnapshot(myFarmGame, updateDetails);
                                if (viewingPlayerName.equals(myPlayerName)) {
                                    refreshBoardFromGameState();
                                }
                                updateCoinsDisplay();
                            });
                        } else if (player.equals(viewingPlayerName)) {
                            Platform.runLater(() -> {
                                updateGameSnapshot(viewingFarmGame, updateDetails);
                                refreshBoardFromGameState();
                                updateCoinsDisplay();
                            });
                        }

                        Platform.runLater(() -> {
                            if (player.equals(myPlayerName)) {
                                if (updateType != null && coordinates != null) {
                                    showStatus("Successfully performed " + updateType.toLowerCase() + " at " + coordinates);
                                } else {
                                    showStatus("Your farm updated!");
                                }
                            } else {
                                if (updateType != null && coordinates != null) {
                                    showStatus("Friend " + player + " performed " + updateType.toLowerCase() + " at " + coordinates);
                                } else {
                                    showStatus("Friend " + player + " updated");
                                }
                            }
                        });
                    }
                } else if (msg.startsWith("OK ")) {
                    Platform.runLater(() -> {
                        if (msg.startsWith("OK LOGGED_IN")) {
                            connected = true;
                            showStatus("Successfully connected to farm server!");
                            updateButtonStates();
                        } else if (msg.contains("{")) {
                            String json = msg.substring(3);
                            if (lastCommand.startsWith("STEAL")) {
                                updateGameSnapshot(myFarmGame, json);
                                updateCoinsDisplay();
                            } else if (lastCommand.startsWith("VIEW")) {
                                updateGameSnapshot(viewingFarmGame, json);
                                refreshBoardFromGameState();
                                updateCoinsDisplay();
                            } else if (lastCommand.startsWith("GET")) {
                                updateGameSnapshot(myFarmGame, json);
                                viewingFarmGame = myFarmGame;
                                viewingPlayerName = myPlayerName;
                                refreshBoardFromGameState();
                                updateCoinsDisplay();
                            } else if (lastCommand.startsWith("PLANT") || lastCommand.startsWith("HARVEST")) {
                                updateGameSnapshot(myFarmGame, json);
                                refreshBoardFromGameState();
                                updateCoinsDisplay();
                            } else {
                                if (viewingPlayerName.equals(myPlayerName)) {
                                    updateGameSnapshot(myFarmGame, json);
                                } else {
                                    updateGameSnapshot(viewingFarmGame, json);
                                }
                                refreshBoardFromGameState();
                                updateCoinsDisplay();
                            }
                        }
                    });
                } else if (msg.startsWith("ERR ")) {
                    Platform.runLater(() -> showStatus("Error: " + msg.substring(4)));
                }
            }
            Platform.runLater(this::onDisconnected); // readLine 返回 null -> 服务器关闭连接
        } catch (IOException e) {
            Platform.runLater(this::onDisconnected); // 网络错误（服务器崩溃 / 断网）
        }
    }
    private void handleServerMsg(String msg) {
        Platform.runLater(() -> {
            if (msg.startsWith("UPDATE")) {
                // Process update message
            } else if (msg.startsWith("OK ")) {
                handleOkResponse(msg);
            } else if (msg.startsWith("ERR ")) {
                showStatus("Error: " + msg.substring(4));
            }
        });
    }
    private void handleOkResponse(String msg) {
        if (msg.startsWith("OK LOGGED_IN")) {
            connected = true;
            showStatus("Successfully connected to farm server!");
            updateButtonStates();
        } else if (msg.contains("{")) {
            String json = msg.substring(3);
            if (lastCommand.startsWith("STEAL")) {
                updateGameSnapshot(myFarmGame, json);
                updateCoinsDisplay();
            } else if (lastCommand.startsWith("VIEW")) {
                // 确保针对不同玩家的 farm 确认 farmGame 不互相混用。
                updateGameSnapshot(viewingFarmGame, json);
                refreshBoardFromGameState();
                updateCoinsDisplay();
            } else if (lastCommand.startsWith("GET")) {
                updateGameSnapshot(myFarmGame, json);
                viewingFarmGame = myFarmGame;
                viewingPlayerName = myPlayerName;
                refreshBoardFromGameState();
                updateCoinsDisplay();
            }
        }
    }
    private void onDisconnected() {
        if (reconnecting) return;
        connected = false;
        showStatus("Disconnected from server. Attempting to reconnect...");
        updateButtonStates();
        startReconnectLoop();
    }

    private synchronized void startReconnectLoop() {
        if (reconnecting) return;
        reconnecting = true;

        new Thread(() -> {
            while (!connected) {
                try {
                    reconnectAttempts++;
                    System.out.println("[RECONNECT] Attempt " + reconnectAttempts);

                    Socket newSocket = new Socket("127.0.0.1", 5050);
                    BufferedReader newIn = new BufferedReader(
                            new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter newOut = new PrintWriter(
                            new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true);

                    socket = newSocket;
                    in = newIn;
                    out = newOut;

                    listenThread = new Thread(this::listenLoop, "server-listener");
                    listenThread.setDaemon(true);
                    listenThread.start();

                    out.println("LOGIN " + myPlayerName);
                    out.println("GET");

                    connected = true;
                    reconnecting = false;

                    Platform.runLater(() -> {
                        showStatus("Reconnected to server as " + myPlayerName);
                        refreshBoardFromGameState();
                    });
                    return;
                } catch (IOException e) {
                    System.out.println("[RECONNECT] Failed to reconnect: " + e.getMessage());
                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ignored) {}
                }
            }
        }, "reconnect-loop").start();
    }

    private void updateGameSnapshot(Game targetGame, String json) {
        if (json == null || json.isEmpty()) return;
        try {
            int coinsIdx = json.indexOf("\"coins\":");
            if (coinsIdx >= 0) {
                int colon = coinsIdx + 8;
                int comma = json.indexOf(",", colon);
                if (comma == -1) comma = json.indexOf("}", colon);
                if (comma > colon) {
                    String coinsStr = json.substring(colon, comma).trim();
                    try {
                        int newCoins = Integer.parseInt(coinsStr);
                        targetGame.setCoins(newCoins);
                    } catch (NumberFormatException ignored) {}
                }
            }
            int boardStart = json.indexOf("\"board\":[");
            if (boardStart >= 0) {
                int arrStart = json.indexOf("[", boardStart + 8);
                int arrEnd = json.lastIndexOf("]]");
                if (arrEnd >= 0) {
                    arrEnd += 1;
                    String boardContent = json.substring(arrStart, arrEnd + 1);
                    parseBoardData(targetGame, boardContent);
                }
            }
        } catch (Exception e) {
            System.err.println("Snapshot parse error: " + e.getMessage());
        }
    }

    private void parseBoardData(Game targetGame, String boardJson) {
        try {
            String content = boardJson.substring(1, boardJson.length() - 1);
            String[] rows = splitRows(content);
            for (int r = 0; r < rows.length && r < targetGame.getRows(); r++) {
                String row = rows[r].trim();
                if (row.startsWith("[")) row = row.substring(1);
                if (row.endsWith("]")) row = row.substring(0, row.length() - 1);
                String[] cells = row.split(",");
                for (int c = 0; c < cells.length && c < targetGame.getCols(); c++) {
                    String cell = cells[c].trim().replace("\"", "");
                    try {
                        Game.PlotState state = Game.PlotState.valueOf(cell);
                        targetGame.setState(r, c, state);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("Board parsing error: " + e.getMessage());
        }
    }

    private String[] splitRows(String content) {
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
                    if (i + 1 < content.length() && content.charAt(i + 1) == ',') {
                        i++;
                    }
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) rows.add(current.toString());
        return rows.toArray(new String[0]);
    }

    private void refreshBoardFromGameState() {
        if (cells == null || viewingFarmGame == null) return;
        for (int row = 0; row < viewingFarmGame.getRows(); row++) {
            for (int col = 0; col < viewingFarmGame.getCols(); col++) {
                ToggleButton cell = cells[row][col];
                if (cell != null) {
                    boolean isSelected = (row == selectedRow && col == selectedCol);
                    cell.setSelected(isSelected);
                    updateCellState(cell, row, col, isSelected);
                }
            }
        }
        updateButtonStates();
    }

    private void createBoard() {
        if (gameBoard == null || viewingFarmGame == null) return;
        gameBoard.getChildren().clear();
        cells = new ToggleButton[viewingFarmGame.getRows()][viewingFarmGame.getCols()];
        for (int row = 0; row < viewingFarmGame.getRows(); row++) {
            for (int col = 0; col < viewingFarmGame.getCols(); col++) {
                ToggleButton cell = new ToggleButton();
                cell.setPrefSize(80, 80);
                cell.setMinSize(80, 80);
                cell.setMaxSize(80, 80);
                cell.setStyle("-fx-font-size: 11px; -fx-border-width: 2px; -fx-background-radius: 5px; -fx-border-radius: 5px;");
                Tooltip tooltip = new Tooltip("Plot (" + row + "," + col + ") - Click to select");
                cell.setTooltip(tooltip);
                int r = row;
                int c = col;
                cell.setOnAction(event -> {
                    selectedRow = r;
                    selectedCol = c;
                    refreshBoardFromGameState();
                    showStatus("Selected plot (" + r + "," + c + ")");
                });
                gameBoard.add(cell, col, row);
                cells[row][col] = cell;
            }
        }
        Platform.runLater(this::refreshBoardFromGameState);
    }

    private void updateButtonStates() {
        boolean onOwnFarm = viewingPlayerName != null && viewingPlayerName.equals(myPlayerName);
        boolean canUse = connected;

        if (plantButton != null) plantButton.setDisable(!canUse || !onOwnFarm);
        if (harvestButton != null) harvestButton.setDisable(!canUse || !onOwnFarm);
        if (stealButton != null) stealButton.setDisable(!canUse || onOwnFarm);
        if (backButton != null) backButton.setDisable(!canUse || onOwnFarm);
        if (visitButton != null && friendField != null) {
            visitButton.setDisable(!canUse || friendField.getText().trim().isEmpty());
        }
    }

    private void updateCellState(ToggleButton cell, int row, int col, boolean isSelected) {
        if (cell == null || viewingFarmGame == null) return;
        Game.PlotState state = viewingFarmGame.getState(row, col);
        if (state == null) state = Game.PlotState.EMPTY;

        String text;
        String backgroundColor;
        String textColor;

        switch (state) {
            case EMPTY -> {
                text = "Empty";
                backgroundColor = "#F5DEB3";
                textColor = "#8B4513";
            }
            case GROWING -> {
                text = "Growing";
                backgroundColor = "#90EE90";
                textColor = "#006400";
            }
            case RIPE -> {
                text = "RIPE!";
                backgroundColor = "#FFD700";
                textColor = "#FF8C00";
            }
            default -> {
                text = "Empty";
                backgroundColor = "#F5DEB3";
                textColor = "#8B4513";
            }
        }

        String borderColor = isSelected ? "#FF0000" : "#8B4513";
        String borderWidth = isSelected ? "4px" : "2px";
        String style = String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-border-color: %s; -fx-border-width: %s; -fx-font-size: 11px; -fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-weight: %s;",
                backgroundColor, textColor, borderColor, borderWidth,
                (state == Game.PlotState.RIPE ? "bold" : "normal")
        );
        cell.setText(text);
        cell.setStyle(style);

        if (cell.getTooltip() != null) {
            String ownerInfo = viewingPlayerName != null && viewingPlayerName.equals(myPlayerName)
                    ? "Your farm"
                    : (viewingPlayerName == null ? "" : viewingPlayerName + "'s farm");
            cell.getTooltip().setText("Plot (" + row + "," + col + ")\nState: " + state + "\n" + ownerInfo);
        }
    }

    private void updateCoinsDisplay() {
        if (coinsLabel == null) return;
        String labelText;
        if (viewingPlayerName != null && viewingPlayerName.equals(myPlayerName)) {
            // 在自己的农场
            labelText = "Player: " + myPlayerName + " | Your Farm | Coins: " + myFarmGame.getCoins();
        } else {
            // 在别人的农场
            labelText = "Player: " + myPlayerName + " | Viewing: " + viewingPlayerName + "'s Farm | " +
                    "My Coins: " + myFarmGame.getCoins() + " | " +
                    viewingPlayerName + "'s Coins: " + viewingFarmGame.getCoins();
        }
        coinsLabel.setText(labelText);
    }

    private void showStatus(String message) {
        statusMessage = (message == null || message.isEmpty()) ? "Ready." : message;
        if (statusLabel != null) {
            statusLabel.setText(statusMessage);
        }
    }

    private void handleVisit() {
        if (friendField == null) {
            showStatus("No friend field available");
            return;
        }
        String friend = friendField.getText().trim();
        if (friend.isEmpty()) {
            showStatus("Enter a friend's name");
            return;
        }
        if (out != null && connected) {
            viewingPlayerName = friend;
            if (friend.equals(myPlayerName)) {
                // 访问自己的农场，直接引用自己的游戏状态
                viewingFarmGame = myFarmGame;
            } else {
                // 确保缓存的朋友农场对象使用最新数据
                viewingFarmGame = friendFarmsCache.computeIfAbsent(friend, k -> new Game());
            }
            selectedRow = -1;
            selectedCol = -1;
            lastCommand = "VIEW " + friend;
            out.println("VIEW " + friend);
            showStatus("Visiting " + friend + "'s farm...");
        } else {
            showStatus("Disconnected from server. Unable to visit.");
        }
    }

    private void handleBack() {
        if (out != null && connected) {
            viewingPlayerName = myPlayerName;
            viewingFarmGame = myFarmGame;
            selectedRow = -1;
            selectedCol = -1;
            lastCommand = "GET";
            out.println("GET");
            showStatus("Back to your farm");
        } else {
            showStatus("Disconnected from server. Unable to go back home.");
        }
    }

    private void handlePlant() {
        if (!ensureSelection()) {
            showStatus("Select a plot first");
            return;
        }
        if (!viewingPlayerName.equals(myPlayerName)) {
            showStatus("You can only plant on your own farm");
            return;
        }
        if (out != null && connected) {
            lastCommand = "PLANT " + selectedRow + " " + selectedCol;
            out.println(lastCommand);
            showStatus("Planting (" + selectedRow + "," + selectedCol + ")...");
        } else {
            showStatus("Disconnected from server. Unable to plant.");
        }
    }

    private void handleHarvest() {
        if (!ensureSelection()) {
            showStatus("Select a plot first");
            return;
        }
        if (!viewingPlayerName.equals(myPlayerName)) {
            showStatus("You can only harvest your own crops");
            return;
        }
        if (out != null && connected) {
            lastCommand = "HARVEST " + selectedRow + " " + selectedCol;
            out.println(lastCommand);
            showStatus("Harvesting (" + selectedRow + "," + selectedCol + ")...");
        } else {
            showStatus("Disconnected from server. Unable to harvest.");
        }
    }

    private void handleSteal() {
        if (viewingPlayerName == null || viewingPlayerName.equals(myPlayerName)) {
            showStatus("Visit a friend's farm first");
            return;
        }
        if (out != null && connected) {
            String victimName = viewingPlayerName;
            lastCommand = "STEAL " + victimName;
            out.println("STEAL " + victimName);
            showStatus("Stealing from " + victimName + "...");
        } else {
            showStatus("Disconnected from server. Unable to steal.");
        }
    }

    public void shutdown() {
        if (refreshTimeline != null) refreshTimeline.stop();
        if (myFarmGame != null) myFarmGame.shutdown();
        connected = false;
        reconnecting = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    private boolean ensureSelection() {
        return selectedRow >= 0 && selectedCol >= 0;
    }

    private void startRefreshTicker() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (cells != null) refreshBoardFromGameState();
        }));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }
}