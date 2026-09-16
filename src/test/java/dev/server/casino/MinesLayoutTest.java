package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class MinesLayoutTest {
    @Test
    void clientHeightMeasurementDoesNotWrapAtMeasuredTextWidth() {
        String row = MinesUi.boardLines(null).getFirst().replaceAll("<[^>]*>", "");
        int measured = row.codePoints().map(c -> c == 0xEFFF ? -1 : 37).sum();
        int cursor = 0;
        for (int code : row.codePoints().toArray()) {
            cursor += code == 0xEFFF ? -1 : 37;
            assertTrue(
                    cursor <= measured,
                    "FocusableTextWidget splits again at measured width, causing phantom rows");
        }
    }

    @Test
    void messageWidthIncludesBitmapAdvanceBeforeNegativeSpacer() {
        // Minecraft checks wrapping at each bitmap glyph before its -1 spacer.
        int cursor = 0;
        for (int width : new int[] {36, 36, 36, 36, 36}) {
            cursor += width + 1;
            assertTrue(
                    cursor <= MinesUi.BOARD_MESSAGE_WIDTH,
                    "Row wraps before trailing negative spacer at " + cursor);
            cursor--;
        }
        assertEquals(180, cursor);
    }

    @Test
    void idleBoardHasOnlyTwentyRowsOfFiveTilesAndNoActions() {
        var lines = MinesUi.boardLines(null);
        assertEquals(20, lines.size());
        for (int y = 0; y < 20; y++)
            assertEquals(MinesUi.finishRow(MinesUi.glyph(0xE000 + y % 4).repeat(5)), lines.get(y));
    }

    @Test
    void onlyUnrevealedActiveCellsCarryClickActions() {
        var round = MinesRound.create(UUID.randomUUID(), 1000, 1, true, 1 << 24);
        round.phase = MinesRound.Phase.ACTIVE;
        round.reveal(0);
        var lines = MinesUi.boardLines(round);
        assertTrue(lines.get(0).startsWith(MinesUi.glyph(0xE010)));
        String board = String.join("\n", lines);
        assertFalse(board.contains("actions=cell0;"));
        for (int cell = 1; cell < 25; cell++)
            assertEquals(4, board.split("actions=cell" + cell + ";", -1).length - 1);
        round.phase = MinesRound.Phase.PAID;
        board = String.join("\n", MinesUi.boardLines(round));
        assertFalse(board.contains("actions="));
        assertTrue(
                MinesUi.boardLines(round)
                        .get(16)
                        .endsWith(MinesUi.finishRow(MinesUi.glyph(0xE020))));
    }

    @Test
    void compactNativeButtonsDisableUnavailableActionsAndReplaceLegacyButtons() {
        var config = new YamlConfiguration();
        var token = UUID.randomUUID();
        config.set("Bottom.buttons.legacy.text", "old");
        MinesUi.mainButtons(config, token, false, false, true);
        assertEquals(3, config.getInt("Bottom.columns"));
        assertFalse(config.contains("Bottom.buttons.legacy"));
        assertEquals(6, config.getConfigurationSection("Bottom.buttons").getKeys(false).size());
        assertEquals(
                "casino:mines:" + token + " start",
                config.getStringList("Bottom.buttons.start.actions").getFirst());
        assertTrue(config.getStringList("Bottom.buttons.cash.actions").isEmpty());
        MinesUi.mainButtons(config, token, true, true, true);
        for (String key : new String[] {"settings", "start", "mode"})
            assertTrue(config.getStringList("Bottom.buttons." + key + ".actions").isEmpty());
        assertEquals(
                "casino:mines:" + token + " cash",
                config.getStringList("Bottom.buttons.cash.actions").getFirst());
        for (String key : config.getConfigurationSection("Bottom.buttons").getKeys(false)) {
            assertEquals(96, config.getInt("Bottom.buttons." + key + ".width"));
            assertFalse(config.getString("Bottom.buttons." + key + ".text").contains("<font:"));
        }
    }
}
