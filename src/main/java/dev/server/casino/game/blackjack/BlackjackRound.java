package dev.server.casino.game.blackjack;

import dev.server.casino.CasinoRules;
import dev.server.casino.game.DemoRound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class BlackjackRound extends DemoRound {
    private final List<Integer> player = new ArrayList<>();
    private final List<Integer> dealer = new ArrayList<>();
    private final List<Integer> deck = new ArrayList<>();
    private int next;

    public BlackjackRound(Random random) {
        super(random);
    }

    public List<Integer> player() {
        return List.copyOf(player);
    }

    public List<Integer> dealer() {
        return List.copyOf(dealer);
    }

    public List<Integer> deck() {
        return List.copyOf(deck);
    }

    public int cardsDrawn() {
        return next;
    }

    @Override
    public void start(long now) {
        if (active) {
            return;
        }
        beginDemo();
        deck.clear();
        for (int card = 0; card < 52; card++) {
            deck.add(card);
        }
        Collections.shuffle(deck, random);
        next = 0;
        player.clear();
        dealer.clear();
        player.add(draw());
        dealer.add(draw());
        player.add(draw());
        dealer.add(draw());
        if (CasinoRules.natural(player) || CasinoRules.natural(dealer)) {
            settleBlackjack();
        }
    }

    public void hit() {
        if (active) {
            player.add(draw());
            if (CasinoRules.total(player) >= 21) {
                stand();
            }
        }
    }

    public void stand() {
        if (!active) {
            return;
        }
        while (CasinoRules.total(player) <= 21 && CasinoRules.total(dealer) < 17) {
            dealer.add(draw());
        }
        settleBlackjack();
    }

    public void doubleDown() {
        if (!active || player.size() != 2) {
            return;
        }
        stake *= 2;
        player.add(draw());
        stand();
    }

    private int draw() {
        return deck.get(next++);
    }

    private void settleBlackjack() {
        settle(CasinoRules.blackjackPayout(stake, player, dealer));
    }
}
