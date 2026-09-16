package dev.server.casino.game.dragon_tower;

import dev.server.casino.game.PracticeRound;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DragonTowerRound extends PracticeRound {
    private int floors;
    private int lane;
    private final List<Integer> traps = new ArrayList<>();

    public DragonTowerRound(Random random) {
        super(random);
    }

    public int floors() {
        return floors;
    }

    public int lane() {
        return lane;
    }

    public List<Integer> traps() {
        return List.copyOf(traps);
    }

    public boolean available(String action) {
        return "play".equals(action) && !active
                || "cash".equals(action) && active && floors > 0
                || active && selection(action) >= 0 && selection(action) < 4;
    }

    public void action(String action) {
        if (!available(action)) {
            return;
        }
        if (action.equals("play")) {
            begin();
            floors = 0;
            traps.clear();
            result = "CHOOSE PATH - 6 FLOORS / 1 TRAP IN 4";
        } else if (action.equals("cash")) {
            finish(payout, "COLLECTED");
        } else {
            int trap = random.nextInt(4);
            traps.add(trap);
            lane = selection(action);
            if (lane == trap) {
                finish(0, "TRAP AT FLOOR " + (floors + 1));
                return;
            }
            floors++;
            payout = Math.round(stake * Math.pow(4.0 / 3, floors));
            result = "FLOOR " + floors + "/6 - CASH OUT " + payout;
            if (floors == 6) {
                finish(payout, "TOWER CLEARED");
            }
        }
    }
}
