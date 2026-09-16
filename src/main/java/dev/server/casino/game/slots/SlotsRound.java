package dev.server.casino.game.slots;

import dev.server.casino.game.PracticeRound;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SlotsRound extends PracticeRound {
    private final List<Integer> symbols = new ArrayList<>();

    public SlotsRound(Random random) {
        super(random);
    }

    public List<Integer> symbols() {
        return List.copyOf(symbols);
    }

    public boolean available(String action) {
        return "play".equals(action) && !active;
    }

    public void action(String action) {
        if (!available(action)) {
            return;
        }
        begin();
        symbols.clear();
        for (int i = 0; i < 9; i++) {
            symbols.add(random.nextInt(5));
        }
        long pay = 0;
        for (int row = 0; row < 3; row++) {
            int a = symbols.get(row * 3);
            int b = symbols.get(row * 3 + 1);
            int c = symbols.get(row * 3 + 2);
            long rowStake = row == 2 ? stake - 2 * (stake / 3) : stake / 3;
            pay +=
                    a == b && b == c
                            ? (a + 1) * 2 * rowStake
                            : a == b || b == c || a == c ? rowStake : 0;
        }
        finish(pay, "SYMBOLS " + symbols);
    }
}
