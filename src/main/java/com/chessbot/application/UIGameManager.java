package com.chessbot.application;

import com.chessbot.engine.ai.Chessbot;
import com.chessbot.engine.core.Board;
import com.chessbot.engine.core.Move;
import com.chessbot.engine.core.Piece;
import com.chessbot.engine.movegen.MoveGenerator;
import com.chessbot.engine.movegen.MoveList;
import com.chessbot.engine.movegen.utils.Checks;
import com.chessbot.engine.utils.ResultDetector;
import com.chessbot.ui.components.Sidebar;
import com.chessbot.ui.components.VisualBoard;
import com.chessbot.ui.input.MoveHandler;
import com.chessbot.ui.utils.SoundManager;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

// This class exists to centralize the move execution logic for UI-based games. It ensures that all the necessary game actions
// are performed here regardless of if the input is coming from the UI, the engine or anything else. This way there is no
// need to write extra code on any specific input class
public class UIGameManager {
    private Board board;
    private final VisualBoard visualBoard;
    private final Sidebar sidebar;

    // Pre-allocates one move list object for the entire lifecycle of the UI
    private final MoveList moveList = new MoveList();

    private boolean inCheck;

    // If white/black is human/engine
    private int whitePlayerType;
    private int blackPlayerType;

    // Pluggable bots of different engine versions for white and black
    private Chessbot whiteBot;
    private Chessbot blackBot;

    // In endless mode when a game ends, instead of the window closing, another game gets setup
    private final boolean isEndlessMode;

    private final AnimationTimer timer;
    private long whiteTimeLeft;
    private long blackTimeLeft;
    private boolean isGameOver;
    private long lastTickNanos;

    // Class variables are used because time controls belong to the player, not the color, so they get swapped in restartGame()
    // together with player types and bots
    private long whiteInitialTime;
    private int whiteIncrementTime;
    private long blackInitialTime;
    private int blackIncrementTime;

    // A class variable which is updated only when a move is actually played is needed because board.getTurn() can't be
    // used on the UI thread since it changes values millions of times per second
    private int currentTurn;


    public UIGameManager(Board board, VisualBoard visualBoard, Sidebar sidebar, boolean isEndlessMode) {
        this.board = board;
        this.visualBoard = visualBoard;
        this.sidebar = sidebar;
        this.isEndlessMode = isEndlessMode;
        this.whiteInitialTime = ApplicationConfig.WHITE_INITIAL_TIME_MS;
        this.whiteIncrementTime = ApplicationConfig.WHITE_INCREMENT_TIME_MS;
        this.blackInitialTime = ApplicationConfig.BLACK_INITIAL_TIME_MS;
        this.blackIncrementTime = ApplicationConfig.BLACK_INCREMENT_TIME_MS;

        boolean isEngineVsEngine = (ApplicationConfig.WHITE_PLAYER_TYPE == PlayerType.ENGINE &&
                                    ApplicationConfig.BLACK_PLAYER_TYPE == PlayerType.ENGINE);

        // Assigns the according bot if white/black is an engine
        if (ApplicationConfig.WHITE_PLAYER_TYPE == PlayerType.ENGINE) {
            whiteBot = isEngineVsEngine ? ApplicationConfig.ENGINE_MATCH_WHITE_BOT : ApplicationConfig.HUMAN_OPPONENT_BOT;
        }

        if (ApplicationConfig.BLACK_PLAYER_TYPE == PlayerType.ENGINE) {
            blackBot = isEngineVsEngine ? ApplicationConfig.ENGINE_MATCH_BLACK_BOT : ApplicationConfig.HUMAN_OPPONENT_BOT;
        }

        this.timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onClockTick(now);
            }
        };
    }


    public MoveList getMoveList() { return moveList; }


    public void startGame(int whitePlayerType, int blackPlayerType) {
        this.whitePlayerType = whitePlayerType;
        this.blackPlayerType = blackPlayerType;
        whiteTimeLeft = whiteInitialTime;
        blackTimeLeft = blackInitialTime;
        isGameOver = false;
        currentTurn = board.getTurn();

        MoveGenerator.generate(board, moveList);
        inCheck = (Checks.calculateSquares(board, currentTurn) != 0L);

        String whiteName = (whitePlayerType == PlayerType.HUMAN) ? "Human" : whiteBot.name();
        String blackName = (blackPlayerType == PlayerType.HUMAN) ? "Human" : blackBot.name();

        // Based on the board perspective, correctly assign the player names and update the clocks
        int boardPerspective = visualBoard.getBoardPerspective();
        sidebar.assignPlayerNames(boardPerspective, whiteName, blackName);
        sidebar.updateClocks(whiteTimeLeft, blackTimeLeft, currentTurn);

        // Have to check if the loaded FEN is an already completed game
        if (getGameResult()) {
            triggerGameOverSequence();
        }

        else {
            lastTickNanos = System.nanoTime();
            timer.start();
            checkTurn();
        }
    }


    // The function starts with timer.start() and it's called about 60 times per second
    private void onClockTick(long now) {
        if (isGameOver) {
            timer.stop();
            return;
        }

        // Because this function uses nanoseconds but everything else is in milliseconds, do integer division and covert
        // the nanos. If not at least 1 ms passed, return
        long elapsedMs = (now - lastTickNanos) / 1_000_000L;
        if (elapsedMs <= 0) {
            return;
        }

        // The remainder is lost in this call because of the integer division, only advance lastTickNanos by the whole milliseconds
        lastTickNanos += elapsedMs * 1_000_000L;

        if (currentTurn == Piece.WHITE) {
            whiteTimeLeft = Math.max(0, whiteTimeLeft - elapsedMs);
        }

        else {
            blackTimeLeft = Math.max(0, blackTimeLeft - elapsedMs);
        }

        sidebar.updateClocks(whiteTimeLeft, blackTimeLeft, currentTurn);

        if (whiteTimeLeft == 0 || blackTimeLeft == 0) {
            triggerGameOverSequence();
        }
    }


    // If it's the engine's turn, lock the board so humans can't move pieces and then play the chosen move of the engine.
    // If it's the human's turn, unlock the board for input
    private void checkTurn() {
        int currentPlayerType = (currentTurn == Piece.WHITE) ? whitePlayerType : blackPlayerType;

        if (currentPlayerType == PlayerType.ENGINE) {
            visualBoard.setIsBoardLocked(true);
            Chessbot currentBot = (currentTurn == Piece.WHITE) ? whiteBot : blackBot;

            int currentTimeLeft = (int) ((currentTurn == Piece.WHITE) ? whiteTimeLeft : blackTimeLeft);
            int currentIncrementTime = (currentTurn == Piece.WHITE) ? whiteIncrementTime : blackIncrementTime;

            // Runs the search on a background thread in order for the window to not freeze when the engine is thinking
            Thread searchThread = new Thread(() -> {
                long startTime = System.nanoTime();
                int move = currentBot.chooseMove(board, currentTimeLeft, currentIncrementTime);
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000L;

                // Adds a delay of about 600ms before the move is played to make the engine moves seem natural. If a move
                // is played in less than 600ms, pause the clock for the remainder of the delay, else just play the move
                long remainingDelayMs = Math.max(0L, 600 - elapsedMs);
                Platform.runLater(() -> {
                    // Discards the chosen move if the engine flagged while it was searching
                    if (isGameOver) {
                        return;
                    }

                    if (remainingDelayMs > 0) {
                        timer.stop();

                        PauseTransition pause = new PauseTransition(Duration.millis(remainingDelayMs));
                        pause.setOnFinished(_ -> {
                            timer.start();
                            playMove(move);
                        });

                        pause.play();
                    }

                    else {
                        playMove(move);
                    }
                });
            });

            // If the user closes the window while the engine is mid-search, the search thread is terminated too
            searchThread.setDaemon(true);

            searchThread.start();
        }

        else {
            visualBoard.setIsBoardLocked(false);
        }
    }


    // Handles human and engine moves
    public void playMove(int legalMove) {
        // Adds the increment time to the player who is just about to play their move
        if (currentTurn == Piece.WHITE) {
            whiteTimeLeft += whiteIncrementTime;
        }

        else {
            blackTimeLeft += blackIncrementTime;
        }

        board.makeMove(legalMove);
        currentTurn = board.getTurn();
        MoveGenerator.generate(board, moveList);
        inCheck = (Checks.calculateSquares(board, currentTurn) != 0L);

        visualBoard.sync(moveList);
        int startingSquare = Move.getStartingSquare(legalMove);
        int endingSquare = Move.getEndingSquare(legalMove);
        visualBoard.highlightPreviousMove(startingSquare, endingSquare);

        if (getGameResult()) { // If the game ended in any way
            triggerGameOverSequence();
        }

        else {
            lastTickNanos = System.nanoTime();
            sidebar.updateClocks(whiteTimeLeft, blackTimeLeft, currentTurn);
            SoundManager.playMoveSound(inCheck, Move.getFlag(legalMove));
            checkTurn();
        }
    }


    private boolean getGameResult() {
        return ResultDetector.isCheckmate(moveList, inCheck) || ResultDetector.isStalemate(moveList, inCheck) ||
                ResultDetector.isFiftyMoveRule(board) || ResultDetector.isInsufficientMaterial(board) ||
                ResultDetector.isThreefoldRepetition(board);
    }


    private void triggerGameOverSequence() {
        visualBoard.setDisable(true);
        timer.stop();
        isGameOver = true;
        SoundManager.playEndSound();

        PauseTransition delay = new PauseTransition(Duration.seconds(5));

        if (isEndlessMode) {
            delay.setOnFinished(_ -> restartGame());
        }

        else {
            delay.setOnFinished(_ -> Platform.exit());
        }

        delay.play();
    }


    private void restartGame() {
        if (whiteBot != null) {
            whiteBot.prepareNewGame();
        }

        if (blackBot != null) {
            blackBot.prepareNewGame();
        }

        // Swaps colors within player types, bots and time controls
        int tempPlayerType = whitePlayerType;
        whitePlayerType = blackPlayerType;
        blackPlayerType = tempPlayerType;

        Chessbot tempBot = whiteBot;
        whiteBot = blackBot;
        blackBot = tempBot;

        long tempWhiteInitialTime = whiteInitialTime;
        whiteInitialTime = blackInitialTime;
        blackInitialTime = tempWhiteInitialTime;

        int tempWhiteIncrementTime = whiteIncrementTime;
        whiteIncrementTime = blackIncrementTime;
        blackIncrementTime = tempWhiteIncrementTime;

        // Resets the engine board
        board = new Board();
        board.loadInitialPosition(ApplicationConfig.ENDLESS_MODE_FEN);

        // Resets the visual board
        visualBoard.setDisable(false);
        visualBoard.setBoard(board);

        // Flip the board if there is only one human player and they are black
        int boardPerspective = (whitePlayerType != blackPlayerType && blackPlayerType == PlayerType.HUMAN) ? Piece.BLACK : Piece.WHITE;
        visualBoard.flip(boardPerspective);

        MoveHandler moveHandler = new MoveHandler(visualBoard, this);
        visualBoard.attachMoveHandler(moveHandler);
        visualBoard.unhighlightPreviousMove();
        visualBoard.sync(null);

        startGame(whitePlayerType, blackPlayerType);
    }
}
