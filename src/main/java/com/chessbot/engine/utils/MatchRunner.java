package com.chessbot.engine.utils;

import com.chessbot.application.ApplicationConfig;
import com.chessbot.engine.ai.Chessbot;
import com.chessbot.engine.core.Board;
import com.chessbot.engine.core.Piece;
import com.chessbot.engine.movegen.MoveGenerator;
import com.chessbot.engine.movegen.MoveList;
import com.chessbot.engine.movegen.utils.Checks;

// Runs a certain amount of games between 2 versions of the engine in order to check if a change in the evaluation/search
// function had positive or negative impact. SPRT is utilized for this
public final class MatchRunner {
    private MatchRunner() {}


    static void main() {
        Sprt sprt = new Sprt(ApplicationConfig.ELO_0, ApplicationConfig.ELO_1, ApplicationConfig.ALPHA, ApplicationConfig.BETA);
        MoveList moveList = new MoveList();
        Chessbot previousVersionBot = ApplicationConfig.PREVIOUS_VERSION_BOT;
        Chessbot newVersionBot = ApplicationConfig.NEW_VERSION_BOT;

        System.out.printf("Starting SPRT Match | %s (%.2fs + %.2fs) vs %s (%.2fs + %.2fs)%n%n",
                previousVersionBot.name(), ApplicationConfig.BLACK_INITIAL_TIME_MS / 1000.0, ApplicationConfig.BLACK_INCREMENT_TIME_MS / 1000.0,
                newVersionBot.name(), ApplicationConfig.WHITE_INITIAL_TIME_MS / 1000.0, ApplicationConfig.WHITE_INCREMENT_TIME_MS / 1000.0
        );

        // Runs a match until SPRT has enough information to decide if the change was positive/negative
        long matchStartTime = System.nanoTime();
        int game = 0;
        while (sprt.getStatus().equals("CONTINUE")) {
            Board board = new Board();
            board.loadPosition(ApplicationConfig.MATCH_RUNNER_FEN);
            previousVersionBot.prepareNewGame();
            newVersionBot.prepareNewGame();

            // Swaps the bots' colors each game
            boolean isEvenGame = (game % 2 == 0);
            Chessbot whiteBot = isEvenGame ? newVersionBot : previousVersionBot;
            Chessbot blackBot = isEvenGame ? previousVersionBot : newVersionBot;

            // Also swaps the bots' time controls after
            int whiteTimeLeft = isEvenGame ? ApplicationConfig.WHITE_INITIAL_TIME_MS : ApplicationConfig.BLACK_INITIAL_TIME_MS;
            int whiteIncrementTime = isEvenGame ? ApplicationConfig.WHITE_INCREMENT_TIME_MS : ApplicationConfig.BLACK_INCREMENT_TIME_MS;
            int blackTimeLeft = isEvenGame ? ApplicationConfig.BLACK_INITIAL_TIME_MS : ApplicationConfig.WHITE_INITIAL_TIME_MS;
            int blackIncrementTime = isEvenGame ? ApplicationConfig.BLACK_INCREMENT_TIME_MS : ApplicationConfig.WHITE_INCREMENT_TIME_MS;

            // Runs a game
            double testBotScore;
            while (true) {
                MoveGenerator.generate(board, moveList);
                boolean inCheck = Checks.calculateSquares(board, board.getTurn()) != 0L;

                if (ResultDetector.isCheckmate(moveList, inCheck)) {
                    // Because the turn changes after every move, if there is checkmate, and it's black's turn, it means
                    // that white is the one that delivered the checkmate
                    boolean whiteWon = (board.getTurn() == Piece.BLACK);
                    testBotScore = (whiteWon == isEvenGame) ? 1.0 : 0.0;
                    break;
                }

                if (ResultDetector.isStalemate(moveList, inCheck) || ResultDetector.isFiftyMoveRule(board) ||
                        ResultDetector.isInsufficientMaterial(board) || ResultDetector.isThreefoldRepetition(board)
                ) {
                    testBotScore = 0.5;
                    break;
                }

                boolean isWhite = (board.getTurn() == Piece.WHITE);
                Chessbot activeBot = isWhite ? whiteBot : blackBot;
                int currentTimeLeft = isWhite ? whiteTimeLeft: blackTimeLeft;
                int currentIncrementTime = isWhite ? whiteIncrementTime : blackIncrementTime;

                long turnStartTime = System.nanoTime();
                int move = activeBot.chooseMove(board, currentTimeLeft, currentIncrementTime, -1);
                int turnElapsedMs = (int) ((System.nanoTime() - turnStartTime) / 1_000_000L);

                if (isWhite) {
                    whiteTimeLeft -= turnElapsedMs;

                    // White flagged and black won
                    if (whiteTimeLeft <= 0) {
                        testBotScore = isEvenGame ? 0.0 : 1.0;
                        break;
                    }

                    whiteTimeLeft += whiteIncrementTime;
                }

                else {
                    blackTimeLeft -= turnElapsedMs;

                    // Black flagged and white won
                    if (blackTimeLeft <= 0) {
                        testBotScore = isEvenGame ? 1.0 : 0.0;
                        break;
                    }

                    blackTimeLeft += blackIncrementTime;
                }

                board.makeMove(move);

                // System.out.printf("Move Played | %s: %.2fs left | %s: %.2fs left%n", blackBot.name(), blackTimeLeft / 1000.0, whiteBot.name(), whiteTimeLeft / 1000.0);
            }

            sprt.addResult(testBotScore);
            game += 1;

            System.out.printf("Game %d | %s: %d | Draws: %d | %s: %d | Progress: %.2f%%%n",
                    game, previousVersionBot.name(), sprt.getLosses(), sprt.getDraws(), newVersionBot.name(), sprt.getWins(),
                    sprt.calculateProgressPercentage()
            );
        }

        long endTime = System.nanoTime();
        System.out.printf("%nMatch Finished | Result: %s | Time Taken: %.2f seconds", sprt.getStatus(), (endTime - matchStartTime) / 1_000_000_000.0);
    }
}
