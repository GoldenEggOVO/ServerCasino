package dev.server.casino.game.hilo;

import dev.server.casino.MachineGeometry;
import dev.server.casino.ShowcaseGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.security.SecureRandom;
import java.util.*;

/** Physical display and animation for hilo; the round owns all game rules. */
public final class HiloMachine extends AnimatedMachine<HiloRound> {

    final List<ItemDisplay> figures = new ArrayList<>(), rail = new ArrayList<>();
    TextDisplay sliderLabel, resultArrow;
    boolean sliding;

    public HiloMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new HiloRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("showcase_hilo");
        for (String action : List.of("under", "over", "play"))
            button(action, "showcase_button_" + action);
        item(model("showcase_hilo_panel"), 0, 1.1, 0, 4, PITCH);
        figures.add(item(model("showcase_slider"), 0, 1.16, .082, 4, PITCH));
        for (int i = 0; i < 12; i++)
            hit("slider", figures.getFirst(), -1.1 + i * .2, 1.04, .08, .2, .20, -1);
        sliderLabel = text(0, 1.03, .22, .25);
        for (int i = 0; i < 2; i++)
            rail.add(item(new ItemStack(Material.RED_CONCRETE), 0, 1.1, 0, 1, PITCH));
        resultArrow = text(0, .9, .3, .22);
        pose(
                resultArrow,
                new Transformation(
                        new Vector3f(),
                        new Quaternionf().rotateX((float) PITCH),
                        new Vector3f(.22f),
                        new Quaternionf()));
    }

    @Override
    protected boolean available(String action) {
        return action.equals("slider") || round.available(action);
    }

    @Override
    protected void perform(String action) {
        round.action(action);
    }

    @Override
    protected void refresh() {
        selectedButtons(a -> a.equals(round.high() ? "over" : "under"));
        double x = ShowcaseGeometry.sliderX(round.threshold());
        var center = MachineGeometry.panelPoint(x, 0, .10, PITCH);
        figures.getFirst().teleport(at(x, 1.1 + center.y(), center.z()));
        sliderLabel.teleport(at(x, 1.03, .22));
        sliderLabel.text(Component.text(round.threshold() + "%"));
        updateRail();
        if (!busy()) showHiloResult(round.rolls().isEmpty() ? 0 : round.rolls().getFirst());
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        showHiloResult(round.rolls().getFirst() * ease);
    }

    void updateRail() {
        double boundary = ShowcaseGeometry.sliderX(round.threshold());
        for (int i = 0; i < 2; i++) {
            double left = i == 0 ? -1.1 : boundary, right = i == 0 ? boundary : 1.1;
            var center = MachineGeometry.panelPoint((left + right) / 2, 0, .06, PITCH);
            var bar = rail.get(i);
            bar.teleport(at(center.x(), 1.1 + center.y(), center.z()));
            bar.setItemStack(
                    new ItemStack(
                            (i == 0 ? !round.high() : round.high())
                                    ? Material.LIME_CONCRETE
                                    : Material.RED_CONCRETE));
            var pose = MachineGeometry.itemPose(1, PITCH, 0);
            pose.getScale().set((float) (right - left), .028f, .018f);
            pose(bar, pose);
        }
    }

    void showHiloResult(double value) {
        var point =
                MachineGeometry.panelPoint(
                        ShowcaseGeometry.sliderX((int) Math.round(value)), -.24, .065, PITCH);
        resultArrow.teleport(at(point.x(), 1.1 + point.y(), point.z()));
        resultArrow.text(Component.text(String.format(Locale.ROOT, "▲\n%.1f%%", value)));
    }

    void adjustSlider() {
        Player p = Bukkit.getPlayer(owner);
        if (p == null
                || !plugin.allowed(p)
                || !plugin.machineAllowed(p)
                || !p.getWorld().equals(origin.getWorld())) {
            sliding = false;
            return;
        }
        var eye = p.getEyeLocation();
        var direction = eye.getDirection();
        var worldLocal =
                MachineGeometry.rotate(
                        eye.getX() - origin.getX(),
                        eye.getY() - origin.getY(),
                        eye.getZ() - origin.getZ(),
                        -origin.getYaw());
        var transformed =
                definition
                        .anchor("playfield")
                        .inverse(worldLocal.x(), worldLocal.y(), worldLocal.z());
        var local = new MachineGeometry.Point(transformed.x(), transformed.y(), transformed.z());
        var rotated =
                MachineGeometry.rotate(
                        direction.getX(), direction.getY(), direction.getZ(), -origin.getYaw());
        var zero = definition.anchor("playfield").inverse(0, 0, 0);
        var transformedRay =
                definition.anchor("playfield").inverse(rotated.x(), rotated.y(), rotated.z());
        var ray =
                new MachineGeometry.Point(
                        transformedRay.x() - zero.x(),
                        transformedRay.y() - zero.y(),
                        transformedRay.z() - zero.z());
        int value = ShowcaseGeometry.aimedThreshold(local, ray);
        if (value > 0 && value != round.threshold()) {
            round.setThreshold(value);
            refresh();
        }
    }

    @Override
    protected void beforeAction(String action) {
        if (action.equals("play")) showHiloResult(0);
    }

    @Override
    protected void idleTick() {
        if (sliding) adjustSlider();
    }

    @Override
    protected void confirmInput() {
        sliding = false;
    }

    @Override
    protected void action(String action) {
        if (sliding) {
            sliding = false;
            refresh();
            return;
        }
        if (action.equals("slider")) {
            sliding = true;
            return;
        }
        super.action(action);
    }
}
