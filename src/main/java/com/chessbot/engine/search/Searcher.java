package com.chessbot.engine.search;

import com.chessbot.engine.core.Board;
import com.chessbot.engine.core.Constants;
import com.chessbot.engine.core.Move;
import com.chessbot.engine.core.Piece;
import com.chessbot.engine.evaluation.Evaluator;
import com.chessbot.engine.evaluation.Material;
import com.chessbot.engine.movegen.MoveGenerator;
import com.chessbot.engine.movegen.MoveList;
import com.chessbot.engine.movegen.utils.Checks;
import com.chessbot.engine.search.moveordering.KillerMoves;
import com.chessbot.engine.search.moveordering.MoveOrderer;
import com.chessbot.engine.utils.CoordinatesConverter;
import com.chessbot.engine.utils.ResultDetector;

// Searcher is a standard class instead of a utility one in order to be able to create multiple instances if needed
public class Searcher {
    private final Board board;

    // Array to hold the pre-allocated move list objects, one object per depth level is needed
    private final MoveList[] moveListPool = new MoveList[Constants.MAX_SEARCH_DEPTH];

    private final TranspositionTable tt;

    private final KillerMoves killerMoves = new KillerMoves();

    // Boolean to apply experimental features only to specific bot versions
    private final boolean experimentalVersion;

    private int nodesSearched;

    private boolean isHardTimeLimitExpired;

    private long hardTimeLimitEndTime;


    public Searcher(Board board, TranspositionTable tt, boolean experimentalVersion) {
        this.board = board;

        for (int i = 0; i < Constants.MAX_SEARCH_DEPTH; i += 1) {
            this.moveListPool[i] = new MoveList();
        }

        this.tt = tt;
        this.experimentalVersion = experimentalVersion;
    }


    /*
    SEARCH ARCHITECTURE:

    1. Negamax: The core tree search algorithm. It's based on the Minimax algorithm which determines an evaluation after
       a certain number of moves. It assumes best play according to the evaluation function. The difference of Negamax is
       that it translates everything into the current player's perspective instead of having to handle 2 players. It traverses
       the possible moves depth-first, assigning a value to each leaf node based on the evaluation function. Each parent
       node takes the maximum of the negated values returned by its children. This allows it to pick the move that results
       in the worst possible position for the current opponent. In the example diagram, every node is a position's evaluation
       from the perspective of the player to the right and every line is a move that caused the position

                   2               | Black
           /       |       \
          6        9       -2      | White
        / | \    / | \    / | \
       4 -6  0  -8 7 -9  4  2  6   | Black

    2. Alpha-Beta Pruning: In a Negamax context, alpha represents the best evaluation the current player has already guaranteed
       elsewhere in the search. Beta represents the best evaluation the current opponent has already guaranteed elsewhere
       in the search. In other words, for every child evaluation of the current player, alpha is the floor and beta is the
       ceiling. If an evaluation is below the floor, it's ignored, if it's at or above the ceiling, the branch is pruned
       because the opponent would never choose to go down this path. Alpha and beta values are swapped and negated at every
       depth to accurately portray the shifting perspectives. In the Negamax example diagram, when the 0 node is reached,
       alpha = 6 and beta = inf for white. When the -8 node is reached, alpha = -inf and beta = 6 for white, that's why
       the remaining 7 and -9 nodes will be pruned. A fail-soft implementation is used. If a move scores 300 but beta is
       100, the search will return 300 to the parent node. The returned evaluation should mean one of three things:

       1. In a Fail-Low node (evaluation <= alpha): An upper bound value should be returned because the position is worth
       at most this much. No move that the current player tried was better than alpha, so the true value can't be higher
       than the evaluation

       2. In a Fail-High node (evaluation >= beta): A lower bound value should be returned because the position is worth
       at least this much. A move was good enough to cause a beta-cutoff and the remaining moves were never looked at, so
       the true value could be even higher

       3. In a PV-Node (Principal Variation = The sequence of moves that the program considers best and therefore expects
       to be played, it can be superseded) (alpha < evaluation < beta): The evaluation is exact

       This extra data will be useful later on

    3. Move Ordering: If the strong moves are evaluated first, massive branches of the search tree can be pruned thanks
       to the way Alpha-Beta Pruning works. A score based on different heuristics is incorporated in the Move int. The node
       with the biggest score is searched first every time. The heuristics used are: MVV-LVA and Promotions, more information
       on the respective classes

    4. Quiescence Search: Performs a more limited search at the end of the normal search. Without it the engine blunders
       all the time. The reason for that is that it doesn't know if the opponent can just capture its queen one move after
       the searched depth, for example. If not in check, the limited search only includes captures and queen promotions,
       otherwise all legal moves have to be searched in order to find check evasions. QS terminates when a quiet position
       is reached (no captures, promotions or check evasions left), when a beta-cutoff occurs or when the maximum search
       depth is reached

    5. Iterative Deepening: Instead of searching directly to the target depth, search depth 1 and depth 2 ... and target
       depth. Searching multiple times seems bad but it performs better. The main reasons are improved move ordering (the
       best move found at depth n-1 is almost always the best move at depth n) and Transposition Table warming. Additionally
       effective time restrictions can now be implemented

    6. Principal Variation Search: With good move ordering the first move searched is usually the best one, so it's searched
       with a full window (-beta, -alpha) to get its exact evaluation. All other moves only need to be confirmed as not
       better than the best move found so far, so they are searched with a null window (-alpha - 1, -alpha [beta is as close
       to alpha as possible]) to get their bounds. If a search with a null window unexpectedly fails high, its exact evaluation
       is not known. If it's a PV-Node, the move is re-searched with a full window, otherwise, a re-search is not needed
       because the evaluation is already high enough to cause a beta-cutoff

    7. Late Move Reductions: Saves time by reducing the search depth of moves that are ordered closer to the end, assuming
       good move ordering. The first few depths are searched fully, the rest are searched at an increasingly reduced number
       of plies. The moves are only re-searched if the reduced search fails high. LMR works alongside PVS, so the on fail
       re-search flow is: LMR Reduced Depth Null Window Search -> PVS Full Depth Null Window Search -> PVS Full Depth Full
       Window Search. It was tested that the engine performs better when it does not reduce moves if they are captures,
       promotions or killer moves or if the side to move is in check. Instead of not reducing moves in the above conditions,
       heuristic ways to reduce less were tested, but the results were negative. Also negative were the results of not reducing
       the first few moves
    */

    // Entry function for the Searcher class. Called once per turn, responsible for searching the root and returning the
    // chosen move. It's separated from search() because only this function needs to return a move. If there are 0 legal
    // moves in this position, moveList.moves[0] would return a null, if it's the first move of the game, or a random illegal
    // move, anywhere else. But this isn't a problem because the end of the game is already handled by whichever class originally
    // called this function
    public int searchRoot(int maxDepth, int timeLeft, int incrementTime) {
        MoveList moveList = moveListPool[0];
        MoveGenerator.generate(board, moveList);

        // If there is only 1 legal move, return it immediately
        if (moveList.count == 1) {
            return moveList.moves[0];
        }

        nodesSearched = 1;
        isHardTimeLimitExpired = false;

        long softTimeLimit = TimeManager.calculateSoftTimeLimit(timeLeft, incrementTime);
        long hardTimeLimit = TimeManager.calculateHardTimeLimit(timeLeft, softTimeLimit);

        long startTime = System.nanoTime();
        long softEndTime = startTime + (softTimeLimit * 1_000_000L);
        hardTimeLimitEndTime = startTime + (hardTimeLimit * 1_000_000L);

        long zobristKey = board.getCurrentZobristKey();
        int bestMove = moveList.moves[0];

        // Only used for printing the evaluation at the end of a search
        // int finalEvaluation = 0;

        for (int currentDepth = 1; currentDepth <= maxDepth; currentDepth += 1) {
            MoveOrderer.prepareMoveScores(board, moveList, Move.getMoveWithoutScore(bestMove), null, 0);

            int alpha = -Constants.INFINITY_SCORE;
            int beta = Constants.INFINITY_SCORE;
            int bestEvaluation = -Constants.INFINITY_SCORE;
            for (int i = 0; i < moveList.count; i += 1) {
                MoveOrderer.selectNextMove(moveList, i);

                // Prints the root moves, useful for debugging move ordering
                // System.out.printf("%s%s%n%n", CoordinatesConverter.indexToString(Move.getStartingSquare(moveList.moves[i])), CoordinatesConverter.indexToString(Move.getEndingSquare(moveList.moves[i])));

                int move = moveList.moves[i];
                int undo = board.makeMove(move);

                int evaluation;
                if (i == 0) {
                    evaluation = -search(currentDepth - 1, 1, -beta, -alpha);
                }

                else {
                    evaluation = -search(currentDepth - 1, 1, -alpha - 1, -alpha);

                    if (evaluation > alpha && !isHardTimeLimitExpired) {
                        evaluation = -search(currentDepth - 1, 1, -beta, -alpha);
                    }
                }

                board.unmakeMove(move, undo);

                if (isHardTimeLimitExpired) {
                    break;
                }

                if (evaluation > bestEvaluation) {
                    alpha = evaluation;
                    bestEvaluation = evaluation;
                    bestMove = move;
                }
            }

            if (isHardTimeLimitExpired) {
                break;
            }

            tt.store(zobristKey, 0, Move.getMoveWithoutScore(bestMove), currentDepth, TranspositionTable.EXACT_BOUND, bestEvaluation);

            // String pvString = buildPrincipalVariationString(currentDepth);
            // System.out.printf("Current Depth: %d | Evaluation: %d | Principal Variation: %s%n%n", currentDepth, bestEvaluation, pvString);

            // finalEvaluation = bestEvaluation;

            // If a checkmate is found, search() returns -Constants.CHECKMATE_EVALUATION + pliesFromRoot. From the root's
            // perspective, the number of plies to mate can be extracted with Constants.CHECKMATE_EVALUATION - Math.abs(bestEvaluation).
            // If it has already searched deep enough to prove no shorter mate exists, stop the search
            int pliesToMate = Constants.CHECKMATE_EVALUATION - Math.abs(bestEvaluation);
            if (pliesToMate <= currentDepth) {
                break;
            }

            if (System.nanoTime() >= softEndTime) {
                break;
            }
        }

        // System.out.printf("Nodes Searched: %d%n", nodesSearched);

        // System.out.printf("Evaluation: %d%n%n", finalEvaluation);

        return bestMove;
    }

    // Recursive function for every depth below the root, returns an evaluation
    private int search(int depth, int pliesFromRoot, int alpha, int beta) {
        nodesSearched += 1;

        if ((nodesSearched & 2047) == 0 && System.nanoTime() >= hardTimeLimitEndTime) {
            isHardTimeLimitExpired = true;
            return 0;
        }

        if (ResultDetector.isFiftyMoveRule(board) || ResultDetector.isInsufficientMaterial(board) || ResultDetector.isRepetition(board)) {
            return 0;
        }

        if (depth == 0) {
            return quiescenceSearch(pliesFromRoot, alpha, beta);
        }

        long zobristKey = board.getCurrentZobristKey();
        long ttEntryData = tt.probeEntryData(zobristKey);
        int ttBestMove = TranspositionTable.unpackBestMove(ttEntryData);

        // If it's a null window (which means this is a non-PV node) and there is a stored evaluation in the TT, return
        // it. PV-Nodes need to be searched with a full window, that's why they are excluded here
        if ((beta - alpha) == 1) {
            if (ttEntryData != TranspositionTable.NO_TT_ENTRY) {
                int ttEvaluation = TranspositionTable.resolveEvaluation(ttEntryData, pliesFromRoot, depth, alpha, beta);
                if (ttEvaluation != TranspositionTable.UNUSABLE_EVALUATION) {
                    return ttEvaluation;
                }
            }
        }

        MoveList moveList = moveListPool[pliesFromRoot];
        MoveGenerator.generate(board, moveList);

        boolean inCheck = Checks.calculateSquares(board, board.getTurn()) != 0L;

        // If this move results in checkmate, return a terrible evaluation. The terrible evaluation is adjusted by the plies
        // from root number, this ensures that the engine favors the current player getting mated in 5 over getting mated
        // in 1. If this move results in stalemate, return an evaluation of 0
        if (moveList.count == 0) {
            return inCheck ? -Constants.CHECKMATE_EVALUATION + pliesFromRoot : 0;
        }

        MoveOrderer.prepareMoveScores(board, moveList, ttBestMove, killerMoves, pliesFromRoot);

        int bestEvaluation = -Constants.INFINITY_SCORE;
        int bestMove = 0;
        byte bound = TranspositionTable.UPPER_BOUND;
        for (int i = 0; i < moveList.count; i += 1) {
            MoveOrderer.selectNextMove(moveList, i);
            int move = moveList.moves[i];
            int undo = board.makeMove(move);

            int evaluation = 0;
            if (i == 0) {
                evaluation = -search(depth - 1, pliesFromRoot + 1, -beta, -alpha);
            }

            else {
                boolean searchFullDepth = true;
                boolean lmrConditions = depth >= Constants.LMR_MIN_DEPTH && Move.getFlag(move) < Move.FLAG_CAPTURE &&
                                        !killerMoves.isKillerMove(Move.getMoveWithoutScore(move), pliesFromRoot) && !inCheck;

                if (lmrConditions) {
                    int reducedDepth = LateMoveReductions.calculateReducedDepth(depth, i);
                    evaluation = -search(reducedDepth, pliesFromRoot + 1, -alpha - 1, -alpha);
                    searchFullDepth = (evaluation > alpha);
                }

                if (searchFullDepth && !isHardTimeLimitExpired) {
                    evaluation = -search(depth - 1, pliesFromRoot + 1, -alpha - 1, -alpha);
                }

                if (evaluation > alpha && evaluation < beta && !isHardTimeLimitExpired) {
                    evaluation = -search(depth - 1, pliesFromRoot + 1, -beta, -alpha);
                }
            }

            board.unmakeMove(move, undo);

            if (isHardTimeLimitExpired) {
                return 0;
            }

            if (evaluation > bestEvaluation) {
                bestEvaluation = evaluation;

                if (evaluation > alpha) {
                    alpha = evaluation;
                    bestMove = Move.getMoveWithoutScore(move);
                    bound = TranspositionTable.EXACT_BOUND;

                    if (alpha >= beta) {
                        // Only quiet moves are stored as Killer Moves because captures and promotions are covered by other
                        // heuristics
                        if (Move.getFlag(move) < Move.FLAG_CAPTURE) {
                            killerMoves.store(bestMove, pliesFromRoot);
                        }

                        tt.store(zobristKey, pliesFromRoot, bestMove, depth, TranspositionTable.LOWER_BOUND, bestEvaluation);
                        return bestEvaluation;
                    }
                }
            }
        }

        tt.store(zobristKey, pliesFromRoot, bestMove, depth, bound, bestEvaluation);

        return bestEvaluation;
    }

    // Since quiescence search has no depth limit, only pliesFromRoot is needed. Because, when not in check, QS only searches
    // captures and queen promotions, it doesn't store entries in the transposition table
    private int quiescenceSearch(int pliesFromRoot, int alpha, int beta) {
        nodesSearched += 1;

        if ((nodesSearched & 2047) == 0 && System.nanoTime() >= hardTimeLimitEndTime) {
            isHardTimeLimitExpired = true;
            return 0;
        }

        if (ResultDetector.isFiftyMoveRule(board) || ResultDetector.isInsufficientMaterial(board) || ResultDetector.isRepetition(board)) {
            return 0;
        }

        // Because a quiescence search can theoretically follow a long chain of moves, return the static evaluation if the
        // search exceeds the given max depth
        if (pliesFromRoot >= Constants.MAX_SEARCH_DEPTH) {
            return Evaluator.evaluate(board, experimentalVersion);
        }

        // Initializes a stand-pat value. Stand-pat is the evaluation of the board after the opponent has made their move
        // and before the current player makes theirs. In other words the baseline evaluation
        int standPat = 0;

        // If in check using stand-pat is not allowed. There are 2 reasons for that. Firstly, we are not sure that there
        // is a move that can match alpha, in many positions a check can mean a serious threat that cannot be resolved.
        // Secondly, stand-pat assumes that even if we finish searching all moves and none of them increase alpha, one
        // of the quiet moves can most likely increase alpha. But because instead of a limited square, a full search is
        // executed when in check, the above can't be valid
        boolean inCheck = Checks.calculateSquares(board, board.getTurn()) != 0L;
        int bestEvaluation = -Constants.INFINITY_SCORE;
        if (!inCheck) {
            standPat = Evaluator.evaluate(board, experimentalVersion);

            // If the baseline evaluation is already so high, the opponent would never choose to go down this path, for that
            // reason the branch is just pruned
            if (standPat >= beta) {
                return standPat;
            }

            // Checks if ANY move can improve alpha. Big delta represents the best theoretical move which is a max value
            // queen capture plus a safety margin plus a potential queen promotion. If not even that move can save the position,
            // it's a truly hopeless node, and it's pruned early. In MoveOrdering.MVV-LVA, a queen capture with a pawn gets
            // a score of 40, in MoveOrdering.Promotions, a queen promotion gets a slightly lower score. That scale is used
            // to calculate the increment to big delta when a pawn can promote. This method doesn't seem to have a noticeable
            // impact on performance, but it doesn't hurt it either
            int BIG_DELTA = Constants.getEvalParam(Constants.MG_PIECE_VALUES_START_INDEX + Piece.QUEEN) + Constants.SAFETY_MARGIN;

            boolean canPromote;
            if (board.getTurn() == Piece.WHITE) {
                // White pawn on the 7th rank
                canPromote = (board.getBitboard(Piece.WHITE, Piece.PAWN) & 0x00FF000000000000L) != 0L;
            }

            else {
                // Black pawn on the 2nd rank
                canPromote = (board.getBitboard(Piece.BLACK, Piece.PAWN) & 0x000000000000FF00L) != 0L;
            }

            if (canPromote) {
                BIG_DELTA += (Constants.getEvalParam(Constants.MG_PIECE_VALUES_START_INDEX + Piece.QUEEN) * Constants.PROMOTION_SCORES[Piece.QUEEN - 1]) / 40;
            }

            if (standPat + BIG_DELTA <= alpha) {
                return standPat + BIG_DELTA;
            }

            // Updates alpha because the baseline might be better than the current guaranteed alpha
            if (standPat > alpha) {
                alpha = standPat;
            }

            bestEvaluation = standPat;
        }

        MoveList moveList = moveListPool[pliesFromRoot];
        MoveGenerator.generate(board, moveList);

        if (moveList.count == 0) {
            return inCheck ? -Constants.CHECKMATE_EVALUATION + pliesFromRoot : 0;
        }

        // Only captures and queen promotions are worth looking at a quiescence search
        if (!inCheck) {
            int tacticalMovesCount = 0;
            for (int i = 0; i < moveList.count; i += 1) {
                int move = moveList.moves[i];
                int flag = Move.getFlag(move);

                if (flag == Move.FLAG_CAPTURE ||
                        flag == Move.FLAG_EN_PASSANT_CAPTURE ||
                        flag == Move.FLAG_QUEEN_PROMOTION ||
                        flag == Move.FLAG_QUEEN_PROMOTION_CAPTURE)
                {
                    moveList.moves[tacticalMovesCount] = move;
                    tacticalMovesCount += 1;
                }
            }

            if (tacticalMovesCount == 0) {
                return bestEvaluation;
            }

            moveList.count = tacticalMovesCount;
        }

        long zobristKey = board.getCurrentZobristKey();
        int ttBestMove = TranspositionTable.unpackBestMove(tt.probeEntryData(zobristKey));
        MoveOrderer.prepareMoveScores(board, moveList, ttBestMove, null, 0);

        boolean canDeltaPrune = !inCheck && (board.getPhase() > Constants.LATE_ENDGAME_PHASE_THRESHOLD);
        for (int i = 0; i < moveList.count; i += 1) {
            MoveOrderer.selectNextMove(moveList, i);
            int move = moveList.moves[i];

            // Delta Pruning. Before a capture is made, test if the captured piece value plus a safety margin are enough
            // to raise alpha. If they are not, the capture is unlikely to improve the position and it's pruned. Should
            // be switched off in the late endgame, since otherwise the search would be blind to insufficient material
            // issues and transitions into won endgames made at the expense of some material. Delta Pruning is technically
            // a Fail-Low filter because it prunes captures if they are less than or equal to alpha. For that reason,
            // the evaluation uses the upper bound/maximum piece value
            if (canDeltaPrune) {
                int flag = Move.getFlag(move);
                if (flag == Move.FLAG_CAPTURE || flag == Move.FLAG_EN_PASSANT_CAPTURE) {
                    int capturedPieceType = (flag == Move.FLAG_EN_PASSANT_CAPTURE) ? Piece.PAWN : board.getPieceTypeAtSquare(Move.getEndingSquare(move));
                    int evaluation = standPat + Material.getMaxPieceValue(capturedPieceType) + Constants.SAFETY_MARGIN;

                    if (evaluation <= alpha) {
                        bestEvaluation = Math.max(bestEvaluation, evaluation);
                        continue;
                    }
                }
            }

            int undo = board.makeMove(move);
            int evaluation = -quiescenceSearch(pliesFromRoot + 1, -beta, -alpha);
            board.unmakeMove(move, undo);

            if (isHardTimeLimitExpired) {
                return 0;
            }

            if (evaluation > bestEvaluation) {
                bestEvaluation = evaluation;

                if (evaluation > alpha) {
                    alpha = evaluation;

                    if (alpha >= beta) {
                        return bestEvaluation;
                    }
                }
            }
        }

        return bestEvaluation;
    }


    private String buildPrincipalVariationString(int targetDepth) {
        // Gets the best move from the Transposition Table, plays it on the board and then gets the next one
        int[] pvMoves = new int[targetDepth];
        int[] undos = new int[targetDepth];
        int pvLength = 0;

        for (int i = 0; i < targetDepth; i += 1) {
            long zobristKey = board.getCurrentZobristKey();
            long ttEntryData = tt.probeEntryData(zobristKey);
            int bestMove = TranspositionTable.unpackBestMove(ttEntryData);

            // The bestMove variable can equal 0 on checkmates, stalemates, draws, hash collisions or Fail-Low entries
            if (bestMove == 0) {
                break;
            }

            pvMoves[pvLength] = bestMove;
            undos[pvLength] = board.makeMove(bestMove);
            pvLength += 1;
        }

        // Unmakes all the played moves
        for (int i = pvLength - 1; i >= 0; i -= 1) {
            board.unmakeMove(pvMoves[i], undos[i]);
        }

        // Builds the full Principal Variation String
        StringBuilder pvString = new StringBuilder();
        for (int i = 0; i < pvLength; i += 1) {
            pvString.append(CoordinatesConverter.moveToString(pvMoves[i])).append(" ");
        }

        return pvString.toString().trim();
    }
}
