package dev.server.casino.game.mines;

import dev.server.casino.MachineGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;

public final class MinesMachine extends PracticeMachine<MinesDemoRound> {
    private final List<ItemDisplay> cells = new ArrayList<>();
    private final Map<ItemDisplay, String> faces = new HashMap<>();
    private final Map<ItemDisplay, Flip> flips = new HashMap<>();
    private TextDisplay setting;
    private boolean wasActive;

    public MinesMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new MinesDemoRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("cabinet_mines");
        button("minus", "cabinet_button_minus");
        button("plus", "cabinet_button_plus");
        button("start", "cabinet_button_play");
        button("cash", "cabinet_button_cashout");
        for (int i = 0; i < 25; i++) {
            var point = MachineGeometry.mineCell(i);
            var cell = model("mine_hidden", point.x(), point.y(), point.z(), .32);
            cells.add(cell);
            faces.put(cell, "mine_hidden");
            hit("cell:" + i, cell, point.x(), point.y() - .16, point.z(), .43, .33, -1);
        }
        setting = text(-.55, 1.43, 1.62, .23);
    }

    @Override
    protected boolean available(String action) {
        return switch (action) {
            case "minus" -> !round.active() && round.mineCount() > 1;
            case "plus" -> !round.active() && round.mineCount() < 24;
            case "start" -> !round.active();
            case "cash" -> round.active() && round.mines().safeCount() > 0;
            default ->
                    action.startsWith("cell:")
                            && round.active()
                            && (round.mines().revealed()
                                            & (1 << Integer.parseInt(action.substring(5))))
                                    == 0;
        };
    }

    @Override
    protected void action(String action) {
        switch (action) {
            case "minus" -> round.changeMines(-1);
            case "plus" -> round.changeMines(1);
            case "start" -> round.start(System.currentTimeMillis());
            case "cash" -> round.cash(System.currentTimeMillis());
            default -> round.reveal(Integer.parseInt(action.substring(5)));
        }
        refresh();
    }

    @Override
    protected void refresh() {
        var state = round.mines();
        for (int i = 0; i < cells.size(); i++) {
            boolean shown =
                    state != null && ((state.revealed() & (1 << i)) != 0 || !round.active());
            String face =
                    !shown
                            ? "mine_hidden"
                            : (state.mask() & (1 << i)) != 0 ? "mine_bomb" : "mine_gem";
            var cell = cells.get(i);
            if (!face.equals(faces.put(cell, face))) flips.put(cell, new Flip(face, age));
        }
        setting.text(Component.text("MINES  " + round.mineCount()));
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

    @Override
    protected void animate() {
        for (var iterator = flips.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            var flip = entry.getValue();
            double t = Math.min(1, (age - flip.start) / 8.0);
            if (t >= .5) entry.getKey().setItemStack(model(flip.model));
            pose(entry.getKey(), MachineGeometry.itemPose(.32, Math.sin(t * Math.PI) * 1.3, 0));
            if (t >= 1) iterator.remove();
        }
    }

    private record Flip(String model, int start) {}
}
