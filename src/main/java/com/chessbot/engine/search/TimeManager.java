package com.chessbot.engine.search;

import com.chessbot.engine.core.Constants;

// Every number is measured in ms
public final class TimeManager {
    private TimeManager() {}


    // The soft time limit is checked at the end of each Iterative Deepening depth, if it's surpassed, the search stops
    // and the incomplete data is discarded
    public static long calculateSoftTimeLimit(int timeLeft, int incrementTime) {
        int usableTime = Math.max(1, timeLeft - Constants.MOVE_OVERHEAD);

        // Calculates the base time to allocate on this move by assuming a constant number of moves remaining in the game
        // and by allowing a constant percentage of the increment time to be used
        int baseTimeAllocation = (usableTime / Constants.ESTIMATED_MOVES_REMAINING) + ((incrementTime * Constants.INCREMENT_USAGE_PERCENT) / 100);

        // Because each new depth requires about 1.5 to 2.5 times more search time than the previous depth (branching factor),
        // the soft time limit is set to x percent of the base time allocation. For example for SOFT_TIME_LIMIT_PERCENT
        // = 50 and branching factor = 2, if 55% of the time is already used, the search should stop (e.g. 3 + 7 + 15 +
        // 30 = 55, 55 + 30 * 2 = 115, 115 > 100), but if only 45% of the time is already used, the search should continue
        // (e.g. 3 + 6 + 12 + 24 = 45, 45 + 24 * 2 = 93, 93 < 100)
        int softTimeLimit = (baseTimeAllocation * Constants.SOFT_TIME_LIMIT_PERCENT) / 100;

        // Doesn't let the soft time limit exceed a certain percent of the remaining clock
        long maxSoftTimeLimit = Math.max(1, (usableTime * Constants.MAX_SOFT_TIME_LIMIT_PERCENT) / 100);

        return Math.clamp(softTimeLimit, 1, maxSoftTimeLimit);
    }


    // The hard time limit is checked inside the search every 2048 nodes, if a single depth explodes in width, the search
    // stops and the incomplete data is discarded
    public static long calculateHardTimeLimit(long timeLeft, long softTimeLimit) {
        long usableTime = Math.max(1, timeLeft - Constants.MOVE_OVERHEAD);

        // The hard time limit is x times bigger than the soft time limit
        long hardTimeLimit = (softTimeLimit * Constants.HARD_TIME_LIMIT_MULTIPLIER_PERCENT) / 100;

        long maxHardTimeLimit = Math.max(1, (usableTime * Constants.MAX_HARD_TIME_LIMIT_PERCENT) / 100);

        return Math.clamp(hardTimeLimit, 1, maxHardTimeLimit);
    }
}
