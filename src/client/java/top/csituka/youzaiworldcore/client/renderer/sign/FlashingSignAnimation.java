package top.csituka.youzaiworldcore.client.renderer.sign;

/** 告示牌闪烁动画的统一时间曲线与颜色处理。 */
public final class FlashingSignAnimation {

    private FlashingSignAnimation() {
    }

    /** 40 tick 为一个完整周期，每 20 tick 在完全显示与完全透明之间过渡。 */
    public static float alpha(long gameTime, float tickProgress) {
        double time = gameTime + tickProgress;
        return (float) (0.5 + 0.5 * Math.cos(time * Math.PI / 20.0));
    }

    /** 按比例缩放 ARGB 颜色的 Alpha 通道。 */
    public static int applyAlpha(int color, float alpha) {
        if (color == 0) {
            return 0;
        }
        int originalAlpha = (color >>> 24) & 0xFF;
        int scaledAlpha = Math.round(originalAlpha * Math.clamp(alpha, 0.0f, 1.0f));
        return (color & 0x00FFFFFF) | (scaledAlpha << 24);
    }
}
