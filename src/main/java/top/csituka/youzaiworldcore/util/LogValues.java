package top.csituka.youzaiworldcore.util;

import java.util.Locale;

/** {} 参数中的精度和补零表示；只在日志真正格式化时构建文本。 */
public final class LogValues {
    private LogValues() { }

    /** 保留原有小数位数，包括 NaN / Infinity 的表示。 */
    public static Object decimal(Object value, int places) {
        if (places < 0 || places > 15) throw new IllegalArgumentException("小数位数必须为0–15");
        return new FormattedValue(value, "%." + places + "f");
    }

    /** 保留整数原有的位宽、符号位和大写十六进制补零。 */
    public static Object hex(Object value, int width) {
        if (width < 1 || width > 16) throw new IllegalArgumentException("十六进制宽度必须为1–16");
        return new FormattedValue(value, "%0" + width + "X");
    }

    private record FormattedValue(Object value, String pattern) {
        @Override public String toString() { return String.format(Locale.ROOT, pattern, value); }
    }
}
