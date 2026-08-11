package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 左下角 HUD 的响应式缩放工具。
 *
 * <p>以 1920×1080、界面尺寸 3 对应的 {@code 640×360} GUI 坐标为设计基准，
 * 同时比较当前 GUI 的宽度和高度，采用较小的比例统一缩放装备栏、物品栏和
 * 状态效果 HUD。这样可保持面板、图标、物品模型、文字和间距的相对比例，
 * 也能避免窄屏下仅按高度放大导致横向空间不足。</p>
 */
public final class HudResponsiveScaler {

    private static final String MODULE = "HudResponsiveScaler";
    private static final float REFERENCE_GUI_WIDTH = 640.0f;
    private static final float REFERENCE_GUI_HEIGHT = 360.0f;

    private static int lastGuiWidth = -1;
    private static int lastGuiHeight = -1;

    private HudResponsiveScaler() {
    }

    /**
     * 根据当前 GUI 宽高计算统一缩放比例。
     *
     * @param graphics HUD 绘制上下文
     * @return 相对于 640×360 设计坐标的缩放比例
     */
    public static float calculateScale(GuiGraphicsExtractor graphics) {
        int guiWidth = Math.max(1, graphics.guiWidth());
        int guiHeight = Math.max(1, graphics.guiHeight());
        float widthScale = guiWidth / REFERENCE_GUI_WIDTH;
        float heightScale = guiHeight / REFERENCE_GUI_HEIGHT;
        float scale = Math.min(widthScale, heightScale);

        if ((guiWidth != lastGuiWidth || guiHeight != lastGuiHeight)
                && DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(MODULE, "HUD响应式缩放: gui=" + guiWidth + "x" + guiHeight
                    + ", scale=" + scale);
        }
        lastGuiWidth = guiWidth;
        lastGuiHeight = guiHeight;
        return scale;
    }

    /**
     * 将实际 GUI 高度换算为缩放矩阵内使用的设计坐标高度。
     *
     * @param graphics HUD 绘制上下文
     * @param scale 当前统一缩放比例
     * @return 设计坐标高度
     */
    public static int logicalGuiHeight(GuiGraphicsExtractor graphics, float scale) {
        return Math.max(1, Math.round(graphics.guiHeight() / scale));
    }
}
