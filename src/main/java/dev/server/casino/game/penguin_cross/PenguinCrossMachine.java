package dev.server.casino.game.penguin_cross;

import dev.server.casino.MachineGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.*;

/** Physical display and animation for penguin_cross; the round owns all game rules. */
public final class PenguinCrossMachine extends AnimatedMachine<PenguinCrossRound> {

    final List<ItemDisplay> figures = new ArrayList<>(), ice = new ArrayList<>();
    int previousStage;

    public PenguinCrossMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new PenguinCrossRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("showcase_penguin_cross");
        button("play", "showcase_button_play");
        button("step", "showcase_button_step");
        button("cash", "showcase_button_cash");
        figures.add(model("showcase_penguin", -1.1, 1.08, 0, 4));
        for (int i = 0; i < 8; i++) {
            var block =
                    item(
                            new ItemStack(Material.PACKED_ICE),
                            -1.1 + (i + 1) * 2.2 / 8,
                            1,
                            0,
                            .22,
                            0);
            var pose = MachineGeometry.itemPose(.22, 0, 0);
            pose.getScale().set(.22f, .16f, .30f);
            pose(block, pose);
            ice.add(block);
        }
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
        boolean fallen = round.finished() && round.payout() == 0;
        figures.getFirst()
                .teleport(
                        at(
                                -1.1 + 2.2 * (round.steps() + (fallen ? 1 : 0)) / 8.0,
                                fallen ? .55 : 1.08,
                                0));
        var pose = MachineGeometry.itemPose(fallen ? 0 : 4, 0, 0);
        pose.getLeftRotation().rotateY((float) (Math.PI / 2));
        pose(figures.getFirst(), pose);
        for (int i = 0; i < ice.size(); i++)
            ice.get(i)
                    .teleport(
                            at(
                                    -1.1 + (i + 1) * 2.2 / 8,
                                    fallen && i == round.steps() ? .45 : 1,
                                    0));
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        boolean fallen = round.finished() && round.payout() == 0;
        double hop = Math.min(1, progress / .6),
                sink = fallen ? Math.max(0, (progress - .6) / .4) : 0;
        double step = previousStage + hop;
        figures.getFirst()
                .teleport(
                        at(
                                -1.1 + 2.2 * step / 8,
                                1.08 + Math.sin(hop * Math.PI) * .22 - sink * .65,
                                0));
        var pose = MachineGeometry.itemPose(4, 0, 0);
        pose.getLeftRotation().rotateY((float) (Math.PI / 2));
        pose(figures.getFirst(), pose);
        if (fallen)
            ice.get(previousStage)
                    .teleport(at(-1.1 + 2.2 * (previousStage + 1) / 8, 1 - sink * .55, 0));
    }

    @Override
    protected boolean shouldAnimate(String action) {
        return action.equals("step");
    }

    @Override
    protected void beforeAction(String action) {
        previousStage = round.steps();
    }
}
