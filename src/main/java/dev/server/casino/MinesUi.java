package dev.server.casino;

import dev.server.casino.ui.PaperMenus;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

final class MinesUi {
    static final int BOARD_MESSAGE_WIDTH = 192;

    record Draft(int stake, int mines, boolean practice) {}

    record Session(UUID token, long expires, Map<String, Consumer<Map<String, String>>> actions) {}

    private final CasinoPlugin plugin;
    private final Path template;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    MinesUi(CasinoPlugin plugin) throws IOException {
        this.plugin = plugin;
        template = plugin.getDataFolder().toPath().resolve("menu.yml");
        Files.createDirectories(template.getParent());
        if (!Files.exists(template)) {
            try (var in = plugin.getResource("menu.yml")) {
                Files.copy(Objects.requireNonNull(in), template);
            }
        }
    }

    void close() {
        sessions.clear();
    }

    void forget(UUID p) {
        sessions.remove(p);
        drafts.remove(p);
    }

    private Draft draft(Player p) {
        return drafts.computeIfAbsent(p.getUniqueId(), k -> new Draft(10, 3, true));
    }

    static String money(long c) {
        return String.format(java.util.Locale.ROOT, "%.2f", c / 100.0);
    }

    private void handle(Player p, String action, Map<String, String> values) {
        String[] a = action.split(" ");
        if (a.length != 2) return;
        var s = sessions.get(p.getUniqueId());
        if (s == null
                || !a[0].equals("casino:mines:" + s.token)
                || s.expires < System.currentTimeMillis()
                || !s.actions.containsKey(a[1])) return;
        sessions.remove(p.getUniqueId());
        if (!plugin.allowed(p)) {
            p.closeDialog();
            return;
        }
        try {
            s.actions.get(a[1]).accept(values);
        } catch (Exception ex) {
            p.sendMessage("§c" + (ex.getMessage() == null ? "操作失败，请联系管理员" : ex.getMessage()));
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Mines 操作未完成 player=" + p.getUniqueId(),
                            ex);
            open(p);
        }
    }

    private YamlConfiguration base(String title) {
        var c = new YamlConfiguration();
        c.set("Title", title);
        c.set("Settings.can_escape", true);
        c.set("Settings.after_action", "NONE");
        c.set("Settings.lifetime", "300s");
        return c;
    }

    private void display(
            Player p,
            YamlConfiguration c,
            UUID token,
            Map<String, Consumer<Map<String, String>>> actions) {
        if (!plugin.allowed(p)) return;
        c.set("Settings.can_escape", true);
        c.set("Settings.after_action", "NONE");
        c.set("Settings.lifetime", "300s");
        c.set("Bottom.type", "multi");
        if (!c.contains("Bottom.columns")) c.set("Bottom.columns", 1);
        c.set("Bottom.exit.text", "&c关闭菜单");
        c.set("Bottom.exit.actions", List.of("casino:mines:" + token + " close"));
        actions.put(
                "close",
                v -> {
                    forget(p.getUniqueId());
                    p.closeDialog();
                });
        sessions.put(
                p.getUniqueId(), new Session(token, System.currentTimeMillis() + 300000, actions));
        PaperMenus.open(plugin, p, c, (action, values) -> handle(p, action, values));
    }

    private static void button(YamlConfiguration c, String key, String text, UUID token) {
        c.set("Bottom.buttons." + key + ".text", text);
        c.set("Bottom.buttons." + key + ".actions", List.of("casino:mines:" + token + " " + key));
    }

    private void simple(
            Player p,
            String title,
            String body,
            String key,
            String label,
            Consumer<Map<String, String>> callback) {
        var c = base(title);
        UUID t = UUID.randomUUID();
        var actions = new HashMap<String, Consumer<Map<String, String>>>();
        c.set("Body.info.type", "message");
        c.set("Body.info.width", 372);
        c.set("Body.info.text", body);
        button(c, key, label, t);
        actions.put(key, callback);
        button(c, "back", "&e返回上一页", t);
        actions.put("back", v -> open(p));
        display(p, c, t, actions);
    }

    private void settings(Player p) {
        var d = draft(p);
        var r = plugin.games.get(p.getUniqueId());
        if (r != null && !r.finished()) {
            open(p);
            return;
        }
        var c = base("Mines · 投注设置");
        var t = UUID.randomUUID();
        var actions = new HashMap<String, Consumer<Map<String, String>>>();
        for (String key : List.of("stake", "mines")) {
            c.set("Inputs." + key + ".type", "input");
            c.set("Inputs." + key + ".text", key.equals("stake") ? "金币（整数 1～100）" : "雷数（1～24）");
            c.set(
                    "Inputs." + key + ".default",
                    Integer.toString(key.equals("stake") ? d.stake : d.mines));
            c.set("Inputs." + key + ".max_length", 3);
        }
        button(c, "save", "&a保存", t);
        c.set("Bottom.buttons.save.actions", List.of("casino:mines:" + t + " save"));
        actions.put(
                "save",
                v -> {
                    int stake = parse(v.get("stake"), 100), mines = parse(v.get("mines"), 24);
                    drafts.put(p.getUniqueId(), new Draft(stake, mines, d.practice));
                    open(p);
                });
        button(c, "back", "&e返回上一页", t);
        actions.put("back", v -> open(p));
        display(p, c, t, actions);
    }

    static int parse(String s, int max) {
        if (s == null || !s.matches("[0-9]{1,3}")) throw new IllegalArgumentException("请输入范围内的整数");
        int n = Integer.parseInt(s);
        if (n < 1 || n > max) throw new IllegalArgumentException("输入超出范围");
        return n;
    }

    private void start(Player p, Draft d) {
        if (!d.practice && !plugin.moneyAvailable()) throw new IllegalStateException("金币模式暂未开放");
        simple(
                p,
                "确认开局",
                (d.practice ? "练习局：不扣除金币。" : "将扣除 " + d.stake + " 金币。")
                        + "\n雷数 "
                        + d.mines
                        + " / 25；理论返还率 98%。\n第一次点击也可能踩雷。关闭菜单不会取消对局。",
                "start",
                "&a确认开始",
                v -> {
                    try {
                        if (!d.practice && !plugin.moneyAvailable())
                            throw new IllegalStateException("金币模式暂未开放");
                        plugin.games.start(p.getUniqueId(), d.stake * 100L, d.mines, d.practice);
                        open(p);
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                });
    }

    void open(Player p) {
        var r = plugin.games.get(p.getUniqueId());
        var d = draft(p);
        if (r != null && !r.finished()) d = new Draft((int) (r.stake / 100), r.mines, r.practice);
        final Draft selected = d;
        boolean active = r != null && r.phase == MinesRound.Phase.ACTIVE;
        if (r != null
                && (r.phase == MinesRound.Phase.DEBIT_PENDING
                        || r.phase == MinesRound.Phase.CREDIT_PENDING)) {
            simple(
                    p,
                    "结算待核对",
                    "检测到未确认的经济操作，已暂停该局，避免重复扣款或发奖。\n请联系管理员。\n玩家：" + p.getUniqueId() + "\n对局：" + r.id,
                    "refresh",
                    "刷新",
                    v -> open(p));
            return;
        }
        YamlConfiguration c = new YamlConfiguration();
        try (var reader = Files.newBufferedReader(template)) {
            c.load(reader);
        } catch (Exception ex) {
            p.sendMessage("§cMines 菜单配置无效");
            return;
        }
        UUID token = UUID.randomUUID();
        var actions = new HashMap<String, Consumer<Map<String, String>>>();
        String status =
                (d.practice ? "&b练习模式" : "&6金币模式")
                        + "  &f投注 "
                        + d.stake
                        + "  雷 "
                        + d.mines
                        + "  &7余额 "
                        + (d.practice ? "练习不查询余额" : plugin.balanceLabel(p));
        if (r != null)
            status +=
                    "\n&f"
                            + switch (r.phase) {
                                case ACTIVE ->
                                        "已翻开 "
                                                + r.safeCount()
                                                + " 格"
                                                + (r.safeCount() > 0
                                                        ? " · 可领取 " + money(r.payout())
                                                        : "");
                                case PAID ->
                                        "已领取 "
                                                + money(r.payoutDue)
                                                + (r.practice ? " 练习金币" : " 金币");
                                case LOST -> "踩雷，本局结束";
                                default -> "本局未开始";
                            };
        c.set(
                "Body.status.text",
                c.getString("Body.status.text", "@status@").replace("@status@", status));
        c.set("Body.status.width", 296);
        c.set("Body.board.text", boardLines(r));
        c.set("Body.board.type", "message");
        c.set("Body.board.width", BOARD_MESSAGE_WIDTH);
        c.set("Body.note.width", 296);
        c.set("Body.note.text", "&7关闭保留对局 · /mines 继续 · 返还率 98%");
        mainButtons(c, token, active, active && r.safeCount() > 0, d.practice);
        actions.put("settings", v -> settings(p));
        actions.put(
                "start",
                v -> {
                    if (active) {
                        open(p);
                        return;
                    }
                    start(p, selected);
                });
        actions.put(
                "cash",
                v -> {
                    try {
                        if (active && r.safeCount() > 0)
                            plugin.games.cashout(p.getUniqueId(), r.id, r.revision);
                        open(p);
                    } catch (IOException ex) {
                        throw new java.io.UncheckedIOException(ex);
                    }
                });
        actions.put(
                "mode",
                v -> {
                    if (selected.practice && !plugin.moneyAvailable())
                        throw new IllegalStateException("经济服务不可用，练习仍可使用");
                    if (!active)
                        drafts.put(
                                p.getUniqueId(),
                                new Draft(selected.stake, selected.mines, !selected.practice));
                    open(p);
                });
        actions.put(
                "rules",
                v ->
                        simple(
                                p,
                                "Mines · 玩法",
                                "25 格中随机布置指定数量的雷。翻开安全格提高奖励，踩雷则失去本局投注。至少成功一次才能领取；全部安全格翻完自动领取。\n"
                                    + "返还倍率 = 0.98 × C(25,k) ÷ C(25-雷数,k)，k 为安全格数；奖励包含本金，向下取整到 0.01"
                                    + " 金币。取整后实际理论返还略低于 98%。\n"
                                    + "没有首格保护，服务器不按输赢修改概率。关闭、断线保留原局；重新输入 /mines 继续。练习不动真实余额。",
                                "ok",
                                "知道了",
                                v2 -> open(p)));
        for (int cell = 0; cell < 25; cell++) {
            final int n = cell;
            actions.put(
                    "cell" + cell,
                    v -> {
                        try {
                            if (active && (r.revealed & (1 << n)) == 0) {
                                var after =
                                        plugin.games.reveal(p.getUniqueId(), r.id, r.revision, n);
                                p.playSound(
                                        p.getLocation(),
                                        after.phase == MinesRound.Phase.LOST
                                                ? Sound.BLOCK_NOTE_BLOCK_BASS
                                                : Sound.BLOCK_NOTE_BLOCK_PLING,
                                        0.5f,
                                        1.2f);
                            }
                            open(p);
                        } catch (IOException ex) {
                            throw new java.io.UncheckedIOException(ex);
                        }
                    });
        }
        if (active) {
            actions.remove("settings");
            actions.remove("start");
            actions.remove("mode");
        }
        if (!active || r.safeCount() == 0) actions.remove("cash");
        for (int cell = 0; cell < 25; cell++)
            if (!active || (r.revealed & (1 << cell)) != 0) actions.remove("cell" + cell);
        c.set("Events.Click", null);
        for (String key : actions.keySet())
            c.set("Events.Click." + key, List.of("casino:mines:" + token + " " + key));
        actions.put(
                "back",
                v -> {
                    forget(p.getUniqueId());
                    p.closeDialog();
                    p.performCommand("casino");
                });
        display(p, c, token, actions);
    }

    static void mainButtons(
            YamlConfiguration c, UUID token, boolean active, boolean canCash, boolean practice) {
        c.set("Bottom.buttons", null);
        c.set("Bottom.columns", 3);
        String[] keys = {"start", "cash", "settings", "mode", "rules", "back"};
        String[] labels = {
            "&a开始新一局", "&a领取奖励", "&f投注与雷数", practice ? "&b切换金币模式" : "&b切换练习模式", "&f玩法与概率", "&e返回游乐场"
        };
        boolean[] enabled = {!active, canCash, !active, !active, true, true};
        for (int i = 0; i < keys.length; i++) {
            button(c, keys[i], enabled[i] ? labels[i] : "&8" + labels[i].substring(2), token);
            c.set("Bottom.buttons." + keys[i] + ".width", 96);
            if (!enabled[i]) c.set("Bottom.buttons." + keys[i] + ".actions", List.of());
        }
    }

    static List<String> boardLines(MinesRound r) {
        List<String> lines = new ArrayList<>();
        for (int y = 0; y < 20; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 5; x++) {
                int cell = (y / 4) * 5 + x;
                boolean revealed = r != null && (r.revealed & (1 << cell)) != 0;
                int tile = 0xE000;
                if (r != null && (revealed || r.finished()))
                    tile = (r.mask & (1 << cell)) != 0 ? 0xE020 : 0xE010;
                boolean clickable = r != null && r.phase == MinesRound.Phase.ACTIVE && !revealed;
                row.append(
                        clickable
                                ? clickGlyph(tile + y % 4, "cell" + cell, "第 " + (cell + 1) + " 格")
                                : glyph(tile + y % 4));
            }
            lines.add(finishRow(row.toString()));
        }
        return lines;
    }

    static String glyph(int code) {
        return "<font:casino:ui>" + (char) code + "\uEFFF</font>";
    }

    // FocusableTextWidget measures height at the rendered width, not Body.width.
    // Keep the final bitmap's +1 advance so its measurement cannot wrap itself.
    static String finishRow(String row) {
        int last = row.lastIndexOf('\uEFFF');
        return last < 0 ? row : row.substring(0, last) + row.substring(last + 1);
    }

    static String clickGlyph(int code, String action, String hover) {
        return "<text=\"<font:casino:ui>"
                + (char) code
                + "</font>\";actions="
                + action
                + ";hover=\""
                + hover
                + "\"><font:casino:ui>\uEFFF</font>";
    }
}
