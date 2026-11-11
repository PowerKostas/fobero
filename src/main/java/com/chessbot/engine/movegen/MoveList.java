package com.chessbot.engine.movegen;

import com.chessbot.engine.core.Constants;
import com.chessbot.engine.core.Move;

// A container of legal moves for a specific board state. A global array of legal moves would get overwritten at every new
// node in a tree search. To avoid this and ensure zero object allocations, instances of MoveList are pre-allocated per
// search depth (or held persistently by GameManager, MatchRunner, ...)
public class MoveList {
    // Holds the legal moves for the current object. The Move ints contain data about the moves, more information in the
    // Move class
    public final int[] moves = new int[Constants.MAX_LEGAL_MOVES_PER_POSITION];

    // Keeps track of how many legal moves are in the array
    public int count = 0;


    public void add(int move) {
        moves[count] = move;
        count += 1;
    }


    // The old moves will still be in memory, but they will get overwritten
    public void clear() {
        count = 0;
    }


    // Loops through all the legal moves to find a move whose starting and ending squares match the given starting and ending
    // squares
    public int searchLegalMove(int startingSquare, int endingSquare) {
        for (int i = 0; i < count; i += 1) {
            int legalMove = moves[i];
            if (Move.getStartingSquare(legalMove) == startingSquare && Move.getEndingSquare(legalMove) == endingSquare) {
                return legalMove;
            }
        }

        return -1;
    }


    // Loops through all the legal moves to find moves whose starting square matches the given starting square, used to
    // find all the piece's legal moves
    public long searchPieceLegalMoves(int startingSquare) {
        long pieceLegalMovesBitboard = 0L;

        for (int i = 0; i < count; i += 1) {
            int move = moves[i];
            if (Move.getStartingSquare(move) == startingSquare) {
                int endingSquare = Move.getEndingSquare(move);
                pieceLegalMovesBitboard |= (1L << endingSquare);
            }
        }

        return pieceLegalMovesBitboard;
    }
}
