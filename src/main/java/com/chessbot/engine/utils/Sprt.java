package com.chessbot.engine.utils;

// Sprt is used by Matchrunner to guarantee a statistically significant answer to if a new feature should be implemented.
// H0 (Null hypothesis): The elo difference caused by the new feature equals exactly the given value. H1 (Alternative
// hypothesis): The elo difference caused by the new feature equals exactly the other given value. If H1 is proven,
// the new feature is an improvement and should be implemented, the reverse is true if H0 is proven. Clarification:
// When H0 is proven, it means that SPRT is confident that H1 is false, not that it's confident that the elo difference
// equals exactly the given value, the reverse is true if H1 is proven
public class Sprt {
    // The results are from the perspective of the initially white bot
    private int wins = 0, draws = 0, losses = 0;

    // LLR (Log Likelihood Ratio) is the logarithmic score of how many times more likely is that H1 is true, instead of
    // H0, derived from the current w/d/l ratio
    private double llr = 0.0;

    // The logarithmic bounds of LLR. If LLR passes the upper bound, the test can guarantee, within the specified degree
    // of confidence, that H1 is true. The reverse is true if the lower bound is passed
    private final double lowerBound, upperBound;

    // The expected scores that correlate to the given elo0 and elo1 elo differences. If 0 is given to elo0, expectedScore0
    // would get a value of 0.5. This means that in a match against the previous version bot, the new version bot is expected
    // to score 50% of the total points. If 5 was given to elo1, expectedScore1 would get a value of 0.507 and the same
    // logic applies
    private final double expectedScore0, expectedScore1, expectedScoreAverage;


    // The elo0 and elo1 variables represent the elo differences in H0 and H1. The bigger the difference between elo0 and
    // elo1, the longer SPRT takes. Alpha and beta represent the tolerated false positive/negative error rates, the LLR
    // bounds are calculated from these. The error rates mean that there is an x% chance of pushing a neutral or microscopically
    // bad feature, sounds bad, but it's acceptable. The higher the tolerated error rate, the faster SPRT is
    public Sprt(double elo0, double elo1, double alpha, double beta) {
        lowerBound = Math.log(beta / (1.0 - alpha));
        upperBound = Math.log((1.0 - beta) / alpha);

        // The elo formula is used
        expectedScore0 = 1.0 / (1.0 + Math.pow(10.0, -elo0 / 400.0));
        expectedScore1 = 1.0 / (1.0 + Math.pow(10.0, -elo1 / 400.0));
        expectedScoreAverage = (expectedScore0 + expectedScore1) / 2.0;
    }


    public int getWins() { return wins; }

    public int getDraws() { return draws; }

    public int getLosses() { return losses; }

    public String getStatus() {
        if (llr >= upperBound) return "H1 Proven";
        if (llr <= lowerBound) return "H0 Proven";
        return "CONTINUE";
    }


    public void addResult(double result) {
        if (result == 1.0) wins += 1;
        else if (result == 0.5) draws += 1;
        else losses += 1;

        updateLlr();
    }


    private void updateLlr() {
        int resultsTotal = wins + draws + losses;

        // Adds 1 to all ratios to prevent zero or near-zero variance (Laplace Smoothing)
        double smoothedResultsTotal = resultsTotal + 3.0;
        double winsRatio = (wins + 1.0) / smoothedResultsTotal;
        double drawsRatio = (draws + 1.0) / smoothedResultsTotal;
        double lossesRatio = (losses + 1.0) / smoothedResultsTotal;

        double score = winsRatio + (drawsRatio / 2.0);
        double variance = winsRatio * Math.pow(1.0 - score, 2) + drawsRatio * Math.pow(0.5 - score, 2) + lossesRatio * Math.pow(0.0 - score, 2);

        // Losses pull LLR towards H0, wins pulls LLR towards H1. Draws pull the score towards 0.5. The smaller the variance,
        // the bigger the changes to LLR. As the results total grows, it scales the difference between the score and the
        // expected score average, driving LLR to a bound
        llr = (resultsTotal * (score - expectedScoreAverage) * (expectedScore1 - expectedScore0)) / variance;
    }


    // Used only in logs to know how close SPRT is to being done. 100% means that H1 has been proven, -100% means that H0
    // has been proven
    public double calculateProgressPercentage() {
        if (llr > 0) {
            return (llr / upperBound) * 100.0;
        }

        else if (llr == 0) {
            return 0.0;
        }

        else {
            return -(llr / lowerBound) * 100.0;
        }
    }
}
