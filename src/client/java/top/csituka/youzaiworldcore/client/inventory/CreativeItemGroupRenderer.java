package top.csituka.youzaiworldcore.client.inventory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.InventoryItemGroupsConfig;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import java.util.List;

/** 分组的缩放过渡、主题底色、加减标记和悬停说明。 */
@SuppressWarnings("null")
public final class CreativeItemGroupRenderer {

    // 原版按钮的九宫格边缘占三像素，内侧预留 5×5 的符号区域。
    private static final int BADGE_SIZE = 11;
    private static final int BADGE_OFFSET = 6;
    private static final Identifier VANILLA_BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier VANILLA_BUTTON_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/button_highlighted");

    private CreativeItemGroupRenderer() {
    }

    /**
     * 开始槽位的局部缩放；不可见时返回 false，其他情况必须在 finally 中调用 endSlot。
     * <p>物品、装饰和边框共用同一矩阵，格子的实际位置与命中区域保持稳定。</p>
     */
    public static boolean beginSlot(GuiGraphicsExtractor graphics, CreativeItemGroups.Entry entry, int x, int y) {
        float scale = 1.0F;
        float offsetY = 0.0F;
        if (entry != null && entry.group() != null) {
            float openness = entry.group().openness();
            if (entry.header()) {
                scale += 0.05F * (float) Math.sin(Math.PI * openness);
            } else {
                scale = openness;
                offsetY = 3.0F * (1.0F - openness);
            }
        }
        if (scale <= 0.01F) {
            return false;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 8.0F, y + 8.0F + offsetY);
        graphics.pose().scale(scale, scale);
        graphics.pose().translate(-x - 8.0F, -y - 8.0F);
        return true;
    }

    /** 结束 beginSlot 创建的变换，避免动画影响其他槽位、鼠标物品或提示框。 */
    public static void endSlot(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    /** 取得只用于绘制的图标；轮播按真实时间推进，不改变底层槽位或取物结果。 */
    public static ItemStack displayStack(CreativeItemGroups.Entry entry) {
        if (entry.header() && InventoryItemGroupsConfig.showItemsInGroup()) {
            List<ItemStack> members = entry.group().members();
            int index = Math.floorMod(System.nanoTime() / 1_000_000_000L, members.size());
            return members.get(index);
        }
        return entry.stack();
    }

    /** 绘制组头边框和展开成员的底色；随 YZUI 开关使用主题色或原版灰色。 */
    public static void background(GuiGraphicsExtractor graphics, CreativeItemGroups.Entry entry,
                                  int x, int y) {
        if (entry == null || entry.group() == null) {
            return;
        }
        int accent = ClientExternalSettings.isYzuiEnabled() ? YzuiTheme.primary() : 0xFF8B8B8B;
        float openness = entry.group().openness();
        int alpha = entry.header() ? 0x50 + Math.round(0x20 * (float) Math.sin(Math.PI * openness))
                : Math.round(0x28 * openness);
        graphics.fill(x, y, x + 16, y + 16, alpha << 24 | accent & 0xFFFFFF);
        if (entry.header()) {
            graphics.fill(x - 1, y - 1, x + 17, y, accent);
            graphics.fill(x - 1, y + 16, x + 17, y + 17, accent);
            graphics.fill(x - 1, y, x, y + 16, accent);
            graphics.fill(x + 16, y, x + 17, y + 16, accent);
        }
    }

    /** 按 YZUI 开关绘制带悬停反馈的加减标记，竖笔画随展开比例旋转为横笔画。 */
    public static void badge(GuiGraphicsExtractor graphics, CreativeItemGroups.Entry entry,
                             int x, int y, boolean hovered) {
        if (entry == null || !entry.header()) {
            return;
        }
        int badgeX = x + BADGE_OFFSET;
        int badgeY = y + BADGE_OFFSET;
        int foreground;
        if (ClientExternalSettings.isYzuiEnabled()) {
            YzuiTheme.button(graphics, badgeX, badgeY, BADGE_SIZE, BADGE_SIZE,
                    hovered ? 1.0F : 0.0F, false, true, 1.0F, YzuiTheme.ButtonStyle.TONAL);
            foreground = YzuiTheme.buttonText(YzuiTheme.ButtonStyle.TONAL, true);
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    hovered ? VANILLA_BUTTON_HIGHLIGHTED : VANILLA_BUTTON,
                    badgeX, badgeY, BADGE_SIZE, BADGE_SIZE);
            foreground = 0xFFFFFFFF;
        }
        int symbolX = badgeX + BADGE_SIZE / 2;
        int symbolY = badgeY + BADGE_SIZE / 2;
        graphics.fill(symbolX - 2, symbolY, symbolX + 3, symbolY + 1, foreground);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(symbolX + 0.5F, symbolY + 0.5F);
            graphics.pose().rotate(entry.group().openness() * (float) (Math.PI / 2.0));
            graphics.pose().translate(-symbolX - 0.5F, -symbolY - 0.5F);
            graphics.fill(symbolX, symbolY - 2, symbolX + 1, symbolY + 3, foreground);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** 组头悬停说明使用组名和物品变体数量，不附带代表物品的组件预览。 */
    public static List<Component> tooltip(CreativeItemGroups.Entry entry) {
        CreativeItemGroups.Group group = entry.group();
        return List.of(group.name(),
                Component.translatable("screen.youzaiworldcore.item_groups.count", group.members().size())
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("screen.youzaiworldcore.item_groups."
                        + (group.expanded() ? "collapse" : "expand")).withStyle(ChatFormatting.GRAY));
    }
}
