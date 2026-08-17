package top.csituka.youzaiworldcore.client.pickup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.client.pickup.display.DisplayEntry;
import top.csituka.youzaiworldcore.client.pickup.display.ItemDisplayEntry;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 拾取通知条目的绘制管理器。
 * <p>
 * 单例实现，管理所有活跃的通知条目：
 * <ul>
 *   <li>收集新条目（通过 {@link #addEntry(Object, DisplayEntry)}）</li>
 *   <li>每 tick 更新所有条目状态（通过 {@link #tick()}）</li>
 *   <li>每帧渲染所有可见条目（通过 {@link #render(GuiGraphicsExtractor)}）</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("null")
public final class DrawEntriesHandler {

    /** 单例实例 */
    public static final DrawEntriesHandler INSTANCE = new DrawEntriesHandler();

    /** 最大同时显示条目数 */
    private static final int MAX_ENTRIES = 16;

    /** 条目显示时间（tick） */
    public static int DISPLAY_TIME = 80;

    /** 显示位置 X 偏移（从右下角开始） */
    public static int OFFSET_X = 4;

    /** 显示位置 Y 偏移 */
    public static int OFFSET_Y = 4;

    /** 默认显示缩放比例 */
    public static float DISPLAY_SCALE = 1.0f;

    /** 条目收集器：按插入顺序维护，键用于去重合并 */
    private final LinkedHashMap<Object, DisplayEntry<?>> collector = new LinkedHashMap<>();

    /** 是否启用 */
    private boolean enabled = true;

    private DrawEntriesHandler() {
    }

    /**
     * 添加一个显示条目。
     * <p>
     * 如果已有相同键的条目，则合并；否则直接添加。
     * 超出 {@link #MAX_ENTRIES} 时移除最早的条目。
     * </p>
     *
     * @param key   去重键
     * @param entry 显示条目
     */
    public void addEntry(@NonNull Object key, @NonNull DisplayEntry<?> entry) {
        if (!enabled) return;

        DebugLogger.entering("DrawEntriesHandler", "addEntry",
                "key=" + key + ", type=" + entry.getClass().getSimpleName());

        // 检查是否超出最大容量
        if (collector.size() >= MAX_ENTRIES && !collector.containsKey(key)) {
            Iterator<Map.Entry<Object, DisplayEntry<?>>> it = collector.entrySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
                DebugLogger.info("DrawEntriesHandler", "Removed oldest entry, collector size=" + collector.size());
            }
        }

        // 合并或新增
        DisplayEntry<?> existing = collector.get(key);
        if (existing != null) {
            existing.mergeWith(entry);
            DebugLogger.info("DrawEntriesHandler", "Merged entry, new amount=" + 
                    (entry instanceof ItemDisplayEntry ? ((ItemDisplayEntry) entry).getAmountText() : ""));
        } else {
            collector.put(key, entry);
            DebugLogger.info("DrawEntriesHandler", "Added new entry, collector size=" + collector.size());
        }

        DebugLogger.exiting("DrawEntriesHandler", "addEntry");
    }

    /**
     * 每 tick 更新所有条目状态。
     * <p>
     * 递减剩余时间、标记过期条目开始移出、清理移出完成的条目。
     * </p>
     */
    public void tick() {
        if (collector.isEmpty()) return;

        Iterator<Map.Entry<Object, DisplayEntry<?>>> it = collector.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, DisplayEntry<?>> entry = it.next();
            DisplayEntry<?> displayEntry = entry.getValue();
            displayEntry.tick();

            // 判断是否完全过期（剩余时间耗尽），标记开始移出
            if (displayEntry.getRelativeRemainingTime() <= 0.0f) {
                displayEntry.startMovingOut();
            }

            // 移出动画完成后移除条目
            if (displayEntry.shouldDiscard()) {
                it.remove();
                DebugLogger.info("DrawEntriesHandler", "Removed expired entry, collector size=" + collector.size());
            }
        }
    }

    /**
     * 每帧渲染所有条目。
     * <p>
     * 条目从屏幕右下角开始向上排列，
     * 支持缩放和淡出动画。调用方在 HUD 提取开始阶段提交这些条目，
     * 使拾取信息和声音字幕位于所有 HUD 组件下方。
     * </p>
     *
     * @param graphics GUI 渲染上下文
     */
    public void render(@NonNull GuiGraphicsExtractor graphics) {
        if (collector.isEmpty() || !enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // 从右下角开始向上绘制
        int currentY = screenHeight - OFFSET_Y;

        // 先计算总高度
        int totalHeight = collector.size() * DisplayEntry.ELEMENT_HEIGHT;

        // 从底部向上排列
        currentY = screenHeight - OFFSET_Y - totalHeight;

        for (DisplayEntry<?> entry : collector.values()) {
            int entryWidth = entry.getWidth();
            float alpha = entry.getFadeAlpha();
            int alphaInt = Math.min(255, Math.max(0, (int) (alpha * 255)));

            if (alphaInt <= 0) {
                currentY += DisplayEntry.ELEMENT_HEIGHT;
                continue;
            }

            // 移出偏移
            int moveOffset = entry.getMoveOffset();

            // X 坐标：右下对齐，减去移出偏移
            int entryX = screenWidth - entryWidth - OFFSET_X + moveOffset;
            int entryY = currentY;

            // 应用整体缩放
            if (DISPLAY_SCALE != 1.0f) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(entryX, entryY);
                graphics.pose().scale(DISPLAY_SCALE, DISPLAY_SCALE);
                entry.render(graphics, 0, 0, alphaInt);
                graphics.pose().popMatrix();
            } else {
                entry.render(graphics, entryX, entryY, alphaInt);
            }

            currentY += DisplayEntry.ELEMENT_HEIGHT;
        }
    }

    /**
     * 清除所有条目。
     */
    public void clear() {
        collector.clear();
        DebugLogger.info("DrawEntriesHandler", "Cleared all entries");
    }

    /**
     * 设置是否启用拾取通知。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        DebugLogger.stateChange("DrawEntriesHandler", "instance", "enabled", enabled);
        if (!enabled) {
            clear();
        }
    }

    /**
     * 获取当前是否启用。
     */
    public boolean isEnabled() {
        return enabled;
    }
}
