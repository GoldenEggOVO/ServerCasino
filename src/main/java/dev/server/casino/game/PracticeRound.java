package dev.server.casino.game;

import java.util.Objects;
import java.util.Random;

/** Shared practice settlement only; each game owns its outcome and progress. */
public abstract class PracticeRound {
    public static final long STAKE = 1000;
    public static final long MIN_STAKE = 100;
    public static final long MAX_STAKE = 10000;
    protected final Random random;
    protected boolean active;
    protected boolean finished;
    protected long stake = STAKE;
    protected long payout;
    protected String result = "FREE PLAY - LOCAL RULES";

    protected PracticeRound(Random random) {
        this.random = Objects.requireNonNull(random);
    }

    public final boolean active() {
        return active;
    }

    public final boolean finished() {
        return finished;
    }

    public final long stake() {
        return stake;
    }

    public final long payout() {
        return payout;
    }

    public final String result() {
        return result;
    }

    public void setStake(long amount) {
        if (amount < MIN_STAKE || amount > MAX_STAKE) {
            throw new IllegalArgumentException("Stake must be between 100 and 10000");
        }
        if (active) {
            throw new IllegalStateException("Cannot change stake during an active round");
        }
        stake = amount;
    }

    protected final void begin() {
        active = true;
        finished = false;
        payout = 0;
        result = "FREE PLAY";
    }

    protected final void finish(long amount, String message) {
        payout = amount;
        active = false;
        finished = true;
        result = message + " / " + amount + " PRACTICE POINTS";
    }

    protected static int selection(String action) {
        if (action == null || !action.startsWith("select:")) {
            return -1;
        }
        try {
            return Integer.parseInt(action.substring(7));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
