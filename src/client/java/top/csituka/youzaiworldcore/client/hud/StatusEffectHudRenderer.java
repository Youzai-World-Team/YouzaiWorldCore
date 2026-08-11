package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.enchlevellangpatch.impl.NumberFormatUtil;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * YZUI 状态效果 HUD 渲染器。
 *
 * <p>面板位于装备栏 HUD 右侧、物品栏 HUD 上方，底边位置固定，
 * 高度随状态效果数量向上增长，最多占用 13 行。状态效果按照获得顺序排列：
 * 最早获得的位于最下方，后获得的依次向上追加。超过 13 个效果后，从最早的
 * 效果开始从最底行压缩：同一行依次容纳 2、3、4 个效果，填满 4 个后才继续
 * 压缩上一行。简略行内按获得顺序从右向左排列，新效果位于左侧；简略效果
 * 只显示图标与叠加在图标右下角的剩余时间。效果剩余时间不超过 10 秒时，
 * 对应的完整行或简略单元格会开始闪烁。</p>
 */
@SuppressWarnings("null")
public final class StatusEffectHudRenderer {

    private static final String MODULE = "StatusEffectHudRenderer";

    private static final float REF_HEIGHT = 360f;

    // ===== 与装备栏、物品栏 HUD 对齐的基础尺寸 =====
    private static final int BASE_SLOT_SIZE = 18;
    private static final int BASE_SLOT_SPACING = 20;
    private static final int BASE_PADDING = 3;
    private static final int BASE_PANEL_RADIUS = 6;
    private static final int BASE_LEFT_OFFSET = 2;
    private static final int BASE_BOTTOM_OFFSET = 2;
    private static final int BASE_TEXT_GAP = 4;
    private static final int BASE_ARMOR_TEXT_WIDTH = 22;
    private static final int BASE_PANEL_GAP = 2;
    private static final int BASE_VERTICAL_GAP = 4;

    private static final int INVENTORY_COLS = 9;
    private static final int INVENTORY_ROWS = 3;

    // ===== 状态效果行尺寸 =====
    private static final int BASE_ROW_HEIGHT = 18;
    private static final int BASE_ROW_GAP = 2;
    private static final int BASE_ICON_SIZE = 16;
    private static final int MAX_ROWS = 13;
    private static final int MAX_COLUMNS = 4;
    private static final int MAX_ROW_SAVINGS = MAX_COLUMNS - 1;
    private static final int MAX_VISIBLE_EFFECTS = MAX_ROWS * MAX_COLUMNS;

    // ===== 颜色与闪烁 =====
    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int ROW_BG = 0x40FFFFFF;
    private static final int COLOR_PRIMARY = 0xFFFFFFFF;
    private static final int COLOR_SECONDARY = 0xFFD0D0D0;
    private static final int FLASH_THRESHOLD_TICKS = 10 * 20;
    private static final long FLASH_INTERVAL_MILLIS = 250L;
    private static final float FLASH_DIM_ALPHA = 0.35f;

    /** 按获得先后保存的效果类型；列表首项是最早获得的效果。 */
    private static final List<Holder<MobEffect>> acquisitionOrder = new ArrayList<>();
    /** 当前顺序记录所属玩家，使用弱引用避免退出世界后保留玩家实例。 */
    private static WeakReference<Player> trackedPlayerRef = new WeakReference<>(null);
    /** 上次记录过的溢出数量，避免极端情况下每帧重复输出警告。 */
    private static int lastOverflowEffectCount = -1;

    private StatusEffectHudRenderer() {
    }

    /**
     * 记录本地玩家新获得的状态效果。
     *
     * <p>同类型效果升级或刷新时间不会改变原有位置；效果完全移除后再次
     * 获得时，会作为新效果追加到面板最上方。</p>
     *
     * @param player 本地玩家
     * @param instance 新增的状态效果实例
     */
    public static void onEffectAdded(Player player, MobEffectInstance instance) {
        ensureTrackedPlayer(player);
        Holder<MobEffect> effect = instance.getEffect();
        if (!acquisitionOrder.contains(effect)) {
            acquisitionOrder.add(effect);
            if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
                DebugLogger.debug(MODULE, "记录状态效果获得顺序: "
                        + effect.getRegisteredName() + ", order=" + acquisitionOrder.size());
            }
        }
    }

    /**
     * 在客户端收到效果移除通知时立即清理顺序记录，使同一效果在同一帧内
     * 被移除后重新获得，也能正确追加到面板最上方。
     *
     * @param player 本地玩家
     * @param instance 被移除的状态效果实例
     */
    public static void onEffectRemoved(Player player, MobEffectInstance instance) {
        ensureTrackedPlayer(player);
        if (acquisitionOrder.remove(instance.getEffect())
                && DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(MODULE, "移除状态效果顺序记录: "
                    + instance.getEffect().getRegisteredName());
        }
    }

    /**
     * 绘制状态效果 HUD。没有状态效果时不会创建或绘制面板。
     *
     * @param graphics HUD 绘制上下文
     */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        ensureTrackedPlayer(player);
        Map<Holder<MobEffect>, MobEffectInstance> activeEffects = player.getActiveEffectsMap();
        synchronizeOrder(activeEffects);
        if (activeEffects.isEmpty()) {
            return;
        }

        List<MobEffectInstance> orderedEffects = collectOrderedEffects(activeEffects);
        if (orderedEffects.isEmpty()) {
            return;
        }

        // 一行最多容纳四个简略效果。极端超过 52 个时优先保留最新效果，
        // 确保刚获得的效果不会因为容量上限而完全不可见。
        if (orderedEffects.size() > MAX_VISIBLE_EFFECTS) {
            int total = orderedEffects.size();
            if (lastOverflowEffectCount != total) {
                DebugLogger.warn(MODULE, "状态效果数量超过 HUD 容量，保留最新效果: "
                        + total + " -> " + MAX_VISIBLE_EFFECTS);
                lastOverflowEffectCount = total;
            }
            orderedEffects = orderedEffects.subList(total - MAX_VISIBLE_EFFECTS, total);
        } else {
            lastOverflowEffectCount = -1;
        }

        int effectCount = orderedEffects.size();
        int requiredSavings = Math.max(0, effectCount - MAX_ROWS);
        int fullCompactRowCount = requiredSavings / MAX_ROW_SAVINGS;
        int partialCompactExtra = requiredSavings % MAX_ROW_SAVINGS;
        int partialCompactColumns = partialCompactExtra > 0
                ? partialCompactExtra + 1
                : 0;
        int compactEffectCount = fullCompactRowCount * MAX_COLUMNS
                + partialCompactColumns;
        int fullRowCount = effectCount - compactEffectCount;
        int displayedRowCount = fullCompactRowCount
                + (partialCompactColumns > 0 ? 1 : 0)
                + fullRowCount;

        float scale = graphics.guiHeight() / REF_HEIGHT;
        Font font = client.font;

        int slotSize = rnd(BASE_SLOT_SIZE, scale);
        int slotSpacing = rnd(BASE_SLOT_SPACING, scale);
        int padding = rnd(BASE_PADDING, scale);
        int textGap = rnd(BASE_TEXT_GAP, scale);
        int armorTextWidth = rnd(BASE_ARMOR_TEXT_WIDTH, scale);
        int panelGap = rnd(BASE_PANEL_GAP, scale);
        int rowHeight = Math.max(font.lineHeight * 2, rnd(BASE_ROW_HEIGHT, scale));
        int rowGap = rnd(BASE_ROW_GAP, scale);
        int iconSize = rnd(BASE_ICON_SIZE, scale);

        // 装备栏右边缘。
        int armorPanelWidth = padding * 2 + slotSize + textGap + armorTextWidth;
        int panelX = rnd(BASE_LEFT_OFFSET, scale) + armorPanelWidth + panelGap;

        // 与物品栏面板右边缘对齐。
        int inventoryGridWidth = (INVENTORY_COLS - 1) * slotSpacing + slotSize;
        int inventoryPanelWidth = inventoryGridWidth + padding * 2;
        int inventoryPanelRight = rnd(BASE_LEFT_OFFSET, scale) + inventoryPanelWidth;
        int panelWidth = Math.max(rnd(80, scale), inventoryPanelRight - panelX);

        // 底边固定在物品栏面板上方，面板随效果数量向上生长。
        int inventoryGridHeight = (INVENTORY_ROWS - 1) * slotSpacing + slotSize;
        int inventoryPanelHeight = inventoryGridHeight + padding * 2;
        int inventoryPanelY = graphics.guiHeight()
                - inventoryPanelHeight - rnd(BASE_BOTTOM_OFFSET, scale);
        int panelBottom = inventoryPanelY - rnd(BASE_VERTICAL_GAP, scale);
        int panelHeight = padding * 2 + displayedRowCount * rowHeight
                + Math.max(0, displayedRowCount - 1) * rowGap;
        int panelY = panelBottom - panelHeight;

        RoundedRect.fillOrSquare(graphics, panelX, panelY, panelWidth, panelHeight,
                rnd(BASE_PANEL_RADIUS, scale), PANEL_BG);

        long nowMillis = System.currentTimeMillis();

        int effectIndex = 0;
        int row = 0;

        // 从最底部开始逐行填满四个简略效果。
        for (int i = 0; i < fullCompactRowCount; i++, row++) {
            int rowY = panelBottom - padding - rowHeight - row * (rowHeight + rowGap);
            drawCompactEffectRow(graphics, font, client, orderedEffects,
                    effectIndex, MAX_COLUMNS,
                    panelX + padding, rowY, panelWidth - padding * 2,
                    rowHeight, iconSize, panelGap, nowMillis);
            effectIndex += MAX_COLUMNS;
        }

        // 尚不足四个的下一行按 2 / 3 列显示，填满后才继续向上压缩。
        if (partialCompactColumns > 0) {
            int rowY = panelBottom - padding - rowHeight - row * (rowHeight + rowGap);
            drawCompactEffectRow(graphics, font, client, orderedEffects,
                    effectIndex, partialCompactColumns,
                    panelX + padding, rowY, panelWidth - padding * 2,
                    rowHeight, iconSize, panelGap, nowMillis);
            effectIndex += partialCompactColumns;
            row++;
        }

        // 其余较新的效果继续使用完整行。
        for (; effectIndex < effectCount; effectIndex++, row++) {
            MobEffectInstance instance = orderedEffects.get(effectIndex);
            int rowY = panelBottom - padding - rowHeight - row * (rowHeight + rowGap);
            drawEffectRow(graphics, font, client, instance,
                    panelX + padding, rowY, panelWidth - padding * 2,
                    rowHeight, iconSize, textGap, nowMillis);
        }
    }

    // ===== 顺序同步 =====

    private static void ensureTrackedPlayer(Player player) {
        if (trackedPlayerRef.get() == player) {
            return;
        }
        acquisitionOrder.clear();
        trackedPlayerRef = new WeakReference<>(player);
        DebugLogger.debug(MODULE, "本地玩家实例已变化，重置状态效果获得顺序");
    }

    private static void synchronizeOrder(Map<Holder<MobEffect>, MobEffectInstance> activeEffects) {
        int previousSize = acquisitionOrder.size();
        acquisitionOrder.removeIf(effect -> !activeEffects.containsKey(effect));

        // 初次进入世界或其他模组绕过标准新增回调时，以当前客户端集合兜底补齐。
        Collection<MobEffectInstance> current = activeEffects.values();
        for (MobEffectInstance instance : current) {
            Holder<MobEffect> effect = instance.getEffect();
            if (!acquisitionOrder.contains(effect)) {
                acquisitionOrder.add(effect);
            }
        }

        if (previousSize != acquisitionOrder.size()
                && DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(MODULE, "同步状态效果顺序: "
                    + previousSize + " -> " + acquisitionOrder.size());
        }
    }

    private static List<MobEffectInstance> collectOrderedEffects(
            Map<Holder<MobEffect>, MobEffectInstance> activeEffects) {
        List<MobEffectInstance> result = new ArrayList<>(acquisitionOrder.size());
        for (Holder<MobEffect> effect : acquisitionOrder) {
            MobEffectInstance instance = activeEffects.get(effect);
            if (instance != null) {
                result.add(instance);
            }
        }
        return result;
    }

    // ===== 单行绘制 =====

    private static void drawCompactEffectRow(GuiGraphicsExtractor graphics, Font font,
            Minecraft client, List<MobEffectInstance> effects,
            int startIndex, int columnCount,
            int rowX, int rowY, int rowWidth, int rowHeight,
            int iconSize, int cellGap, long nowMillis) {
        int availableWidth = rowWidth - cellGap * (columnCount - 1);
        int baseCellWidth = availableWidth / columnCount;
        int remainder = availableWidth % columnCount;
        int cellX = rowX;

        for (int column = 0; column < columnCount; column++) {
            int cellWidth = baseCellWidth + (column < remainder ? 1 : 0);
            int effectIndex = startIndex + columnCount - 1 - column;
            drawCompactEffect(graphics, font, client, effects.get(effectIndex),
                    cellX, rowY, cellWidth, rowHeight, iconSize, nowMillis);
            cellX += cellWidth + cellGap;
        }
    }

    private static void drawCompactEffect(GuiGraphicsExtractor graphics, Font font,
            Minecraft client, MobEffectInstance instance,
            int cellX, int cellY, int cellWidth, int cellHeight,
            int iconSize, long nowMillis) {
        float alpha = flashAlpha(instance, nowMillis);
        graphics.fill(cellX, cellY, cellX + cellWidth, cellY + cellHeight,
                ARGB.multiplyAlpha(ROW_BG, alpha));

        int iconX = cellX + Math.max(0, (cellWidth - iconSize) / 2);
        int iconY = cellY + Math.max(0, (cellHeight - iconSize) / 2);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                Hud.getMobEffectSprite(instance.getEffect()),
                iconX, iconY, iconSize, iconSize, ARGB.white(alpha));

        Component duration = formatDuration(client, instance);
        FormattedCharSequence clippedDuration = clipWithoutEllipsis(
                duration, font, cellWidth - 2);
        int durationX = Math.max(cellX + 1,
                iconX + iconSize - font.width(clippedDuration));
        int durationY = iconY + iconSize - font.lineHeight + 1;
        graphics.text(font, clippedDuration, durationX, durationY,
                ARGB.multiplyAlpha(COLOR_PRIMARY, alpha), true);
    }

    private static void drawEffectRow(GuiGraphicsExtractor graphics, Font font,
            Minecraft client, MobEffectInstance instance,
            int rowX, int rowY, int rowWidth, int rowHeight,
            int iconSize, int textGap, long nowMillis) {
        float alpha = flashAlpha(instance, nowMillis);
        graphics.fill(rowX, rowY, rowX + rowWidth, rowY + rowHeight,
                ARGB.multiplyAlpha(ROW_BG, alpha));

        int iconX = rowX + Math.max(0, (rowHeight - iconSize) / 2);
        int iconY = rowY + Math.max(0, (rowHeight - iconSize) / 2);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                Hud.getMobEffectSprite(instance.getEffect()),
                iconX, iconY, iconSize, iconSize, ARGB.white(alpha));

        int textX = rowX + rowHeight + textGap;
        int textWidth = Math.max(0, rowWidth - rowHeight - textGap - 2);
        int textBlockY = rowY + (rowHeight - font.lineHeight * 2) / 2;

        String romanLevel = NumberFormatUtil.intToRoman(instance.getAmplifier() + 1);
        if (romanLevel == null) {
            romanLevel = Integer.toString(instance.getAmplifier() + 1);
        }
        Component nameAndLevel = instance.getEffect().value().getDisplayName().copy()
                .append(" ")
                .append(romanLevel);
        FormattedCharSequence clippedName = clipWithoutEllipsis(
                nameAndLevel, font, textWidth);
        graphics.text(font, clippedName, textX, textBlockY,
                ARGB.multiplyAlpha(COLOR_PRIMARY, alpha), true);

        Component duration = formatDuration(client, instance);
        graphics.text(font, duration, textX, textBlockY + font.lineHeight,
                ARGB.multiplyAlpha(COLOR_SECONDARY, alpha), true);
    }

    private static Component formatDuration(Minecraft client, MobEffectInstance instance) {
        return MobEffectUtil.formatDuration(instance, 1.0f,
                client.level.tickRateManager().tickrate());
    }

    /** 按宽度截断文字并保留样式，不在末尾追加省略号。 */
    private static FormattedCharSequence clipWithoutEllipsis(
            Component text, Font font, int maxWidth) {
        return Language.getInstance().getVisualOrder(
                font.substrByWidth(text, Math.max(0, maxWidth)));
    }

    private static float flashAlpha(MobEffectInstance instance, long nowMillis) {
        if (instance.isInfiniteDuration() || !instance.endsWithin(FLASH_THRESHOLD_TICKS)) {
            return 1.0f;
        }
        return (nowMillis / FLASH_INTERVAL_MILLIS & 1L) == 0L
                ? 1.0f
                : FLASH_DIM_ALPHA;
    }

    private static int rnd(float base, float scale) {
        return Math.round(base * scale);
    }
}
