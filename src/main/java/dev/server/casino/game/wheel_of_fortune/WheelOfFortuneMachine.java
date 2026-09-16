package dev.server.casino.game.wheel_of_fortune;

import dev.server.casino.MachineGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import net.kyori.adventure.text.Component;

import org.bukkit.*;
import org.bukkit.entity.*;

import java.security.SecureRandom;
import java.util.*;

/** Physical display and animation for wheel_of_fortune; the round owns all game rules. */
public final class WheelOfFortuneMachine extends AnimatedMachine<WheelOfFortuneRound> {

    ItemDisplay disc;
    double discAngle, spinFrom, spinTo;
    final List<TextDisplay> wheelLabels = new ArrayList<>();

    public WheelOfFortuneMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new WheelOfFortuneRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("showcase_wheel_of_fortune");
        button("play", "showcase_button_round_spin");
        disc = model("showcase_wheel_fortune", 0, 2, .25, 4);
        model("showcase_pointer", 0, 2.93, .36, 4);
        for (double value :
                dev.server.casino.game.wheel_of_fortune.WheelOfFortuneRound.multipliers()) {
            var label = text(0, 2, .30, .32);
            label.text(
                    Component.text(
                            value < 0
                                    ? "AGAIN"
                                    : String.format(
                                            Locale.ROOT,
                                            "%sX",
                                            java.math.BigDecimal.valueOf(value)
                                                    .stripTrailingZeros()
                                                    .toPlainString())));
            wheelLabels.add(label);
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
        if (round.finished())
            discAngle =
                    2 * Math.PI * (round.segment() + .5) / WheelOfFortuneRound.multipliers().length;
        rotateDisc(discAngle);
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        rotateDisc(spinFrom + (spinTo - spinFrom) * ease);
    }

    void rotateDisc(double angle) {
        discAngle = angle;
        var transform = MachineGeometry.itemPose(4, 0, 0);
        transform.getLeftRotation().rotateZ((float) angle);
        pose(disc, transform);
        disc.setInterpolationDelay(0);
        for (int i = 0; i < wheelLabels.size(); i++) {
            double theta = 2 * Math.PI * (i + .5) / wheelLabels.size() - angle;
            wheelLabels
                    .get(i)
                    .teleport(at(Math.sin(theta) * .70, 2 + Math.cos(theta) * .70 - .03, .30));
        }
    }

    void planSpin() {
        spinFrom = discAngle;
        double target =
                2 * Math.PI * (round.segment() + .5) / WheelOfFortuneRound.multipliers().length;
        double delta = (target - spinFrom) % (2 * Math.PI);
        if (delta < 0) delta += 2 * Math.PI;
        spinTo = spinFrom + 10 * Math.PI + delta;
    }

    @Override
    protected void animationPlanned() {
        planSpin();
    }

    @Override
    protected boolean repeatAnimation() {
        if (round.active()) {
            rotateDisc(spinTo);
            round.replayFortune();
            return true;
        }
        return false;
    }
}
