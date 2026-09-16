package dev.server.casino;

import java.util.List;

/** All payouts include the original stake, rounded down to cents. */
public final class CasinoRules {
    public static final long RANDOM_SPACE = 1_000_000_000L;
    static final int[] PLINKO_WEIGHTS = {640, 160, 40, 20, 10, 5, 2, 5, 10, 20, 40, 160, 640};
    private static final long PLINKO_SUM = plinkoSum();

    private CasinoRules() {}

    public static long dicePayout(long stake, int chance, int roll) {
        if (chance < 5 || chance > 95 || roll < 0 || roll >= 10000) {
            throw new IllegalArgumentException("无效 Dice 参数");
        }
        return roll < chance * 100 ? stake * 98 / chance : 0;
    }

    public static int point(long sample) {
        if (sample < 0 || sample >= RANDOM_SPACE) {
            throw new IllegalArgumentException("无效随机样本");
        }
        return (int) Math.min(10000, 98 * RANDOM_SPACE / (RANDOM_SPACE - sample));
    }

    public static long targetPayout(long stake, int target, int point) {
        return point >= target ? stake * target / 100 : 0;
    }

    private static long plinkoSum() {
        int combinations = 1;
        long sum = 0;
        for (int i = 0; i <= 12; i++) {
            sum += (long) combinations * PLINKO_WEIGHTS[i];
            if (i < 12) {
                combinations = combinations * (12 - i) / (i + 1);
            }
        }
        return sum;
    }

    public static long plinkoPayout(long stake, int slot) {
        if (slot < 0 || slot > 12) {
            throw new IllegalArgumentException("无效 Plinko 槽位");
        }
        return stake * 98 * 4096 * PLINKO_WEIGHTS[slot] / (100 * PLINKO_SUM);
    }

    public static int total(List<Integer> cards) {
        int sum = 0;
        int aces = 0;
        for (int card : cards) {
            int rank = card % 13;
            sum += rank == 0 ? 11 : Math.min(rank + 1, 10);
            if (rank == 0) {
                aces++;
            }
        }
        while (sum > 21 && aces-- > 0) {
            sum -= 10;
        }
        return sum;
    }

    public static boolean natural(List<Integer> cards) {
        return cards.size() == 2 && total(cards) == 21;
    }

    public static long blackjackPayout(long stake, List<Integer> player, List<Integer> dealer) {
        int playerTotal = total(player);
        int dealerTotal = total(dealer);
        if (playerTotal > 21) {
            return 0;
        }
        if (natural(dealer)) {
            return natural(player) ? stake : 0;
        }
        if (natural(player)) {
            return stake * 5 / 2;
        }
        if (dealerTotal > 21 || playerTotal > dealerTotal) {
            return stake * 2;
        }
        return playerTotal == dealerTotal ? stake : 0;
    }

    public static int crashMultiplier(long started, long now) {
        return (int) Math.min(10000, 100 + Math.max(0, now - started) / 30);
    }

    public static String cards(List<Integer> cards) {
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        String[] suits = {"♠", "♥", "♣", "♦"};
        return String.join(
                "  ", cards.stream().map(card -> suits[card / 13] + ranks[card % 13]).toList());
    }
}
