package com.chessbot.engine.utils;

import com.chessbot.application.ChessbotApplication;
import com.chessbot.engine.core.Board;
import com.chessbot.engine.core.Move;
import com.chessbot.engine.core.Piece;
import com.chessbot.engine.movegen.MoveGenerator;
import com.chessbot.engine.movegen.MoveList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

public final class OpeningBook {
    // A Polyglot opening book entry contains these fields: a standard long Zobrist key representing a position, a short
    // move for that position and a short weight for that move (there is also an unused int learn field)
    private final long[] ZOBRIST_KEYS;
    private final short[] MOVES;
    private final short[] WEIGHTS;

    private final Random RANDOM = new Random();

    private final MoveList LEGAL_MOVES = new MoveList();
    private final int[] CANDIDATE_MOVES = new int[128];
    private final int[] CANDIDATE_WEIGHTS = new int[128];


    // The opening book is stored in bytes, each entry has 16 bytes (128 bits) and big-endian is used
    public OpeningBook(String name) {
        try (InputStream stream = ChessbotApplication.class.getResourceAsStream("/com/chessbot/datasets/" + name)) {
            if (stream == null) {
                throw new IllegalArgumentException();
            }

            byte[] bytes = stream.readAllBytes();
            int entryCount = bytes.length / 16;

            long[] zobristKeys = new long[entryCount];
            short[] moves = new short[entryCount];
            short[] weights = new short[entryCount];

            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            for (int i = 0; i < entryCount; i += 1) {
                zobristKeys[i] = buffer.getLong();
                moves[i] = buffer.getShort();
                weights[i] = buffer.getShort();
                buffer.getInt(); // Skips the unused int learn field
            }

            ZOBRIST_KEYS = zobristKeys;
            MOVES = moves;
            WEIGHTS = weights;
        }

        catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }


    // In the opening book there are duplicate Zobrist keys because there can be multiple candidate moves, with different
    // weights, for a single position. The keys are sorted in unsigned ascending order. A custom binary search is used to
    // compare the unsigned keys and to find the first matching key index
    private int findFirstMatchingIndex(long[] zobristKeys, long targetZobristKey) {
        int low = 0;
        int high = zobristKeys.length - 1;
        int index = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int keyCompareResult = Long.compareUnsigned(zobristKeys[mid], targetZobristKey);

            if (keyCompareResult < 0) {
                low = mid + 1;
            }

            else if (keyCompareResult > 0) {
                high = mid - 1;
            }

            else {
                index = mid;
                high = mid - 1; // Keeps searching left to find the first match
            }
        }

        return index;
    }


    // Transforms a Polyglot opening book short move into the corresponding Move int for the engine. The first 6 bits of
    // the Polyglot move are for the ending square, the next 6 are for the starting square, the next 3 are for the promoted
    // piece and the last one is unused. The square indexes start from A1 and the promoted piece index translates to: 0
    // = none, 1 = knight, 2 = bishop, 3 = rook, 4 = queen
    private int transformPolyglotMove(Board board, int polyglotMove) {
        int endingSquare = polyglotMove & 0b111111;
        int startingSquare = (polyglotMove >>> 6) & 0b111111;
        int promotedPiece = (polyglotMove >>> 12) & 0b111;

        // Converts Polyglot castling notation into the standard 2-square notation
        if (board.getPieceTypeAtSquare(startingSquare) == Piece.KING) {
            if (startingSquare == 4 && endingSquare == 7) endingSquare = 6; // White kingside castling (e1h1 -> e1g1)
            else if (startingSquare == 4 && endingSquare == 0) endingSquare = 2; // White queenside castling (e1a1 -> e1c1)
            else if (startingSquare == 60 && endingSquare == 63) endingSquare = 62; // Black kingside castling (e8h8 -> e8g8)
            else if (startingSquare == 60 && endingSquare == 56) endingSquare = 58; // Black queenside castling (e8a8 -> e8c8)
        }

        // Matches against legal moves in order for the returned Move int to have the correct flag
        for (int i = 0; i < LEGAL_MOVES.count; i += 1) {
            int legalMove = LEGAL_MOVES.moves[i];
            if (Move.getStartingSquare(legalMove) == startingSquare && Move.getEndingSquare(legalMove) == endingSquare) {
                // If it's not a promotion move, just return the legal move
                if (promotedPiece == 0) {
                    return legalMove;
                }

                // If it's a promotion move, the returned legal move must also match the promoted piece
                else {
                    int flag = Move.getFlag(legalMove);
                    if (flag >= Move.FLAG_KNIGHT_PROMOTION && ((flag & 3) + 1) == promotedPiece) {
                        return legalMove;
                    }
                }
            }
        }

        return -1;
    }


    // Gets all the candidate moves and weights for this position and returns a weighted-random opening book move. Return
    // -1 if the position isn't in the book. This function can be executed once at the start of every turn with no problem
    // because it's very fast
    public int getMove(Board board) {
        long targetZobristKey = board.getCurrentZobristKey();
        int firstMatchingIndex = findFirstMatchingIndex(ZOBRIST_KEYS, targetZobristKey);

        if (firstMatchingIndex == -1) {
            return -1;
        }

        MoveGenerator.generate(board, LEGAL_MOVES);

        int candidateCount = 0;
        int totalWeight = 0;
        for (int i = firstMatchingIndex; i < ZOBRIST_KEYS.length && ZOBRIST_KEYS[i] == targetZobristKey; i += 1) {
            int engineMove = transformPolyglotMove(board, Short.toUnsignedInt(MOVES[i]));
            int weight = Short.toUnsignedInt(WEIGHTS[i]);

            if (candidateCount < CANDIDATE_MOVES.length) {
                CANDIDATE_MOVES[candidateCount] = engineMove;
                CANDIDATE_WEIGHTS[candidateCount] = weight;
                totalWeight += weight;
                candidateCount += 1;
            }
        }

        // Weighted-random selection. Each move adds upon the cumulative weight, if this is the one that causes the cumulative
        // weight to surpass the randomly assigned weight, return it. This way, moves with bigger weights are more likely
        // to be returned
        int randomWeight = RANDOM.nextInt(totalWeight);
        int cumulativeWeight = 0;
        for (int i = 0; i < candidateCount; i += 1) {
            cumulativeWeight += CANDIDATE_WEIGHTS[i];

            if (cumulativeWeight > randomWeight) {
                return CANDIDATE_MOVES[i];
            }
        }

        return CANDIDATE_MOVES[0];
    }
}
