package com.chessbot.engine.core;

import com.chessbot.engine.evaluation.PieceSquareTables;
import com.chessbot.engine.utils.FenParser;
import com.chessbot.engine.utils.Zobrist;

import java.util.Arrays;

import static com.chessbot.engine.core.Constants.PHASE_WEIGHTS_START_INDEX;

// The board class should only hold information about one specific game state, this includes all the data contained in a
// FEN string
public class Board {
    // 12 64 bit variables, one for each piece. The first 6 bitboards are for the white pieces (pawn, knight, bishop, rook,
    // queen, king), the other 6 are for the black pieces. Each bit indicates a square on the board, if the bit equals 0,
    // there is no piece in that square, if it's 1, there is
    private final long[] bitboards = new long[12];

    // 0 = White's bitboard, 1 = Black's bitboard, 2 = All pieces bitboard
    private final long[] otherBitboards = new long[3];

    // Each int of the array indicates a square. Empty squares get values of -1. Populated squares hold an encoded int which
    // equals: (pieceColor << 3) | pieceType. A mailbox is used to avoid looping through all the bitboards to find what
    // piece color or piece type occupies a square
    private final int[] mailbox = new int[64];

    // 0 = White's turn, 1 = Black's turn
    private int turn;

    // Holds the castling rights info, 1111 means all castling rights are available
    private int castlingRights = 0b1111;

    // Each constant points to a bit of castling rights
    public static final int WHITE_KINGSIDE = 1;
    public static final int WHITE_QUEENSIDE = 2;
    public static final int BLACK_KINGSIDE = 4;
    public static final int BLACK_QUEENSIDE = 8;

    // Helper mask to update castling rights when pieces move from/to critical squares. All non-critical squares get an initial
    // value of 15 which equals the initial value of castlingRights. The reason for that is that if the player still has
    // castling rights and a move in a non-critical square happens, the operation becomes castlingRights = castlingRights
    // & CASTLING_MASK = 1111 & 1111 = 1111, so the variable remains untouched. If a move in a critical square happens,
    // the appropriate bits of castlingRights are turned off
    private static final int[] CASTLING_MASKS = new int[64];
    static {
        Arrays.fill(CASTLING_MASKS, 15);

        // If the a1 rook moves/gets captured, white queenside castling gets turned off. If the e1 king moves, white kingside/queenside
        // castling gets turned off, the same logic applies for the h1 rook, the a8 rook, the e8 king and the h8 rook
        CASTLING_MASKS[0] = 15 ^ WHITE_QUEENSIDE;
        CASTLING_MASKS[4] = 15 ^ (WHITE_KINGSIDE | WHITE_QUEENSIDE);
        CASTLING_MASKS[7] = 15 ^ WHITE_KINGSIDE;
        CASTLING_MASKS[56] = 15 ^ BLACK_QUEENSIDE;
        CASTLING_MASKS[60] = 15 ^ (BLACK_KINGSIDE | BLACK_QUEENSIDE);
        CASTLING_MASKS[63] = 15 ^ BLACK_KINGSIDE;
    }

    // 1 for the square that an en passant capture can happen, 0 for everything else
    private long enPassantSquareBitboard = 0L;

    // Counter of plies since the last capture or pawn push, used in the 50-move rule
    private int halfMoveClock;

    // Counter of plies since the start of the game, used as an index in any history array
    private int plyIndex = 0;

    // A history array containing every past Zobrist key created in this game is needed. The array is used for detecting
    // threefold repetitions and easily unmaking moves. The currentZobristKey variable is used for easily referencing the
    // key of the current position
    private final long[] zobristKeyHistory;
    private long currentZobristKey = 0L;

    // The total Material and Piece-Square Tables scores. These scores assume that we are in a middlegame/endgame. The scores
    // are positive/negative when white/black is winning. They are placed here because incrementally updating them on make/unmake
    // move avoids recalculations in the evaluation function
    private int currentMgTotalScore = 0;
    private int currentEgTotalScore = 0;

    // The phase weights sum, used to calculate the current game phase and determine how close we are to the endgame. More
    // information in the Material class
    private int phase = 0;


    // Most classes call the empty constructor in order to use the default size for any history array. When a lightweight
    // version of a Board object is needed, for example in TexelTuner, a parameter is passed to the constructor to make
    // the capacity of any history arrays zero
    public Board() {
        this(Constants.MAX_GAME_MOVES);
    }

    public Board(int historyCapacity) {
        zobristKeyHistory = new long[historyCapacity];
        Arrays.fill(mailbox, -1);
    }


    public long getBitboard(int color, int pieceType) { return bitboards[(color * 6) + pieceType]; }

    public long getOtherBitboard(int index) {
        return otherBitboards[index];
    }

    public int getTurn() {
        return turn;
    }

    public void setTurn(int turn) {
        this.turn = turn;
    }

    // Returns the castlingRights bit that is given as a parameter
    public boolean getCastlingRight(int castlingRight) {
        return (castlingRights & castlingRight) != 0;
    }

    public void setCastlingRights(int castlingRights) { this.castlingRights = castlingRights; }

    public long getEnPassantSquareBitboard() {
        return enPassantSquareBitboard;
    }

    public void setEnPassantSquareBitboard(long bitboard) { enPassantSquareBitboard = bitboard; }

    public int getHalfMoveClock() { return halfMoveClock; }

    public void setHalfMoveClock(int halfMoveClock) { this.halfMoveClock = halfMoveClock; }

    public int getPlyIndex() { return plyIndex; }

    public long[] getZobristKeyHistory() { return zobristKeyHistory; }

    public long getCurrentZobristKey() { return currentZobristKey; }

    public int getCurrentMgTotalScore() { return currentMgTotalScore; }

    public int getCurrentEgTotalScore() { return currentEgTotalScore; }

    public int getPhase() { return phase; }


    public void calculateInitialScores() {
        // Resets the scores in case this function is called multiple times in a game, done in TexelTuner for example
        currentMgTotalScore = 0;
        currentEgTotalScore = 0;
        phase = 0;

        long allPiecesBitboard = otherBitboards[2];
        while (allPiecesBitboard != 0L) {
            int square = Long.numberOfTrailingZeros(allPiecesBitboard);
            allPiecesBitboard &= allPiecesBitboard - 1;

            int pieceColor = getPieceColorAtSquare(square);
            int pieceType = getPieceTypeAtSquare(square);
            int sign = 1 - 2 * pieceColor; // 1 for white, -1 for black

            currentMgTotalScore += sign * PieceSquareTables.getMgScore(pieceColor, pieceType, square);
            currentEgTotalScore += sign * PieceSquareTables.getEgScore(pieceColor, pieceType, square);
            phase += Constants.getEvalParam(PHASE_WEIGHTS_START_INDEX + pieceType);
        }
    }


    // Uses the precomputed longs to calculate the Zobrist hash key that represents the loaded position
    public void calculateZobristKey() {
        currentZobristKey = 0L;

        for (int piece = 0; piece < 12; piece += 1) {
            long bitboard = bitboards[piece];
            while (bitboard != 0L) {
                int square = Long.numberOfTrailingZeros(bitboard);
                currentZobristKey ^= Zobrist.PIECES[piece][square];
                bitboard ^= (1L << square);
            }
        }

        if (turn == Piece.WHITE) {
            currentZobristKey ^= Zobrist.TURN;
        }

        currentZobristKey ^= Zobrist.CASTLING_RIGHTS[castlingRights];

        if (enPassantSquareBitboard != 0L) {
            int epFile = Long.numberOfTrailingZeros(enPassantSquareBitboard) & 7;
            currentZobristKey ^= Zobrist.EN_PASSANT_FILE[epFile];
        }
    }


    // Coordinates every needed job at the start of the game
    public void loadPosition(String fen) {
        Arrays.fill(bitboards, 0L);
        Arrays.fill(otherBitboards, 0L);
        Arrays.fill(mailbox, -1);
        plyIndex = 0;
        currentMgTotalScore = 0;
        currentEgTotalScore = 0;
        phase = 0;

        FenParser.loadFen(fen, this);
        calculateZobristKey();
    }


    // Adds a piece to the board at the start of the game
    public void addPiece(int pieceColor, int pieceType, int squareIndex) {
        long addMask = 1L << squareIndex;

        bitboards[pieceColor * 6 + pieceType] |= addMask;
        otherBitboards[pieceColor] |= addMask;
        otherBitboards[2] |= addMask;
        mailbox[squareIndex] = (pieceColor << 3) | pieceType;

        int sign = (pieceColor == Piece.WHITE) ? 1 : -1;
        currentMgTotalScore += PieceSquareTables.getMgScore(pieceColor, pieceType, squareIndex) * sign;
        currentEgTotalScore += PieceSquareTables.getEgScore(pieceColor, pieceType, squareIndex) * sign;
        phase += Constants.getEvalParam(PHASE_WEIGHTS_START_INDEX + pieceType);
    }


    // Removes a piece whenever a capture happens
    public void removePiece(int pieceColor, int pieceType, int squareIndex) {
        long removeMask = ~(1L << squareIndex);

        bitboards[pieceColor * 6 + pieceType] &= removeMask;
        otherBitboards[pieceColor] &= removeMask;
        otherBitboards[2] &= removeMask;
        mailbox[squareIndex] = -1;

        int sign = (pieceColor == Piece.WHITE) ? 1 : -1;
        currentMgTotalScore -= PieceSquareTables.getMgScore(pieceColor, pieceType, squareIndex) * sign;
        currentEgTotalScore -= PieceSquareTables.getEgScore(pieceColor, pieceType, squareIndex) * sign;
        phase -= Constants.getEvalParam(PHASE_WEIGHTS_START_INDEX + pieceType);
    }


    // Moves a piece every turn
    public void movePiece(int startingSquare, int endingSquare, int pieceColor, int pieceType) {
        long removeMask = 1L << startingSquare;
        long addMask = 1L << endingSquare;

        bitboards[pieceColor * 6 + pieceType] &= ~removeMask;
        bitboards[pieceColor * 6 + pieceType] |= addMask;

        otherBitboards[pieceColor] &= ~removeMask;
        otherBitboards[pieceColor] |= addMask;

        otherBitboards[2] &= ~removeMask;
        otherBitboards[2] |= addMask;

        mailbox[startingSquare] = -1;
        mailbox[endingSquare] = (pieceColor << 3) | pieceType;

        int sign = (pieceColor == Piece.WHITE) ? 1 : -1;
        currentMgTotalScore -= PieceSquareTables.getMgScore(pieceColor, pieceType, startingSquare) * sign;
        currentMgTotalScore += PieceSquareTables.getMgScore(pieceColor, pieceType, endingSquare) * sign;
        currentEgTotalScore -= PieceSquareTables.getEgScore(pieceColor, pieceType, startingSquare) * sign;
        currentEgTotalScore += PieceSquareTables.getEgScore(pieceColor, pieceType, endingSquare) * sign;
    }


    // Coordinates every job of a move cycle and returns an Undo long object in order to, if needed, unmake the move in the
    // search algorithm
    public int makeMove(int move) {
        // Before the move is made, save the reversible state to the corresponding history arrays
        zobristKeyHistory[plyIndex] = currentZobristKey;
        plyIndex += 1;

        // The mathematical formula for updating a Zobrist key is: new hash = old hash ^ (old hash with the old state removed)
        // ^ (old hash with the new state added). For that reason, before the move is made, the old irreversible data has
        // to be XORed out from the key
        currentZobristKey ^= Zobrist.TURN; // Turns on/off the TURN long

        if (enPassantSquareBitboard != 0L) {
            int epFile = Long.numberOfTrailingZeros(enPassantSquareBitboard) & 7;
            currentZobristKey ^= Zobrist.EN_PASSANT_FILE[epFile];
        }

        currentZobristKey ^= Zobrist.CASTLING_RIGHTS[castlingRights];

        int startingSquare = Move.getStartingSquare(move);
        int endingSquare = Move.getEndingSquare(move);
        int moveFlag = Move.getFlag(move);
        int pieceColor = turn;
        int pieceType = getPieceTypeAtSquare(startingSquare);
        int enemyColor = pieceColor ^ 1;

        // Snapshots the irreversible data that's about to be overwritten, defaults capturedPieceType to NONE_PIECE_TYPE,
        // but the capture/promotion capture cases below overwrite it if needed
        int capturedPieceType = Undo.NONE_PIECE_TYPE;
        long previousEnPassantSquareBitboard = enPassantSquareBitboard;
        int previousCastlingRights = castlingRights;
        int previousHalfMoveClock = halfMoveClock;

        // Resets the en passant bitboard after each move
        enPassantSquareBitboard = 0L;

        // Handles all cases of the move flag. For each one, update the Zobrist key by XORing out the longs that represent
        // pieces on the starting and potential capture squares and XORing in the long that represents the piece on the
        // ending square. The middlegame/endgame score and phase is updated in captures/promotions because the material
        // difference between the 2 players changed
        switch (moveFlag) {
            // If there is no special move flag, just move the piece
            case Move.FLAG_QUIET:
                movePiece(startingSquare, endingSquare, pieceColor, pieceType);
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + pieceType][startingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + pieceType][endingSquare];
                break;

            // If it's a double pawn push, move the pawn and make the square down of the pawn an en passant target
            case Move.FLAG_DOUBLE_PAWN_PUSH:
                movePiece(startingSquare, endingSquare, pieceColor, Piece.PAWN);

                // If the ending square isn't on the a/h file, get the squares that are left/right adjacent to the ending
                // square
                long adjacentSquaresMask = 0L;
                if ((endingSquare & 7) != 0) adjacentSquaresMask |= 1L << (endingSquare - 1);
                if ((endingSquare & 7) != 7) adjacentSquaresMask |= 1L << (endingSquare + 1);

                // Only sets the enPassantSquareBitboard if an enemy pawn is adjacent to the ending square. This is done
                // for Zobrist hashing in order to avoid 2 identical positions being unique just because in one of them
                // a pawn pushed 2 squares in the last move. If there are no adjacent enemy pawns, the enPassantSquareBitboard
                // shouldn't get a value. Don't worry about the bitwise operation
                if ((adjacentSquaresMask & bitboards[enemyColor * 6 + Piece.PAWN]) != 0) {
                    enPassantSquareBitboard = 1L << (endingSquare ^ 8);
                }

                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + pieceType][startingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + pieceType][endingSquare];
                break;

            // If it's a normal capture, remove the captured piece from the ending square and move the piece
            case Move.FLAG_CAPTURE:
                capturedPieceType = getPieceTypeAtSquare(endingSquare);
                removePiece(enemyColor, capturedPieceType, endingSquare);
                movePiece(startingSquare, endingSquare, pieceColor, pieceType);
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + pieceType][startingSquare];
                currentZobristKey ^= Zobrist.PIECES[enemyColor * 6 + capturedPieceType][endingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + pieceType][endingSquare];
                break;

            // If it's an en passant capture, for white, remove the captured piece from the square that is a rank below
            // the ending square. For black, the captured piece is a rank above the ending square. Then move the pawn
            case Move.FLAG_EN_PASSANT_CAPTURE:
                capturedPieceType = Piece.PAWN;
                int capturedPawnSquare = endingSquare + (pieceColor * 16) - 8;
                removePiece(enemyColor, Piece.PAWN, capturedPawnSquare);
                movePiece(startingSquare, endingSquare, pieceColor, Piece.PAWN);
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.PAWN][startingSquare];
                currentZobristKey ^= Zobrist.PIECES[enemyColor * 6 + Piece.PAWN][capturedPawnSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.PAWN][endingSquare];
                break;

            // Kingside castling for the white/black king
            case Move.FLAG_KING_CASTLE:
                movePiece(startingSquare, endingSquare, pieceColor, pieceType); // King move

                // Rook move
                int kingRookStartingSquare = (pieceColor == Piece.WHITE) ? 7 : 63;
                int kingRookEndingSquare = (pieceColor == Piece.WHITE) ? 5 : 61;
                movePiece(kingRookStartingSquare, kingRookEndingSquare, pieceColor, Piece.ROOK);

                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.KING][startingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.KING][endingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.ROOK][kingRookStartingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.ROOK][kingRookEndingSquare];
                break;

            // Queenside castling for the white/black king
            case Move.FLAG_QUEEN_CASTLE:
                movePiece(startingSquare, endingSquare, pieceColor, pieceType); // King move

                // Rook move
                int queenRookStartingSquare = (pieceColor == Piece.WHITE) ? 0 : 56;
                int queenRookEndingSquare = (pieceColor == Piece.WHITE) ? 3 : 59;
                movePiece(queenRookStartingSquare, queenRookEndingSquare, pieceColor, Piece.ROOK);

                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.KING][startingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.KING][endingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.ROOK][queenRookStartingSquare];
                currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.ROOK][queenRookEndingSquare];
                break;

            // If it's none of the above, it must be a promotion
            default:
                // Because of how move flag is structured, all promotion or promotion capture move flags are after the knight
                // promotion move flag
                if (moveFlag >= Move.FLAG_KNIGHT_PROMOTION) {
                    // All promotion capture move flags are after the knight promotion capture move flag, removes the enemy
                    // piece if it's a promotion capture
                    if (moveFlag >= Move.FLAG_KNIGHT_PROMOTION_CAPTURE) {
                        capturedPieceType = getPieceTypeAtSquare(endingSquare);
                        removePiece(enemyColor, capturedPieceType, endingSquare);
                        currentZobristKey ^= Zobrist.PIECES[enemyColor * 6 + capturedPieceType][endingSquare];
                    }

                    // Removes the pawn from the second to last rank, derive the promoted piece from the 1st and 2nd special
                    // bits of move flag, add the promoted piece to the last rank
                    removePiece(pieceColor, Piece.PAWN, startingSquare);
                    int promotedPiece = (moveFlag & 3) + 1;
                    addPiece(pieceColor, promotedPiece, endingSquare);
                    currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + Piece.PAWN][startingSquare];
                    currentZobristKey ^= Zobrist.PIECES[pieceColor * 6 + promotedPiece][endingSquare];
                }

                break;
        }

        // Updates castling rights as described above, the starting square mask is about rook/king moves, the ending square
        // mask is about rooks getting captured
        castlingRights &= CASTLING_MASKS[startingSquare] & CASTLING_MASKS[endingSquare];

        // XORs in the new irreversible data to the Zobrist key
        currentZobristKey ^= Zobrist.CASTLING_RIGHTS[castlingRights];

        if (enPassantSquareBitboard != 0L) {
            int epFile = Long.numberOfTrailingZeros(enPassantSquareBitboard) & 7;
            currentZobristKey ^= Zobrist.EN_PASSANT_FILE[epFile];
        }

        // Resets the half move clock if a capture or a pawn push happened, otherwise it's incremented
        if (pieceType == Piece.PAWN || moveFlag == Move.FLAG_CAPTURE || moveFlag == Move.FLAG_EN_PASSANT_CAPTURE) {
            halfMoveClock = 0;
        }

        else {
            halfMoveClock += 1;
        }

        turn ^= 1;
        return Undo.packUndo(capturedPieceType, previousEnPassantSquareBitboard, previousCastlingRights, previousHalfMoveClock);
    }


    // Reverses a move made by makeMove
    public void unmakeMove(int move, int undo) {
        plyIndex -= 1;
        currentZobristKey = zobristKeyHistory[plyIndex];

        int startingSquare = Move.getStartingSquare(move);
        int endingSquare = Move.getEndingSquare(move);
        int moveFlag = Move.getFlag(move);
        int pieceType = getPieceTypeAtSquare(endingSquare);

        // Flips the turn back in order to reference whoever made the move being undone
        turn ^= 1;
        int pieceColor = turn;
        int enemyColor = pieceColor ^ 1;

        // Restores the irreversible data from before the move was made
        int capturedPieceType = Undo.unpackCapturedPieceType(undo);
        castlingRights = Undo.unpackCastlingRights(undo);
        enPassantSquareBitboard = Undo.unpackEnPassantSquareBitboard(undo);
        halfMoveClock = Undo.unpackHalfMoveClock(undo);

        switch (moveFlag) {
            // Since the en passant square bitboard is gonna get recovered from the Undo long object, quiet moves and double
            // pawn pushes are undone by just moving the piece back
            case Move.FLAG_QUIET:
            case Move.FLAG_DOUBLE_PAWN_PUSH:
                movePiece(endingSquare, startingSquare, pieceColor, pieceType);
                break;

            // Moves the piece back and restores the captured piece onto the now empty ending square
            case Move.FLAG_CAPTURE:
                movePiece(endingSquare, startingSquare, pieceColor, pieceType);
                addPiece(enemyColor, capturedPieceType, endingSquare);
                break;

            // Moves the friendly en passant pawn back and restores the captured en passant pawn to the square it was, a
            // rank away from the ending square
            case Move.FLAG_EN_PASSANT_CAPTURE:
                int capturedPawnSquare = endingSquare + (pieceColor * 16) - 8;
                movePiece(endingSquare, startingSquare, pieceColor, Piece.PAWN);
                addPiece(enemyColor, Piece.PAWN, capturedPawnSquare);
                break;

            case Move.FLAG_KING_CASTLE:
                // Moves the king back
                movePiece(endingSquare, startingSquare, pieceColor, Piece.KING);

                // Moves the rook back
                if (pieceColor == Piece.WHITE) {
                    movePiece(5, 7, Piece.WHITE, Piece.ROOK);
                }

                else {
                    movePiece(61, 63, Piece.BLACK, Piece.ROOK);
                }

                break;

            case Move.FLAG_QUEEN_CASTLE:
                // Moves the king back
                movePiece(endingSquare, startingSquare, pieceColor, Piece.KING);

                // Moves the rook back
                if (pieceColor == Piece.WHITE) {
                    movePiece(3, 0, Piece.WHITE, Piece.ROOK);
                }

                else {
                    movePiece(59, 56, Piece.BLACK, Piece.ROOK);
                }

                break;

            default:
                if (moveFlag >= Move.FLAG_KNIGHT_PROMOTION) {
                    // Derives the promoted piece from the 1st and 2nd special bits of move flag and removes it from the
                    // last rank
                    int promotedPiece = (moveFlag & 3) + 1;
                    removePiece(pieceColor, promotedPiece, endingSquare);

                    // Puts the pawn back on the second to last rank
                    addPiece(pieceColor, Piece.PAWN, startingSquare);

                    // If it was a promotion capture, restore the captured piece onto the now empty ending square
                    if (moveFlag >= Move.FLAG_KNIGHT_PROMOTION_CAPTURE) {
                        addPiece(enemyColor, capturedPieceType, endingSquare);
                    }
                }

                break;
        }
    }


    // Gets a piece's color at a specific square
    public int getPieceColorAtSquare(int squareIndex) {
        int piece = mailbox[squareIndex];
        return (piece == -1) ? -1 : piece >>> 3;
    }


    // Gets a piece's type at a specific square
    public int getPieceTypeAtSquare(int squareIndex) {
        int piece = mailbox[squareIndex];
        return (piece == -1) ? -1 : piece & 7;
    }
}
