package dev.server.casino.game.duck_race;

import dev.server.casino.game.PracticeRound;

import java.util.Random;

public final class DuckRaceRound extends PracticeRound {
    private int winner;
    private int choice;

    public DuckRaceRound(Random random) {
        super(random);
    }

    public int winner() {
        return winner;
    }

    public int choice() {
        return choice;
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
            winner = random.nextInt(4);
            finish(winner == choice ? 4 * stake : 0, "DUCK " + (winner + 1));
        }
    }
}
