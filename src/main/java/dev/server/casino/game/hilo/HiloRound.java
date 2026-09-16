package dev.server.casino.game.hilo;

import dev.server.casino.game.PracticeRound;

import java.util.List;
import java.util.Random;

public final class HiloRound extends PracticeRound {
    private int threshold = 50;
    private boolean high = true;
    private List<Integer> rolls = List.of();

    public HiloRound(Random random) {
        super(random);
    }

    public int threshold() {
        return threshold;
    }

    public boolean high() {
        return high;
    }

    public List<Integer> rolls() {
        return rolls;
    }

    public void setThreshold(int threshold) {
        if (!active) {
            this.threshold = Math.clamp(threshold, 1, 98);
        }
    }

    public boolean available(String action) {
        return !active
                && ("play".equals(action)
                        || "flip".equals(action)
                        || "under".equals(action)
                        || "over".equals(action)
                        || "next".equals(action)
                        || "previous".equals(action));
    }

    public void action(String action) {
        if (!available(action)) {
            return;
        }
        switch (action) {
            case "next" -> setThreshold(threshold + 1);
            case "previous" -> setThreshold(threshold - 1);
            case "flip" -> high = !high;
            case "under" -> high = false;
            case "over" -> high = true;
            case "play" -> {
                begin();
                int roll = random.nextInt(100);
                rolls = List.of(roll);
                boolean win = high ? roll > threshold : roll < threshold;
                int winningNumbers = high ? 99 - threshold : threshold;
                finish(
                        win ? stake * 100 / winningNumbers : 0,
                        "ROLL " + roll + (high ? " > " : " < ") + threshold);
            }
            default -> {}
        }
    }
}
