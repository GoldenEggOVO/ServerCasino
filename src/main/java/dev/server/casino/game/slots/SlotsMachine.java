package dev.server.casino.game.slots;

import dev.server.casino.MachineGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.*;

/** Physical display and animation for slots; the round owns all game rules. */
public final class SlotsMachine extends AnimatedMachine<SlotsRound> {
    private static final Material[] SYMBOLS = {
        Material.APPLE,
        Material.GOLD_INGOT,
        Material.EMERALD,
        Material.DIAMOND,
        Material.NETHER_STAR
    };
    final List<ItemDisplay> figures = new ArrayList<>();
    TextDisplay slotPopup;
    int popupUntil;

    public SlotsMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new SlotsRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("showcase_slots");
        button("play", "showcase_button_spin");
        for (int i = 0; i < 9; i++)
            figures.add(
                    item(
                            new ItemStack(SYMBOLS[i % 5]),
                            (i % 3 - 1) * .5,
                            2.36 - (i / 3) * .48,
                            .46,
                            .32,
                            0));
        slotPopup = text(0, 1.82, .9, .75);
    }

    @Override
    protected boolean available(String action) {
        return round.available(action);
    }

    @Override
    protected void perform(String action) {
        round.action(action);
    }

    @Override
    protected void refresh() {
        for (int i = 0; i < 9 && i < round.symbols().size(); i++) {
            figures.get(i)
                    .setItemStack(
                            new ItemStack(
                                    SYMBOLS[
                                            Math.floorMod(
                                                    round.symbols().get(i), SYMBOLS.length)]));
            pose(figures.get(i), MachineGeometry.itemPose(.32, 0, 0));
        }
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        for (int i = 0; i < 9; i++) {
            boolean settled = progress > (.55 + (i % 3) * .15);
            if (age % 3 == 0 || settled)
                figures.get(i)
                        .setItemStack(
                                new ItemStack(
                                        SYMBOLS[
                                                settled
                                                        ? round.symbols().get(i)
                                                        : (age / 3 + i * 2) % SYMBOLS.length]));
        }
    }

    @Override
    protected void beforeAction(String action) {
        if (action.equals("play")) {
            slotPopup.text(Component.empty());
            popupUntil = 0;
        }
    }

    @Override
    protected void idleTick() {
        if (popupUntil > 0 && age >= popupUntil) {
            slotPopup.text(Component.empty());
            popupUntil = 0;
        }
    }

    @Override
    protected void animationFinished() {
        slotPopup.text(
                Component.text(
                        "X"
                                + java.math.BigDecimal.valueOf(round.payout())
                                        .divide(
                                                java.math.BigDecimal.valueOf(round.stake()),
                                                3,
                                                java.math.RoundingMode.HALF_UP)
                                        .stripTrailingZeros()
                                        .toPlainString()));
        popupUntil = age + 35;
    }
}
