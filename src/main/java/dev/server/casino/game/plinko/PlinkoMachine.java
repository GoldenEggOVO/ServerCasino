package dev.server.casino.game.plinko;

import dev.server.casino.*;
import dev.server.casino.game.PracticeRound;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.security.SecureRandom;
import java.util.*;

/** Multiple concurrent balls each retain the stake at launch; no economy calls. */
public final class PlinkoMachine extends PracticeMachine<PlinkoMachine.Round> {
    public static final double PLAY_BUTTON_Y = .30,
            PLAY_BUTTON_Z = .79,
            PLAY_BUTTON_WIDTH = .90,
            PLAY_BUTTON_SIZE = .65,
            PLAY_BUTTON_PITCH = -Math.toRadians(25);
    private final SecureRandom random = new SecureRandom();
    private final PlinkoFlights flights = new PlinkoFlights();
    private final Map<PlinkoFlights.Flight, ItemDisplay> balls = new HashMap<>();
    private final Map<PlinkoFlights.Flight, Long> stakes = new HashMap<>();
    private BlockDisplay light;

    public PlinkoMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new Round());
    }

    public static Transformation playButtonPose(boolean pressed) {
        return MachineGeometry.buttonPose(
                PLAY_BUTTON_WIDTH, PLAY_BUTTON_PITCH, pressed ? .055 : 0, PLAY_BUTTON_SIZE);
    }

    public record ButtonHit(double y, double z, double width, double height) {}

    public static ButtonHit playButtonHit() {
        var top =
                MachineGeometry.panelPoint(
                        0, .4 * PLAY_BUTTON_SIZE, .18 * PLAY_BUTTON_SIZE, PLAY_BUTTON_PITCH);
        var center =
                MachineGeometry.panelPoint(
                        0, .2 * PLAY_BUTTON_SIZE, .09 * PLAY_BUTTON_SIZE, PLAY_BUTTON_PITCH);
        double pressedY = playButtonPose(true).getTranslation().y;
        return new ButtonHit(
                PLAY_BUTTON_Y + (pressedY - .01) / .75,
                PLAY_BUTTON_Z + center.z() / .75,
                PLAY_BUTTON_WIDTH + .02,
                top.y() - pressedY + .02);
    }

    public static double slotLabelX(int slot) {
        return (slot - 6) * .36;
    }

    public static String multiplierLabel(int slot) {
        return String.format(
                Locale.ROOT, "%.2fX", CasinoRules.plinkoPayout(1_000_000, slot) / 1_000_000.0);
    }

    @Override
    protected void buildGame() {
        body("cabinet_plinko");
        button("play", "cabinet_button_play");
        for (int i = 0; i < 13; i++) {
            var label = text(slotLabelX(i), .76, .49, .26 / .75);
            label.text(Component.text(multiplierLabel(i), NamedTextColor.GOLD));
        }
        light =
                origin.getWorld()
                        .spawn(
                                at(0, 1.04, .43),
                                BlockDisplay.class,
                                display -> {
                                    common(display);
                                    display.setBlock(Material.SEA_LANTERN.createBlockData());
                                    pose(
                                            display,
                                            new Transformation(
                                                    new Vector3f(-.15f / .75f, 0, -.08f / .75f),
                                                    new Quaternionf(),
                                                    new Vector3f(
                                                            .30f / .75f, .12f / .75f, .16f / .75f),
                                                    new Quaternionf()));
                                });
    }

    @Override
    protected boolean available(String action) {
        return action.equals("play");
    }

    @Override
    protected void action(String action) {
        var flight = flights.launch(random.nextInt(4096));
        if (flight == null) return;
        var ball = model("plinko_ball", 0, 4.5, .43, .135 / .75);
        // Original Plinko uses FIXED item transform for the ball resource.
        ball.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        balls.put(flight, ball);
        stakes.put(flight, round.stake());
    }

    @Override
    protected void refresh() {}

    @Override
    protected void animate() {
        for (var flight : flights.active()) {
            var point = PlinkoPath.at(flight.path(), flight.frame() / 8.0);
            balls.get(flight).teleport(at(point.x(), point.y(), .43));
        }
        if (!balls.isEmpty() && age % 8 == 0)
            origin.getWorld().playSound(at(0, 3, .43), Sound.BLOCK_NOTE_BLOCK_HAT, .2f, 1.5f);
        for (var flight : flights.advance()) {
            int slot = PlinkoPath.slot(flight.path());
            light.teleport(at((slot - 6) * .36, 1.04, .43));
            var ball = balls.remove(flight);
            ball.remove();
            parts.remove(ball);
            round.settle(CasinoRules.plinkoPayout(stakes.remove(flight), slot));
            origin.getWorld().playSound(at(0, 1, .43), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .3f, 1.2f);
        }
    }

    @Override
    protected boolean busy() {
        return false;
    }

    @Override
    protected boolean canEditStake() {
        return balls.isEmpty();
    }

    public static final class Round extends PracticeRound {
        private Round() {
            super(new SecureRandom());
        }

        void settle(long amount) {
            payout = amount;
        }
    }
}
