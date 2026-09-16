package dev.server.casino.game;

import java.util.Locale;
import java.util.Random;

/** Shared practice stake capture and result text for the original demo machines. */
public abstract class DemoRound extends PracticeRound {
    private long configuredStake = STAKE;

    protected DemoRound(Random random) {
        super(random);
        result = "点击开始 · 每局 10 练习金币";
    }

    public final long configuredStake() {
        return configuredStake;
    }

    public final void setConfiguredStake(long amount) {
        if (!active && amount >= MIN_STAKE && amount <= MAX_STAKE && amount % 100 == 0) {
            configuredStake = amount;
        }
    }

    protected final void beginDemo() {
        begin();
        stake = configuredStake;
        result = "进行中";
    }

    protected final void settle(long amount) {
        payout = amount;
        active = false;
        finished = true;
        result = amount > 0 ? "练习返还 " + money(amount) + " · 点击再来一局" : "本局结束 · 点击再来一局";
    }

    protected static String money(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }

    public abstract void start(long now);
}
