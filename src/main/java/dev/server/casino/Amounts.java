package dev.server.casino;

import java.util.Locale;

final class Amounts {
    private Amounts() {}

    static String money(long cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }

    static int parse(String value, int max) {
        if (value == null || !value.matches("[0-9]{1,3}")) {
            throw new IllegalArgumentException("请输入范围内的整数");
        }
        int number = Integer.parseInt(value);
        if (number < 1 || number > max) throw new IllegalArgumentException("输入超出范围");
        return number;
    }
}
