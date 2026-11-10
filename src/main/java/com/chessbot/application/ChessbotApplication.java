package com.chessbot.application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

// UI starting class
public class ChessbotApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ChessbotApplication.class.getResource("/com/chessbot/views/view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());

        stage.setTitle("chessbot");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        System.setProperty("prism.lcdtext", "false"); // Fixes text artifacts
        launch(args);
    }
}
