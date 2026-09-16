package dev.server.casino;

// Frozen 0.3.3-preview behavior oracle. Production uses the independent game rounds.

import java.util.*;

/** In-memory, free previews only. No accounts, balance or persistence. */
final class FrozenCasinoDemoRound {
    final String kind;
    final Random random;
    final List<Integer> player = new ArrayList<>(),
            dealer = new ArrayList<>(),
            deck = new ArrayList<>();
    MinesRound mines;
    boolean active, cashed;
    long stake = 1000, configuredStake = 1000;
    int mineCount = 3;
    int chance = 50, target = 200, point = 100, value = 100, next;
    long started, payout;
    String result = "点击开始 · 每局 10 练习金币";

    FrozenCasinoDemoRound(String kind, Random random) {
        this.kind = kind;
        this.random = random;
    }

    void setConfiguredStake(long amount) {
        if (!active && amount >= 100 && amount <= 10000 && amount % 100 == 0)
            configuredStake = amount;
    }

    void changeMines(int delta) {
        if (!active) mineCount = (int) Math.max(1, Math.min(24, (long) mineCount + delta));
    }

    void start(long now) {
        if (active) return;
        payout = 0;
        cashed = false;
        stake = configuredStake;
        started = now;
        active = true;
        value = 100;
        result = "进行中";
        switch (kind) {
            case "mines" -> {
                int mask = 0;
                while (Integer.bitCount(mask) < mineCount) mask |= 1 << random.nextInt(25);
                mines = MinesRound.create(UUID.randomUUID(), stake, mineCount, true, mask);
                mines.phase = MinesRound.Phase.ACTIVE;
            }
            case "dice" -> {
                value = random.nextInt(10000);
                finish(CasinoRules.dicePayout(stake, chance, value));
            }
            case "limbo" -> {
                point = CasinoRules.point(random.nextLong(CasinoRules.RANDOM_SPACE));
                value = point;
                finish(CasinoRules.targetPayout(stake, target, point));
            }
            case "crash" -> point = CasinoRules.point(random.nextLong(CasinoRules.RANDOM_SPACE));
            case "blackjack" -> {
                deck.clear();
                for (int i = 0; i < 52; i++) deck.add(i);
                Collections.shuffle(deck, random);
                next = 0;
                player.clear();
                dealer.clear();
                player.add(draw());
                dealer.add(draw());
                player.add(draw());
                dealer.add(draw());
                if (CasinoRules.natural(player) || CasinoRules.natural(dealer)) settleBlackjack();
            }
            default -> throw new IllegalArgumentException(kind);
        }
    }

    void reveal(int cell) {
        if (!active || mines == null || (mines.revealed & (1 << cell)) != 0) return;
        mines.reveal(cell);
        if (mines.phase == MinesRound.Phase.LOST) finish(0);
        else if (mines.allSafe()) cash(0);
    }

    boolean cash(long now) {
        if (!active) return false;
        if (kind.equals("mines")) {
            if (mines.safeCount() == 0) return false;
            long reward = mines.payout();
            mines.phase = MinesRound.Phase.PAID;
            finish(reward);
            return true;
        }
        if (kind.equals("crash")) {
            tick(now);
            if (!active || cashed) return false;
            cashed = true;
            payout = stake * value / 100;
            result =
                    "已领取 "
                            + String.format(java.util.Locale.ROOT, "%.2f", payout / 100.0)
                            + " · 等待爆点";
            return true;
        }
        return false;
    }

    void tick(long now) {
        if (active && kind.equals("crash")) {
            value = CasinoRules.crashMultiplier(started, now);
            if (value >= point) {
                value = point;
                finish(cashed ? payout : 0);
            }
        }
    }

    void hit() {
        if (active && kind.equals("blackjack")) {
            player.add(draw());
            if (CasinoRules.total(player) >= 21) stand();
        }
    }

    void stand() {
        if (!active || !kind.equals("blackjack")) return;
        while (CasinoRules.total(player) <= 21 && CasinoRules.total(dealer) < 17)
            dealer.add(draw());
        settleBlackjack();
    }

    void doubleDown() {
        if (!active || !kind.equals("blackjack") || player.size() != 2) return;
        stake *= 2;
        player.add(draw());
        stand();
    }

    private int draw() {
        return deck.get(next++);
    }

    private void settleBlackjack() {
        finish(CasinoRules.blackjackPayout(stake, player, dealer));
    }

    private void finish(long amount) {
        payout = amount;
        active = false;
        result =
                amount > 0
                        ? "练习返还 "
                                + String.format(java.util.Locale.ROOT, "%.2f", amount / 100.0)
                                + " · 点击再来一局"
                        : "本局结束 · 点击再来一局";
    }
}
