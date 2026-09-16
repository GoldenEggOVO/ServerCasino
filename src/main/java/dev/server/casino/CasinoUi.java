package dev.server.casino;

import dev.server.casino.ui.PaperMenus;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Native text and native dialog buttons; the font is only used for decorative icons. */
public final class CasinoUi {
    record Draft(int stake, int parameter, boolean practice) {}

    record Session(
            UUID token,
            long expires,
            Map<String, Consumer<Map<String, String>>> actions,
            boolean crash) {}

    @FunctionalInterface
    interface Checked {
        void run() throws IOException;
    }

    private final CasinoPlugin plugin;
    private final CasinoService games;
    private final BukkitTask ticker;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, EnumMap<CasinoRound.Game, Draft>> drafts = new HashMap<>();

    CasinoUi(CasinoPlugin plugin) throws IOException {
        this.plugin = plugin;
        games =
                new CasinoService(
                        plugin.getDataFolder().toPath().resolve("casino-rounds"), plugin.wallet());
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5, 5);
    }

    void close() {
        ticker.cancel();
        sessions.clear();
    }

    void forget(UUID player) {
        sessions.remove(player);
        drafts.remove(player);
    }

    void resolve(UUID player, UUID round, boolean applied) throws IOException {
        games.resolve(player, round, applied);
    }

    private void tick() {
        for (UUID id : games.players()) {
            var before = games.get(id);
            if (before.finished()
                    || before.phase == CasinoRound.Phase.CREDIT_PENDING
                    || before.phase == CasinoRound.Phase.DEBIT_PENDING
                    || before.phase == CasinoRound.Phase.DOUBLE_PENDING) continue;
            try {
                var r = games.advance(id);
                var s = sessions.get(id);
                var p = Bukkit.getPlayer(id);
                if (s != null
                        && s.crash
                        && s.expires >= System.currentTimeMillis()
                        && p != null
                        && plugin.allowed(p)) {
                    // Never reopen a dialog on a timer: Esc and other plugin menus retain focus.
                    p.sendActionBar(
                            Component.text(
                                    r.finished()
                                            ? "Crash 已结束 · 奖励 " + money(r.payout) + " · 点击刷新查看"
                                            : "Crash "
                                                    + money(
                                                            CasinoRules.crashMultiplier(
                                                                    r.started,
                                                                    System.currentTimeMillis()))
                                                    + "× · 自动领取 "
                                                    + money(r.parameter)
                                                    + "×"));
                    if (r.finished())
                        sessions.put(id, new Session(s.token, s.expires, s.actions, false));
                }
            } catch (Exception ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "游乐场结算暂停 player=" + id, ex);
            }
        }
    }

    private Draft draft(Player p, CasinoRound.Game game) {
        return drafts.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(CasinoRound.Game.class))
                .computeIfAbsent(
                        game, k -> new Draft(10, game == CasinoRound.Game.DICE ? 50 : 200, true));
    }

    private void put(Player p, CasinoRound.Game game, Draft d) {
        drafts.computeIfAbsent(p.getUniqueId(), k -> new EnumMap<>(CasinoRound.Game.class))
                .put(game, d);
    }

    private void handle(Player p, String action, Map<String, String> values) {
        if (!plugin.menusEnabled()) {
            forget(p.getUniqueId());
            p.closeDialog();
            return;
        }
        String[] parts = action.split(" ");
        if (parts.length != 2) return;
        var s = sessions.get(p.getUniqueId());
        if (s == null
                || !parts[0].equals("servercasino:" + s.token)
                || s.expires < System.currentTimeMillis()
                || !s.actions.containsKey(parts[1])) return;
        sessions.remove(p.getUniqueId());
        if (!plugin.allowed(p)) {
            p.closeDialog();
            return;
        }
        try {
            s.actions.get(parts[1]).accept(values);
        } catch (Exception ex) {
            p.sendMessage("§c" + (ex.getMessage() == null ? "操作未完成，请联系管理员" : ex.getMessage()));
            plugin.getLogger()
                    .log(java.util.logging.Level.WARNING, "游乐场操作暂停 player=" + p.getUniqueId(), ex);
            open(p);
        }
    }

    private void checked(Checked action) {
        try {
            action.run();
        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    static String money(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }

    public void machineSettings(
            Player p,
            String name,
            java.util.function.LongSupplier stake,
            java.util.function.LongConsumer setStake,
            java.util.function.BooleanSupplier canEdit,
            java.util.function.BooleanSupplier exists,
            Runnable remove) {
        if (!plugin.allowed(p) || !plugin.machineAllowed(p) || !exists.getAsBoolean()) {
            p.closeDialog();
            return;
        }
        var page =
                new Page(
                        p,
                        name + " · 机器设置",
                        "&f练习下注："
                                + money(stake.getAsLong())
                                + "\n&7免费练习，不扣款、不发放余额。\n"
                                + (canEdit.getAsBoolean() ? "金额在下一局生效。" : "当前对局或动画进行中，结束后可修改金额。"));
        if (canEdit.getAsBoolean()) {
            page.input("stake", "下注金额（1～100，整数）", Long.toString(stake.getAsLong() / 100));
            page.button(
                    "save",
                    "保存下注金额",
                    v -> {
                        if (!machineValid(p, exists)) return;
                        if (!canEdit.getAsBoolean()) {
                            p.sendMessage("§e请等当前对局结束后修改金额。");
                            p.closeDialog();
                            return;
                        }
                        try {
                            setStake.accept(parse(v.get("stake"), 100) * 100L);
                            p.sendMessage("§a练习下注金额已保存。");
                        } catch (IllegalArgumentException ex) {
                            p.sendMessage("§c请输入 1～100 的整数。");
                        }
                        machineSettings(p, name, stake, setStake, canEdit, exists, remove);
                    });
        }
        page.button(
                "delete",
                "删除这台机器",
                v -> {
                    if (machineValid(p, exists)) {
                        remove.run();
                        p.closeDialog();
                        p.sendMessage("§e已删除这台机器。");
                    }
                });
        page.show();
    }

    private boolean machineValid(Player p, java.util.function.BooleanSupplier exists) {
        if (plugin.allowed(p) && plugin.machineAllowed(p) && exists.getAsBoolean()) return true;
        p.closeDialog();
        return false;
    }

    static String name(CasinoRound.Game game) {
        return switch (game) {
            case DICE -> "Dice 骰子";
            case BLACKJACK -> "Blackjack 21点";
            case PLINKO -> "Plinko 弹珠";
            case LIMBO -> "Limbo 倍率";
            case CRASH -> "Crash 升空";
        };
    }

    private final class Page {
        final Player player;
        final UUID token = UUID.randomUUID();
        final YamlConfiguration config = new YamlConfiguration();
        final Map<String, Consumer<Map<String, String>>> actions = new LinkedHashMap<>();
        boolean crash;

        Page(Player player, String title, String body) {
            this.player = player;
            config.set("Title", title);
            config.set("Body.content.type", "message");
            config.set("Body.content.width", 380);
            config.set("Body.content.text", body);
        }

        void artwork(List<String> lines) {
            var content = config.getConfigurationSection("Body.content").getValues(false);
            config.set("Body", null);
            config.set("Body.visual.type", "message");
            config.set("Body.visual.width", 260);
            config.set("Body.visual.text", lines);
            config.set("Body.content", content);
        }

        void button(String key, String text, Consumer<Map<String, String>> action) {
            config.set("Bottom.buttons." + key + ".text", text);
            config.set(
                    "Bottom.buttons." + key + ".actions",
                    List.of("servercasino:" + token + " " + key));
            actions.put(key, action);
        }

        void input(String key, String label, String value) {
            config.set("Inputs." + key + ".type", "input");
            config.set("Inputs." + key + ".text", label);
            config.set("Inputs." + key + ".default", value);
            config.set("Inputs." + key + ".max_length", 6);
        }

        void show() {
            if (!plugin.menusEnabled()) {
                forget(player.getUniqueId());
                player.sendMessage("§e菜单已关闭。使用 /casino create <game>、/casino bet <game> <1-100>、/casino remove <game>。");
                return;
            }
            if (!plugin.allowed(player)) return;
            config.set("Settings.can_escape", true);
            config.set("Settings.after_action", "NONE");
            config.set("Settings.lifetime", "300s");
            config.set("Bottom.type", "multi");
            config.set("Bottom.columns", 2);
            config.set("Bottom.exit.text", "关闭");
            config.set("Bottom.exit.actions", List.of("servercasino:" + token + " close"));
            actions.put(
                    "close",
                    v -> {
                        forget(player.getUniqueId());
                        player.closeDialog();
                    });
            sessions.put(
                    player.getUniqueId(),
                    new Session(token, System.currentTimeMillis() + 300000, actions, crash));
            PaperMenus.open(
                    plugin, player, config, (action, values) -> handle(player, action, values));
        }
    }

    static int parse(String value, int max) {
        if (value == null || !value.matches("[0-9]{1,3}")) {
            throw new IllegalArgumentException("请输入范围内的整数");
        }
        int number = Integer.parseInt(value);
        if (number < 1 || number > max) throw new IllegalArgumentException("输入超出范围");
        return number;
    }

    void open(Player p) {
        var r = games.get(p.getUniqueId());
        String status = r != null && !r.finished() ? "\n当前对局：" + name(r.game) + " · 可继续" : "";
        var page = new Page(p, "金币游乐场", "&6手动游戏 · 1～100 虚拟金币&f\n默认练习，不扣款、不发放余额。每局由你确认开始。" + status);
        if (plugin.machineAllowed(p)) page.button("machines", "创建测试机", v -> machines(p));
        if (r != null && !r.finished()) page.button("resume", "继续当前对局", v -> game(p, r.game));
        page.button(
                "lobby",
                "返回棋牌游戏",
                v -> {
                    forget(p.getUniqueId());
                    p.closeDialog();
                    p.performCommand("sg");
                });
        page.show();
    }

    private void machines(Player p) {
        if (!plugin.machineAllowed(p)) {
            p.closeDialog();
            return;
        }
        var page = new Page(p, "创建测试机", "点击游戏，在你前方创建免费测试机。Shift＋右键机器可设置下注金额或删除；离线后自动清理。");
        for (var entry : MachineCatalog.ENTRIES)
            page.button(
                    entry.id(),
                    entry.label(),
                    v -> {
                        forget(p.getUniqueId());
                        p.closeDialog();
                        plugin.testMachine(p, new String[] {"create", entry.id()});
                    });
        page.button(
                "remove",
                "&c清除我的测试机",
                v -> {
                    forget(p.getUniqueId());
                    p.closeDialog();
                    plugin.testMachine(p, new String[] {"remove"});
                });
        page.button("back", "&e返回上一页", v -> open(p));
        page.show();
    }

    private void game(Player p, CasinoRound.Game selected) {
        checked(
                () -> {
                    var r = games.advance(p.getUniqueId());
                    var d = draft(p, selected);
                    if (r != null && !r.finished() && r.game != selected) {
                        game(p, r.game);
                        return;
                    }
                    boolean active = r != null && !r.finished();
                    String body =
                            "&6"
                                    + name(selected)
                                    + "&f\n"
                                    + (active
                                            ? (r.practice ? "练习" : "金币")
                                                    + " · 总投注 "
                                                    + money(r.stake)
                                            : (d.practice ? "练习模式" : "金币模式") + " · 投注 " + d.stake)
                                    + "\n";
                    if (active || r != null && r.game == selected) body += result(r);
                    else body += "选择参数后开始一局 · 完整概率见玩法按钮";
                    var page = new Page(p, name(selected), body);
                    page.artwork(
                            r != null
                                            && r.game == selected
                                            && (r.phase == CasinoRound.Phase.ACTIVE
                                                    || r.finished()
                                                    || r.phase == CasinoRound.Phase.CREDIT_READY)
                                    ? CasinoGraphics.board(r, System.currentTimeMillis())
                                    : CasinoGraphics.hero(selected));
                    if (active) {
                        if (r.phase == CasinoRound.Phase.ACTIVE
                                && r.game == CasinoRound.Game.BLACKJACK) {
                            page.button("hit", "要牌", v -> act(p, r, "hit"));
                            page.button("stand", "停牌", v -> act(p, r, "stand"));
                            if (r.hand.size() == 2 && r.stake <= 5000)
                                page.button(
                                        "double",
                                        "加倍（再投 " + money(r.stake) + "）",
                                        v -> {
                                            var confirm =
                                                    new Page(
                                                            p,
                                                            "确认加倍",
                                                            "追加 "
                                                                    + money(r.stake)
                                                                    + (r.practice ? " 练习金币" : " 金币")
                                                                    + "，只再发一张牌后自动停牌。\n总投注 "
                                                                    + money(r.stake * 2));
                                            confirm.button(
                                                    "confirm",
                                                    "确认加倍",
                                                    values -> act(p, r, "double"));
                                            confirm.button(
                                                    "back", "返回", values -> game(p, selected));
                                            confirm.show();
                                        });
                        } else if (r.phase == CasinoRound.Phase.ACTIVE
                                && r.game == CasinoRound.Game.CRASH) {
                            page.crash = true;
                            page.button("cash", "按当前服务端倍率领取", v -> act(p, r, "cash"));
                        } else if (r.phase == CasinoRound.Phase.CREDIT_READY)
                            page.button("retry", "重新领取已确认未发放的奖励", v -> act(p, r, "retry"));
                        page.button("refresh", "刷新对局", v -> game(p, selected));
                    } else {
                        page.button(
                                "start",
                                CasinoGraphics.button(selected) + "开始一局",
                                v -> confirm(p, selected, d, r == null ? null : r.id));
                        page.button("settings", "投注与参数", v -> settings(p, selected));
                        page.button(
                                "mode",
                                d.practice ? "切换金币模式" : "切换练习模式",
                                v -> {
                                    if (d.practice && (!plugin.moneyAvailable()))
                                        throw new IllegalStateException("服务器未开放金币模式，练习仍可使用");
                                    put(p, selected, new Draft(d.stake, d.parameter, !d.practice));
                                    game(p, selected);
                                });
                    }
                    page.button(
                            "rules",
                            "玩法与概率",
                            v -> {
                                var rules =
                                        new Page(
                                                p, name(selected) + " · 规则", description(selected));
                                rules.button("back", "返回游戏", v2 -> game(p, selected));
                                rules.show();
                            });
                    page.button("back", "返回游乐场", v -> open(p));
                    page.show();
                });
    }

    private void act(Player p, CasinoRound round, String action) {
        checked(
                () -> {
                    games.action(p.getUniqueId(), round.id, round.revision, action);
                    game(p, round.game);
                });
    }

    private void settings(Player p, CasinoRound.Game game) {
        var d = draft(p, game);
        var page =
                new Page(p, name(game) + " · 设置", "所有游戏均为手动开局。金币单位为服务器虚拟余额。\n" + description(game));
        page.input("stake", "投注整数金币：1～100", Integer.toString(d.stake));
        if (game == CasinoRound.Game.DICE)
            page.input("parameter", "胜率百分比：5～95", Integer.toString(d.parameter));
        if (game == CasinoRound.Game.LIMBO || game == CasinoRound.Game.CRASH)
            page.input(
                    "parameter",
                    game == CasinoRound.Game.CRASH ? "自动领取倍率：1.01～100.00" : "目标倍率：1.01～100.00",
                    money(d.parameter));
        page.button(
                "save",
                "保存设置",
                v -> {
                    int stake = parse(v.get("stake"), 100), parameter = d.parameter;
                    if (game == CasinoRound.Game.DICE) {
                        parameter = parse(v.get("parameter"), 95);
                        if (parameter < 5) throw new IllegalArgumentException("胜率最低5%");
                    }
                    if (game == CasinoRound.Game.LIMBO || game == CasinoRound.Game.CRASH)
                        parameter = parseMultiplier(v.get("parameter"));
                    put(p, game, new Draft(stake, parameter, d.practice));
                    game(p, game);
                });
        page.button("back", "返回", v -> game(p, game));
        page.show();
    }

    static int parseMultiplier(String value) {
        if (value == null || !value.matches("[0-9]{1,3}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("倍率最多两位小数");
        int result = new java.math.BigDecimal(value).movePointRight(2).intValueExact();
        if (result < 101 || result > 10000) throw new IllegalArgumentException("倍率范围1.01～100.00");
        return result;
    }

    private void confirm(Player p, CasinoRound.Game game, Draft d, UUID previous) {
        String parameter =
                switch (game) {
                    case DICE ->
                            "胜率 "
                                    + d.parameter
                                    + "% · 获胜总倍率 "
                                    + String.format(Locale.ROOT, "%.4f", 98.0 / d.parameter);
                    case LIMBO -> "目标 " + money(d.parameter) + "×";
                    case CRASH -> "自动领取 " + money(d.parameter) + "×";
                    default -> "";
                };
        var page =
                new Page(
                        p,
                        "确认开始 · " + name(game),
                        (d.practice ? "练习局，不扣除余额。" : "将扣除 " + d.stake + " 虚拟金币。")
                                + "\n"
                                + parameter
                                + "\n关闭、断线不取消本局。Crash 的时间和自动领取继续生效。");
        page.button(
                "start",
                "确认开始",
                v ->
                        checked(
                                () -> {
                                    if (!d.practice && (!plugin.moneyAvailable()))
                                        throw new IllegalStateException("金币模式未开放");
                                    games.start(
                                            p.getUniqueId(),
                                            previous,
                                            game,
                                            d.stake * 100L,
                                            d.parameter,
                                            d.practice);
                                    game(p, game);
                                }));
        page.button("back", "返回", v -> game(p, game));
        page.show();
    }

    private static String result(CasinoRound r) {
        if (r.phase == CasinoRound.Phase.DEBIT_PENDING
                || r.phase == CasinoRound.Phase.DOUBLE_PENDING
                || r.phase == CasinoRound.Phase.CREDIT_PENDING)
            return "经济操作待核对，已阻止重复扣款或发奖。\n请管理员检查：\n玩家 " + r.player + "\n对局 " + r.id;
        String outcome =
                switch (r.game) {
                    case DICE -> "掷出 " + money(r.roll) + " / 100；小于 " + r.parameter + " 获胜。";
                    case LIMBO -> "本局倍率 " + money(r.point) + "× · 目标 " + money(r.parameter) + "×";
                    case PLINKO ->
                            "轨迹 "
                                    + path(r.path)
                                    + "\n落入第 "
                                    + (Integer.bitCount(r.path) + 1)
                                    + " / 13 槽";
                    case BLACKJACK ->
                            "你的牌："
                                    + CasinoRules.cards(r.hand)
                                    + "（"
                                    + CasinoRules.total(r.hand)
                                    + "点）\n庄家："
                                    + (r.finished() || r.phase == CasinoRound.Phase.CREDIT_READY
                                            ? CasinoRules.cards(r.dealer)
                                                    + "（"
                                                    + CasinoRules.total(r.dealer)
                                                    + "点）"
                                            : CasinoRules.cards(r.dealer.subList(0, 1)) + "  [暗牌]");
                    case CRASH ->
                            r.finished()
                                    ? "爆点 "
                                            + money(r.point)
                                            + "× · 自动领取目标 "
                                            + money(r.parameter)
                                            + "×"
                                    : "实时倍率显示于动作栏 · 自动领取 "
                                            + money(r.parameter)
                                            + "×\n点击领取按服务器收到操作时的倍率计算。";
                };
        if (r.finished())
            outcome +=
                    "\n"
                            + (r.phase == CasinoRound.Phase.CANCELLED
                                    ? "扣款未成功，已取消"
                                    : "本局结束 · 总返还 "
                                            + money(r.payout)
                                            + (r.practice ? " 练习金币" : " 金币")
                                            + "（含本金）");
        if (r.phase == CasinoRound.Phase.CREDIT_READY)
            outcome += "\n已核对未发放，可手动重新领取 " + money(r.payout) + " 金币。";
        return outcome;
    }

    static String path(int path) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 12; i++) s.append((path & (1 << i)) == 0 ? "↙" : "↘");
        return s.toString();
    }

    static String description(CasinoRound.Game game) {
        String common = "\n奖励含本金，向下取整至0.01金币。只支持服务器虚拟金币；无自动投注。";
        return switch (game) {
            case DICE -> "均匀掷出0.00～99.99；严格小于所选胜率时获胜。胜率5～95%；总倍率=98÷胜率百分数。取整前理论返还98%。" + common;
            case LIMBO ->
                    "预先选择1.01～100.00倍，随机结果达到目标即获胜。结果由均匀10亿种样本和0.98÷(1-u)生成，向下取两位，最高100倍。离散化及金币取整后理论返还略低于98%。"
                            + common;
            case PLINKO ->
                    "12层，每层独立50%向左／右，共13槽。下列为投注1金币的总返还：\n"
                            + plinkoTable()
                            + "\n对称权重640,160,40,20,10,5,2,5,10,20,40,160,640按二项概率归一到98%；取整后略低。"
                            + common;
            case CRASH ->
                    "单人回合，开局预存爆点；1倍起每30毫秒增加0.01倍。可随时手动领取；自动领取目标1.01～100.00倍必须在开局前设定。目标等于爆点时自动领取成功。离线、关菜单及重启后仍按原时间和目标结算。爆点可低于1倍，立即失败；分布同Limbo，自动目标取整前返还约98%。动作栏每秒最多刷新4次。"
                            + common;
            case BLACKJACK ->
                    "每局重新洗一副52张牌。A算1或11，J/Q/K算10。庄家软17停牌；自然21点净赢3:2，其余获胜净赢1:1，平局退本金。要牌／停牌；首两张可加倍再取一张后停牌，加倍后总投注≤100。无分牌、保险、投降。庄家暗牌开局即检查自然21点。返还率取决于策略，本游戏不承诺98%。"
                            + common;
        };
    }

    private static String plinkoTable() {
        List<String> amounts = new ArrayList<>();
        for (int i = 0; i <= 12; i++) amounts.add(money(CasinoRules.plinkoPayout(100, i)));
        return String.join(" | ", amounts);
    }
}
