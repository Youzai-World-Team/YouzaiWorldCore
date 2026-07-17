package top.csituka.youzaiworldcore.enchlevellangpatch.impl;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
public final class NumberFormatUtil {
    private NumberFormatUtil() {}

    static boolean isDigit(CharSequence s, final int offset) {
        int li = s.length() - 1;
        if (li < offset) return false;
        int c = s.charAt(offset);
        if (c == '0') return li == offset;

        for (int idx = li; idx >= offset; idx--) {
            c = s.charAt(idx);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    @Nullable
    public static String intToRoman(int num) {
        if (num <= 0 || num >= 3999) {
            return null;
        }
        return ValueTableHolder.ROMAN[num];
    }

    static String intToRomanImpl(
            int num,
            int type
    ) {
        if (num < 0) return Integer.toString(num);
        if (type == ChineseExchange.NORMAL) {
            return ChineseExchange.numberToChinese(num, ChineseExchange.NORMAL);
        } else if (type == ChineseExchange.UPPER) {
            return ChineseExchange.numberToChinese(num, ChineseExchange.UPPER);
        } else {
            String ret = intToRoman(num);
            return ret == null ? Integer.toString(num) : ret;
        }
    }
}
