package com.chessbot.engine.tuning;

import com.chessbot.application.ApplicationConfig;
import com.chessbot.engine.core.Board;
import com.chessbot.engine.core.Constants;
import com.chessbot.engine.core.Piece;
import com.chessbot.engine.evaluation.Evaluator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// The reason for the Boards array is to be able to use the already made evaluation function, this avoids having to maintain
// 2 separate evaluation functions or Board objects. The optimization method can take multiple hours to complete but because
// I will only need to tune the evaluation function parameters like 3 times, it's okay
public final class TexelTuner {
    private static Board[] boards;
    private static double[] results; // 0.0 = Black win, 0.5 Draw, 1.0 White win
    private static final double SIGMOID_SCALE = Math.log(10.0) / 400.0;

    private TexelTuner() {}


    // Extracts the fens/results from the dataset. For speed purposes, boards objects are preloaded using each FEN
    private static void loadDataset() {
        List<Board> tempBoards = new ArrayList<>();
        List<Double> tempResults = new ArrayList<>();

        try (Stream<String> lines = Files.lines(Path.of("src/main/resources/com/chessbot/datasets/quiet-labeled.epd"))) {
            lines.forEach(line -> {
                String[] whitespaceSplit = line.split("\\s");
                String fen = whitespaceSplit[0] + " " + whitespaceSplit[1] + " " + whitespaceSplit[2] + " " + whitespaceSplit[3];

                String[] quotationSplit = line.split("\"");
                String resultString = quotationSplit[1];

                double result;
                switch (resultString) {
                    case "1-0":
                        result = 1.0;
                        break;

                    case "0-1":
                        result = 0.0;
                        break;

                    case "1/2-1/2":
                        result = 0.5;
                        break;

                    default:
                        return;
                }

                Board board = new Board(0);
                board.loadPosition(fen);
                tempBoards.add(board);
                tempResults.add(result);
            });
        }

        catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        boards = tempBoards.toArray(new Board[0]);
        results = tempResults.stream().mapToDouble(Double::doubleValue).toArray();
    }


    // Returns the evaluation of the given FEN's position, it's positive/negative when white/black is winning
    private static int evaluate(Board board) {
        int evaluation = Evaluator.evaluate(board, true);
        return (board.getTurn() == Piece.WHITE) ? evaluation : -evaluation;
    }


    // The elo formula is used for the sigmoid function
    private static double sigmoid(int evaluation) {
        return 1.0 / (1.0 + Math.exp(-evaluation * SIGMOID_SCALE));
    }


    // Based on every FEN in the dataset, compute the Mean Squared Error between the engine's evaluations and the actual
    // results. Because they are 2 different metrics, a sigmoid function is used to turn the evaluations to win probabilities
    // from the perspective of white. The loop is done in parallel for speed. Additionally, scores have to be recalculated
    // here every time because if a Material or PieceSquareTables parameter changes, the scores are out of sync
    private static double computeMSE() {
        return IntStream.range(0, boards.length)
                .parallel()
                .mapToDouble(i -> {
                    Board board = boards[i];
                    board.calculateInitialScores();
                    double diff = results[i] - sigmoid(evaluate(board));
                    return diff * diff;
                })
                .average()
                .orElse(0.0);
    }


    // Local search, optimizes the evaluation function parameters by testing incremental changes repeatedly. If MSE improves
    // over the parameter tolerance when a step size is added/subtracted, the change is kept and another pass over all the
    // parameters is needed. This goes on until MSE can't improve over the pass tolerance anymore, even with the smallest
    // step size
    static void main() {
        loadDataset();
        System.out.printf("Loaded %d positions%n%n", boards.length);

        // Dynamic step sizing (steps are centipawn increments). It executes bigger leaps first and when a pass yields zero
        // improvements to MSE, it narrows down
        double bestError = computeMSE();
        for (int step = ApplicationConfig.STARTING_STEP_SIZE; step >= 1; step /= 2) {
            boolean improved = true;
            int pass = 0;

            while (improved) {
                improved = false;
                pass += 1;
                double passStartError = bestError;

                System.out.printf("Step: %d | Pass: %d | MSE: %f%n", step, pass, bestError);

                for (int i = 0; i < Constants.EVAL_PARAMS_COUNT; i += 1) {
                    // Skips the parameter if it shouldn't be tuned
                    if (Arrays.binarySearch(Constants.UNTUNABLE_EVAL_PARAM_INDICES, i) >= 0) {
                        continue;
                    }

                    int originalEvalParam = Constants.getEvalParam(i);

                    Constants.setEvalParam(i, originalEvalParam + step);
                    double errorUp = computeMSE();
                    if (errorUp < bestError - ApplicationConfig.MIN_IMPROVEMENT_PER_PARAMETER) {
                        bestError = errorUp;
                        improved = true;
                        continue;
                    }

                    Constants.setEvalParam(i, originalEvalParam - step);
                    double errorDown = computeMSE();
                    if (errorDown < bestError - ApplicationConfig.MIN_IMPROVEMENT_PER_PARAMETER) {
                        bestError = errorDown;
                        improved = true;
                        continue;
                    }

                    // If neither direction helped, revert back to the original state
                    Constants.setEvalParam(i, originalEvalParam);
                }

                if (passStartError - bestError < ApplicationConfig.MIN_IMPROVEMENT_PER_PASS) {
                    improved = false;
                }
            }
        }

        // Prints the optimized evaluation function parameters so I can write them down
        for (int i = 0; i < Constants.EVAL_PARAMS_COUNT; i += 1) {
            System.out.printf("%nEVAL_PARAMS[%d] = %d", i, Constants.getEvalParam(i));
        }
    }
}
