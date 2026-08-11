package top.csituka.youzaiworldcore.client.hud;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YZUI 装备栏 HUD 渲染器。
 * <p>
 * 通过 {@code Inventory.getTimesChanged()} + Trinkets 缓存比对检测变化，
 * 渲染走缓存数据。装备耐久改显示剩余数字（满→绿/≤10%→红/其他→白），
 * 底部追加箭矢、烟花火箭数量与背包空位数指示器。
 * </p>
 * <p>装备与计数器共享槽位动画，支持入场、退场、数量/修复脉冲和低耐久摇晃。</p>
 */
@SuppressWarnings("null")
public final class ArmorHudRenderer {

    private static final String MODULE = "ArmorHudRenderer";

    private static final int BASE_SLOT_SIZE = 18;
    private static final int BASE_SLOT_SPACING = 20;
    private static final int BASE_ITEM_INSET = 1;
    private static final int BASE_PADDING = 3;
    private static final int BASE_PANEL_RADIUS = 6;
    private static final int BASE_LEFT_OFFSET = 2;
    private static final int BASE_TEXT_GAP = 4;
    private static final int BASE_TEXT_WIDTH = 22;
    private static final int BASE_BOTTOM_OFFSET = 70;

    private static final int SLOT_EMPTY_COLOR = 0x40FFFFFF;
    private static final int SLOT_FILLED_COLOR = 0x5AFFFFFF;
    private static final int PANEL_BG = 0x80FFFFFF;

    /** 装备槽位数（含主手） */
    private static final int EQUIP_SLOT_COUNT = 9;
    /** 指示器槽位数（箭矢 + 烟花火箭 + 空位数） */
    private static final int INDICATOR_SLOT_COUNT = 3;
    /** 总槽位数 = 装备 9 + 指示器 3 = 12 */
    private static final int TOTAL_SLOT_COUNT = EQUIP_SLOT_COUNT + INDICATOR_SLOT_COUNT;

    // ===== 颜色 =====
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final int COLOR_RED = 0xFFFF5555;

    private static final String TRINKET_TOTEM = "offhand/totem";
    private static final String TRINKET_HEART = "offhand/heart";
    private static final String TRINKET_ELYTRA = "chest/elytra";

    private static final Identifier EMPTY_SLOT_HELMET = Identifier.withDefaultNamespace("container/slot/helmet");
    private static final Identifier EMPTY_SLOT_CHESTPLATE = Identifier
            .withDefaultNamespace("container/slot/chestplate");
    private static final Identifier EMPTY_SLOT_LEGGINGS = Identifier.withDefaultNamespace("container/slot/leggings");
    private static final Identifier EMPTY_SLOT_BOOTS = Identifier.withDefaultNamespace("container/slot/boots");
    private static final Identifier EMPTY_SLOT_SHIELD = Identifier.withDefaultNamespace("container/slot/shield");

    /** 背包空位数指示器贴图（GUI sprite：textures/gui/sprites/inventory_hud_slot.png） */
    private static final Identifier EMPTY_SLOTS_ICON =
            Identifier.fromNamespaceAndPath("youzaiworldcore", "inventory_hud_slot");
    /** 主手槽位空位贴图（GUI sprite：textures/gui/sprites/hud_hand_solt.png） */
    private static final Identifier HAND_EMPTY_ICON =
            Identifier.fromNamespaceAndPath("youzaiworldcore", "hud_hand_solt");
    /** 箭矢指示器图标：原版普通箭物品（用物品模型渲染，非贴图） */
    private static final ItemStack ARROW_STACK = new ItemStack(Items.ARROW);
    /** 烟花火箭指示器图标：忽略飞行时间等组件，仅作为固定渲染模型。 */
    private static final ItemStack FIREWORK_STACK = new ItemStack(Items.FIREWORK_ROCKET);

    private static final Map<String, Identifier> trinketIconCache = new ConcurrentHashMap<>();

    /** 饰品槽键名，提为常量避免 {@code checkTrinketsChanged} 每帧分配数组 */
    private static final String[] TRINKET_KEYS = { TRINKET_TOTEM, TRINKET_HEART, TRINKET_ELYTRA };

    /**
     * {@link #trinketIconCache} 的空值哨兵。
     * <p>
     * {@code ConcurrentHashMap.computeIfAbsent} 对返回 {@code null} 的映射函数
     * <b>不写入缓存</b>，导致「该饰品槽查不到图标」这一结果永远缓存不住，
     * 于是每帧都要重跑一遍 {@code trinketIcon()} 的 SlotGroup 双层遍历。
     * 用哨兵值把「查不到」也缓存下来。
     * </p>
     */
    private static final Identifier ICON_NONE =
            Identifier.fromNamespaceAndPath("youzaiworldcore", "none");

    // ===== 缓存 =====
    private static int lastTimesChanged = -1;
    private static final ItemStack[] cachedTrinkets = new ItemStack[3];
    /** 每装备槽位独立的缓存物品渲染器 */
    private static final CachedItemRenderer[] itemRenderers = new CachedItemRenderer[EQUIP_SLOT_COUNT];
    /** 每个装备槽位独立的出现、退场、数量与耐久动画状态。 */
    private static final HudSlotAnimationState[] equipAnimations =
            new HudSlotAnimationState[EQUIP_SLOT_COUNT];
    /** 箭矢指示器图标渲染器（渲染原版箭物品模型） */
    private static final CachedItemRenderer arrowRenderer = new CachedItemRenderer();
    /** 烟花火箭指示器图标渲染器。 */
    private static final CachedItemRenderer fireworkRenderer = new CachedItemRenderer();
    /** 箭矢、烟花火箭与空位数量指示器动画。 */
    private static final HudSlotAnimationState arrowAnimation = new HudSlotAnimationState();
    private static final HudSlotAnimationState fireworkAnimation = new HudSlotAnimationState();
    private static final HudSlotAnimationState emptySlotsAnimation = new HudSlotAnimationState();
    /** 缓存的背包+快捷栏空位数（仅在 timesChanged 变化时刷新） */
    private static int cachedEmptySlots = 0;
    /** 缓存的背包+快捷栏箭矢总数（普通箭 + 药水箭 + 光灵箭）。 */
    private static int cachedArrowCount = 0;
    /** 缓存的背包+快捷栏烟花火箭总数（忽略飞行时间等组件）。 */
    private static int cachedFireworkCount = 0;

    /**
     * 缓存的 9 个装备槽决议结果。
     * <p>
     * {@link SlotEntry} 持有的是物品栏里 {@link ItemStack} 的<b>活引用</b>而非副本，
     * 因此耐久数字依旧实时跟随，只有「哪个槽放了什么」这一层被缓存。
     * </p>
     */
    private static final SlotEntry[] cachedEntries = new SlotEntry[EQUIP_SLOT_COUNT];

    /**
     * 缓存的槽位数量文本值（非耐久物品显示的「全背包同类物品总数」）。
     * <p>
     * 原实现每帧、每个非耐久槽位都要调用一次 {@code countInInventory}，
     * 而后者遍历全部 41 个槽位并逐个做 {@code isSameItemSameComponents}
     * （组件深比较）——最坏 9×41 = 369 次深比较/帧。改为与
     * {@link #cachedEmptySlots} 同一套脏检测下的预计算值。
     * </p>
     */
    private static final int[] cachedSlotCounts = new int[EQUIP_SLOT_COUNT];

    /**
     * 上次缓存刷新时的选中快捷栏下标。
     * <p>
     * 槽位 8 显示的是「当前主手物品」，而滚轮换手<b>不会</b>递增
     * {@code timesChanged}，必须单独作为脏检测输入。
     * </p>
     */
    private static int lastSelectedSlot = -1;

    /**
     * 上次缓存对应的玩家实例（弱引用，避免退出世界后仍持有 {@code LocalPlayer}）。
     * <p>
     * 重生 / 换维度会重建玩家实例，而新实例的 {@code timesChanged} 从 0 起算，
     * 有极小概率与上次残留值相同而漏刷新。加一道身份校验兜底。
     * </p>
     */
    private static java.lang.ref.WeakReference<Player> lastPlayerRef =
            new java.lang.ref.WeakReference<>(null);

    static {
        for (int i = 0; i < 3; i++)
            cachedTrinkets[i] = ItemStack.EMPTY;
        for (int i = 0; i < EQUIP_SLOT_COUNT; i++) {
            itemRenderers[i] = new CachedItemRenderer();
            equipAnimations[i] = new HudSlotAnimationState();
            cachedEntries[i] = SlotEntry.empty(null);
        }
    }

    private ArmorHudRenderer() {
    }

    /**
     * 使用统一缩放矩阵内的设计坐标绘制装备栏 HUD。
     *
     * @param graphics HUD 绘制上下文
     * @param guiHeight 当前缩放矩阵内的设计坐标高度
     */
    public static void render(GuiGraphicsExtractor graphics, int guiHeight) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;

        // 脏检测：timesChanged 变化时刷新物品栏快照 + 空位数；Trinkets 变化时刷新饰品缓存
        boolean playerChanged = lastPlayerRef.get() != player;
        if (playerChanged) {
            lastPlayerRef = new java.lang.ref.WeakReference<>(player);
        }
        int nowChanged = player.getInventory().getTimesChanged();
        boolean trinketsChanged = checkTrinketsChanged(player);
        boolean inventoryChanged = playerChanged || nowChanged != lastTimesChanged;
        if (inventoryChanged) {
            updateInventoryCache(player);
            lastTimesChanged = nowChanged;
        }
        if (playerChanged || trinketsChanged) {
            updateTrinketCache(player);
        }

        // 槽位决议 + 数量统计只在真正变化时重算：
        // 物品栏变动、饰品变动，或滚轮切换了主手（后者不递增 timesChanged）
        int selectedSlot = player.getInventory().getSelectedSlot();
        if (inventoryChanged || trinketsChanged || selectedSlot != lastSelectedSlot) {
            updateSlotCache(player);
            lastSelectedSlot = selectedSlot;
        }

        long nowMillis = System.currentTimeMillis();
        for (int i = 0; i < EQUIP_SLOT_COUNT; i++) {
            SlotEntry entry = cachedEntries[i];
            ItemStack stack = entry.stack == null ? ItemStack.EMPTY : entry.stack;
            int displayCount = entry.showDurability ? 1 : cachedSlotCounts[i];
            equipAnimations[i].synchronize(stack, displayCount,
                    nowMillis, !playerChanged);
        }
        arrowAnimation.synchronize(ARROW_STACK, cachedArrowCount,
                nowMillis, !playerChanged);
        fireworkAnimation.synchronize(FIREWORK_STACK, cachedFireworkCount,
                nowMillis, !playerChanged);
        emptySlotsAnimation.synchronize(ARROW_STACK, cachedEmptySlots,
                nowMillis, !playerChanged);

        int slotSize = BASE_SLOT_SIZE;
        int slotSpacing = BASE_SLOT_SPACING;
        int itemInset = BASE_ITEM_INSET;
        int padding = BASE_PADDING;
        int textGap = BASE_TEXT_GAP;
        int textWidth = BASE_TEXT_WIDTH;

        int gridH = (TOTAL_SLOT_COUNT - 1) * slotSpacing + slotSize;
        int panelH = gridH + padding * 2;
        int panelW = padding + slotSize + textGap + textWidth + padding;

        int panelX = BASE_LEFT_OFFSET;
        int panelY = guiHeight - panelH - BASE_BOTTOM_OFFSET;
        Font font = client.font;

        RoundedRect.fillOrSquare(graphics, panelX, panelY, panelW, panelH,
                BASE_PANEL_RADIUS, PANEL_BG);

        int iconSize = 16;

        // 9 个装备槽位
        for (int i = 0; i < EQUIP_SLOT_COUNT; i++) {
            int slotX = panelX + padding;
            int slotY = panelY + padding + i * slotSpacing;
            drawEquipSlot(graphics, font, i, slotX, slotY,
                    slotSize, itemInset, textGap, iconSize, nowMillis);
        }

        // 槽位 10：箭矢数量指示器
        int arrowSlotY = panelY + padding + EQUIP_SLOT_COUNT * slotSpacing;
        drawArrowIndicator(graphics, font, panelX + padding, arrowSlotY,
                slotSize, itemInset, textGap, nowMillis);

        // 槽位 11：烟花火箭数量指示器
        int fireworkSlotY = panelY + padding + (EQUIP_SLOT_COUNT + 1) * slotSpacing;
        drawFireworkIndicator(graphics, font, panelX + padding, fireworkSlotY,
                slotSize, itemInset, textGap, nowMillis);

        // 槽位 12：背包+快捷栏空位数指示器
        int emptySlotY = panelY + padding + (EQUIP_SLOT_COUNT + 2) * slotSpacing;
        drawEmptySlotsIndicator(graphics, font, panelX + padding, emptySlotY,
                slotSize, itemInset, textGap, iconSize, nowMillis);
    }

    private static void drawEquipSlot(GuiGraphicsExtractor g, Font font,
            int index, int slotX, int slotY,
            int slotSize, int itemInset, int textGap, int iconSize,
            long nowMillis) {
        SlotEntry entry = cachedEntries[index];
        boolean hasItem = entry.stack != null && !entry.stack.isEmpty();

        g.fill(slotX, slotY, slotX + slotSize, slotY + slotSize,
                hasItem ? SLOT_FILLED_COLOR : SLOT_EMPTY_COLOR);

        if (!hasItem && entry.placeholderIcon != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, entry.placeholderIcon,
                    slotX + itemInset, slotY + itemInset, iconSize, iconSize);
        }

        HudSlotAnimationState animation = equipAnimations[index];
        ItemStack outgoing = animation.outgoingStack(nowMillis);
        if (!outgoing.isEmpty()) {
            drawEquipItem(g, font, animation, outgoing, index,
                    slotX, slotY, slotSize, itemInset, textGap,
                    animation.outgoingDisplayCount(), nowMillis, true);
        }
        if (hasItem) {
            int displayCount = entry.showDurability
                    ? durabilityRemaining(entry.stack)
                    : cachedSlotCounts[index];
            drawEquipItem(g, font, animation, entry.stack, index,
                    slotX, slotY, slotSize, itemInset, textGap,
                    displayCount, nowMillis, false);
        }
    }

    private static void drawEquipItem(GuiGraphicsExtractor g, Font font,
            HudSlotAnimationState animation, ItemStack stack, int rendererIndex,
            int slotX, int slotY, int slotSize, int itemInset, int textGap,
            int displayCount, long nowMillis, boolean outgoing) {
        int itemX = slotX + itemInset;
        int itemY = slotY + itemInset;
        int animatedWidth = slotSize + textGap + BASE_TEXT_WIDTH;
        float centerX = slotX + animatedWidth / 2.0f;
        float centerY = slotY + slotSize / 2.0f;

        if (outgoing) {
            animation.pushOutgoingTransform(g, centerX, centerY, nowMillis);
            animation.outgoingRenderer().render(g, stack, itemX, itemY);
        } else {
            animation.pushCurrentTransform(g, stack, centerX, centerY, nowMillis);
            itemRenderers[rendererIndex].render(g, stack, itemX, itemY);
        }
        ItemBorderRenderer.renderBorder(g, itemX, itemY, stack);

        int textX = slotX + slotSize + textGap;
        int textY = slotY + (slotSize - font.lineHeight) / 2;
        boolean showDurability = stack.getMaxDamage() > 0;
        int shownValue = showDurability ? durabilityRemaining(stack) : displayCount;
        int color = showDurability
                ? durabilityColor(shownValue, stack)
                : COLOR_WHITE;
        g.text(font, Integer.toString(shownValue), textX, textY, color, true);

        if (outgoing) {
            animation.drawOutgoingOverlay(g, slotX, slotY,
                    animatedWidth, slotSize, nowMillis);
        } else {
            animation.drawCurrentOverlay(g, stack, slotX, slotY,
                    animatedWidth, slotSize, nowMillis);
        }
        animation.popTransform(g);
    }

    /**
     * 绘制箭矢数量指示器（槽位 10）。
     */
    private static void drawArrowIndicator(GuiGraphicsExtractor g, Font font,
            int slotX, int slotY, int slotSize, int itemInset, int textGap,
            long nowMillis) {
        g.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, SLOT_FILLED_COLOR);

        int animatedWidth = slotSize + textGap + BASE_TEXT_WIDTH;
        float centerX = slotX + animatedWidth / 2.0f;
        float centerY = slotY + slotSize / 2.0f;
        arrowAnimation.pushCurrentTransform(g, ARROW_STACK,
                centerX, centerY, nowMillis);

        // 原版普通箭物品模型图标（缓存解析，外观与物品栏一致）
        arrowRenderer.render(g, ARROW_STACK, slotX + itemInset, slotY + itemInset);

        // 箭矢总数文字
        int count = cachedArrowCount;
        int color;
        if (count >= 64) {
            color = COLOR_GREEN;
        } else if (count <= 20) {
            color = COLOR_RED;
        } else {
            color = COLOR_WHITE;
        }

        int textX = slotX + slotSize + textGap;
        int textY = slotY + (slotSize - font.lineHeight) / 2;
        g.text(font, Integer.toString(count), textX, textY, color, true);
        arrowAnimation.drawCurrentOverlay(g, ARROW_STACK,
                slotX, slotY, animatedWidth, slotSize, nowMillis);
        arrowAnimation.popTransform(g);
    }

    /**
     * 绘制烟花火箭数量指示器（槽位 11）。
     */
    private static void drawFireworkIndicator(GuiGraphicsExtractor g, Font font,
            int slotX, int slotY, int slotSize, int itemInset, int textGap,
            long nowMillis) {
        g.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, SLOT_FILLED_COLOR);

        int animatedWidth = slotSize + textGap + BASE_TEXT_WIDTH;
        float centerX = slotX + animatedWidth / 2.0f;
        float centerY = slotY + slotSize / 2.0f;
        fireworkAnimation.pushCurrentTransform(g, FIREWORK_STACK,
                centerX, centerY, nowMillis);

        fireworkRenderer.render(g, FIREWORK_STACK,
                slotX + itemInset, slotY + itemInset);

        int count = cachedFireworkCount;
        int color;
        if (count >= 64) {
            color = COLOR_GREEN;
        } else if (count <= 20) {
            color = COLOR_RED;
        } else {
            color = COLOR_WHITE;
        }

        int textX = slotX + slotSize + textGap;
        int textY = slotY + (slotSize - font.lineHeight) / 2;
        g.text(font, Integer.toString(count), textX, textY, color, true);
        fireworkAnimation.drawCurrentOverlay(g, FIREWORK_STACK,
                slotX, slotY, animatedWidth, slotSize, nowMillis);
        fireworkAnimation.popTransform(g);
    }

    /**
     * 绘制背包+快捷栏空位数指示器（槽位 12）。
     */
    private static void drawEmptySlotsIndicator(GuiGraphicsExtractor g, Font font,
            int slotX, int slotY, int slotSize, int itemInset, int textGap,
            int iconSize, long nowMillis) {
        g.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, SLOT_FILLED_COLOR);

        int animatedWidth = slotSize + textGap + BASE_TEXT_WIDTH;
        float centerX = slotX + animatedWidth / 2.0f;
        float centerY = slotY + slotSize / 2.0f;
        emptySlotsAnimation.pushCurrentTransform(g, ItemStack.EMPTY,
                centerX, centerY, nowMillis);

        // 贴图占位图标
        g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY_SLOTS_ICON,
                slotX + itemInset, slotY + itemInset, iconSize, iconSize);

        // 空位数文字
        int empty = cachedEmptySlots;
        int color;
        if (empty >= 27) {
            color = COLOR_GREEN;
        } else if (empty <= 5) {
            color = COLOR_RED;
        } else {
            color = COLOR_WHITE;
        }

        int textX = slotX + slotSize + textGap;
        int textY = slotY + (slotSize - font.lineHeight) / 2;
        g.text(font, Integer.toString(empty), textX, textY, color, true);
        emptySlotsAnimation.drawCurrentOverlay(g, ItemStack.EMPTY,
                slotX, slotY, animatedWidth, slotSize, nowMillis);
        emptySlotsAnimation.popTransform(g);
    }

    // ===== 缓存更新 =====

    private static void updateInventoryCache(Player player) {
        // 重新统计快捷栏+背包空槽数（slots 0..35）
        int empty = 0;
        // 重新统计箭矢总数（普通箭 + 药水箭 + 光灵箭）与全部烟花火箭。
        int arrows = 0;
        int fireworks = 0;
        var inventory = player.getInventory();
        for (int i = 0; i <= 35; i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) {
                empty++;
                continue;
            }
            if (s.is(Items.ARROW) || s.is(Items.TIPPED_ARROW)
                    || s.is(Items.SPECTRAL_ARROW)) {
                arrows += s.getCount();
            } else if (s.is(Items.FIREWORK_ROCKET)) {
                // 仅按物品类型统计，不比较飞行时间或烟花效果组件。
                fireworks += s.getCount();
            }
        }
        if ((arrows != cachedArrowCount || fireworks != cachedFireworkCount)
                && DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(MODULE, "更新 HUD 弹药统计: arrows=" + arrows
                    + ", fireworks=" + fireworks);
        }
        cachedEmptySlots = empty;
        cachedArrowCount = arrows;
        cachedFireworkCount = fireworks;
    }

    /**
     * 重算 9 个装备槽的决议结果与数量文本。
     * <p>
     * 仅在物品栏变动 / 饰品变动 / 主手切换时调用，把原先每帧执行的
     * {@code resolveEquipSlot} 与 {@code countInInventory} 移出渲染热路径。
     * </p>
     */
    private static void updateSlotCache(Player player) {
        for (int i = 0; i < EQUIP_SLOT_COUNT; i++) {
            SlotEntry entry = resolveEquipSlot(player, i);
            cachedEntries[i] = entry;
            if (!entry.showDurability && entry.stack != null && !entry.stack.isEmpty()) {
                cachedSlotCounts[i] = entry.stack.getCount()
                        + countInInventory(player, entry.stack, entry.excludeSlot());
            } else {
                cachedSlotCounts[i] = 0;
            }
        }
    }

    private static void updateTrinketCache(Player player) {
        TrinketAttachment a = TrinketsApi.getAttachment(player);
        for (int i = 0; i < 3; i++) {
            if (a != null) {
                TrinketInventory inv = a.getInventories().get(TRINKET_KEYS[i]);
                ItemStack s = (inv != null && inv.getContainerSize() > 0)
                        ? inv.getItem(0)
                        : ItemStack.EMPTY;
                cachedTrinkets[i] = (s != null && !s.isEmpty()) ? s.copy() : ItemStack.EMPTY;
            } else {
                cachedTrinkets[i] = ItemStack.EMPTY;
            }
        }
    }

    private static boolean checkTrinketsChanged(Player player) {
        TrinketAttachment a = TrinketsApi.getAttachment(player);
        if (a == null)
            return false;
        for (int i = 0; i < 3; i++) {
            TrinketInventory inv = a.getInventories().get(TRINKET_KEYS[i]);
            ItemStack cur = (inv != null && inv.getContainerSize() > 0)
                    ? inv.getItem(0)
                    : ItemStack.EMPTY;
            if (!ItemStack.matches(cur, cachedTrinkets[i]))
                return true;
        }
        return false;
    }

    // ===== 槽位决议 =====

    private static SlotEntry resolveEquipSlot(Player player, int index) {
        return switch (index) {
            case 0 -> {
                ItemStack s = player.getOffhandItem();
                yield s.isEmpty() ? SlotEntry.empty(EMPTY_SLOT_SHIELD)
                        : SlotEntry.of(s, 40, EMPTY_SLOT_SHIELD);
            }
            case 1 -> {
                yield trinketSlotEntry(cachedTrinkets[0], TRINKET_TOTEM, player);
            }
            case 2 -> {
                yield trinketSlotEntry(cachedTrinkets[1], TRINKET_HEART, player);
            }
            case 3 -> {
                yield trinketSlotEntry(cachedTrinkets[2], TRINKET_ELYTRA, player);
            }
            case 4 -> armorSlot(player, 39, EMPTY_SLOT_HELMET);
            case 5 -> armorSlot(player, 38, EMPTY_SLOT_CHESTPLATE);
            case 6 -> armorSlot(player, 37, EMPTY_SLOT_LEGGINGS);
            case 7 -> armorSlot(player, 36, EMPTY_SLOT_BOOTS);
            case 8 -> {
                int sel = player.getInventory().getSelectedSlot();
                ItemStack s = player.getInventory().getItem(sel);
                yield s.isEmpty() ? SlotEntry.empty(HAND_EMPTY_ICON) : SlotEntry.of(s, sel, HAND_EMPTY_ICON);
            }
            default -> SlotEntry.empty(null);
        };
    }

    private static SlotEntry trinketSlotEntry(ItemStack s, String groupKey, Player player) {
        // 用 ICON_NONE 哨兵把「查不到图标」也缓存进去：
        // computeIfAbsent 不会缓存 null，否则每次都要重跑 SlotGroup 双层遍历
        Identifier icon = trinketIconCache.computeIfAbsent(groupKey, k -> {
            Identifier found = trinketIcon(player, k);
            return found != null ? found : ICON_NONE;
        });
        Identifier placeholder = (icon == ICON_NONE) ? null : icon;
        return s.isEmpty() ? SlotEntry.empty(placeholder) : SlotEntry.of(s, -1, placeholder);
    }

    private static Identifier trinketIcon(Player player, String groupKey) {
        try {
            for (var ge : eu.pb4.trinkets.api.SlotGroup
                    .getEntityGroups(player).entrySet()) {
                for (var st : ge.getValue().getSlots()) {
                    if (st.getId().equals(groupKey))
                        return st.icon();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static SlotEntry armorSlot(Player player, int slot, Identifier emptyIcon) {
        ItemStack s = player.getInventory().getItem(slot);
        return s.isEmpty() ? SlotEntry.empty(emptyIcon) : SlotEntry.of(s, slot, emptyIcon);
    }

    // ===== 辅助 =====

    /** 剩余耐久数值（不带单位） */
    private static int durabilityRemaining(ItemStack stack) {
        int max = stack.getMaxDamage();
        int dmg = stack.getDamageValue();
        return Math.max(0, max - dmg);
    }

    /**
     * 耐久颜色：满耐久（100%）绿色，剩余 ≤10% 红色，其他白色。
     */
    private static int durabilityColor(int remaining, ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0)
            return COLOR_WHITE;
        if (remaining >= max)
            return COLOR_GREEN; // 满耐久
        if (remaining * 10 <= max)
            return COLOR_RED; // 剩余 ≤10%
        return COLOR_WHITE;
    }

    private static int countInInventory(Player player, ItemStack ref, int exclude) {
        int total = 0;
        var inventory = player.getInventory();
        int size = inventory.getContainerSize();
        for (int i = 0; i < size; i++) {
            if (i == exclude)
                continue;
            ItemStack s = inventory.getItem(i);
            if (ItemStack.isSameItemSameComponents(s, ref))
                total += s.getCount();
        }
        return total;
    }

    private record SlotEntry(ItemStack stack, boolean showDurability,
            Identifier placeholderIcon, int excludeSlot) {
        /**
         * 有物品的槽位。
         * <p>
         * {@code placeholderIcon} 对非空槽位同样要带上：本类的槽位决议是<b>带缓存</b>的，
         * 而物品可能在原地被清空（盔甲损坏、消耗品用尽）却尚未触发 {@code timesChanged}。
         * 此时 {@code stack.isEmpty()} 已为真，若不带占位图标，该槽会既不画物品也不画图标。
         * </p>
         */
        static SlotEntry of(ItemStack s, int ex, Identifier placeholderIcon) {
            return new SlotEntry(s, s.getMaxDamage() > 0, placeholderIcon, ex);
        }

        static SlotEntry empty(Identifier icon) {
            return new SlotEntry(ItemStack.EMPTY, false, icon, -1);
        }
    }
}
