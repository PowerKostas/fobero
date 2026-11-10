package com.chessbot.engine.core;

import com.chessbot.engine.evaluation.Material;
import com.chessbot.engine.evaluation.PieceSquareTables;

public final class Constants {
    // Stockfish has an absolute maximum search depth of 245 plies, I went with about half of that
    public static final int MAX_SEARCH_DEPTH = 128;

    // The maximum number of plies a single game can last. The longest recorded chess game in history lasted 538 plies,
    // I went with about double of that
    public static final int MAX_GAME_MOVES = 1024;

    // Safe values that allow the search function operations to work without overflows
    public static final int INFINITY_SCORE = 10000000;
    public static final int CHECKMATE_EVALUATION = 1000000;

    // Any evaluation above this threshold is a checkmate evaluation
    public static final int CHECKMATE_EVALUATION_THRESHOLD = CHECKMATE_EVALUATION - MAX_SEARCH_DEPTH;

    // Move ordering score, must be higher than MVV-LVA + Promotions + ...
    public static final int PREVIOUS_BEST_MOVE_SCORE = 1000;

    // Safety buffer to account for communication latency between the engine and the online platform
    public static final int MOVE_OVERHEAD = 50;

    // Search function parameters, tuned with SPSA
    public static final int[] PROMOTION_SCORES = {-45, -50, -50, 35};
    public static final int FIRST_KILLER_MOVE_SCORE = 2;
    public static final int SECOND_KILLER_MOVE_SCORE = 1;
    public static final int SAFETY_MARGIN = 400;
    public static final int LATE_ENDGAME_PHASE_THRESHOLD = 0;
    public static final int ESTIMATED_MOVES_REMAINING = 35;
    public static final int INCREMENT_USAGE_PERCENT = 75;
    public static final int SOFT_TIME_LIMIT_PERCENT = 50;
    public static final int MAX_SOFT_TIME_LIMIT_PERCENT = 50;
    public static final int HARD_TIME_LIMIT_MULTIPLIER_PERCENT = 300;
    public static final int MAX_HARD_TIME_LIMIT_PERCENT = 80;

    // These are evaluation function parameters, but because they are used to win quickly before the search depth runs out,
    // they are also tuned with SPSA
    public static final int KING_PROXIMITY_MULTIPLIER = 10;
    public static final int LOSING_KING_EDGE_MULTIPLIER = 10;

    // Evaluation function parameters, tuned with Texel's Tuning. They are placed in one big array for simplicity in TexelTuner
    public static final int MG_PIECE_VALUES_START_INDEX = 0;
    public static final int EG_PIECE_VALUES_START_INDEX = 6;
    public static final int PHASE_WEIGHTS_START_INDEX = 12;
    public static final int MG_TEMPO_BONUS_INDEX = 18;
    public static final int EG_TEMPO_BONUS_INDEX = 19;
    public static final int MG_PST_START_INDEX = 20;
    public static final int EG_PST_START_INDEX = 404;

    public static final int EVAL_PARAMS_COUNT = 788;
    public static final int[] UNTUNABLE_EVAL_PARAM_INDICES = {5, 11, 12, 17};
    private static final int[] EVAL_PARAMS = {
        // MG piece values
        142, 611, 691, 883, 1820, 0,

        // EG piece values
        142, 345, 329, 704, 1201, 0,

        // Phase weights
        0, 11, 35, 53, 78, 0,

        // MG tempo bonus
        47,

        // EG tempo bonus
        38,

        // MG PST pawn table
          0,   0,   0,   0,   0,   0,   0,   0,
         80,  94,  51, 101, 143, 136,  40, -30,
        -36,   3,  46,  31,  65, 112,  25, -22,
        -18,  21,   6,  29,  47,  20,  18, -27,
        -49,   2,  -5,  28,  25,   6,  22, -33,
        -46,  -4,   4, -10,  19, -13,  65,   0,
        -57,   7, -20, -31,  -7,  40,  80, -14,
          0,   0,   0,   0,   0,   0,   0,   0,

        // MG PST knight table
        -357,  -72,  -36,  -65,  100, -175, -168, -154,
         -57,   21,  181,   92,  201,  190,   67,   46,
          44,   84,  104,  161,  225,  267,  165,   57,
          63,   65,   74,  131,   77,  125,   44,   94,
          23,   53,   70,   65,   86,   79,   62,    4,
           7,   31,   58,   62,   77,   49,   75,    5,
          -1,   -6,   32,   37,   39,   58,   40,   23,
        - 46,   -5,  -38,   -5,   -3,   10,  -11,  -78,

        // MG PST bishop table
        -33,  -54,  -13,  -89, -158,  -52,    8,  -66,
        -10,   60,   16,   33,   66,  143,   50,   77,
          6,   40,  121,   72,  142,  118,  102,   82,
        -14,   13,   65,   78,   73,   43,   27,   17,
         12,   39,   34,   72,   82,   32,   36,   -8,
         46,   53,   47,   30,   38,   43,   42,   40,
          5,   55,   32,   20,   23,   59,   69,   30,
         23,   -7,    2,   -5,   -9,  -24,   15,    1,

        // MG PST rook table
        114, 138, 166, 175, 220, 260, 130,  85,
         59,  42, 102, 159,  92, 175, 198, 132,
         41,  51,  55,  90, 125, 145, 173, 120,
        -13,  14,  55,  74,  45,  55,  82,  86,
        -22, -22, -18,   7,  24,   3,  40,   9,
        -41, -15,   2,  19,  11,  -8,  51,   9,
        -50,  -9, -16,  -5,   3,   3,  34, -80,
        -19,  -9,  17,  21,  32,   7, -37, -18,

        // MG PST queen table
        -54,  17, 107, 142, 199, 226,  47,   5,
        -18, -31, -21, -57, -31, 203,  76, 232,
        -17,  -1,  45,  62, 105, 166, 208, 109,
        -31, -18,   9,  10,  17,  21, -14,  37,
        -27,   2,  -8,  -2,  22,   4,  18,  -3,
        -18,   6,  -5,  -2,  -3,   6,  22,   3,
        -25,   8,  11,   6,  14,  33,  37,   3,
         -7, -26,  -9,  10, -19, -38, -85, -25,

        // MG PST king table
        161,  112,  175,  119,  168,  135,  108,   92,
         60,  188,   96,   80,   69,  168,    1,  -60,
         57,  193,   34,   65,  -18,   96,   82,  -29,
        -21,   30,   51,  -23,  -32,  -17,   -8,  -82,
         20,   25,  -13, -106,  -82, -100,  -61, -135,
        -14,    4, -112, -128, -131,  -90,  -39,  -75,
         36,  -43,  -64, -152, -123,  -72,    1,    0,
        -92,   16,  -24, -128,  -28,  -84,   24,   18,

        // EG PST pawn table
          0,   0,   0,   0,   0,   0,   0,   0,
        302, 300, 272, 212, 194, 221, 293, 291,
        182, 176, 149, 119,  96,  81, 138, 148,
         68,  48,  29,   9,  -6,  12,  33,  33,
         35,  21,  -3, -11, -11,  -8,   7,   7,
         24,  19,  -6,  17,   4,   7,  -1,  -8,
         41,  24,  24,  34,  17,   8,   2,  -7,
          0,   0,   0,   0,   0,   0,   0,   0,

        // EG PST knight table
         26, -25,  17,  26,  -7,  13,  18, -85,
          5,  25,  -3,  30,  -7,  -3,  -5, -25,
          2,  24,  62,  53,  11,  23,   7,  17,
         23,  63,  70,  82,  87,  69,  64,  14,
         26,  44,  76,  75,  80,  61,  32,  38,
          9,  45,  43,  71,  65,  53,  28,   7,
          4,  16,  26,  47,  42,  33,  17,   8,
        -38, -15,  28,  37,  22,  39, -14,   6,

        // EG PST bishop table
        66,  59,  61,  94, 100,  81,  59,  90,
        61,  72,  83,  60,  73,  43,  76,  15,
        86,  83,  68,  93,  60,  86,  72,  58,
        97, 105,  90,  99, 100,  82,  91,  64,
        70,  78, 105,  87,  87, 106,  66,  62,
        53,  77,  86,  98, 105,  87,  57,  51,
        66,  46,  61,  87,  84,  67,  61,  39,
        41,  69,  41,  79,  75,  68,  46,  55,

        // EG PST rook table
        55, 40, 34, 27, 12, -3, 40, 45,
        59, 81, 61, 35, 48, 35, 16, 27,
        63, 61, 59, 49, 35, 29, 23, 25,
        66, 58, 59, 49, 54, 47, 33, 24,
        59, 65, 71, 68, 59, 52, 32, 29,
        52, 55, 51, 47, 53, 46, 16, 28,
        58, 52, 68, 62, 53, 55, 40, 64,
        47, 62, 59, 75, 43, 39, 64,  4,

        // EG PST queen table
        243, 245, 216, 227, 189, 187, 212, 268,
        231, 248, 268, 339, 310, 285, 290, 120,
        194, 226, 261, 251, 307, 235, 204, 212,
        217, 263, 248, 293, 317, 317, 297, 246,
        238, 242, 261, 295, 271, 268, 263, 235,
        180, 185, 261, 249, 265, 249, 250, 207,
        217, 223, 210, 240, 216, 167, 148, 192,
        215, 200, 198, 141, 219, 185, 214, 161,

        // EG PST king table
        -116,  -59,  -66,  -44,  -53,  -33,  -36,  -77,
         -47,  -27,  -20,  -18,  -13,    4,   26,   11,
         -38,  -19,    7,   -3,   18,   21,   36,   17,
         -44,  -13,    3,   23,   22,   35,   24,   11,
         -62,  -20,   13,   40,   43,   43,   17,    4,
         -51,  -15,   27,   43,   47,   38,   11,   -9,
         -56,  -13,   12,   41,   38,   25,   -5,  -33,
         -54,  -50,  -29,  -11,  -52,  -14,  -56,  -95
    };

    private Constants() {}


    public static int getEvalParam(int index) { return EVAL_PARAMS[index]; }

    public static void setEvalParam(int index, int value) {
        EVAL_PARAMS[index] = value;
        Material.updateMaterialValues(index);
        PieceSquareTables.updateScoreTables(index);
    }
}
