package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.csituka.youzaiworldcore.client.screen.widget.YzuiCloseButton;

/** 工作方块共用的主题外壳；原版菜单、预览及点击坐标保持一致。 */
@SuppressWarnings("null")
public enum YzuiWorkstationStyle {
    ENCHANTMENT(Items.ENCHANTING_TABLE, -1),
    LOOM(Items.LOOM, 3),
    CARTOGRAPHY(Items.CARTOGRAPHY_TABLE, 2),
    STONECUTTER(Items.STONECUTTER, 1),
    GRINDSTONE(Items.GRINDSTONE, 2),
    BEACON(Items.BEACON, 0);

    /** 标题栏向原版内容区上方延伸，避免遮住附魔选项、图案及地图预览。 */
    public static final int HEADER_HEIGHT = 20;
    private static final int CLOSE_SIZE = 14;
    private static final int CLOSE_MARGIN = 6;
    private final ItemStack icon;
    private final int accentSlot;

    YzuiWorkstationStyle(Item item, int accentSlot) {
        this.icon = new ItemStack(item);
        this.accentSlot = accentSlot;
    }

    /** 使用与全屏地图及已有工作方块一致的关闭控件。 */
    public static YzuiCloseButton closeButton(int left, int top, int width, Runnable close) {
        return new YzuiCloseButton(left + width - CLOSE_MARGIN - CLOSE_SIZE,
                top - HEADER_HEIGHT + 2, CLOSE_SIZE, close);
    }

    /** 扩展可点击的面板范围，点击标题空白处时不会把光标物品丢到界面外。 */
    public static boolean hasClickedOutside(double mouseX, double mouseY, int left, int top,
            int width, int height) {
        return mouseX < left || mouseY < top - HEADER_HEIGHT
                || mouseX >= left + width || mouseY >= top + height;
    }

    /** 绘制卡片、标题、槽位和操作图示，随后由原版绘制动态内容。 */
    public void drawBackground(GuiGraphicsExtractor g, Font font, Component title, AbstractContainerMenu menu,
            int left, int top, int width, int height, int mouseX, int mouseY) {
        YzuiTheme.card(g, left, top - HEADER_HEIGHT, width, height + HEADER_HEIGHT);
        drawTitle(g, font, title, left, top, width);
        drawAreas(g, left, top, menu);
        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            if (!slot.isActive()) continue;
            int x = left + slot.x, y = top + slot.y;
            boolean hovered = mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
            RoundedRect.fill(g, x, y, 16, 16, 3, hovered ? YzuiTheme.slotHover() : YzuiTheme.slot());
            if (index == accentSlot) {
                YzuiTheme.border(g, x - 2, y - 2, 20, 20, 4, YzuiTheme.primary());
            }
        }
    }

    /** 背包标签按内容留出空间：制图台避开地图，信标避开付款确认按钮。 */
    public void drawInventoryLabel(GuiGraphicsExtractor g, Font font, Component title, int x, int y, int width) {
        int available = this == CARTOGRAPHY ? 55 : this == BEACON ? 120 : width - x - 8;
        YzuiTheme.label(g, font, title, x, y, available, YzuiTheme.text(), false);
    }

    private void drawTitle(GuiGraphicsExtractor g, Font font, Component title, int left, int top, int width) {
        int x = left + 8, y = top - HEADER_HEIGHT + 5;
        RoundedRect.fill(g, x, y, 12, 12, 3, YzuiTheme.slot());
        g.pose().pushMatrix();
        try {
            g.pose().translate(x, y);
            g.pose().scale(0.75f, 0.75f);
            g.item(icon, 0, 0, 0);
        } finally {
            g.pose().popMatrix();
        }
        int textX = x + 16;
        int available = width - 24 - CLOSE_MARGIN - CLOSE_SIZE - 4;
        YzuiTheme.label(g, font, title, textX, y, available, YzuiTheme.primary(), false);
        RoundedRect.fill(g, textX, y + 10, Math.min(font.width(title), available), 2, 1, YzuiTheme.primary());
    }

    private void drawAreas(GuiGraphicsExtractor g, int left, int top, AbstractContainerMenu menu) {
        switch (this) {
            case ENCHANTMENT -> area(g, left + 12, top + 12, 42, 35);
            case LOOM -> {
                area(g, left + 8, top + 20, 44, 45);
                area(g, left + 60, top + 13, 56, 56);
                area(g, left + 119, top + 13, 12, 56);
                area(g, left + 139, top + 6, 24, 44);
            }
            case CARTOGRAPHY -> {
                plus(g, left + 21, top + 41);
                arrow(g, left + 38, top + 35, 22);
            }
            case STONECUTTER -> {
                area(g, left + 52, top + 14, 64, 54);
                area(g, left + 119, top + 15, 12, 56);
            }
            case GRINDSTONE -> {
                area(g, left + 45, top + 15, 34, 56);
                arrow(g, left + 95, top + 35, 22);
            }
            case BEACON -> {
                area(g, left + 8, top + 5, 106, 94);
                area(g, left + 122, top + 5, 100, 94);
                int levels = menu instanceof BeaconMenu beacon ? beacon.getLevels() : 0;
                for (int tier = 1; tier <= 3; tier++) {
                    beaconPyramid(g, left + 27, top + 39 + (tier - 1) * 25, tier, levels >= tier);
                }
                beaconPyramid(g, left + 167, top + 43, 4, levels >= 4);
            }
        }
    }

    private static void area(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        RoundedRect.fill(g, x, y, w, h, 4, YzuiTheme.surfaceLow());
    }

    /** 信标层级示意图以几何图标绘制，解锁颜色由原版菜单同步的层数决定。 */
    private static void beaconPyramid(GuiGraphicsExtractor g, int centerX, int bottom, int tier, boolean unlocked) {
        int color = unlocked ? YzuiTheme.primary() : YzuiTheme.outlineVariant();
        for (int row = 0; row < tier; row++) {
            int halfWidth = 2 + row * 3;
            int y = bottom - (tier - row) * 3;
            g.fill(centerX - halfWidth, y, centerX + halfWidth + 1, y + 2, color);
        }
        int top = bottom - tier * 3;
        g.fill(centerX - 1, top - 4, centerX + 2, top - 1, color);
        g.fill(centerX, top - 9, centerX + 1, top - 4, YzuiTheme.alpha(color, 0.5f));
    }

    /** 加号及箭头以几何线条绘制，不依赖字体字形。 */
    private static void plus(GuiGraphicsExtractor g, int x, int y) {
        int color = YzuiTheme.textMuted();
        g.fill(x - 4, y - 1, x + 5, y + 1, color);
        g.fill(x - 1, y - 4, x + 1, y + 5, color);
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y, int width) {
        int color = YzuiTheme.textMuted();
        g.fill(x, y + 5, x + width - 1, y + 7, color);
        for (int row = 0; row < 13; row++) {
            int offset = Math.abs(row - 6);
            int end = x + width - offset;
            g.fill(end - 2, y + row, end, y + 1 + row, color);
        }
    }

    /** 仅替换控件底色；物品占位图、旗帜图案、等级标识、错误和锁定图标继续用原版资源。 */
    public static boolean drawSprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h) {
        if (!sprite.getNamespace().equals("minecraft")) return false;
        String path = sprite.getPath();
        switch (path) {
            case "container/loom/scroller", "container/stonecutter/scroller",
                    "container/loom/scroller_disabled", "container/stonecutter/scroller_disabled" -> {
                boolean disabled = path.endsWith("_disabled");
                RoundedRect.fill(g, x + 3, y + 1, w - 6, h - 2, 3,
                        disabled ? YzuiTheme.outlineVariant() : YzuiTheme.primary());
            }
            case "container/loom/pattern", "container/loom/pattern_selected", "container/loom/pattern_highlighted",
                    "container/stonecutter/recipe", "container/stonecutter/recipe_selected",
                    "container/stonecutter/recipe_highlighted", "container/enchanting_table/enchantment_slot",
                    "container/enchanting_table/enchantment_slot_highlighted",
                    "container/enchanting_table/enchantment_slot_disabled" -> {
                boolean selected = path.endsWith("_selected"), hovered = path.endsWith("_highlighted");
                boolean disabled = path.endsWith("_disabled");
                int color = selected ? YzuiTheme.primaryContainer() : hovered ? YzuiTheme.slotHover()
                        : disabled ? YzuiTheme.surfaceLow() : YzuiTheme.slot();
                RoundedRect.fill(g, x + 1, y + 1, w - 2, h - 2, 3, color);
                YzuiTheme.border(g, x + 1, y + 1, w - 2, h - 2, 3,
                        selected || hovered ? YzuiTheme.primary() : YzuiTheme.outlineVariant());
            }
            case "container/cartography_table/map", "container/cartography_table/scaled_map" -> {
                mapFrame(g, x, y, w, h);
                if (path.endsWith("/scaled_map")) {
                    YzuiTheme.border(g, x + 17, y + 17, 32, 32, 2, YzuiTheme.outline());
                }
            }
            case "container/cartography_table/duplicated_map" -> {
                // 原版的 50×66 sprite 在上、下两处绘制；实际地图框只有 50×50。
                mapFrame(g, x, y, w, w);
            }
            default -> { return false; }
        }
        return true;
    }

    private static void mapFrame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        RoundedRect.fill(g, x, y, w, h, 4, YzuiTheme.surfaceHigh());
        YzuiTheme.border(g, x, y, w, h, 4, YzuiTheme.outlineVariant());
    }
}
