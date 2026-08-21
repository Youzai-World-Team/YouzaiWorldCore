package top.csituka.youzaiworldcore.redstone;

/**
 * 无线红石频道的取值规则。
 * <p>
 * 频道是一个 {@code 0 ~ 9999} 的整数：无线红石发射器只会驱动
 * <b>频道号完全相同</b>且在 {@link WirelessRedstoneNetwork#RANGE} 格内的接收器。
 * <p>
 * 规则集中放在这里，供三处复用，避免各写一套导致「客户端能填、服务端不收」：
 * <ul>
 *   <li>客户端频道设置界面（{@code WirelessRedstoneChannelScreen}）实时裁剪输入；</li>
 *   <li>服务端 C2S 接收器（{@code ModNetworking}）复核提交值；</li>
 *   <li>方块实体读档时兜底（存档可能被外部工具改坏）。</li>
 * </ul>
 *
 * @see WirelessRedstoneNetwork
 */
public final class WirelessRedstoneChannel {

    /** 最小频道号。 */
    public static final int MIN = 0;

    /** 最大频道号。四位数足够区分，也刚好能在编辑框里一眼看完。 */
    public static final int MAX = 9999;

    /** 默认频道号：新放置的元件都在 0 频道，因此「放下就能用」。 */
    public static final int DEFAULT = 0;

    /** {@link #MAX} 的十进制位数，供编辑框设置最大输入长度。 */
    public static final int MAX_DIGITS = Integer.toString(MAX).length();

    private WirelessRedstoneChannel() {
    }

    /**
     * 判断频道号是否落在合法区间内。
     *
     * @param channel 待校验的频道号
     * @return 合法时返回 true
     */
    public static boolean isValid(int channel) {
        return channel >= MIN && channel <= MAX;
    }

    /**
     * 把任意整数夹到合法区间内。
     *
     * @param channel 原始值
     * @return {@code [MIN, MAX]} 内的频道号
     */
    public static int clamp(int channel) {
        if (channel < MIN) {
            return MIN;
        }
        return Math.min(channel, MAX);
    }

    /**
     * 把用户输入的字符串解析为频道号。
     * <p>
     * 空串、非数字、溢出一律回落到 {@code fallback}，不抛异常——
     * 编辑框里允许出现「正在输入」的中间态。
     *
     * @param raw      输入框内容
     * @param fallback 无法解析时的回落值
     * @return 解析并夹紧后的频道号
     */
    public static int parse(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        try {
            return clamp(Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 只保留输入中的数字并截到 {@link #MAX_DIGITS} 位。
     * <p>
     * 供编辑框的 {@code setResponder} 实时裁剪：玩家粘贴 {@code "12ab34"}
     * 会被改写成 {@code "1234"}，粘贴超长数字则截断而不是报错。
     *
     * @param raw 输入框内容
     * @return 只含数字、长度不超过 {@link #MAX_DIGITS} 的字符串
     */
    public static String clampInput(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder digits = new StringBuilder(MAX_DIGITS);
        for (int i = 0; i < raw.length() && digits.length() < MAX_DIGITS; i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.toString();
    }
}
