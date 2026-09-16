package dev.server.casino.game.wheel_of_fortune;

import dev.server.casino.game.PracticeRound;

import java.util.Random;

public final class WheelOfFortuneRound extends PracticeRound {
    private static final double[] MULTIPLIERS = {
        -1, 3, .1, .5, .25, 5, .1, .25, 2, .1, -1, 3, .5, .1, .25, 0, .5, .1, 2, .25
    };
    private int segment;

    public WheelOfFortuneRound(Random random) {
        super(random);
    }

    public int segment() {
        return segment;
    }

    public static double[] multipliers() {
        return MULTIPLIERS.clone();
    }

    public boolean available(String action) {
        return "play".equals(action) && !active;
    }

    public void action(String action) {
        if (available(action)) {
            begin();
            spin();
        }
    }

    public void replayFortune() {
        if (active && MULTIPLIERS[segment] == -1) {
            spin();
        }
    }

    private void spin() {
        segment = random.nextInt(MULTIPLIERS.length);
        if (MULTIPLIERS[segment] == -1) {
            result = "SPIN AGAIN";
        } else {
            finish(Math.round(stake * MULTIPLIERS[segment]), MULTIPLIERS[segment] + "X");
        }
    }
}
