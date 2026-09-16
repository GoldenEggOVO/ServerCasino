package dev.server.casino.game.crash;

import dev.server.casino.MachineGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;

public final class CrashMachine extends PracticeMachine<CrashRound> {
    private ItemDisplay rocket;
    private TextDisplay readout;
    private boolean wasActive;

    public CrashMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new CrashRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("cabinet_crash");
        button("start", "cabinet_button_play");
        button("cash", "cabinet_button_cashout");
        rocket = model("cabinet_rocket", -.55, 1.65, .57, 4);
        readout = text(.53, 2.75, .64, .75);
    }

    @Override
    protected boolean available(String action) {
        return action.equals("start")
                ? !round.active()
                : action.equals("cash") && round.active() && !round.cashed();
    }

    @Override
    protected void action(String action) {
        if (action.equals("start")) round.start(System.currentTimeMillis());
        else round.cash(System.currentTimeMillis());
        refresh();
    }

    @Override
    protected void refresh() {
        readout.text(
                Component.text(String.format(Locale.ROOT, "%.2f×", round.multiplier() / 100.0)));
        rocket.teleport(at(-.55, MachineGeometry.rocketHeight(round.multiplier()), .57));
        rocket.setGlowing(round.active());
        if (wasActive && !round.active()) {
            origin.getWorld()
                    .playSound(
                            origin,
                            round.payout() > 0
                                    ? Sound.BLOCK_AMETHYST_BLOCK_CHIME
                                    : Sound.BLOCK_NOTE_BLOCK_BASS,
                            .45f,
                            round.payout() > 0 ? 1.4f : .65f);
            origin.getWorld()
                    .spawnParticle(Particle.SMOKE, rocket.getLocation(), 8, .12, .12, .08, .015);
        }
        wasActive = round.active();
    }

    @Override
    protected void animate() {
        if (round.active()) {
            round.tick(System.currentTimeMillis());
            if (age % 2 == 0 || !round.active()) refresh();
        }
    }
}
