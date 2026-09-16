package dev.server.casino;

import static dev.server.casino.Amounts.*;

import dev.server.casino.ui.PaperMenus;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/** Optional presentation only; services and scheduled settlement live in CasinoRuntime. */
final class CasinoMenus {
    record Session(
            UUID token,
            long expires,
            Map<String, Consumer<Map<String, String>>> actions,
            boolean crash) {}

    private final CasinoPlugin plugin;
    private final RoundRecoveryMenu recovery;
    private final Map<UUID, Session> sessions = new HashMap<>();

    CasinoMenus(CasinoPlugin plugin, CasinoRuntime runtime) {
        this.plugin = plugin;
        recovery = new RoundRecoveryMenu(this, runtime.games());
    }

    void close() {
        sessions.clear();
    }

    void forget(UUID player) {
        sessions.remove(player);
    }

    void roundUpdated(CasinoRound round) {
        if (!plugin.menusEnabled()) return;
        var session = sessions.get(round.player);
        var player = Bukkit.getPlayer(round.player);
        if (session == null
                || !session.crash
                || session.expires < System.currentTimeMillis()
                || player == null
                || !plugin.allowed(player)) return;
        player.sendActionBar(
                Component.text(
                        round.finished()
                                ? "Crash 已结束 · 奖励 " + money(round.payout) + " · 点击刷新查看"
                                : "Crash "
                                        + money(
                                                CasinoRules.crashMultiplier(
                                                        round.started, System.currentTimeMillis()))
                                        + "× · 自动领取 "
                                        + money(round.parameter)
                                        + "×"));
        if (round.finished())
            sessions.put(
                    round.player,
                    new Session(session.token, session.expires, session.actions, false));
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
                || !parts[0].equals("casino:" + s.token)
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

    final class Page {
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
                    "Bottom.buttons." + key + ".actions", List.of("casino:" + token + " " + key));
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
                player.sendMessage(
                        "§e菜单已关闭。使用 /casino create <game>、/casino bet <game> <1-100>、/casino remove"
                            + " <game>。");
                return;
            }
            if (!plugin.allowed(player)) return;
            config.set("Settings.can_escape", true);
            config.set("Settings.after_action", "NONE");
            config.set("Settings.lifetime", "300s");
            config.set("Bottom.type", "multi");
            config.set("Bottom.columns", 2);
            config.set("Bottom.exit.text", "关闭");
            config.set("Bottom.exit.actions", List.of("casino:" + token + " close"));
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

    void open(Player p) {
        var page = new Page(p, "Casino", "实体机器免费练习 · 机器布置永久保存");
        if (plugin.machineAllowed(p)) page.button("machines", "创建机器", v -> machines(p));
        if (recovery.pending(p)) page.button("resume", "处理已有对局", v -> recovery.open(p));
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
        var page = new Page(p, "创建机器", "点击游戏，在你前方创建免费机器。Shift＋右键机器可设置下注金额或删除；机器永久保存，每种游戏可放置一台。");
        for (var entry : MachineCatalog.ENTRIES)
            page.button(
                    entry.id(),
                    entry.label(),
                    v -> {
                        forget(p.getUniqueId());
                        p.closeDialog();
                        plugin.machineCommand(p, new String[] {"create", entry.id()});
                    });
        page.button(
                "remove",
                "&c清除我的机器",
                v -> {
                    forget(p.getUniqueId());
                    p.closeDialog();
                    plugin.machineCommand(p, new String[] {"remove"});
                });
        page.button("back", "&e返回上一页", v -> open(p));
        page.show();
    }
}
