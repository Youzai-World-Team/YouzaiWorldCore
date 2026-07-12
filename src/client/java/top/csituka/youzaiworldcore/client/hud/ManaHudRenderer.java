package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.item.ModItems;

@SuppressWarnings("null")
public class ManaHudRenderer {

    /** 客户端缓存的当前魔力值 */
    private static int clientMana = 100;

    /** HUD 尺寸 */
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 4;

    public static void setClientMana(int mana) {
        clientMana = Math.max(0, Math.min(100, mana));
    }

    public static int getClientMana() {
        return clientMana;
    }

    /**
     * 在 HUD 渲染结束时调用，绘制魔力条。
     */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        // 只有在手持法杖时才显示，或者魔力不满时也显示
        boolean holdingStaff = isHoldingAnyStaff(client);
        boolean manaNotFull = clientMana < 100;

        if (!holdingStaff && !manaNotFull) {
            return;
        }

        renderManaBar(graphics, client);
    }

    private static boolean isHoldingAnyStaff(Minecraft client) {
        var mainHand = client.player.getMainHandItem().getItem();
        var offHand = client.player.getOffhandItem().getItem();
        return mainHand == ModItems.FLAME_STAFF
                || mainHand == ModItems.SKY_STAR_STAFF
                || mainHand == ModItems.VOID_STAFF
                || offHand == ModItems.FLAME_STAFF
                || offHand == ModItems.SKY_STAR_STAFF
                || offHand == ModItems.VOID_STAFF;
    }

    private static void renderManaBar(GuiGraphicsExtractor graphics, Minecraft client) {
        int screenHeight = graphics.guiHeight();

        // 魔力条位置：左下角（在经验条/快捷栏上方）
        int x = 4;
        int y = screenHeight - 22;

        // 背景（深灰色）
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF222222);

        // 魔力填充（蓝紫色）
        int fillWidth = (int) ((clientMana / 100.0f) * BAR_WIDTH);
        int color = getManaColor(clientMana);
        graphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, color);

        // 文字（左对齐）
        String text = clientMana + " / 100";
        int textY = y - 10;
        graphics.text(client.font, text, x + 1, textY + 1, 0xFF000000, false);
        graphics.text(client.font, text, x, textY, 0xFFFFFFFF, false);
    }

    private static int getManaColor(int mana) {
        // 根据魔力值改变颜色
        if (mana >= 70) {
            return 0xFF00BFFF; // 深天蓝
        } else if (mana >= 30) {
            return 0xFF1E90FF; // 道奇蓝
        } else {
            return 0xFF4169E1; // 皇家蓝
        }
    }

    public static void register() {
        // 不再通过 HudRenderCallback 注册，改由 HudMixin 注入调用
    }
}
