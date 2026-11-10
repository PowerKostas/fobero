package com.chessbot.ui.controllers;

import com.chessbot.application.ApplicationConfig;
import com.chessbot.application.PlayerType;
import com.chessbot.application.UIGameManager;
import com.chessbot.engine.core.Board;
import com.chessbot.engine.core.Piece;
import com.chessbot.ui.components.Sidebar;
import com.chessbot.ui.components.VisualBoard;
import com.chessbot.ui.input.MoveHandler;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;

// This class runs after view.fxml is loaded from ChessApplication, it's responsible for initializing the UI components
// and connecting them to the underlying game state
public class MainController {
    @FXML
    private HBox mainContainer;


    public void initialize() {
        // If there is only one human player, and they are black, set the board's perspective to black pieces first
        int whitePlayerType = ApplicationConfig.WHITE_PLAYER_TYPE;
        int blackPlayerType = ApplicationConfig.BLACK_PLAYER_TYPE;
        int boardPerspective = (whitePlayerType != blackPlayerType && blackPlayerType == PlayerType.HUMAN) ? Piece.BLACK : Piece.WHITE;

        VisualBoard visualBoardOne = new VisualBoard(boardPerspective, false, ApplicationConfig.UI_GAME_FEN);
        Sidebar sidebar = new Sidebar();
        mainContainer.getChildren().addAll(visualBoardOne, sidebar);

        Board boardOne = visualBoardOne.getBoard();
        UIGameManager uiGameManager = new UIGameManager(boardOne, visualBoardOne, sidebar, true);
        MoveHandler moveHandler = new MoveHandler(visualBoardOne, uiGameManager);
        visualBoardOne.attachMoveHandler(moveHandler);
        uiGameManager.startGame(whitePlayerType, blackPlayerType);
    }
}
