package dev.server.casino;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Persistent Mines state; field names remain stable for existing JSON records. */
public final class MinesRound {
    public enum Phase {
        DEBIT_PENDING,
        ACTIVE,
        CREDIT_PENDING,
        PAID,
        LOST,
        CANCELLED
    }

    UUID id;
    UUID player;
    long stake;
    long created;
    long payoutDue;
    int mines;
    int mask;
    int revealed;
    int revision;
    boolean practice;
    Phase phase;

    public static MinesRound create(
            UUID player, long stake, int mines, boolean practice, int mask) {
        if (stake < 100
                || stake > 10000
                || stake % 100 != 0
                || mines < 1
                || mines > 24
                || Integer.bitCount(mask) != mines
                || (mask >>> 25) != 0) {
            throw new IllegalArgumentException("投注须为 1～100 整数金币，雷数须为 1～24");
        }
        MinesRound round = new MinesRound();
        round.id = UUID.randomUUID();
        round.player = player;
        round.stake = stake;
        round.mines = mines;
        round.practice = practice;
        round.mask = mask;
        round.created = System.currentTimeMillis();
        round.phase = Phase.DEBIT_PENDING;
        return round;
    }

    public void reveal(int cell) {
        if (phase != Phase.ACTIVE || allSafe()) {
            throw new IllegalStateException("这一局不能继续翻格");
        }
        if (cell < 0 || cell >= 25) {
            throw new IllegalArgumentException("无效格子");
        }
        int bit = 1 << cell;
        if ((revealed & bit) != 0) {
            throw new IllegalStateException("这个格子已经翻开");
        }
        revealed |= bit;
        revision++;
        if ((mask & bit) != 0) {
            phase = Phase.LOST;
        }
    }

    public int safeCount() {
        return Integer.bitCount(revealed & ~mask);
    }

    public boolean allSafe() {
        return safeCount() == 25 - mines;
    }

    public boolean finished() {
        return phase == Phase.PAID || phase == Phase.LOST || phase == Phase.CANCELLED;
    }

    public long payout() {
        if (phase == Phase.LOST) {
            return 0;
        }
        int safe = safeCount();
        if (safe == 0) {
            throw new IllegalStateException("至少翻开一个安全格才能领取");
        }
        return BigDecimal.valueOf(stake)
                .multiply(new BigDecimal("0.98"))
                .multiply(new BigDecimal(choose(25, safe)))
                .divide(new BigDecimal(choose(25 - mines, safe)), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    static long choose(int n, int k) {
        long value = 1;
        for (int i = 1; i <= k; i++) {
            value = value * (n - i + 1) / i;
        }
        return value;
    }

    public int mask() {
        return mask;
    }

    public int revealed() {
        return revealed;
    }

    public Phase phase() {
        return phase;
    }

    public long stake() {
        return stake;
    }

    public void activatePractice() {
        if (!practice || phase != Phase.DEBIT_PENDING) {
            throw new IllegalStateException("Not a pending practice round");
        }
        phase = Phase.ACTIVE;
    }

    public void payPractice() {
        if (!practice || phase != Phase.ACTIVE || safeCount() == 0) {
            throw new IllegalStateException("Not a payable practice round");
        }
        phase = Phase.PAID;
    }

    public MinesRound copy() {
        MinesRound round = new MinesRound();
        round.id = id;
        round.player = player;
        round.stake = stake;
        round.created = created;
        round.payoutDue = payoutDue;
        round.mines = mines;
        round.mask = mask;
        round.revealed = revealed;
        round.revision = revision;
        round.practice = practice;
        round.phase = phase;
        return round;
    }
}
