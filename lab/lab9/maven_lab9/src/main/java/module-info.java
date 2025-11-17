module com.example.broadcastchat {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.broadcastchat to javafx.fxml;
    opens org.example to javafx.graphics, javafx.controls;
    exports com.example.broadcastchat;
}