package org.example.demo;
// mvn compile exec:java "-Dexec.mainClass=org.example.demo.server.Server"
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * 登录窗口 - 用户输入用户名并连接服务器
 */
public class LoginWindow {
    private String username;
    private boolean confirmed = false;

    public LoginWindow(Stage primaryStage) {
        // 创建登录窗口
        Stage loginStage = new Stage();
        loginStage.setTitle("QQ Farm - Login");
        loginStage.setWidth(400);
        loginStage.setHeight(250);
        loginStage.setOnCloseRequest(e -> System.exit(0)); // 关闭登录窗口则退出程序

        VBox root = new VBox(15);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #F0F8FF;");

        // 标题
        Label title = new Label("🌱 QQ Farm - Welcome");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2E8B57;");

        // 提示文本
        Label hint = new Label("Please enter your username to login:");
        hint.setStyle("-fx-font-size: 14px; -fx-text-fill: #4169E1;");

        // 用户名输入框
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter your username (e.g., alice, bob)");
        usernameField.setPrefHeight(40);
        usernameField.setStyle("-fx-font-size: 14px; -fx-padding: 10px; -fx-border-radius: 5px;");

        // 按钮区域
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button loginBtn = new Button("Login");
        loginBtn.setPrefWidth(100);
        loginBtn.setPrefHeight(40);
        loginBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #90EE90; -fx-text-fill: #006400; -fx-border-radius: 5px;");

        Button exitBtn = new Button("Exit");
        exitBtn.setPrefWidth(100);
        exitBtn.setPrefHeight(40);
        exitBtn.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-background-color: #FF6B6B; -fx-text-fill: white; -fx-border-radius: 5px;");

        // 登录按钮处理
        loginBtn.setOnAction(e -> {
            String input = usernameField.getText().trim();
            if (input.isEmpty()) {
                showAlert("Warning", "Please enter a username!");
                return;
            }
            this.username = input;
            this.confirmed = true;
            loginStage.close();
        });

        // 退出按钮
        exitBtn.setOnAction(e -> System.exit(0));

        // 支持回车登录
        usernameField.setOnKeyPressed(e -> {
            if (e.getCode().toString().equals("ENTER")) {
                loginBtn.fire();
            }
        });

        buttonBox.getChildren().addAll(loginBtn, exitBtn);

        root.getChildren().addAll(title, hint, usernameField, buttonBox);

        Scene scene = new Scene(root);
        loginStage.setScene(scene);
        loginStage.showAndWait(); // 模态对话框，等待用户响应
    }

    public String getUsername() {
        return username;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}