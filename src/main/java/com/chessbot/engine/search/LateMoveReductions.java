package com.chessbot.engine.search;

import com.chessbot.engine.core.Constants;

public final class LateMoveReductions {
    // The precomputed REDUCTION_PLIES table holds the number of plies to reduce a search by. It grows with both the remaining
    // depth and how late the move is in the ordering
    private static final int[][] REDUCTION_PLIES = new int[Constants.MAX_SEARCH_DEPTH][Constants.MAX_LEGAL_MOVES_PER_POSITION];
    static {
        for (int depth = 1; depth < Constants.MAX_SEARCH_DEPTH; depth += 1) {
            for (int moveIndex = 1; moveIndex < Constants.MAX_LEGAL_MOVES_PER_POSITION; moveIndex += 1) {
                REDUCTION_PLIES[depth][moveIndex] = (int) (Constants.LMR_BASE + Math.log(depth) * Math.log(moveIndex) / Constants.LMR_DIVISOR);
            }
        }
    }

    private LateMoveReductions() {}


    public static int calculateReducedDepth(int depth, int moveIndex) {
        int pliesToReduce = REDUCTION_PLIES[depth][moveIndex];

        // Clamps reducedDepth between 1 (in order to not drop straight into the Quiescence Search) and depth - 2 (to guarantee
        // that the LMR search is shallower than the full depth - 1 search)
        return Math.clamp(depth - 1 - pliesToReduce, 1, depth - 2);
    }
}
