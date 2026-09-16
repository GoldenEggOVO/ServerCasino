package dev.server.casino;

import java.util.List;

/** Stable identifiers shared by the test-machine menu and command dispatcher. */
final class MachineCatalog {
    record Entry(String id, String label) {}

    static final List<Entry> ENTRIES =
            List.of(
                    new Entry("plinko", "Plinko 弹珠"),
                    new Entry("mines", "Mines 扫雷"),
                    new Entry("blackjack", "Blackjack 21 点"),
                    new Entry("crash", "Crash 升空"),
                    new Entry("slots", "Slots 老虎机"),
                    new Entry("duck_race", "Duck Race 赛鸭"),
                    new Entry("wheel_of_fortune", "Wheel of Fortune 幸运轮"),
                    new Entry("money_wheel", "Money Wheel 金钱轮"),
                    new Entry("penguin_cross", "Penguin Cross 企鹅过街"),
                    new Entry("keno", "Keno 选号码"),
                    new Entry("hilo", "HiLo 数字骰子"),
                    new Entry("dragon_tower", "Dragon Tower 龙塔"));

    private MachineCatalog() {}
}
