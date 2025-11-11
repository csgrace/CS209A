module com.example.broadcastchat {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.broadcastchat to javafx.fxml;
    exports com.example.broadcastchat;
}