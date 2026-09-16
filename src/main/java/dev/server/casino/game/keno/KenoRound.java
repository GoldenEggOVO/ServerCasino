package dev.server.casino.game.keno;

import dev.server.casino.game.PracticeRound;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class KenoRound extends PracticeRound {
    private final Set<Integer> selected = new HashSet<>();
    private final List<Integer> drawnNumbers = new ArrayList<>();
    private int hits;

    public KenoRound(Random random) {
        super(random);
    }

    public Set<Integer> selected() {
        return Set.copyOf(selected);
    }

    public List<Integer> drawnNumbers() {
        return List.copyOf(drawnNumbers);
    }

    public int hits() {
        return hits;
    }

    public boolean available(String action) {
        if (active) {
            return false;
        }
        int number = selection(action);
        return "play".equals(action) && !selected.isEmpty()
                || number >= 1
                        && number <= 40
                        && (selected.contains(number) || selected.size() < 10);
    }

    public void action(String action) {
        if (!available(action)) {
            return;
        }
        if (action.startsWith("select:")) {
            int number = selection(action);
            if (!selected.remove(number)) {
                selected.add(number);
            }
            return;
        }
        begin();
        drawnNumbers.clear();
        List<Integer> pool = new ArrayList<>();
        for (int number = 1; number <= 40; number++) {
            pool.add(number);
        }
        for (int i = 0; i < 10; i++) {
            drawnNumbers.add(pool.remove(random.nextInt(pool.size())));
        }
        hits = (int) drawnNumbers.stream().filter(selected::contains).count();
        finish(stake * 4 * hits / selected.size(), "HITS " + hits + "/" + selected.size());
    }
}
