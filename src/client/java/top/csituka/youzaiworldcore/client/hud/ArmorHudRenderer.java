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
import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YZUI 装备栏 HUD 渲染器。<p>
 * 通过 {@code Inventory.getTimesChanged()} + Trinkets 缓存比对检测变化，
 * 仅在变化时更新缓存，渲染走缓存数据。</p>
 */
@SuppressWarnings("null")
public final class ArmorHudRenderer {

    private static final String LOG_TAG = "ArmorHudRenderer";
    private static final float REF_HEIGHT = 360f;

    private static final int BASE_SLOT_SIZE = 18;
    private static final int BASE_SLOT_RADIUS = 3;
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
    private static final int SLOT_COUNT = 9;

    private static final int DURABILITY_COLOR_HIGH = 0xFFFFFFFF;
    private static final int DURABILITY_COLOR_MED  = 0xFFFFFF55;
    private static final int DURABILITY_COLOR_LOW  = 0xFFFF5555;
    private static final int COUNT_COLOR = 0xC0FFFFFF;

    private static final String TRINKET_TOTEM  = "offhand/totem";
    private static final String TRINKET_HEART  = "offhand/heart";
    private static final String TRINKET_ELYTRA = "chest/elytra";

    private static final Identifier EMPTY_SLOT_HELMET =
            Identifier.withDefaultNamespace("container/slot/helmet");
    private static final Identifier EMPTY_SLOT_CHESTPLATE =
            Identifier.withDefaultNamespace("container/slot/chestplate");
    private static final Identifier EMPTY_SLOT_LEGGINGS =
            Identifier.withDefaultNamespace("container/slot/leggings");
    private static final Identifier EMPTY_SLOT_BOOTS =
            Identifier.withDefaultNamespace("container/slot/boots");
    private static final Identifier EMPTY_SLOT_SHIELD =
            Identifier.withDefaultNamespace("container/slot/shield");

    private static final int[][] CORNER_R3 = buildCornerTable(3);
    private static final int[][] CORNER_R6 = buildCornerTable(6);
    private static final Map<String, Identifier> trinketIconCache = new ConcurrentHashMap<>();

    // ===== 缓存 =====
    private static int lastTimesChanged = -1;
    private static final ItemStack[] cachedTrinkets = new ItemStack[3];
    static {
        for (int i = 0; i < 3; i++)
            cachedTrinkets[i] = ItemStack.EMPTY;
    }

    private ArmorHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null)
            return;

        // 脏检测
        int nowChanged = player.getInventory().getTimesChanged();
        boolean trinketsChanged = checkTrinketsChanged(player);
        if (nowChanged != lastTimesChanged || trinketsChanged) {
            updateCache(player);
            lastTimesChanged = nowChanged;
        }

        float s = graphics.guiHeight() / REF_HEIGHT;
        int slotSize = rnd(BASE_SLOT_SIZE, s);
        int slotSpacing = rnd(BASE_SLOT_SPACING, s);
        int itemInset = rnd(BASE_ITEM_INSET, s);
        int padding = rnd(BASE_PADDING, s);
        int textGap = rnd(BASE_TEXT_GAP, s);
        int textWidth = rnd(BASE_TEXT_WIDTH, s);

        int gridH = (SLOT_COUNT - 1) * slotSpacing + slotSize;
        int panelH = gridH + padding * 2;
        int panelW = padding + slotSize + textGap + textWidth + padding;

        int sh = graphics.guiHeight();
        int panelX = rnd(BASE_LEFT_OFFSET, s);
        int panelY = sh - panelH - rnd(BASE_BOTTOM_OFFSET, s);
        Font font = client.font;

        fillRounded(graphics, panelX, panelY, panelW, panelH,
                rnd(BASE_PANEL_RADIUS, s), PANEL_BG, CORNER_R6);

        int slotRadius = rnd(BASE_SLOT_RADIUS, s);
        int[][] cornerR = (slotRadius == 3) ? CORNER_R3 : buildCornerTable(slotRadius);
        int iconSize = rnd(16, s);

        for (int i = 0; i < SLOT_COUNT; i++) {
            int slotX = panelX + padding;
            int slotY = panelY + padding + i * slotSpacing;
            drawSlot(graphics, font, player, s, i, slotX, slotY,
                    slotSize, slotRadius, itemInset, textGap, iconSize, cornerR);
        }
    }

    private static void drawSlot(GuiGraphicsExtractor g, Font font,
            Player player, float scale, int index, int slotX, int slotY,
            int slotSize, int slotRadius, int itemInset, int textGap,
            int iconSize, int[][] cornerR) {
        SlotEntry entry = resolveSlotCached(player, index);
        boolean hasItem = entry.stack != null && !entry.stack.isEmpty();

        fillRounded(g, slotX, slotY, slotSize, slotSize, slotRadius,
                hasItem ? SLOT_FILLED_COLOR : SLOT_EMPTY_COLOR, cornerR);

        if (hasItem) {
            g.item(entry.stack, slotX + itemInset, slotY + itemInset);
            ItemBorderRenderer.renderBorder(g,
                    slotX + itemInset, slotY + itemInset, entry.stack);

            int textX = slotX + slotSize + textGap;
            int textY = slotY + (slotSize - font.lineHeight) / 2;

            if (entry.showDurability) {
                int p = durabilityPercent(entry.stack);
                g.text(font, p + "%", textX, textY, durabilityColor(p), true);
            } else {
                int cnt = entry.stack.getCount()
                        + countInInventory(player, entry.stack, entry.excludeSlot());
                g.text(font, Integer.toString(cnt), textX, textY, COUNT_COLOR, true);
            }
        } else if (entry.placeholderIcon != null) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, entry.placeholderIcon,
                    slotX + itemInset, slotY + itemInset, iconSize, iconSize);
        }
    }

    // ===== 缓存更新 =====

    private static boolean checkTrinketsChanged(Player player) {
        TrinketAttachment a = TrinketsApi.getAttachment(player);
        if (a == null) return false;
        String[] keys = { TRINKET_TOTEM, TRINKET_HEART, TRINKET_ELYTRA };
        for (int i = 0; i < 3; i++) {
            TrinketInventory inv = a.getInventories().get(keys[i]);
            ItemStack cur = (inv != null && inv.getContainerSize() > 0)
                    ? inv.getItem(0) : ItemStack.EMPTY;
            if (cur == null) cur = ItemStack.EMPTY;
            if (!ItemStack.matches(cur, cachedTrinkets[i])) return true;
        }
        return false;
    }

    private static void updateCache(Player player) {
        TrinketAttachment a = TrinketsApi.getAttachment(player);
        String[] keys = { TRINKET_TOTEM, TRINKET_HEART, TRINKET_ELYTRA };
        for (int i = 0; i < 3; i++) {
            if (a != null) {
                TrinketInventory inv = a.getInventories().get(keys[i]);
                ItemStack s = (inv != null && inv.getContainerSize() > 0)
                        ? inv.getItem(0) : ItemStack.EMPTY;
                cachedTrinkets[i] = (s != null && !s.isEmpty()) ? s.copy() : ItemStack.EMPTY;
            } else {
                cachedTrinkets[i] = ItemStack.EMPTY;
            }
        }
    }

    // ===== 槽位决议（走缓存） =====

    private static SlotEntry resolveSlotCached(Player player, int index) {
        return switch (index) {
            case 0 -> { // 副手
                ItemStack s = player.getOffhandItem();
                yield s.isEmpty() ? SlotEntry.empty(EMPTY_SLOT_SHIELD) : SlotEntry.of(s, 40);
            }
            case 1 -> { // 不死图腾（缓存）
                ItemStack s = cachedTrinkets[0];
                if (s.isEmpty()) {
                    Identifier icon = trinketIconCache.computeIfAbsent(TRINKET_TOTEM,
                            k -> trinketIcon(player, k));
                    yield SlotEntry.empty(icon);
                }
                yield SlotEntry.of(s, -1);
            }
            case 2 -> { // 守护之星
                ItemStack s = cachedTrinkets[1];
                if (s.isEmpty()) {
                    Identifier icon = trinketIconCache.computeIfAbsent(TRINKET_HEART,
                            k -> trinketIcon(player, k));
                    yield SlotEntry.empty(icon);
                }
                yield SlotEntry.of(s, -1);
            }
            case 3 -> { // 鞘翅
                ItemStack s = cachedTrinkets[2];
                if (s.isEmpty()) {
                    Identifier icon = trinketIconCache.computeIfAbsent(TRINKET_ELYTRA,
                            k -> trinketIcon(player, k));
                    yield SlotEntry.empty(icon);
                }
                yield SlotEntry.of(s, -1);
            }
            case 4 -> armorSlot(player, 39, EMPTY_SLOT_HELMET);
            case 5 -> armorSlot(player, 38, EMPTY_SLOT_CHESTPLATE);
            case 6 -> armorSlot(player, 37, EMPTY_SLOT_LEGGINGS);
            case 7 -> armorSlot(player, 36, EMPTY_SLOT_BOOTS);
            case 8 -> { // 主手
                int sel = player.getInventory().getSelectedSlot();
                ItemStack s = player.getInventory().getItem(sel);
                yield s.isEmpty() ? SlotEntry.empty(null) : SlotEntry.of(s, sel);
            }
            default -> SlotEntry.empty(null);
        };
    }

    private static Identifier trinketIcon(Player player, String groupKey) {
        try {
            for (var ge : eu.pb4.trinkets.api.SlotGroup
                    .getEntityGroups(player).entrySet()) {
                for (var st : ge.getValue().getSlots()) {
                    if (st.getId().equals(groupKey)) return st.icon();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static SlotEntry armorSlot(Player player, int slot, Identifier emptyIcon) {
        ItemStack s = player.getInventory().getItem(slot);
        return s.isEmpty() ? SlotEntry.empty(emptyIcon) : SlotEntry.of(s, slot);
    }

    // ===== 辅助 =====

    private static int durabilityPercent(ItemStack stack) {
        int max = stack.getMaxDamage();
        return max > 0 ? (max - stack.getDamageValue()) * 100 / max : 100;
    }

    private static int durabilityColor(int p) {
        return p > 60 ? DURABILITY_COLOR_HIGH : p > 30 ? DURABILITY_COLOR_MED : DURABILITY_COLOR_LOW;
    }

    private static int countInInventory(Player player, ItemStack ref, int exclude) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (i == exclude) continue;
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
                if (i * i + j * j < r * r) count++;
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
