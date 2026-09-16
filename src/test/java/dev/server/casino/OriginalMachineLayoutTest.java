package dev.server.casino;

import static org.junit.jupiter.api.Assertions.*;

import dev.server.casino.game.blackjack.BlackjackMachine;
import dev.server.casino.game.plinko.PlinkoMachine;

import org.junit.jupiter.api.Test;

class OriginalMachineLayoutTest {
    @Test
    void blackjackTableIsJustAboveControlsAndStillMeetsTheFloor() throws Exception {
        var model =
                com.google.gson.JsonParser.parseString(
                                java.nio.file.Files.readString(
                                        java.nio.file.Path.of(
                                                "craftengine/resources/casino/resourcepack/assets/casino/models/item/cabinet_blackjack.json")))
                        .getAsJsonObject();
        double bottom = Double.POSITIVE_INFINITY, top = Double.NEGATIVE_INFINITY;
        for (var element : model.getAsJsonArray("elements")) {
            var box = element.getAsJsonObject();
            bottom = Math.min(bottom, (box.getAsJsonArray("from").get(1).getAsDouble() - 8) / 4);
            top = Math.max(top, (box.getAsJsonArray("to").get(1).getAsDouble() - 8) / 4);
        }
        assertEquals(0, bottom, 1e-9);
        assertEquals(.92, top, 1e-9);
        var buttonTop =
                MachineGeometry.panelPoint(
                        0,
                        .4 * BlackjackMachine.BLACKJACK_PANEL_SCALE / 4,
                        .18 * BlackjackMachine.BLACKJACK_PANEL_SCALE / 4,
                        -Math.toRadians(35));
        assertTrue(top > BlackjackMachine.BLACKJACK_BUTTON_Y + buttonTop.y());
        assertTrue(top - (BlackjackMachine.BLACKJACK_BUTTON_Y + buttonTop.y()) < .1);
    }

    @Test
    void plinkoCompactButtonRestoresAndKeepsItsHitboxBelowMultipliers() throws Exception {
        var rest = PlinkoMachine.playButtonPose(false);
        var pressed = PlinkoMachine.playButtonPose(true);
        assertEquals(.9, rest.getScale().x * .6 / 4, 1e-6);
        assertEquals(.65, rest.getScale().y / 4, 1e-6);
        assertEquals(rest.getScale(), pressed.getScale());
        assertEquals(rest.getLeftRotation(), pressed.getLeftRotation());
        assertEquals(0, rest.getTranslation().length(), 1e-9);
        assertTrue(pressed.getTranslation().y < 0);
        var hit = PlinkoMachine.playButtonHit();
        // Hit location is in cabinet coordinates; width/height are world units.
        double buttonBase = .30 * .75;
        var top = MachineGeometry.panelPoint(0, .4 * .65, .18 * .65, -Math.toRadians(25));
        assertEquals(.92, hit.width(), 1e-9);
        assertTrue(hit.y() * .75 <= buttonBase);
        assertTrue(hit.y() * .75 <= buttonBase + pressed.getTranslation().y);
        assertTrue(hit.y() * .75 + hit.height() >= buttonBase + top.y());
        assertTrue(hit.y() * .75 + hit.height() < .76 * .75);
    }

    @Test
    void blackjackButtonsFitPanelWithoutOverlappingEachOtherOrStatus() {
        double size = BlackjackMachine.BLACKJACK_PANEL_SCALE / 4;
        double panelHalfWidth = 2.7 * size / 2;
        assertTrue(
                BlackjackMachine.blackjackButtonX(3)
                                - BlackjackMachine.blackjackButtonX(0)
                                + BlackjackMachine.BLACKJACK_BUTTON_WIDTH
                        <= 2);
        for (int i = 0; i < 4; i++) {
            double x = BlackjackMachine.blackjackButtonX(i);
            assertTrue(Math.abs(x) + BlackjackMachine.BLACKJACK_BUTTON_WIDTH / 2 < panelHalfWidth);
            if (i > 0)
                assertTrue(
                        x - BlackjackMachine.blackjackButtonX(i - 1)
                                > BlackjackMachine.BLACKJACK_BUTTON_WIDTH + .02);
        }
        assertEquals(
                0,
                BlackjackMachine.blackjackButtonX(0) + BlackjackMachine.blackjackButtonX(3),
                1e-9);
        // Status text starts at .48; its .32-scale default font occupies .08 blocks.
        var bottom = MachineGeometry.panelPoint(0, 0, .08 * size, -Math.toRadians(35));
        assertTrue(BlackjackMachine.BLACKJACK_BUTTON_Y + bottom.y() > .48 + .08);
        var top = MachineGeometry.panelPoint(0, .4 * size, .18 * size, -Math.toRadians(35));
        assertTrue(BlackjackMachine.BLACKJACK_BUTTON_Y + top.y() < .9);
    }

    @Test
    void plinkoLabelsMatchThePayoutOfTheirOwnLandingSlots() {
        for (int slot = 0; slot < 13; slot++) {
            String label = PlinkoMachine.multiplierLabel(slot);
            assertTrue(label.endsWith("X"));
            double shown = Double.parseDouble(label.substring(0, label.length() - 1));
            double payout = CasinoRules.plinkoPayout(1_000_000, slot) / 1_000_000.0;
            assertEquals(payout, shown, .0051);
            assertEquals(label, PlinkoMachine.multiplierLabel(12 - slot));
            var landing = PlinkoPath.at((1 << slot) - 1, 12);
            assertEquals(landing.x(), PlinkoMachine.slotLabelX(slot), 1e-9);
        }
        assertTrue(Double.parseDouble(PlinkoMachine.multiplierLabel(0).replace("X", "")) > 1);
        assertTrue(Double.parseDouble(PlinkoMachine.multiplierLabel(6).replace("X", "")) < 1);
    }
}
