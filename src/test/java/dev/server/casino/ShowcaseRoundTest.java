package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

class ShowcaseRoundTest {
    private static Random rolls(int... values) {
        return new Random(0) {
            int index;

            @Override
            public int nextInt(int bound) {
                return Math.floorMod(values[index++ % values.length], bound);
            }
        };
    }

    @Test
    void stakeDefaultsAndAcceptsOnlyPublishedRange() {
        FrozenShowcaseRound game = new FrozenShowcaseRound("slots", rolls(4));
        assertEquals(1000, game.stake);
        for (long invalid : new long[] {Long.MIN_VALUE, -1, 0, 99, 10001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> game.setStake(invalid));
            assertEquals(1000, game.stake);
        }
        game.setStake(100);
        assertEquals(100, game.stake);
        game.setStake(10000);
        assertEquals(10000, game.stake);
    }

    @Test
    void activeRoundsLockStakeAndFortuneAgainKeepsIt() {
        for (String kind : List.of("penguin_cross", "dragon_tower", "wheel_of_fortune")) {
            FrozenShowcaseRound game = new FrozenShowcaseRound(kind, rolls(0, 5));
            game.setStake(2400);
            game.action("play");
            assertTrue(game.active);
            assertThrows(IllegalStateException.class, () -> game.setStake(100));
            assertEquals(2400, game.stake);
            if (kind.equals("wheel_of_fortune")) {
                game.replayFortune();
                assertEquals(12000, game.payout);
                game.setStake(100);
                assertEquals(100, game.stake);
                assertEquals(12000, game.payout);
            }
        }
    }

    @Test
    void everyGamePaysUsingConfiguredStakeWithoutChangingOdds() {
        for (long stake : new long[] {300, 2400}) {
            for (String kind :
                    List.of(
                            "slots",
                            "duck_race",
                            "wheel_of_fortune",
                            "money_wheel",
                            "penguin_cross",
                            "dragon_tower",
                            "keno",
                            "hilo")) {
                int roll =
                        switch (kind) {
                            case "slots" -> 4;
                            case "wheel_of_fortune" -> 5;
                            case "penguin_cross", "hilo" -> 99;
                            case "dragon_tower" -> 3;
                            default -> 0;
                        };
                FrozenShowcaseRound game = new FrozenShowcaseRound(kind, rolls(roll));
                game.setStake(stake);
                if (kind.equals("keno")) game.action("select:1");
                if (kind.equals("hilo")) game.setThreshold(49);
                game.action("play");
                if (kind.equals("penguin_cross")) game.action("step");
                if (kind.equals("dragon_tower")) game.action("select:0");
                long expected =
                        switch (kind) {
                            case "slots" -> stake * 10;
                            case "duck_race", "keno" -> stake * 4;
                            case "wheel_of_fortune" -> stake * 5;
                            case "money_wheel", "hilo" -> stake * 2;
                            case "penguin_cross" -> Math.round(stake * 1.25);
                            case "dragon_tower" -> Math.round(stake * 4.0 / 3);
                            default -> throw new AssertionError(kind);
                        };
                assertEquals(expected, game.payout, kind + " stake " + stake);
            }
        }
    }

    @Test
    void slotsAndDuckExposeDeterministicResults() {
        FrozenShowcaseRound slots = new FrozenShowcaseRound("slots", rolls(4));
        slots.action("play");
        assertEquals(List.of(4, 4, 4, 4, 4, 4, 4, 4, 4), slots.values);
        assertEquals(10000, slots.payout);
        FrozenShowcaseRound duck = new FrozenShowcaseRound("duck_race", rolls(3));
        duck.action("select:3");
        duck.action("play");
        assertEquals(4000, duck.payout);
        assertEquals(3, duck.value);
    }

    @Test
    void wheelsHaveDifferentOutcomeRules() {
        FrozenShowcaseRound fortune = new FrozenShowcaseRound("wheel_of_fortune", rolls(5));
        fortune.action("play");
        assertEquals(5000, fortune.payout);
        FrozenShowcaseRound money = new FrozenShowcaseRound("money_wheel", rolls(0));
        money.action("select:1");
        money.action("play");
        assertEquals(0, money.payout);
    }

    @Test
    void retiredGamesAreRejectedAndAbsentFromCatalog() {
        for (String kind : List.of("street_craps", "poker", "baccarat", "roulette")) {
            assertThrows(
                    IllegalArgumentException.class, () -> new FrozenShowcaseRound(kind, rolls(0)));
            assertTrue(MachineCatalog.ENTRIES.stream().noneMatch(entry -> entry.id().equals(kind)));
        }
        assertEquals(12, MachineCatalog.ENTRIES.size());
    }

    @Test
    void fortuneHasTwentySegmentsAndPaysFractionalMultipliers() {
        assertEquals(20, FrozenShowcaseRound.FORTUNE.length);
        for (int[] outcome : new int[][] {{2, 100}, {3, 500}, {4, 250}, {5, 5000}, {15, 0}}) {
            FrozenShowcaseRound game =
                    new FrozenShowcaseRound("wheel_of_fortune", rolls(outcome[0]));
            game.action("play");
            assertEquals(outcome[1], game.payout);
            assertTrue(game.finished);
            assertFalse(game.active);
        }
    }

    @Test
    void fortuneReplayWaitsForAnimationAndDrawsOnlyOncePerRequest() {
        FrozenShowcaseRound game = new FrozenShowcaseRound("wheel_of_fortune", rolls(0, 10, 4));
        game.action("play");
        assertTrue(game.active);
        assertFalse(game.finished);
        assertEquals("SPIN AGAIN", game.result);
        assertEquals(List.of(0), game.values);
        assertFalse(game.available("play"));
        game.replayFortune();
        assertTrue(game.active);
        assertEquals(10, game.value);
        assertEquals("SPIN AGAIN", game.result);
        game.replayFortune();
        assertFalse(game.active);
        assertTrue(game.finished);
        assertEquals(250, game.payout);
        game.replayFortune();
        assertEquals(250, game.payout);
        assertEquals(4, game.value);
    }

    @Test
    void moneyWheelInterleavesCategoriesWithoutChangingWeights() {
        assertArrayEquals(
                new int[] {0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 1, 0, 2, 0, 1},
                FrozenShowcaseRound.MONEY_SEGMENTS);
    }

    @Test
    void progressionCannotCashTwiceOrChangeFinishedPayout() {
        FrozenShowcaseRound penguin = new FrozenShowcaseRound("penguin_cross", rolls(99));
        penguin.action("play");
        assertFalse(penguin.available("cash"));
        penguin.action("step");
        assertEquals(1, penguin.stage);
        penguin.action("cash");
        long paid = penguin.payout;
        assertTrue(paid > 1000);
        penguin.action("step");
        penguin.action("cash");
        assertEquals(paid, penguin.payout);
        FrozenShowcaseRound tower = new FrozenShowcaseRound("dragon_tower", rolls(2));
        tower.action("play");
        tower.action("select:0");
        assertEquals(1, tower.stage);
        tower.action("select:2");
        assertEquals(0, tower.payout);
        assertFalse(tower.active);
    }

    @Test
    void kenoCapsSelectionsAndDrawsWithoutReplacement() {
        FrozenShowcaseRound game = new FrozenShowcaseRound("keno", new Random(17));
        for (int i = 1; i <= 11; i++) game.action("select:" + i);
        assertEquals(10, game.selected.size());
        assertFalse(game.available("select:0"));
        assertFalse(game.available("select:41"));
        game.action("play");
        assertEquals(10, game.values.size());
        assertEquals(10, new HashSet<>(game.values).size());
        assertTrue(game.values.stream().allMatch(n -> n >= 1 && n <= 40));
    }

    @Test
    void hiloStrictEqualityLosesAndThresholdIsBounded() {
        FrozenShowcaseRound game = new FrozenShowcaseRound("hilo", rolls(50));
        game.action("play");
        assertEquals(0, game.payout);
        for (int i = 0; i < 200; i++) game.action("next");
        assertEquals(98, game.value);
        for (int i = 0; i < 200; i++) game.action("previous");
        assertEquals(1, game.value);
        game.action("flip");
        game.action("play");
        assertEquals(0, game.payout);
        FrozenShowcaseRound win = new FrozenShowcaseRound("hilo", rolls(99));
        win.action("play");
        assertEquals(100000 / 49, win.payout);
    }

    @Test
    void invalidActionsAreNoOpsAndUnknownKindRejected() {
        FrozenShowcaseRound game = new FrozenShowcaseRound("hilo", rolls(0));
        game.action("draw");
        game.action("select:garbage");
        game.action("select:-1");
        assertTrue(game.values.isEmpty());
        assertFalse(game.available(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrozenShowcaseRound("missing", new Random()));
    }

    @Test
    void outcomesDoNotDependOnSelectionsAndPayoutTablesMatch() {
        for (int category = 0; category < 4; category++) {
            long wheelReturn = 0;
            for (int segment = 0; segment < 20; segment++) {
                FrozenShowcaseRound game = new FrozenShowcaseRound("money_wheel", rolls(segment));
                game.action("select:" + category);
                game.action("play");
                assertEquals(segment, game.value);
                wheelReturn += game.payout;
            }
            assertEquals(new long[] {20000, 18000, 15000, 10000}[category], wheelReturn);
        }
    }

    @Test
    void progressionCompletesAtPublishedLimitsAndCannotAdvanceAgain() {
        FrozenShowcaseRound penguin = new FrozenShowcaseRound("penguin_cross", rolls(20));
        penguin.action("play");
        for (int i = 0; i < 8; i++) penguin.action("step");
        assertEquals(8, penguin.stage);
        assertTrue(penguin.finished);
        long pay = penguin.payout;
        penguin.action("step");
        penguin.action("cash");
        assertEquals(pay, penguin.payout);
        FrozenShowcaseRound tower = new FrozenShowcaseRound("dragon_tower", rolls(2));
        tower.action("play");
        for (int i = 0; i < 6; i++) tower.action("select:1");
        assertEquals(6, tower.stage);
        assertTrue(tower.finished);
        assertEquals(5619, tower.payout);
    }

    @Test
    void slotsPayOnlyHorizontalRowsAndSplitWholeStake() {
        FrozenShowcaseRound game =
                new FrozenShowcaseRound("slots", rolls(4, 4, 4, 1, 1, 2, 0, 2, 3));
        game.action("play");
        assertEquals(3663, game.payout);
        FrozenShowcaseRound vertical =
                new FrozenShowcaseRound("slots", rolls(0, 1, 2, 0, 1, 2, 0, 1, 2));
        vertical.action("play");
        assertEquals(0, vertical.payout);
        FrozenShowcaseRound pairs = new FrozenShowcaseRound("slots", rolls(0, 0, 1));
        pairs.action("play");
        assertEquals(1000, pairs.payout);
    }

    @Test
    void dragonHasFourLanesAndOneTrap() {
        for (int lane = 0; lane < 4; lane++) {
            FrozenShowcaseRound game = new FrozenShowcaseRound("dragon_tower", rolls(3));
            game.action("play");
            assertTrue(game.available("select:3"));
            assertFalse(game.available("select:4"));
            game.action("select:" + lane);
            assertEquals(lane == 3 ? 0 : 1333, game.payout);
            assertEquals(List.of(3), game.values);
        }
    }

    @Test
    void hiloExplicitDirectionsAndThresholdSetterRespectState() {
        FrozenShowcaseRound game = new FrozenShowcaseRound("hilo", rolls(50));
        game.setThreshold(-100);
        assertEquals(1, game.value);
        game.setThreshold(100);
        assertEquals(98, game.value);
        game.action("under");
        game.action("under");
        assertFalse(game.high);
        game.action("over");
        game.action("over");
        assertTrue(game.high);
        game.active = true;
        game.setThreshold(40);
        game.action("under");
        assertEquals(98, game.value);
        assertTrue(game.high);
    }
}
