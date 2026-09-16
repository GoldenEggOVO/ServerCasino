package dev.server.casino.game.mines;

import dev.server.casino.MinesRound;
import dev.server.casino.game.DemoRound;

import java.util.Random;
import java.util.UUID;

public final class MinesDemoRound extends DemoRound {
    private MinesRound mines;
    private int mineCount = 3;

    public MinesDemoRound(Random random) {
        super(random);
    }

    public int mineCount() {
        return mineCount;
    }

    public MinesRound mines() {
        return mines == null ? null : mines.copy();
    }

    public void changeMines(int delta) {
        if (!active) {
            mineCount = (int) Math.max(1, Math.min(24, (long) mineCount + delta));
        }
    }

    @Override
    public void start(long now) {
        if (active) {
            return;
        }
        beginDemo();
        int mask = 0;
        while (Integer.bitCount(mask) < mineCount) {
            mask |= 1 << random.nextInt(25);
        }
        mines = MinesRound.create(UUID.randomUUID(), stake, mineCount, true, mask);
        mines.activatePractice();
    }

    public void reveal(int cell) {
        if (!active || (mines.revealed() & (1 << cell)) != 0) {
            return;
        }
        mines.reveal(cell);
        if (mines.phase() == MinesRound.Phase.LOST) {
            settle(0);
        } else if (mines.allSafe()) {
            cash(0);
        }
    }

    public boolean cash(long now) {
        if (!active || mines.safeCount() == 0) {
            return false;
        }
        long reward = mines.payout();
        mines.payPractice();
        settle(reward);
        return true;
    }
}
