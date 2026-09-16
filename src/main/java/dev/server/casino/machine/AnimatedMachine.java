package dev.server.casino.machine;

import dev.server.casino.game.PracticeRound;
import dev.server.casino.model.MachineDefinition;

import org.bukkit.Location;
import org.bukkit.Sound;

import java.util.UUID;

/** Timing and completion sounds shared by games with finite reveal animations. */
public abstract class AnimatedMachine<R extends PracticeRound> extends PracticeMachine<R> {
    protected int animationStart;
    protected int animationEnd;

    protected AnimatedMachine(
            MachineManager manager,
            UUID owner,
            Location origin,
            MachineDefinition definition,
            R round) {
        super(manager, owner, origin, definition, round);
    }

    @Override
    protected final boolean busy() {
        return age < animationEnd;
    }

    protected abstract void perform(String action);

    protected abstract void animateFrame(double progress, double ease);

    protected boolean shouldAnimate(String action) {
        return action.equals("play") || action.equals("step");
    }

    protected void beforeAction(String action) {}

    protected void animationPlanned() {}

    protected boolean repeatAnimation() {
        return false;
    }

    protected void animationFinished() {}

    protected void idleTick() {}

    @Override
    protected void action(String action) {
        beforeAction(action);
        perform(action);
        if (shouldAnimate(action)) {
            animationStart = age;
            animationEnd = age + (action.equals("play") ? 80 : 30);
            animationPlanned();
        } else refresh();
    }

    @Override
    protected final void animate() {
        idleTick();
        if (animationEnd == 0) return;
        if (!busy()) {
            if (repeatAnimation()) {
                animationStart = age;
                animationEnd = age + 80;
                animationPlanned();
                return;
            }
            animationEnd = 0;
            refresh();
            animationFinished();
            origin.getWorld()
                    .playSound(
                            origin,
                            round.payout() > 0
                                    ? Sound.BLOCK_AMETHYST_BLOCK_CHIME
                                    : Sound.BLOCK_NOTE_BLOCK_BASS,
                            .35f,
                            round.payout() > 0 ? 1.35f : .8f);
            return;
        }
        double progress = (age - animationStart) / (double) (animationEnd - animationStart);
        animateFrame(progress, 1 - Math.pow(1 - progress, 3));
        if (age % 12 == 0)
            origin.getWorld().playSound(origin, Sound.BLOCK_NOTE_BLOCK_HAT, .15f, 1.5f);
    }
}
