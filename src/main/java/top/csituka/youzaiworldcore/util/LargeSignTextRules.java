package top.csituka.youzaiworldcore.util;

/**
 * 大字牌文本规则：服务端与客户端共用的输入校验。
 * <p>
 * 大字牌只允许填入极短的内容，容量以「宽度单位」计量，总预算为
 * {@link #MAX_WIDTH_UNITS}（= 2）：
 * <ul>
 *   <li><b>全角字符</b>（中文、中文标点、表情符号等一切非 ASCII 可见字符）占 <b>2</b> 单位
 *       → 因此只能填 1 个；</li>
 *   <li><b>半角字符</b>（英文字母、阿拉伯数字、ASCII 标点符号）占 <b>1</b> 单位
 *       → 因此可以填 2 个；</li>
 *   <li><b>零宽修饰符</b>（变体选择符 / 零宽连接符 / 肤色修饰符）占 <b>0</b> 单位，
 *       用于放行 {@code ✈️} 这类「基础字符 + 变体选择符」写法的表情符号。</li>
 * </ul>
 * 零宽修饰符不占宽度，单靠宽度预算无法阻止超长的零宽序列，
 * 因此额外用 {@link #MAX_CODE_POINTS} 限制码点总数。
 * <p>
 * 校验必须由服务端复核：客户端界面只是即时反馈，
 * {@code ModNetworking} 收到 C2S 包后仍会调用 {@link #isValid(String)}。
 */
public final class LargeSignTextRules {

    /** 字牌总宽度预算（单位：半角字符宽度）。 */
    public static final int MAX_WIDTH_UNITS = 2;

    /** 码点数量上限：防止零宽修饰符被用来堆叠超长序列。 */
    public static final int MAX_CODE_POINTS = 4;

    /** 章节符号：原版格式化代码前缀，禁止写入以免注入颜色/样式。 */
    private static final char SECTION_SIGN = '§';

    private LargeSignTextRules() {
    }

    /**
     * 返回单个码点占用的宽度单位。
     *
     * @param codePoint Unicode 码点
     * @return 0（零宽修饰符）、1（ASCII 可见字符）或 2（其余全角字符）
     */
    public static int unitWidth(int codePoint) {
        if (isZeroWidthModifier(codePoint)) {
            return 0;
        }
        // ASCII 可见区间（含空格）按半角计，其余一律按全角计
        return (codePoint >= 0x20 && codePoint <= 0x7E) ? 1 : 2;
    }

    /**
     * 判断码点是否为零宽修饰符（不单独成字，仅修饰前一个字符）。
     *
     * @param codePoint Unicode 码点
     * @return 是零宽修饰符时返回 true
     */
    public static boolean isZeroWidthModifier(int codePoint) {
        return codePoint == 0x200D                              // 零宽连接符 ZWJ
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F) // 变体选择符 VS1-VS16
                || (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF); // 肤色修饰符
    }

    /**
     * 判断码点是否允许写入字牌。
     * <p>
     * 拒绝控制字符（含换行）、DEL 与格式化前缀 {@code §}。
     *
     * @param codePoint Unicode 码点
     * @return 允许写入时返回 true
     */
    public static boolean isAllowed(int codePoint) {
        if (codePoint < 0x20 || codePoint == 0x7F) {
            return false;
        }
        return codePoint != SECTION_SIGN;
    }

    /**
     * 计算文本占用的总宽度单位。
     *
     * @param text 待测文本，可为 null
     * @return 宽度单位总和；null 视为 0
     */
    public static int measure(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            total += unitWidth(codePoint);
            i += Character.charCount(codePoint);
        }
        return total;
    }

    /**
     * 校验文本是否可以写入字牌。
     * <p>
     * 空串合法（表示清空字牌）。
     *
     * @param text 待校验文本，可为 null
     * @return 合法时返回 true
     */
    public static boolean isValid(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }

        int units = 0;
        int codePoints = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (!isAllowed(codePoint)) {
                return false;
            }
            units += unitWidth(codePoint);
            codePoints++;
            if (units > MAX_WIDTH_UNITS || codePoints > MAX_CODE_POINTS) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    /**
     * 把任意文本裁剪成合法的字牌内容：丢弃非法码点，并在超出预算时截断。
     * <p>
     * 供客户端输入框实时约束使用；服务端仍以 {@link #isValid(String)} 拒绝非法包，
     * 不做静默裁剪。
     *
     * @param text 原始文本，可为 null
     * @return 裁剪后的合法文本，绝不为 null
     */
    public static String clamp(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(text.length());
        int units = 0;
        int codePoints = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            i += charCount;

            if (!isAllowed(codePoint)) {
                continue;
            }

            int width = unitWidth(codePoint);
            if (units + width > MAX_WIDTH_UNITS || codePoints + 1 > MAX_CODE_POINTS) {
                break;
            }

            result.appendCodePoint(codePoint);
            units += width;
            codePoints++;
        }
        return result.toString();
    }

    /**
     * 判断在已有文本之后追加一个码点是否仍然合法，供输入框拦截超量输入。
     *
     * @param current   当前已输入的文本
     * @param codePoint 准备追加的码点
     * @return 可以追加时返回 true
     */
    public static boolean canAppend(String current, int codePoint) {
        if (!isAllowed(codePoint)) {
            return false;
        }
        int codePoints = current == null ? 0 : current.codePointCount(0, current.length());
        return measure(current) + unitWidth(codePoint) <= MAX_WIDTH_UNITS
                && codePoints + 1 <= MAX_CODE_POINTS;
    }
}
