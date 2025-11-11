package com.example.broadcastchat;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

public class ChatClient extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ChatClient.class.getResource("client-view.fxml"));
        Parent root = fxmlLoader.load();

        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter your name:");

        Optional<String> result = dialog.showAndWait();
        String userName = result.orElse("null");


        ChatClientController controller = fxmlLoader.getController();
        controller.setUserName(userName);


        Scene scene = new Scene(root, 320, 240);
        stage.setTitle(userName);
        stage.setScene(scene);

        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }

}
