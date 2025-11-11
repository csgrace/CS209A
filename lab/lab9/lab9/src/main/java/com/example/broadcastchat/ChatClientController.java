package com.example.broadcastchat;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;


public class ChatClientController {
    @FXML
    private TextField inputField;

    @FXML
    private TextArea displayArea;

    private String userName;


    public void setUserName(String userName) {
        this.userName = userName;
        displayArea.appendText("Welcome, " + userName + "!\n");

    }

    @FXML
    public void initialize() {
        // this method is automatically invoked
        // once everything is loaded

        // display message when user presses Enter key
        inputField.setOnAction(e -> {
            String userInput = inputField.getText();
            if (!userInput.isEmpty()) {
                displayArea.appendText(userName + ": " + userInput + "\n");
                inputField.clear();
            }
        });
    }

}