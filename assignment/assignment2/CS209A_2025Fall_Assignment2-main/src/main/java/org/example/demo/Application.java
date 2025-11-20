package org.example.demo;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.demo.game.Game;

/**
 * Entry point for the simplified QQ Farm demo.
 */
public class Application extends javafx.application.Application {

    @Override
    public void start(Stage stage) throws Exception {
        // 🔥 修复1：先显示登录窗口，等待用户输入
        LoginWindow loginWindow = new LoginWindow(stage);

        if (!loginWindow.isConfirmed()) {
            // 用户取消登录，直接退出
            System.exit(0);
            return;
        }

        String playerName = loginWindow.getUsername();
        System.out.println("User logged in as: " + playerName);

        // 🔥 修复2：只有在用户登录成功后，才创建Controller和Game
        Controller controller = new Controller();
        Game game = new Game();
        controller.init(game, playerName); // 🔥 修改：传入playerName给controller

        // 创建UI并获取根节点
        Scene scene = new Scene(controller.createUI(), 700, 600);

        // 使用登录窗口返回的用户名建立连接
        controller.connectAndLogin(playerName);

        stage.setTitle("QQ Farm Demo");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}