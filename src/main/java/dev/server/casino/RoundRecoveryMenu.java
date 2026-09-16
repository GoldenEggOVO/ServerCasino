package dev.server.casino;

import static dev.server.casino.Amounts.money;

import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

/** Finishes existing persisted rounds; never starts new menu games. */
final class RoundRecoveryMenu {
    private final CasinoMenus menus;
    private final CasinoService games;

    RoundRecoveryMenu(CasinoMenus menus, CasinoService games) {
        this.menus = menus;
        this.games = games;
    }

    boolean pending(Player player) {
        var round = games.get(player.getUniqueId());
        return round != null && !round.finished();
    }

    private interface Checked {
        void run() throws IOException;
    }

    private void checked(Checked action) {
        try {
            action.run();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    void open(Player p) {
        checked(
                () -> {
                    var r = games.advance(p.getUniqueId());
                    if (r == null) {
                        menus.open(p);
                        return;
                    }
                    var selected = r.game;
                    boolean active = !r.finished();
                    String body =
                            (r.practice ? "练习" : "金币")
                                    + " · 总投注 "
                                    + money(r.stake)
                                    + "\n"
                                    + result(r);
                    var page = menus.new Page(p, name(selected), body);
                    page.artwork(
                            (r.phase == CasinoRound.Phase.ACTIVE
                                            || r.finished()
                                            || r.phase == CasinoRound.Phase.CREDIT_READY)
                                    ? CasinoGraphics.board(r, System.currentTimeMillis())
                                    : List.of());
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
                                                    menus
                                                    .new Page(
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
                                            confirm.button("back", "返回", values -> open(p));
                                            confirm.show();
                                        });
                        } else if (r.phase == CasinoRound.Phase.ACTIVE
                                && r.game == CasinoRound.Game.CRASH) {
                            page.crash = true;
                            page.button("cash", "按当前服务端倍率领取", v -> act(p, r, "cash"));
                        } else if (r.phase == CasinoRound.Phase.CREDIT_READY)
                            page.button("retry", "重新领取已确认未发放的奖励", v -> act(p, r, "retry"));
                        page.button("refresh", "刷新对局", v -> open(p));
                    }
                    page.button(
                            "rules",
                            "玩法与概率",
                            v -> {
                                var rules =
                                        menus
                                        .new Page(
                                                p, name(selected) + " · 规则", description(selected));
                                rules.button("back", "返回游戏", v2 -> open(p));
                                rules.show();
                            });
                    page.button("back", "返回游乐场", v -> menus.open(p));
                    page.show();
                });
    }

    private void act(Player p, CasinoRound round, String action) {
        checked(
                () -> {
                    games.action(p.getUniqueId(), round.id, round.revision, action);
                    open(p);
                });
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
