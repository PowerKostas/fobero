package com.chessbot.engine.ai;

import com.chessbot.engine.core.Board;

// Interface that all the engine's versions have to adhere to
public interface Chessbot {
    // Returns the name of the version, used for logs
    String name();

    // Returns the chosen engine move
    int chooseMove(Board board, int timeLeft, int incrementTime, int moveTime);

    // Coordinates every bot related job before starting a new game
    default void prepareNewGame() {}
}
