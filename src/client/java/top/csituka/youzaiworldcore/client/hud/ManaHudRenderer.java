package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.FlameStaffItem;
import top.csituka.youzaiworldcore.mana.ManaManager;

@SuppressWarnings("null")
public class ManaHudRenderer {

    // 蓝条尺寸
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 4;

    // 蓝条显隐动画
    private static final int SHOW_MS = 300;
    private static final int HIDE_MS = 200;
    private static final int SLIDE_DIST = 15;

    private enum AnimState { HIDDEN, SHOWING, HIDEING, VISIBLE }
    private static AnimState animState = AnimState.HIDDEN;
    private static float animProgress = 0.0f;

    // 魔力消耗缓动
    private static float displayMana = 100.0f;
    private static long lastManaDecreaseTime = 0;
    private static final int LOSS_DELAY_MS = 200;
    private static final int LOSS_SHRINK_MS = 400;

    private static long prevFrameTime = 0;

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        long now = System.currentTimeMillis();
        if (prevFrameTime == 0) prevFrameTime = now;
        float deltaMs = now - prevFrameTime;
        prevFrameTime = now;
        if (deltaMs > 100) deltaMs = 16;

        int actualMana = ManaManager.getClientMana();
        boolean show = isHoldingAnyStaff(client) || actualMana < 100;

        // 蓝条显隐动画
        if (show && animState == AnimState.HIDDEN) {
            animState = AnimState.SHOWING;
            animProgress = 0.0f;
        } else if (!show && animState == AnimState.VISIBLE) {
            animState = AnimState.HIDEING;
        }
        if (animState == AnimState.SHOWING) {
            animProgress += deltaMs / SHOW_MS;
            if (animProgress >= 1.0f) { animProgress = 1.0f; animState = AnimState.VISIBLE; }
        } else if (animState == AnimState.HIDEING) {
            animProgress -= deltaMs / HIDE_MS;
            if (animProgress <= 0.0f) { animProgress = 0.0f; animState = AnimState.HIDDEN; }
        }

        // 魔力消耗缓动
        if (actualMana < displayMana) {
            if (lastManaDecreaseTime == 0) lastManaDecreaseTime = now;
            long elapsed = now - lastManaDecreaseTime;
            if (elapsed > LOSS_DELAY_MS) {
                float t = Math.min(1.0f, (elapsed - LOSS_DELAY_MS) / (float) LOSS_SHRINK_MS);
                displayMana = actualMana + (displayMana - actualMana) * (1.0f - t);
                if (displayMana <= actualMana + 0.5f) displayMana = actualMana;
            }
        } else if (actualMana > displayMana) {
            displayMana = actualMana;
            lastManaDecreaseTime = 0;
        } else {
            lastManaDecreaseTime = 0;
        }

        int alpha = Math.min(255, Math.max(0, (int) (animProgress * 255)));
        int slide = (int) ((1.0f - animProgress) * SLIDE_DIST);
        int sw = graphics.guiWidth();
        int sh = graphics.guiHeight();

        if (animState != AnimState.HIDDEN) {
            renderManaBar(graphics, sw, sh, slide, alpha, actualMana);
        }
        renderChargeBar(graphics, client, sw, sh, deltaMs);
    }

    private static boolean isHoldingAnyStaff(Minecraft client) {
        var main = client.player.getMainHandItem().getItem();
        var off = client.player.getOffhandItem().getItem();
        return main == ModItems.FLAME_STAFF || main == ModItems.SKY_STAR_STAFF || main == ModItems.VOID_STAFF
            || off  == ModItems.FLAME_STAFF || off  == ModItems.SKY_STAR_STAFF || off  == ModItems.VOID_STAFF;
    }

    private static void renderManaBar(GuiGraphicsExtractor g, int sw, int sh, int slide, int alpha, int mana) {
        int x = 4;
        int y = sh - 15 + slide; // YZUI 热键栏上移 6px 同步调整

        long elapsed = System.currentTimeMillis() - ManaManager.getLastInsufficientManaTime();
        boolean flash = elapsed < 1500;
        boolean flashOn = flash && ((elapsed / 200) % 2 == 0);

        // 背景
        g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, packARGB(0x22, 0x22, 0x22, alpha));

        int actualW = (int) ((mana / 100.0f) * BAR_WIDTH);
        int displayW = (int) ((displayMana / 100.0f) * BAR_WIDTH);

        // 黄色消耗残影
        if (displayW > actualW) {
            int ya = alpha;
            if (displayMana > mana) {
                float ratio = (displayMana - mana) / (100.0f - mana);
                ya = (int) (alpha * Math.max(0.3f, 1.0f - ratio * 0.5f));
            }
            g.fill(x + actualW, y, x + displayW, y + BAR_HEIGHT, packARGB(0xFF, 0xD7, 0x00, ya));
        }

        // 蓝色实际填充
        int color;
        if (flash && flashOn) color = packARGB(0xFF, 0x44, 0x44, alpha);
        else if (flash)        color = packARGB(0x88, 0x22, 0x22, alpha);
        else                   color = packColorWithAlpha(getManaColor(mana), alpha);
        if (actualW > 0) g.fill(x, y, x + actualW, y + BAR_HEIGHT, color);

        // 文字
        int ty = y - 10;
        if (flash) {
            String t = mana + " / 100";
            int tw = Minecraft.getInstance().font.width(t);
            g.text(Minecraft.getInstance().font, t, x, ty, packARGB(0xFF, 0xFF, 0xFF, alpha), false);
            g.text(Minecraft.getInstance().font, "魔力不足", x + tw + 4, ty, packARGB(0xFF, 0x55, 0x55, alpha), false);
        } else {
            String t = mana + " / 100";
            g.text(Minecraft.getInstance().font, t, x + 1, ty + 1, packARGB(0x00, 0x00, 0x00, alpha), false);
            g.text(Minecraft.getInstance().font, t, x, ty, packARGB(0xFF, 0xFF, 0xFF, alpha), false);
        }
    }

    // 蓄力条
    private static float chargeDisplay = 0.0f;
    private static boolean wasCharging = false;
    private static final int CHG_W = 40, CHG_H = 3;
    private static final float CHG_GROW = 0.08f, CHG_SHRINK = 0.12f;

    private static void renderChargeBar(GuiGraphicsExtractor g, Minecraft client, int sw, int sh, float dt) {
        boolean charging = client.player.isUsingItem()
                && client.player.getUseItem().getItem() instanceof FlameStaffItem;
        if (charging && !wasCharging) chargeDisplay = 0.0f;
        wasCharging = charging;

        float target = charging
                ? Math.min(1.0f, Math.min(FlameStaffItem.MAX_CHARGE_TICKS, client.player.getTicksUsingItem())
                        / (float) FlameStaffItem.MAX_CHARGE_TICKS)
                : 0.0f;
        float f = charging ? CHG_GROW : CHG_SHRINK;
        float t = 1.0f - (float) Math.pow(1.0 - f, dt / 16.67);
        chargeDisplay += (target - chargeDisplay) * t;
        if (Math.abs(chargeDisplay) < 0.001f) chargeDisplay = 0.0f;
        if (chargeDisplay <= 0.001f) return;

        int bx = (sw - CHG_W) / 2, by = sh / 2 + 15;
        g.fill(bx, by, bx + CHG_W, by + CHG_H, packARGB(0x33, 0x33, 0x33, 0xFF));
        int fw = (int) (chargeDisplay * CHG_W);
        if (fw > 0) g.fill(bx, by, bx + fw, by + CHG_H,
                packARGB(0xFF, Math.max(0, 0xFF - (int) (chargeDisplay * 0xAA)), 0x00, 0xFF));
    }

    // 颜色工具
    private static int getManaColor(int mana) {
        if (mana >= 70) return 0xFF00BFFF;
        else if (mana >= 30) return 0xFF1E90FF;
        else return 0xFF4169E1;
    }

    private static int packColorWithAlpha(int argb, int alpha) {
        return packARGB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, alpha);
    }

    private static int packARGB(int r, int g, int b, int a) {
        return (a << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static void register() {}
}
