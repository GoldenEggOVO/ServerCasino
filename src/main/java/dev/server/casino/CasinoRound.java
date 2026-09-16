package dev.server.casino;

import java.util.*;

final class CasinoRound {
    enum Game {
        DICE,
        BLACKJACK,
        PLINKO,
        LIMBO,
        CRASH
    }

    enum Phase {
        DEBIT_PENDING,
        DOUBLE_PENDING,
        DOUBLE_APPLIED,
        ACTIVE,
        CREDIT_PENDING,
        CREDIT_READY,
        PAID,
        CANCELLED
    }

    UUID id, player;
    Game game;
    Phase phase;
    long stake, payout, started;
    int parameter, point, roll, path, revision, cursor;
    boolean practice, standing;
    List<Integer> deck = new ArrayList<>(), hand = new ArrayList<>(), dealer = new ArrayList<>();

    boolean finished() {
        return phase == Phase.PAID || phase == Phase.CANCELLED;
    }

    CasinoRound copy() {
        var r = new CasinoRound();
        r.id = id;
        r.player = player;
        r.game = game;
        r.phase = phase;
        r.stake = stake;
        r.payout = payout;
        r.started = started;
        r.parameter = parameter;
        r.point = point;
        r.roll = roll;
        r.path = path;
        r.revision = revision;
        r.cursor = cursor;
        r.practice = practice;
        r.standing = standing;
        r.deck = new ArrayList<>(deck);
        r.hand = new ArrayList<>(hand);
        r.dealer = new ArrayList<>(dealer);
        return r;
    }
}
