package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    // ===== 名字牌 ping 组件缓存 =====

    /**
     * 取得该玩家当前 ping 对应的「已着色组件」，ping 未变时复用上次结果。
     * <p>
     * 名字牌注入点每帧、每个可见玩家各跑一次。其中三项开销是纯浪费：
     * {@link #getPingText} 拼出的 {@code "45ms"} 字符串、包装它的
     * {@code Component.literal}、以及 {@code withStyle(style -> ...)} 里那个
     * <b>捕获了颜色值的 lambda 实例</b>（捕获型 lambda 每次调用都要新建对象）。
     * 而 ping 大约每秒才变一次，这三者在两次变化之间完全可以复用。
     * </p>
     * <p>
     * <b>为什么不缓存整条名字牌：</b>{@code EntityRenderState.nameTag} 由原版每帧通过
     * {@code getDisplayName()} 重新构造（队伍前缀、称号等都在其中），对象身份逐帧变化，
     * 整条缓存永远命不中，反而会白白多出一次查表。因此只缓存真正稳定的这一段。
     * </p>
     * <p>
     * 仅在渲染线程调用，无需同步。放在本类而非 Mixin 内：Mixin 类里的嵌套类型会被
     * 一并合并进目标类（此处目标是 {@code EntityRenderer}，被大量模组共同注入），
     * 放在普通类里更稳妥。
     * </p>
     *
     * @param playerId 玩家 UUID
     * @param ping     当前延迟（负数表示未知）
     */
    public static Component getStyledPingComponent(UUID playerId, int ping) {
        Entry cached = PING_CACHE.get(playerId);
        if (cached != null && cached.ping == ping) {
            return cached.component;
        }

        // 玩家进出世界会不断引入新 UUID，加一道上限避免 Map 无界增长；
        // 清空后下一帧自然重建，无功能影响。
        if (PING_CACHE.size() > CACHE_LIMIT) {
            PING_CACHE.clear();
        }

        // withColor(int) 直接写入 Style，避免 withStyle(UnaryOperator) 的捕获型 lambda 分配
        Component built = Component.literal(getPingText(ping)).withColor(getPingColor(ping));
        PING_CACHE.put(playerId, new Entry(ping, built));
        return built;
    }

    /** 名字牌 ping 组件缓存上限。 */
    private static final int CACHE_LIMIT = 256;

    /** 玩家 UUID → 已着色 ping 组件。 */
    private static final Map<UUID, Entry> PING_CACHE = new HashMap<>();

    /** 缓存条目：ping 数值 + 对应的已着色组件。 */
    private static final class Entry {
        final int ping;
        final Component component;

        Entry(int ping, Component component) {
            this.ping = ping;
            this.component = component;
        }
    }
}
