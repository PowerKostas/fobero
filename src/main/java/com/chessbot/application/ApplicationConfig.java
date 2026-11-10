package com.chessbot.application;

import com.chessbot.engine.ai.Chessbot;
import com.chessbot.engine.ai.NewVersionBot;
import com.chessbot.engine.ai.PreviousVersionBot;
import com.chessbot.engine.ai.RandomBot;

public final class ApplicationConfig {
    private ApplicationConfig() {}


    public static final int WHITE_PLAYER_TYPE = PlayerType.ENGINE;
    public static final int BLACK_PLAYER_TYPE = PlayerType.ENGINE;

    // Configures the versions of the engine when it's a human vs engine game and when it's an engine vs engine game
    public static final Chessbot RANDOM_BOT = new RandomBot();
    public static final Chessbot PREVIOUS_VERSION_BOT = new PreviousVersionBot("v16.2 - MAX_HARD_TIME_LIMIT_PERCENT = 80");
    public static final Chessbot NEW_VERSION_BOT = new NewVersionBot("v16.3 - MAX_HARD_TIME_LIMIT_PERCENT = 85");

    public static final Chessbot HUMAN_OPPONENT_BOT = NEW_VERSION_BOT;
    public static final Chessbot ENGINE_MATCH_WHITE_BOT = NEW_VERSION_BOT;
    public static final Chessbot ENGINE_MATCH_BLACK_BOT = PREVIOUS_VERSION_BOT;

    public static final int WHITE_INITIAL_TIME_MS = 5000;
    public static final int WHITE_INCREMENT_TIME_MS = 0;
    public static final int BLACK_INITIAL_TIME_MS = 5000;
    public static final int BLACK_INCREMENT_TIME_MS = 0;

    // Transposition table size. Bigger size equals faster speeds but also bigger memory requirements. Put 1 << 20 for instant
    // games, 1 << 22 for bullet, 1 << 24 for blitz, 1 << 25 for rapid and 1 << 26 for classical
    public static final int TT_SIZE = 1 << 22;

    // Jeroen.bin = Standard, Daring.bin = Risky
    public static final String OPENING_BOOK_FILE = "Jeroen.bin";

    // SPRT parameters
    public static final double ELO_0 = 0;
    public static final double ELO_1 = 10;
    public static final double ALPHA = 0.05;
    public static final double BETA = 0.05;

    // Texel's Tuning parameters
    public static final int STARTING_STEP_SIZE = 16;
    public static final double MIN_IMPROVEMENT_PER_PARAMETER = 1e-10;
    public static final double MIN_IMPROVEMENT_PER_PASS  = 1e-6;

    public static final int PERFT_SEARCH_DEPTH = 6;

    public static final String UI_GAME_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public static final String ENDLESS_MODE_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public static final String MATCH_RUNNER_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public static final String PERFT_FEN = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
}
