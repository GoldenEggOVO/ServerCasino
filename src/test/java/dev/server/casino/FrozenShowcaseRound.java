package dev.server.casino;

// Frozen 0.3.3-preview behavior oracle. Production uses the independent game rounds.

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * Standalone free-play rules. Payout includes the configured practice stake. These published local
 * rules are adaptations, not GWYF's original paytables.
 */
final class FrozenShowcaseRound {
    static final long STAKE = 1000;
    static final long MIN_STAKE = 100;
    static final long MAX_STAKE = 10000;
    static final double[] FORTUNE = {
        -1, 3, .1, .5, .25, 5, .1, .25, 2, .1, -1, 3, .5, .1, .25, 0, .5, .1, 2, .25
    };
    static final int[] MONEY_SEGMENTS = {
        0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 1, 0, 2, 0, 1
    };
    static final int[] MONEY_MULTIPLIERS = {2, 3, 5, 10};
    private static final Set<String> KINDS =
            Set.of(
                    "slots",
                    "duck_race",
                    "wheel_of_fortune",
                    "money_wheel",
                    "penguin_cross",
                    "keno",
                    "hilo",
                    "dragon_tower");

    final String kind;
    boolean active;
    boolean finished;
    boolean high = true;
    long stake = STAKE;
    long payout;
    String result = "FREE PLAY - LOCAL RULES";
    int stage, value, choice;
    final List<Integer> values = new ArrayList<>();
    final Set<Integer> selected = new HashSet<>();
    private final Random random;

    FrozenShowcaseRound(String kind, Random random) {
        if (!KINDS.contains(kind)) throw new IllegalArgumentException("Unknown game: " + kind);
        this.kind = kind;
        this.random = Objects.requireNonNull(random);
        if (kind.equals("hilo")) value = 50;
    }

    boolean available(String action) {
        if (action == null) return false;
        if (action.startsWith("select:")) {
            int n = selection(action);
            return switch (kind) {
                case "duck_race", "money_wheel" -> !active && n >= 0 && n < 4;
                case "keno" ->
                        !active
                                && n >= 1
                                && n <= 40
                                && (selected.contains(n) || selected.size() < 10);
                case "dragon_tower" -> active && n >= 0 && n < 4;
                default -> false;
            };
        }
        return switch (action) {
            case "play" -> !active && (!kind.equals("keno") || !selected.isEmpty());
            case "cash" ->
                    active
                            && stage > 0
                            && (kind.equals("penguin_cross") || kind.equals("dragon_tower"));
            case "step" -> active && kind.equals("penguin_cross");
            case "flip" -> !active && kind.equals("hilo");
            case "under", "over" -> !active && kind.equals("hilo");
            case "next", "previous" ->
                    !active && Set.of("duck_race", "money_wheel", "hilo").contains(kind);
            default -> false;
        };
    }

    void action(String action) {
        if (!available(action)) return;
        if (action.startsWith("select:")) {
            int n = selection(action);
            if (kind.equals("dragon_tower")) climb(n);
            else if (kind.equals("keno")) {
                if (!selected.remove(n)) selected.add(n);
            } else choice = n;
            return;
        }
        switch (action) {
            case "next", "previous" -> {
                int delta = action.equals("next") ? 1 : -1;
                if (kind.equals("hilo")) value = Math.clamp(value + delta, 1, 98);
                else choice = Math.floorMod(choice + delta, 4);
            }
            case "flip" -> high = !high;
            case "under" -> high = false;
            case "over" -> high = true;
            case "cash" -> finish(payout, "COLLECTED");
            case "step" -> step();
            case "play" -> start();
            default -> {}
        }
    }

    void setStake(long stake) {
        if (stake < MIN_STAKE || stake > MAX_STAKE)
            throw new IllegalArgumentException("Stake must be between 100 and 10000");
        if (active) throw new IllegalStateException("Cannot change stake during an active round");
        this.stake = stake;
    }

    void setThreshold(int threshold) {
        if (kind.equals("hilo") && !active) value = Math.clamp(threshold, 1, 98);
    }

    private static int selection(String action) {
        try {
            return Integer.parseInt(action.substring(7));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void start() {
        active = true;
        finished = false;
        payout = 0;
        stage = 0;
        values.clear();
        if (!kind.equals("keno")) selected.clear();
        result = "FREE PLAY";
        switch (kind) {
            case "slots" -> {
                for (int i = 0; i < 9; i++) values.add(random.nextInt(5));
                long pay = 0;
                for (int row = 0; row < 3; row++) {
                    int a = values.get(row * 3),
                            b = values.get(row * 3 + 1),
                            c = values.get(row * 3 + 2);
                    long rowStake = row == 2 ? stake - 2 * (stake / 3) : stake / 3;
                    pay +=
                            a == b && b == c
                                    ? (a + 1) * 2 * rowStake
                                    : a == b || b == c || a == c ? rowStake : 0;
                }
                finish(pay, "SYMBOLS " + values);
            }
            case "duck_race" -> {
                value = random.nextInt(4);
                values.add(value);
                finish(value == choice ? 4 * stake : 0, "DUCK " + (value + 1));
            }
            case "wheel_of_fortune" -> spinFortune();
            case "money_wheel" -> {
                value = random.nextInt(MONEY_SEGMENTS.length);
                values.add(value);
                int category = MONEY_SEGMENTS[value];
                finish(
                        category == choice ? stake * MONEY_MULTIPLIERS[category] : 0,
                        "WEDGE " + MONEY_MULTIPLIERS[category] + "X");
            }
            case "penguin_cross" -> result = "STEP OR STOP - 8 STEPS / 20% RISK";
            case "dragon_tower" -> result = "CHOOSE PATH - 6 FLOORS / 1 TRAP IN 4";
            case "keno" -> keno();
            case "hilo" -> {
                int roll = random.nextInt(100);
                values.add(roll);
                boolean win = high ? roll > value : roll < value;
                int winningNumbers = high ? 99 - value : value;
                finish(
                        win ? stake * 100 / winningNumbers : 0,
                        "ROLL " + roll + (high ? " > " : " < ") + value);
            }
            default -> throw new IllegalStateException(kind);
        }
    }

    private void finish(long amount, String message) {
        payout = amount;
        active = false;
        finished = true;
        result = message + " / " + amount + " PRACTICE POINTS";
    }

    void replayFortune() {
        if (kind.equals("wheel_of_fortune") && active && FORTUNE[value] == -1) spinFortune();
    }

    private void spinFortune() {
        value = random.nextInt(FORTUNE.length);
        values.clear();
        values.add(value);
        if (FORTUNE[value] == -1) result = "SPIN AGAIN";
        else finish(Math.round(stake * FORTUNE[value]), FORTUNE[value] + "X");
    }

    private void step() {
        value = random.nextInt(100);
        if (value < 20) {
            finish(0, "FELL AT STEP " + (stage + 1));
            return;
        }
        stage++;
        payout = Math.round(stake * Math.pow(1.25, stage));
        result = "STEP " + stage + "/8 - CASH OUT " + payout;
        if (stage == 8) finish(payout, "CROSSED");
    }

    private void climb(int lane) {
        int trap = random.nextInt(4);
        values.add(trap);
        value = lane;
        if (lane == trap) {
            finish(0, "TRAP AT FLOOR " + (stage + 1));
            return;
        }
        stage++;
        payout = Math.round(stake * Math.pow(4.0 / 3, stage));
        result = "FLOOR " + stage + "/6 - CASH OUT " + payout;
        if (stage == 6) finish(payout, "TOWER CLEARED");
    }

    private void keno() {
        List<Integer> pool = new ArrayList<>();
        for (int i = 1; i <= 40; i++) pool.add(i);
        for (int i = 0; i < 10; i++) values.add(pool.remove(random.nextInt(pool.size())));
        value = (int) values.stream().filter(selected::contains).count();
        // Local transparent score table: each hit returns 4/N of the practice stake.
        finish(stake * 4 * value / selected.size(), "HITS " + value + "/" + selected.size());
    }
}
