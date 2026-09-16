package dev.server.casino.game.penguin_cross;

import dev.server.casino.game.PracticeRound;

import java.util.Random;

public final class PenguinCrossRound extends PracticeRound {
    private int steps;
    private int lastRoll;

    public PenguinCrossRound(Random random) {
        super(random);
    }

    public int steps() {
        return steps;
    }

    public int lastRoll() {
        return lastRoll;
    }

    public boolean available(String action) {
        return "play".equals(action) && !active
                || "step".equals(action) && active
                || "cash".equals(action) && active && steps > 0;
    }

    public void action(String action) {
        if (!available(action)) {
            return;
        }
        switch (action) {
            case "play" -> {
                begin();
                steps = 0;
                result = "STEP OR STOP - 8 STEPS / 20% RISK";
            }
            case "cash" -> finish(payout, "COLLECTED");
            case "step" -> {
                lastRoll = random.nextInt(100);
                if (lastRoll < 20) {
                    finish(0, "FELL AT STEP " + (steps + 1));
                    return;
                }
                steps++;
                payout = Math.round(stake * Math.pow(1.25, steps));
                result = "STEP " + steps + "/8 - CASH OUT " + payout;
                if (steps == 8) {
                    finish(payout, "CROSSED");
                }
            }
            default -> {}
        }
    }
}
