package dev.server.casino.game.money_wheel;

import dev.server.casino.game.PracticeRound;

import java.util.Random;

public final class MoneyWheelRound extends PracticeRound {
    private static final int[] SEGMENTS = {
        0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 1, 0, 2, 0, 1
    };
    private static final int[] MULTIPLIERS = {2, 3, 5, 10};
    private int segment;
    private int choice;

    public MoneyWheelRound(Random random) {
        super(random);
    }

    public int segment() {
        return segment;
    }

    public int choice() {
        return choice;
    }

    public static int[] segments() {
        return SEGMENTS.clone();
    }

    public static int[] multipliers() {
        return MULTIPLIERS.clone();
    }

    public boolean available(String action) {
        return !active
                && ("play".equals(action)
                        || "next".equals(action)
                        || "previous".equals(action)
                        || selection(action) >= 0 && selection(action) < 4);
    }

    public void action(String action) {
        if (!available(action)) {
            return;
        }
        if (action.startsWith("select:")) {
            choice = selection(action);
        } else if (action.equals("next") || action.equals("previous")) {
            choice = Math.floorMod(choice + (action.equals("next") ? 1 : -1), 4);
        } else {
            begin();
            segment = random.nextInt(SEGMENTS.length);
            int category = SEGMENTS[segment];
            finish(
                    category == choice ? stake * MULTIPLIERS[category] : 0,
                    "WEDGE " + MULTIPLIERS[category] + "X");
        }
    }
}
