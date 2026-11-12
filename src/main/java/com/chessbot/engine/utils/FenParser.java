package com.chessbot.engine.utils;

import com.chessbot.engine.core.Board;
import com.chessbot.engine.core.Piece;

public final class FenParser {
    private FenParser() {}


    private static int getPieceTypeFromLetter(char letter) {
        letter = Character.toLowerCase(letter);

        return switch (letter) {
            case 'p' -> Piece.PAWN;
            case 'n' -> Piece.KNIGHT;
            case 'b' -> Piece.BISHOP;
            case 'r' -> Piece.ROOK;
            case 'q' -> Piece.QUEEN;
            case 'k' -> Piece.KING;
            default -> throw new IllegalArgumentException("Unknown piece: " + letter);
        };
    }


    // Reads a FEN sequence and updates the bitboards. The sixth part of a FEN string, the fullmove number, isn't included
    // in the calculations because it's not needed anywhere in the engine
    public static void loadFen(String fen, Board board) {
        // Splits the fen string on every whitespace in it
        String[] parts = fen.split("\\s");

        int col = 0;
        int row = 7;

        // Example: First part of the FEN string looks like this rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR. A lowercase
        // letter represents a black piece, an uppercase letter represents a white piece. Numbers indicate the number of
        // empty squares, slashes indicate new rows
        for (char letter : parts[0].toCharArray()) {
            if (Character.isLetter(letter)) {
                int color = Character.isUpperCase(letter) ? Piece.WHITE : Piece.BLACK;
                int pieceType = getPieceTypeFromLetter(letter);
                board.addPiece(color, pieceType, (row << 3) + col);

                col += 1;
            }

            else if (Character.isDigit(letter)) {
                col += letter - '0';
            }

            else if (letter == '/') {
                col = 0;
                row -= 1;
            }
        }

        // Second part of the FEN string dictates whose turn it is, if the string doesn't include that information, it's
        // white's turn
        if (parts.length > 1) {
            board.setTurn(parts[1].equals("w") ? Piece.WHITE : Piece.BLACK);
        }

        else {
            board.setTurn(Piece.WHITE);
            return;
        }

        // Third part of the FEN string dictates what castling rights remain
        if (parts.length > 2) {
            String castlingRightsString = parts[2];
            int castlingRights = 0;
            if (castlingRightsString.contains("K")) castlingRights |= Board.WHITE_KINGSIDE;
            if (castlingRightsString.contains("Q")) castlingRights |= Board.WHITE_QUEENSIDE;
            if (castlingRightsString.contains("k")) castlingRights |= Board.BLACK_KINGSIDE;
            if (castlingRightsString.contains("q")) castlingRights |= Board.BLACK_QUEENSIDE;
            board.setCastlingRights(castlingRights);
        }

        else {
            board.setCastlingRights(0b1111);
            return;
        }

        // Fourth part of the FEN string indicates the target square that an en passant move is available on, if that part
        // equals "-" or the string doesn't include that information, no square is available for en passant. The code works
        // in a similar way to Board.makeMove() -> case Move.FLAG_DOUBLE_PAWN_PUSH
        if (parts.length > 3 && !parts[3].equals("-")) {
            int epCol = parts[3].charAt(0) - 'a';
            int epRow = parts[3].charAt(1) - '1';
            int epSquare = (epRow << 3) + epCol;
            int pushedPawnSquare = epSquare ^ 8;

            long adjacentSquaresMask = 0L;
            if ((pushedPawnSquare & 7) != 0) adjacentSquaresMask |= 1L << (pushedPawnSquare - 1);
            if ((pushedPawnSquare & 7) != 7) adjacentSquaresMask |= 1L << (pushedPawnSquare + 1);

            if ((adjacentSquaresMask & board.getBitboard(board.getTurn(), Piece.PAWN)) != 0L) {
                board.setEnPassantSquareBitboard(1L << epSquare);
            }

            else {
                board.setEnPassantSquareBitboard(0L);
            }
        }

        else {
            board.setEnPassantSquareBitboard(0L);
        }

        // Fifth part of the FEN string indicates the half move clock
        if (parts.length > 4) {
            board.setHalfMoveClock(Integer.parseInt(parts[4]));
        }

        else {
            board.setHalfMoveClock(0);
        }
    }
}
