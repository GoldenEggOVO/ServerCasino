package dev.server.casino;

import java.util.*;

/** Nine-pixel strips keep dialog layout and painted bounds aligned. */
final class CasinoGraphics {
    private CasinoGraphics() {}

    static String glyph(int code) {
        return "<font:casino:advanced>" + (char) code + "\uEFFF</font>";
    }

    static String row(String value) {
        // Preserve the final bitmap advance so text measurement does not wrap the row.
        int last = value.lastIndexOf('\uEFFF');
        return last < 0 ? value : value.substring(0, last) + value.substring(last + 1);
    }

    static String button(CasinoRound.Game game) {
        return "";
    }

    static List<String> hero(CasinoRound.Game game) {
        List<String> out = new ArrayList<>();
        for (int y = 0; y < 6; y++) out.add(row(glyph(0xE300 + game.ordinal() * 6 + y)));
        return out;
    }

    static List<String> cards(List<Integer> cards) {
        List<String> out = new ArrayList<>();
        for (int start = 0; start < cards.size(); start += 8)
            for (int y = 0; y < 4; y++) {
                StringBuilder line = new StringBuilder();
                for (int i = start; i < Math.min(start + 8, cards.size()); i++)
                    line.append(glyph(0xE400 + cards.get(i) * 4 + y));
                out.add(row(line.toString()));
            }
        return out;
    }

    static List<String> number(int cents) {
        String text = CasinoUi.money(cents) + "x";
        List<String> out = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            StringBuilder line = new StringBuilder();
            for (char c : text.toCharArray())
                line.append(glyph(0xE600 + "0123456789.x".indexOf(c) * 3 + y));
            out.add(row(line.toString()));
        }
        return out;
    }

    static List<String> board(CasinoRound r, long now) {
        List<String> out = new ArrayList<>();
        switch (r.game) {
            case BLACKJACK -> {
                out.add("&7庄家");
                out.addAll(
                        cards(
                                r.finished() || r.phase == CasinoRound.Phase.CREDIT_READY
                                        ? r.dealer
                                        : List.of(r.dealer.getFirst(), 52)));
                out.add("&f你的手牌 · " + CasinoRules.total(r.hand) + " 点");
                out.addAll(cards(r.hand));
            }
            case PLINKO -> {
                int column = 12;
                for (int y = 0; y <= 12; y++) {
                    StringBuilder line = new StringBuilder();
                    for (int x = 0; x < 25; x++)
                        line.append(
                                glyph(
                                        0xE700
                                                + (x == column
                                                        ? 2
                                                        : (x >= 12 - y
                                                                        && x <= 12 + y
                                                                        && (x - (12 - y)) % 2 == 0
                                                                ? 1
                                                                : 0))));
                    out.add(row(line.toString()));
                    if (y < 12) column += (r.path & (1 << y)) == 0 ? -1 : 1;
                }
            }
            case DICE -> {
                out.addAll(hero(r.game));
                StringBuilder line = new StringBuilder();
                int selected = r.roll * 25 / 10000;
                for (int i = 0; i < 25; i++)
                    line.append(
                            glyph(0xE700 + (i == selected ? 2 : (i * 4 < r.parameter ? 5 : 4))));
                out.add(row(line.toString()));
                out.add("&7 0                 50                 100");
            }
            case LIMBO -> {
                out.addAll(hero(r.game));
                out.addAll(number(r.point));
            }
            case CRASH -> {
                int multiplier =
                        r.finished()
                                ? r.point
                                : Math.min(
                                        r.parameter, CasinoRules.crashMultiplier(r.started, now));
                out.addAll(number(multiplier));
                int end =
                        Math.min(
                                24,
                                Math.max(
                                        0,
                                        (multiplier - 100) * 24 / Math.max(1, r.parameter - 100)));
                for (int y = 0; y < 6; y++) {
                    StringBuilder line = new StringBuilder();
                    for (int x = 0; x < 25; x++)
                        line.append(glyph(0xE700 + (x <= end && 5 - x * 5 / 24 == y ? 5 : 0)));
                    out.add(row(line.toString()));
                }
                out.add("&7曲线为本页快照 · 实时倍率见动作栏");
            }
        }
        return out;
    }
}
