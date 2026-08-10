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
import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YZUI 装备栏 HUD 渲染器。
 * <p>
 * 通过 {@code Inventory.getTimesChanged()} + Trinkets 缓存比对检测变化，
 * 渲染走缓存数据。装备耐久改显示剩余数字（满→绿/≤10%→红/其他→白），
 * 底部追加箭矢数量与背包空位数指示器。
 * </p>
 */
@SuppressWarnings("null")
public final class ArmorHudRenderer {

    private static final float REF_HEIGHT = 360f;

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
    /** 指示器槽位数（箭矢 + 空位数） */
    private static final int INDICATOR_SLOT_COUNT = 2;
    /** 总槽位数 = 装备 9 + 指示器 2 = 11 */
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
    /** 箭矢指示器图标：原版普通箭物品（用物品模型渲染，非贴图） */
    private static final ItemStack ARROW_STACK = new ItemStack(Items.ARROW);

    private static final int[][] CORNER_R6 = buildCornerTable(6);
    private static final Map<String, Identifier> trinketIconCache = new ConcurrentHashMap<>();

    // ===== 缓存 =====
    private static int lastTimesChanged = -1;
    private static final ItemStack[] cachedTrinkets = new ItemStack[3];
    /** 每装备槽位独立的缓存物品渲染器 */
    private static final CachedItemRenderer[] itemRenderers = new CachedItemRenderer[EQUIP_SLOT_COUNT];
    /** 箭矢指示器图标渲染器（渲染原版箭物品模型） */
    private static final CachedItemRenderer arrowRenderer = new CachedItemRenderer();
    /** 缓存的背包+快捷栏空位数（仅在 timesChanged 变化时刷新） */
    private static int cachedEmptySlots = 0;
    /** 缓存的背包+快捷栏箭矢总数（普通箭 + 药水箭，仅在 timesChanged 变化时刷新） */
    private static int cachedArrowCount = 0;

    static {
        for (int i = 0; i < 3; i++)
            cachedTrinkets[i] = ItemStack.EMPTY;
        for (int i = 0; i < EQUIP_SLOT_COUNT; i++)
            itemRenderers[i] = new CachedItemRenderer();
    }

    private ArmorHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;

        // 脏检测：timesChanged 变化时刷新物品栏快照 + 空位数；Trinkets 变化时刷新饰品缓存
        int nowChanged = player.getInventory().getTimesChanged();
        boolean trinketsChanged = checkTrinketsChanged(player);
        if (nowChanged != lastTimesChanged) {
            updateInventoryCache(player);
            lastTimesChanged = nowChanged;
        }
        if (trinketsChanged) {
            updateTrinketCache(player);
        }

        float s = graphics.guiHeight() / REF_HEIGHT;
        int slotSize = rnd(BASE_SLOT_SIZE, s);
        int slotSpacing = rnd(BASE_SLOT_SPACING, s);
        int itemInset = rnd(BASE_ITEM_INSET, s);
        int padding = rnd(BASE_PADDING, s);
        int textGap = rnd(BASE_TEXT_GAP, s);
        int textWidth = rnd(BASE_TEXT_WIDTH, s);

        int gridH = (TOTAL_SLOT_COUNT - 1) * slotSpacing + slotSize;
        int panelH = gridH + padding * 2;
        int panelW = padding + slotSize + textGap + textWidth + padding;

        int sh = graphics.guiHeight();
        int panelX = rnd(BASE_LEFT_OFFSET, s);
        int panelY = sh - panelH - rnd(BASE_BOTTOM_OFFSET, s);
        Font font = client.font;

        fillRounded(graphics, panelX, panelY, panelW, panelH,
                rnd(BASE_PANEL_RADIUS, s), PANEL_BG, CORNER_R6);

        int iconSize = rnd(16, s);

        // 9 个装备槽位
        for (int i = 0; i < EQUIP_SLOT_COUNT; i++) {
            int slotX = panelX + padding;
            int slotY = panelY + padding + i * slotSpacing;
            drawEquipSlot(graphics, font, player, i, slotX, slotY,
                    slotSize, itemInset, textGap, iconSize);
        }

        // 槽位 10：箭矢数量指示器
        int arrowSlotY = panelY + padding + EQUIP_SLOT_COUNT * slotSpacing;
        drawArrowIndicator(graphics, font, panelX + padding, arrowSlotY,
                slotSize, itemInset, textGap);

        // 槽位 11：背包+快捷栏空位数指示器
        int emptySlotY = panelY + padding + (EQUIP_SLOT_COUNT + 1) * slotSpacing;
        drawEmptySlotsIndicator(graphics, font, panelX + padding, emptySlotY,
                slotSize, itemInset, textGap, iconSize);
    }

    private static void drawEquipSlot(GuiGraphicsExtractor g, Font font,
            Player player, int index, int slotX, int slotY,
            int slotSize, int itemInset, int textGap, int iconSize) {
        SlotEntry entry = resolveEquipSlot(player, index);
        boolean hasItem = entry.stack != null && !entry.stack.isEmpty();

        g.fill(slotX, slotY, slotX + slotSize, slotY + slotSize,
                hasItem ? SLOT_FILLED_COLOR : SLOT_EMPTY_COLOR);

        if (hasItem) {
            int ix = slotX + itemInset;
            int iy = slotY + itemInset;
            itemRenderers[index].render(g, entry.stack, ix, iy);
            ItemBorderRenderer.renderBorder(g, ix, iy, entry.stack);

            int textX = slotX + slotSize + textGap;
            int textY = slotY + (slotSize - font.lineHeight) / 2;

            if (entry.showDurability) {
                int remaining = durabilityRemaining(entry.stack);
                g.text(font, Integer.toString(remaining), textX, textY,
                        durabilityColor(remaining, entry.stack), true);
            } else {
                int cnt = entry.stack.getCount()
                        + countInInventory(player, entry.stack, entry.excludeSlot());
                g.text(font, Integer.toString(cnt), textX, textY, COLOR_WHITE, true);
            }
        } else if (entry.placeholderIcon != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, entry.placeholderIcon,
                    slotX + itemInset, slotY + itemInset, iconSize, iconSize);
        }
    }

    /**
     * 绘制箭矢数量指示器（槽位 10）。
     */
    private static void drawArrowIndicator(GuiGraphicsExtractor g, Font font,
            int slotX, int slotY, int slotSize, int itemInset, int textGap) {
        g.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, SLOT_FILLED_COLOR);

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
    }

    /**
     * 绘制背包+快捷栏空位数指示器（槽位 11）。
     */
    private static void drawEmptySlotsIndicator(GuiGraphicsExtractor g, Font font,
            int slotX, int slotY, int slotSize, int itemInset, int textGap, int iconSize) {
        g.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, SLOT_FILLED_COLOR);

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
    }

    // ===== 缓存更新 =====

    private static void updateInventoryCache(Player player) {
        // 重新统计快捷栏+背包空槽数（slots 0..35）
        int empty = 0;
        // 重新统计箭矢总数（普通箭 + 药水箭）
        int arrows = 0;
        for (int i = 0; i <= 35; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.isEmpty()) {
                empty++;
                continue;
            }
            if (s.is(Items.ARROW) || s.is(Items.TIPPED_ARROW)) {
                arrows += s.getCount();
            }
        }
        cachedEmptySlots = empty;
        cachedArrowCount = arrows;
    }

    private static void updateTrinketCache(Player player) {
        TrinketAttachment a = TrinketsApi.getAttachment(player);
        String[] keys = { TRINKET_TOTEM, TRINKET_HEART, TRINKET_ELYTRA };
        for (int i = 0; i < 3; i++) {
            if (a != null) {
                TrinketInventory inv = a.getInventories().get(keys[i]);
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
        String[] keys = { TRINKET_TOTEM, TRINKET_HEART, TRINKET_ELYTRA };
        for (int i = 0; i < 3; i++) {
            TrinketInventory inv = a.getInventories().get(keys[i]);
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
                yield s.isEmpty() ? SlotEntry.empty(EMPTY_SLOT_SHIELD) : SlotEntry.of(s, 40);
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
                yield s.isEmpty() ? SlotEntry.empty(null) : SlotEntry.of(s, sel);
            }
            default -> SlotEntry.empty(null);
        };
    }

    private static SlotEntry trinketSlotEntry(ItemStack s, String groupKey, Player player) {
        if (s.isEmpty()) {
            Identifier icon = trinketIconCache.computeIfAbsent(groupKey,
                    k -> trinketIcon(player, k));
            return SlotEntry.empty(icon);
        }
        return SlotEntry.of(s, -1);
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
        return s.isEmpty() ? SlotEntry.empty(emptyIcon) : SlotEntry.of(s, slot);
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
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (i == exclude)
                continue;
            ItemStack s = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(s, ref))
                total += s.getCount();
        }
        return total;
    }

    private static int rnd(float base, float scale) {
        return Math.round(base * scale);
    }

    private static int[][] buildCornerTable(int r) {
        int count = 0;
        for (int i = 0; i < r; i++)
            for (int j = 0; j < r; j++)
                if (i * i + j * j < r * r)
                    count++;
        int[][] tbl = new int[count][2];
        int idx = 0;
        for (int i = 0; i < r; i++)
            for (int j = 0; j < r; j++)
                if (i * i + j * j < r * r)
                    tbl[idx++] = new int[] { i, j };
        return tbl;
    }

    private static void fillRounded(GuiGraphicsExtractor g,
            int x, int y, int w, int h, int r, int color, int[][] corners) {
        if (w > r * 2) {
            g.fill(x + r, y, x + w - r, y + h, color);
        } else {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        for (int[] c : corners) {
            int i = c[0], j = c[1];
            g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
            g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
            g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
            g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
        }
    }

    private record SlotEntry(ItemStack stack, boolean showDurability,
            Identifier placeholderIcon, int excludeSlot) {
        static SlotEntry of(ItemStack s, int ex) {
            return new SlotEntry(s, s.getMaxDamage() > 0, null, ex);
        }

        static SlotEntry empty(Identifier icon) {
            return new SlotEntry(ItemStack.EMPTY, false, icon, -1);
        }
    }
}