package com.chessbot.engine.utils;

import com.chessbot.application.ApplicationConfig;
import com.chessbot.engine.ai.Chessbot;
import com.chessbot.engine.core.Board;
import com.chessbot.engine.core.Piece;
import com.chessbot.engine.movegen.MoveGenerator;
import com.chessbot.engine.movegen.MoveList;
import com.chessbot.engine.search.Searcher;

import java.util.Scanner;

/*
A full UCI exchange looks like this (> = input, < = output):

> uci
< id name x
< id author x
< uciok
> isready
< readyokposition startpos moves f2f3
> ucinewgame
> position startpos moves e2e4
> go wtime 300000 btime 300000 winc 0 binc 0
< bestmove e7e5
> position startpos moves e2e4 e7e5 g1f3
> go wtime 294000 btime 298000 winc 0 binc 0
< bestmove b8c6
...
*/
public final class Uci {
    // The search runs on a separate thread so the engine can continue reading UCI input (primarily the stop command) while
    // thinking
    private static Thread searchThread;

    private Uci() {}


    // Runs a continuous loop that waits for input and routes the incoming UCI commands to the appropriate functions
    static void main() {
        Scanner scanner = new Scanner(System.in);

        Board board = new Board();
        board.loadPosition(ApplicationConfig.UCI_FEN);
        MoveList moveList = new MoveList();
        Chessbot chessBot = ApplicationConfig.NEW_VERSION_BOT;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s"); // Splits on every whitespace
            String command = parts[0];

            switch (command) {
                case "uci" -> {
                    System.out.println("id name Fobero 17.2");
                    System.out.println("id author Konstantinos Vrousgos");
                    System.out.println("uciok");
                }

                case "isready" -> System.out.println("readyok");

                case "ucinewgame" -> chessBot.prepareNewGame();

                case "position" -> parsePosition(parts, board, moveList);

                case "go" -> parseGo(parts, board, chessBot);

                case "stop" -> stopSearchThread();

                case "quit" -> {
                    return;
                }
            }
        }
    }


    // Matches a UCI move (e.g. "e2e4") against a Move int
    private static int matchMove(Board board, MoveList moveList, String uciMove) {
        MoveGenerator.generate(board, moveList);

        for (int i = 0; i < moveList.count; i += 1) {
            int move = moveList.moves[i];
            if (uciMove.equals(CoordinatesConverter.moveToString(move))) {
                return move;
            }
        }

        return 0;
    }


    // Handles: "position startpos", "position fen ...", "positions startpos moves e2e4 e7e5 ..." and "position fen ...
    // moves e2e4 e7e5 ...". Every turn, the board has to be rebuilt in this function because chess engines are expected
    // to be stateless
    private static void parsePosition(String[] parts, Board board, MoveList moveList) {
        // Finds the index of the word "moves" in the full command
        int movesWordIndex = -1;
        for (int i = 1; i < parts.length; i += 1) {
            if (parts[i].equals("moves")) {
                movesWordIndex = i;
                break;
            }
        }

        if (parts[1].equals("startpos")) {
            board.loadPosition(ApplicationConfig.UCI_FEN);
        }

        // Reassembles the split FEN parts back into a single String and loads it
        else if (parts[1].equals("fen")) {
            int fenEndIndex = (movesWordIndex == -1) ? parts.length : movesWordIndex;
            StringBuilder fenBuilder = new StringBuilder();
            for (int i = 2; i < fenEndIndex; i += 1) {
                fenBuilder.append(parts[i]).append(" ");
            }

            board.loadPosition(fenBuilder.toString().trim());
        }

        // Plays each move listed after "moves" onto the engine board
        if (movesWordIndex != -1) {
            for (int i = movesWordIndex + 1; i < parts.length; i += 1) {
                int matchedMove = matchMove(board, moveList, parts[i]);
                board.makeMove(matchedMove);
            }
        }
    }


    // Handles: "go wtime 300000 btime 300000 winc 5000 binc 5000", "go movetime 100000" (movetime is used for fixed timeframes,
    // it can be sent in correspondence games or custom challenges) and "go infinite"
    private static void parseGo(String[] parts, Board board, Chessbot chessbot) {
        int whiteTime = 0;
        int whiteIncrementTime = 0;
        int blackTime = 0;
        int blackIncrementTime = 0;
        int moveTime = -1;

        for (int i = 1; i < parts.length; i += 1) {
            switch (parts[i]) {
                case "wtime" -> whiteTime = Integer.parseInt(parts[++i]);
                case "winc" -> whiteIncrementTime = Integer.parseInt(parts[++i]);
                case "btime" -> blackTime = Integer.parseInt(parts[++i]);
                case "binc" -> blackIncrementTime = Integer.parseInt(parts[++i]);
                case "movetime" -> moveTime = Integer.parseInt(parts[++i]);
                case "infinite" -> moveTime = Integer.MAX_VALUE;
            }
        }

        int timeLeft = 0;
        int incrementTime = 0;

        if (moveTime == -1) {
            boolean isWhite = (board.getTurn() == Piece.WHITE);
            timeLeft = isWhite ? whiteTime : blackTime;
            incrementTime = isWhite ? whiteIncrementTime : blackIncrementTime;
        }

        // Have to be final for searchThread to work
        final int finalTimeLeft = timeLeft;
        final int finalIncrementTime = incrementTime;
        final int finalMoveTime = moveTime;

        searchThread = new Thread(() -> {
            int bestMove = chessbot.chooseMove(board, finalTimeLeft, finalIncrementTime, finalMoveTime);
            System.out.println("bestmove " + CoordinatesConverter.moveToString(bestMove));
        });

        searchThread.setDaemon(true);
        searchThread.start();
    }


    private static void stopSearchThread() {
        if (searchThread != null && searchThread.isAlive()) {
            Searcher.stopRequested = true;

            // Waits for the search thread to fully terminate before continuing. If interrupted while waiting, it restores
            // the interrupt flag so higher-level code can handle it
            try {
                searchThread.join();
            }

            catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
