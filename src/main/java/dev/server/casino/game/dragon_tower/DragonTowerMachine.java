package dev.server.casino.game.dragon_tower;

import dev.server.casino.MachineGeometry;
import dev.server.casino.ShowcaseGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

import java.security.SecureRandom;
import java.util.*;

/** Physical display and animation for dragon_tower; the round owns all game rules. */
public final class DragonTowerMachine extends AnimatedMachine<DragonTowerRound> {

    final List<ItemDisplay> tiles = new ArrayList<>();

    public DragonTowerMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new DragonTowerRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("showcase_dragon_tower");
        button("play", "showcase_button_play");
        button("cash", "showcase_button_cash");
        for (int row = 0; row < 6; row++)
            for (int column = 0; column < 4; column++) {
                var point = ShowcaseGeometry.dragonCell(row, column);
                var tile =
                        item(
                                new ItemStack(Material.GRAY_CONCRETE),
                                point.x(),
                                point.y(),
                                point.z(),
                                .28,
                                0);
                pose(tile, dragonPose(column));
                tiles.add(tile);
                hit("select:" + column, tile, point.x(), point.y() - .14, point.z(), .32, .28, row);
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
        for (int i = 0; i < tiles.size(); i++) {
            int row = i / 4, column = i % 4;
            boolean revealed = row < round.traps().size();
            tiles.get(i)
                    .setItemStack(
                            new ItemStack(
                                    revealed
                                            ? (round.traps().get(row) == column
                                                    ? Material.TNT
                                                    : Material.EMERALD_BLOCK)
                                            : Material.GRAY_CONCRETE));
            pose(tiles.get(i), dragonPose(column));
            tiles.get(i).setGlowing(round.active() && row == round.floors());
            tiles.get(i).setGlowColorOverride(Color.YELLOW);
        }
    }

    @Override
    protected void animateFrame(double progress, double ease) {}

    Transformation dragonPose(int column) {
        var pose = MachineGeometry.itemPose(.28, 0, 0);
        pose.getLeftRotation().rotateY((float) ShowcaseGeometry.dragonAngle(column));
        return pose;
    }

    @Override
    protected boolean shouldAnimate(String action) {
        return false;
    }

    @Override
    protected boolean rowAvailable(int row) {
        return row < 0 || row == round.floors();
    }
}
