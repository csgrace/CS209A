package com.example.broadcastchat;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.*;
import java.net.Socket;

public class ChatClientController {
    @FXML
    private TextField inputField;
    @FXML
    private TextArea displayArea;

    private String userName;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // 可选，绑定窗口关闭事件
    private Stage stage;

    public void setUserName(String userName) {
        this.userName = userName;
        displayArea.appendText("Welcome, " + userName + "!\n");
        connectToServer();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        stage.setOnCloseRequest(e -> {
            sendQuit();
            closeConnection();
        });
    }

    @FXML
    public void initialize() {
        inputField.setOnAction(e -> {
            String userInput = inputField.getText().trim();
            if (!userInput.isEmpty() && out != null) {
                out.println(userInput); // 发给服务器
                inputField.clear();
            }
        });
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 1234);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(userName);
            // 启动后台接收线程
            Thread t = new Thread(this::receiveMessages);
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            appendMessage("Unable to connect to server.");
        }
    }

    private void receiveMessages() {
        try {
            String msg;
            while ((msg = in.readLine()) != null) {
                String finalMsg = msg;
                Platform.runLater(() -> appendMessage(finalMsg));
            }
        } catch (IOException e) {
            Platform.runLater(() -> appendMessage("Disconnected from server"));
        }
    }

    private void sendQuit() {
        if (out != null) out.println("QUIT");
    }

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    private void appendMessage(String msg) {
        displayArea.appendText(msg + "\n");
    }
}