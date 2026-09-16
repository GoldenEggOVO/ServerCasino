package dev.server.casino.game.blackjack;

import dev.server.casino.CasinoRules;
import dev.server.casino.MachineGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;

public final class BlackjackMachine extends PracticeMachine<BlackjackRound> {
    public static final double BLACKJACK_PANEL_SCALE = 3.1,
            BLACKJACK_BUTTON_WIDTH = .46,
            BLACKJACK_BUTTON_Y = .55;
    private final List<ItemDisplay> cards = new ArrayList<>();
    private final Map<ItemDisplay, Slide> slides = new HashMap<>();
    private final Map<ItemDisplay, String> faces = new HashMap<>();
    private TextDisplay readout;
    private boolean wasActive;

    public BlackjackMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new BlackjackRound(new SecureRandom()));
    }

    public static double blackjackButtonX(int index) {
        return (index - 1.5) * .5;
    }

    public static String readout(List<Integer> player, List<Integer> dealer, boolean active) {
        return "PLAYER  "
                + (player.isEmpty() ? "—" : CasinoRules.total(player))
                + "\nDEALER  "
                + (dealer.isEmpty() ? "—" : active ? "?" : CasinoRules.total(dealer));
    }

    @Override
    protected void buildGame() {
        body("cabinet_blackjack");
        var offset = MachineGeometry.panelPoint(0, -.03 * BLACKJACK_PANEL_SCALE / 4, 0, PITCH);
        var panel =
                model(
                        "cabinet_control_panel",
                        0,
                        BLACKJACK_BUTTON_Y + offset.y(),
                        1.45 + offset.z(),
                        BLACKJACK_PANEL_SCALE);
        pose(panel, MachineGeometry.itemPose(BLACKJACK_PANEL_SCALE, PITCH, 0));
        for (String action : List.of("double", "stand", "hit", "start"))
            button(action, "cabinet_button_" + (action.equals("start") ? "play" : action));
        model("cabinet_blackjack_screen", 0, 0, 0, 4);
        readout = text(0, 1.24, -.76, .25);
    }

    @Override
    protected boolean available(String action) {
        return switch (action) {
            case "start" -> !round.active();
            case "double" -> round.active() && round.player().size() == 2;
            case "hit", "stand" -> round.active();
            default -> false;
        };
    }

    @Override
    protected void action(String action) {
        switch (action) {
            case "start" -> round.start(System.currentTimeMillis());
            case "double" -> round.doubleDown();
            case "hit" -> round.hit();
            case "stand" -> round.stand();
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }
        refresh();
    }

    @Override
    protected void refresh() {
        int used = hand(round.dealer(), -.32, round.active(), 0);
        used = hand(round.player(), .35, false, used);
        while (cards.size() > used) {
            var card = cards.removeLast();
            slides.remove(card);
            faces.remove(card);
            parts.remove(card);
            card.remove();
        }
        readout.text(Component.text(readout(round.player(), round.dealer(), round.active())));
        if (wasActive && !round.active())
            origin.getWorld()
                    .playSound(
                            origin,
                            round.payout() > 0
                                    ? Sound.BLOCK_AMETHYST_BLOCK_CHIME
                                    : Sound.BLOCK_NOTE_BLOCK_BASS,
                            .45f,
                            round.payout() > 0 ? 1.4f : .65f);
        wasActive = round.active();
    }

    private int hand(List<Integer> hand, double z, boolean hidden, int index) {
        for (int i = 0; i < hand.size(); i++, index++) {
            String face = "card_" + (hidden && i > 0 ? 52 : hand.get(i));
            var target = at(MachineGeometry.cardX(i, hand.size()), .96 + i * .004, z);
            ItemDisplay card;
            if (index < cards.size()) card = cards.get(index);
            else {
                card = model(face, 1.3, 1.1, -.55, .43);
                pose(card, MachineGeometry.itemPose(.43, -Math.PI / 2, 0));
                cards.add(card);
            }
            if (!face.equals(faces.get(card))) {
                card.setItemStack(model(face));
                faces.put(card, face);
                slides.put(card, new Slide(at(1.3, 1.1, -.55), target, age));
            } else if (slides.containsKey(card)) {
                var previous = slides.get(card);
                slides.put(card, new Slide(previous.from, target, previous.start));
            } else card.teleport(target);
        }
        return index;
    }

    @Override
    protected void animate() {
        for (var iterator = slides.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var slide = entry.getValue();
            double t = Math.min(1, (age - slide.start) / 8.0);
            entry.getKey()
                    .teleport(
                            slide.from
                                    .clone()
                                    .add(
                                            slide.to
                                                    .toVector()
                                                    .subtract(slide.from.toVector())
                                                    .multiply(t)));
            if (t >= 1) iterator.remove();
        }
    }

    private record Slide(Location from, Location to, int start) {}
}
