package dev.server.casino.game.keno;

import dev.server.casino.MachineGeometry;
import dev.server.casino.machine.*;
import dev.server.casino.model.MachineDefinition;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.*;

/** Physical display and animation for keno; the round owns all game rules. */
public final class KenoMachine extends AnimatedMachine<KenoRound> {

    final List<ItemDisplay> tiles = new ArrayList<>(), gems = new ArrayList<>();
    int kenoRevealed = -1;

    public KenoMachine(
            MachineManager manager, UUID owner, Location origin, MachineDefinition definition) {
        super(manager, owner, origin, definition, new KenoRound(new SecureRandom()));
    }

    @Override
    protected void buildGame() {
        body("showcase_keno");
        button("play", "showcase_button_play");
        for (int i = 1; i <= 40; i++) {
            double x = -1.15 + (i - 1) % 8 * (2.3 / 7), z = -.72 + (i - 1) / 8 * .335;
            var tile = item(model("showcase_tile"), x, .85, z, 4, -Math.PI / 2);
            tiles.add(tile);
            hit("select:" + i, tile, x, .85, z, .27, .16, -1);
        }
        for (int i = 0; i < 10; i++)
            gems.add(item(new ItemStack(Material.EMERALD), 0, .93, 0, 0, 0));
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
        kenoRevealed = -1;
        revealKeno(round.finished() ? 10 : 0);
    }

    @Override
    protected void animateFrame(double progress, double ease) {
        revealKeno(Math.min(10, (int) (progress * 10) + 1));
    }

    void revealKeno(int count) {
        if (kenoRevealed == count) return;
        kenoRevealed = count;
        for (int i = 0; i < tiles.size(); i++) {
            boolean picked = round.selected().contains(i + 1),
                    drawn =
                            round.drawnNumbers()
                                    .subList(0, Math.min(count, round.drawnNumbers().size()))
                                    .contains(i + 1);
            var tile = tiles.get(i);
            tile.setItemStack(model(picked ? "showcase_tile_selected" : "showcase_tile"));
            tile.setGlowColorOverride(drawn ? Color.AQUA : Color.YELLOW);
            tile.setGlowing(drawn || picked);
        }
        for (int i = 0; i < gems.size(); i++) {
            boolean shown =
                    i < count
                            && i < round.drawnNumbers().size()
                            && round.selected().contains(round.drawnNumbers().get(i));
            if (shown) {
                int index = round.drawnNumbers().get(i) - 1;
                gems.get(i)
                        .teleport(at(-1.15 + index % 8 * (2.3 / 7), 1.07, -.72 + index / 8 * .335));
            }
            pose(gems.get(i), MachineGeometry.itemPose(shown ? .19 : 0, 0, 0));
        }
    }

    @Override
    protected void beforeAction(String action) {
        kenoRevealed = -1;
    }
}
