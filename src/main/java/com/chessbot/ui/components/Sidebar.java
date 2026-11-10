package com.chessbot.ui.components;

import com.chessbot.engine.core.Piece;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class Sidebar extends VBox {
    private static final String SIDEBAR_STYLE = "-fx-background-color: #202020;";
    private static final String CLOCK_INACTIVE_STYLE = "-fx-background-color: #2a2824; -fx-background-radius: 4; -fx-border-color: #47443f; -fx-border-radius: 4; -fx-border-width: 2;";
    private static final String CLOCK_ACTIVE_STYLE = "-fx-background-color: #384722; -fx-background-radius: 4; -fx-border-color: #629924; -fx-border-radius: 4; -fx-border-width: 2;";

    private final StackPane topClockStackPane = new StackPane();
    private final Label topClockLabel = new Label();
    private final Circle topStatusDot = new Circle(7.5);
    private final Label topName = new Label();

    private final StackPane bottomClockStackPane = new StackPane();
    private final Label bottomClockLabel = new Label();
    private final Circle bottomStatusDot = new Circle(7.5);
    private final Label bottomName = new Label();

    // Which color gets the bottom side
    private int bottomSideColor = Piece.WHITE;

    private Boolean previousIsBottomSideActive = null;


    public Sidebar() {
        setPrefWidth(400);
        setPadding(new Insets(24));
        setSpacing(16);
        setAlignment(Pos.TOP_CENTER);
        setStyle(SIDEBAR_STYLE);

        configureClockStackPane(topClockStackPane, topClockLabel);
        HBox topPlayerRow = createPlayerRow(topStatusDot, topName);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        configureClockStackPane(bottomClockStackPane, bottomClockLabel);
        HBox bottomPlayerRow = createPlayerRow(bottomStatusDot, bottomName);

        getChildren().addAll(
                topClockStackPane,
                topPlayerRow,
                spacer,
                bottomClockStackPane,
                bottomPlayerRow
        );
    }


    private void configureClockStackPane(StackPane clockStackPane, Label clockLabel) {
        clockStackPane.setPrefHeight(80);
        clockStackPane.setMinHeight(80);
        clockStackPane.setStyle(CLOCK_INACTIVE_STYLE);

        clockLabel.setFont(Font.font("Monospaced", FontWeight.BOLD, 40));
        clockStackPane.getChildren().add(clockLabel);
    }


    private HBox createPlayerRow(Circle statusDot, Label name) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        statusDot.setFill(Color.web("#629924"));
        name.setTextFill(Color.web("#dcdcdc"));
        name.setFont(Font.font("System", FontWeight.BOLD, 16));

        row.getChildren().addAll(statusDot, name);
        return row;
    }


    public void assignPlayerNames(int boardPerspective, String whiteName, String blackName) {
        this.bottomSideColor = boardPerspective;

        if (bottomSideColor == Piece.WHITE) {
            topName.setText(blackName);
            bottomName.setText(whiteName);
        }

        else {
            topName.setText(whiteName);
            bottomName.setText(blackName);
        }
    }


    public void updateClocks(long whiteTimeLeft, long blackTimeLeft, int turn) {
        long topTimeLeft = (bottomSideColor == Piece.WHITE) ? blackTimeLeft : whiteTimeLeft;
        long bottomTimeLeft = (bottomSideColor == Piece.WHITE) ? whiteTimeLeft : blackTimeLeft;

        topClockLabel.setText(formatTime(topTimeLeft));
        bottomClockLabel.setText(formatTime(bottomTimeLeft));

        // Only restyles when the active side changes
        boolean isBottomSideActive = (turn == bottomSideColor);
        if (previousIsBottomSideActive == null || previousIsBottomSideActive != isBottomSideActive) {
            previousIsBottomSideActive = isBottomSideActive;
            styleSide(topClockStackPane, topClockLabel, topStatusDot, !isBottomSideActive);
            styleSide(bottomClockStackPane, bottomClockLabel, bottomStatusDot, isBottomSideActive);
        }
    }


    private static String formatTime(long timeLeft) {
        // Formats time as 00:ss.d if there are under 10 seconds left
        if (timeLeft < 10000) {
            long seconds = timeLeft / 1000L;
            long tenths = (timeLeft % 1000L) / 100L;
            return String.format("00:%02d.%d", seconds, tenths);
        }

        // Else, format time as mm:ss
        long secondsRoundedUp = (timeLeft + 999L) / 1000L;
        long minutes = secondsRoundedUp / 60L;
        long seconds = secondsRoundedUp % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }


    private void styleSide(StackPane clockStackPane, Label clockLabel, Circle statusDot, boolean isSideActive) {
        if (isSideActive) {
            clockStackPane.setStyle(CLOCK_ACTIVE_STYLE);
            clockLabel.setTextFill(Color.web("#dcdcdc"));
            statusDot.setFill(Color.web("#629924"));
        }

        else {
            clockStackPane.setStyle(CLOCK_INACTIVE_STYLE);
            clockLabel.setTextFill(Color.web("#8c8c8c"));
            statusDot.setFill(Color.web("#47443f"));
        }
    }
}
