package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Ping 显示渲染工具。
 * <p>
 * 提供 Tab 列表 ping 文字绘制和名字牌 ping 文字构造的共享逻辑。
 * 颜色阈值与显示选项使用默认值，无需外部配置。
 * </p>
 */
public final class PingDisplayRender {

    private PingDisplayRender() {
    }

    // ===== 颜色常量（ARGB int，alpha 固定 0xFF） =====
    private static final int COLOR_GOOD    = 0xFF55FF55; // < 50ms  绿
    private static final int COLOR_OK      = 0xFFFFFF55; // 50-99ms 黄
    private static final int COLOR_BAD     = 0xFFFFAA00; // 100-199ms橙
    private static final int COLOR_TERRIBLE= 0xFFFF5555; // ≥ 200ms 红
    private static final int COLOR_UNKNOWN = 0xFFAAAAAA; // 未知   灰

    // ===== 显示选项 =====
    private static final boolean SHOW_MS   = true;  // 显示 "ms" 后缀
    private static final boolean TEXT_SHADOW = true; // 文字阴影

    /**
     * 在 Tab 列表中渲染 ping 文字，替代原版信号格图标。
     */
    @SuppressWarnings("null")
    public static void renderPingText(Minecraft mc, GuiGraphicsExtractor context,
                                      int slotWidth, int x, int y, PlayerInfo entry) {
        int ping = entry.getLatency();
        String pingText = getPingText(ping);
        int color = getPingColor(ping);

        Font font = mc.font;
        int textX = x + slotWidth - font.width(pingText);
        context.text(font, pingText, textX, y, color, TEXT_SHADOW);
    }

    /**
     * 根据延迟值生成显示的文本。
     *
     * @param ping 延迟值（负数表示未知）
     * @return 要显示的字符串，如 "45ms" 或 "N/A"
     */
    public static String getPingText(int ping) {
        if (ping < 0) return "N/A";
        return SHOW_MS ? ping + "ms" : String.valueOf(ping);
    }

    /**
     * 根据延迟值返回对应颜色 ARGB int。
     */
    public static int getPingColor(int ping) {
        if (ping < 0) {
            return COLOR_UNKNOWN;
        } else if (ping < 50) {
            return COLOR_GOOD;
        } else if (ping < 100) {
            return COLOR_OK;
        } else if (ping < 200) {
            return COLOR_BAD;
        } else {
            return COLOR_TERRIBLE;
        }
    }
}
