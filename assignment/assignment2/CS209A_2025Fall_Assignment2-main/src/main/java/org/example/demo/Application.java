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
        // 不再使用FXML，直接创建Controller
        Controller controller = new Controller();
        controller.init(new Game());

        // 创建UI并获取根节点
        Scene scene = new Scene(controller.createUI(), 700, 600);

        // TODO: 在此处建立与服务器的真实连接，并把共享的 Game 状态换成网络同步模型。
        controller.connectAndLogin("alice"); // 新增方法（见 Controller.java）

        stage.setTitle("QQ Farm Demo");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}