package org.example.demo;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.demo.game.Game;

public class Application extends javafx.application.Application {
    @Override
    public void start(Stage stage) throws Exception {
        LoginWindow loginWindow = new LoginWindow(stage);
        if (!loginWindow.isConfirmed()) {
            System.exit(0);
            return;
        }

        String playerName = loginWindow.getUsername();
        System.out.println("User logged in as: " + playerName);

        Controller controller = new Controller();
        Game game = new Game();
        controller.init(game, playerName); // 传入playerName给controller

        Scene scene = new Scene(controller.createUI(), 700, 600);

        controller.connectAndLogin(playerName); // 使用登录窗口返回的用户名建立连接

        stage.setTitle("QQ Farm Demo");
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}