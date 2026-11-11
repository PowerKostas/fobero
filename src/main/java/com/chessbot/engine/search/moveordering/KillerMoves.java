package com.chessbot.engine.search.moveordering;

import com.chessbot.engine.core.Constants;

// Killer moves are quiet moves that caused a beta cutoff at a given ply. The idea is, if at that ply a move was very good
// in one position, it will likely be very good in other positions too, so it's tried early
public class KillerMoves {
    /**
     * In the move ordering score ranking, Killer Moves should be above quiet moves and below captures/promotions. The first
     * killer move should have a higher score than the second one
     * @see Constants#FIRST_KILLER_MOVE_SCORE
     * @see Constants#SECOND_KILLER_MOVE_SCORE
    */

    private final int[] firstKillerMoves = new int[Constants.MAX_SEARCH_DEPTH];
    private final int[] secondKillerMoves = new int[Constants.MAX_SEARCH_DEPTH];


    // Stores a newly found killer move at the given ply, shifts the old first killer move to the second slot
    public void store(int move, int pliesFromRoot) {
        if (move != firstKillerMoves[pliesFromRoot]) {
            secondKillerMoves[pliesFromRoot] = firstKillerMoves[pliesFromRoot];
            firstKillerMoves[pliesFromRoot] = move;
        }
    }


    public int score(int move, int pliesFromRoot) {
        if (move == firstKillerMoves[pliesFromRoot]) {
            return Constants.FIRST_KILLER_MOVE_SCORE;
        }

        if (move == secondKillerMoves[pliesFromRoot]) {
            return Constants.SECOND_KILLER_MOVE_SCORE;
        }

        return 0;
    }


    public boolean isKillerMove(int move, int pliesFromRoot) {
        return move == firstKillerMoves[pliesFromRoot] || move == secondKillerMoves[pliesFromRoot];
    }
}
