package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.FlameStaffItem;
import top.csituka.youzaiworldcore.mana.ManaManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 客户端魔力条与法杖蓄力条渲染器。
 *
 * <p>YZUI 启用时，魔力条位于饱食度栏上方；氧气条显示时继续向上堆叠。
 * YZUI 未启用或状态栏不可用时保留左下角布局。</p>
 */
@SuppressWarnings("null")
public final class ManaHudRenderer {

    // 左下角回退布局尺寸；YZUI 布局复用状态栏的 85×5 尺寸。
    private static final int LEGACY_BAR_WIDTH = 100;
    private static final int LEGACY_BAR_HEIGHT = 4;
    /** YZUI 状态条相邻两行的间距，与 HealthBarMixin 保持一致。 */
    private static final int YZUI_ROW_GAP = 12;

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

    private ManaHudRenderer() {
    }

    /**
     * 更新动画并渲染魔力条与法杖蓄力条。
     *
     * @param graphics HUD 图形提取器
     */
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
        if (!GuiAnimationController.isEnabled()) {
            animProgress = show ? 1.0f : 0.0f;
            animState = show ? AnimState.VISIBLE : AnimState.HIDDEN;
            displayMana = actualMana;
            lastManaDecreaseTime = 0;
        } else {
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
        }

        // 魔力消耗缓动
        if (GuiAnimationController.isEnabled() && actualMana < displayMana) {
            if (lastManaDecreaseTime == 0) lastManaDecreaseTime = now;
            long elapsed = now - lastManaDecreaseTime;
            if (elapsed > LOSS_DELAY_MS) {
                float t = Math.min(1.0f, (elapsed - LOSS_DELAY_MS) / (float) LOSS_SHRINK_MS);
                displayMana = actualMana + (displayMana - actualMana) * (1.0f - t);
                if (displayMana <= actualMana + 0.5f) displayMana = actualMana;
            }
        } else if (!GuiAnimationController.isEnabled() || actualMana > displayMana) {
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
            if (usesYzuiStatusLayout(client)) {
                int barWidth = HealthBarRenderer.BAR_WIDTH;
                int totalWidth = barWidth * 2 + HealthBarRenderer.BAR_GAP;
                int statusStartX = (sw - totalWidth) / 2;
                int foodX = statusStartX + barWidth + HealthBarRenderer.BAR_GAP;

                int rowStep = HealthBarRenderer.BAR_HEIGHT + YZUI_ROW_GAP;
                int manaY = sh - HealthBarRenderer.Y_OFFSET_FROM_BOTTOM - rowStep;
                if (client.player.getAirSupply() < client.player.getMaxAirSupply()) {
                    manaY -= rowStep;
                }

                // 从上方滑入，避免显隐动画穿过下方的饱食度栏。
                renderManaBar(graphics, foodX, manaY - slide,
                        barWidth, HealthBarRenderer.BAR_HEIGHT, alpha, actualMana, true);
            } else {
                renderManaBar(graphics, 4, sh - 15 + slide,
                        LEGACY_BAR_WIDTH, LEGACY_BAR_HEIGHT, alpha, actualMana, false);
            }
        }
        renderChargeBar(graphics, client, sw, sh, deltaMs);
    }

    /**
     * 判断魔力条当前是否占用 YZUI 饱食度栏上方的状态行。
     */
    static boolean isYzuiManaBarVisible() {
        return animState != AnimState.HIDDEN && usesYzuiStatusLayout(Minecraft.getInstance());
    }

    private static boolean usesYzuiStatusLayout(Minecraft client) {
        return ClientExternalSettings.isYzuiEnabled()
                && client.player != null
                && client.player.isAlive()
                && !client.player.isCreative()
                && !client.player.isSpectator();
    }

    private static boolean isHoldingAnyStaff(Minecraft client) {
        var main = client.player.getMainHandItem().getItem();
        var off = client.player.getOffhandItem().getItem();
        return main == ModItems.FLAME_STAFF || main == ModItems.SKY_STAR_STAFF || main == ModItems.VOID_STAFF
            || off  == ModItems.FLAME_STAFF || off  == ModItems.SKY_STAR_STAFF || off  == ModItems.VOID_STAFF;
    }

    private static void renderManaBar(GuiGraphicsExtractor g, int x, int y,
            int barWidth, int barHeight, int alpha, int mana, boolean yzuiStyle) {
        long elapsed = System.currentTimeMillis() - ManaManager.getLastInsufficientManaTime();
        boolean flash = elapsed < 1500;
        boolean flashOn = flash && ((elapsed / 200) % 2 == 0);

        // 背景
        fillManaBarLayer(g, x, y, 0, barWidth, barWidth, barHeight,
                packARGB(0x22, 0x22, 0x22, alpha), yzuiStyle, true);

        int actualW = (int) ((mana / 100.0f) * barWidth);
        int displayW = (int) ((displayMana / 100.0f) * barWidth);
        boolean hasLossTrail = displayW > actualW;

        // 黄色消耗残影
        if (hasLossTrail) {
            int ya = alpha;
            if (displayMana > mana) {
                float ratio = (displayMana - mana) / (100.0f - mana);
                ya = (int) (alpha * Math.max(0.3f, 1.0f - ratio * 0.5f));
            }
            fillManaBarLayer(g, x, y, actualW, displayW, barWidth, barHeight,
                    packARGB(0xFF, 0xD7, 0x00, ya), yzuiStyle,
                    displayW >= barWidth - 1);
        }

        // 蓝色实际填充
        int color;
        if (flash && flashOn) color = packARGB(0xFF, 0x44, 0x44, alpha);
        else if (flash)        color = packARGB(0x88, 0x22, 0x22, alpha);
        else                   color = packColorWithAlpha(getManaColor(mana), alpha);
        if (actualW > 0) {
            fillManaBarLayer(g, x, y, 0, actualW, barWidth, barHeight,
                    color, yzuiStyle, !hasLossTrail && actualW >= barWidth - 1);
        }

        // 文字
        var font = Minecraft.getInstance().font;
        int ty = y - 10;
        String t = mana + " / 100";
        if (flash) {
            String warning = "魔力不足";
            int tw = font.width(t);
            int textX = yzuiStyle
                    ? x + (barWidth - tw - 4 - font.width(warning)) / 2
                    : x;
            g.text(font, t, textX, ty, packARGB(0xFF, 0xFF, 0xFF, alpha), false);
            g.text(font, warning, textX + tw + 4, ty,
                    packARGB(0xFF, 0x55, 0x55, alpha), false);
        } else {
            int textX = yzuiStyle ? x + (barWidth - font.width(t)) / 2 : x;
            g.text(font, t, textX + 1, ty + 1, packARGB(0x00, 0x00, 0x00, alpha), false);
            g.text(font, t, textX, ty, packARGB(0xFF, 0xFF, 0xFF, alpha), false);
        }
    }

    /**
     * 绘制魔力条中的一个水平色层；YZUI 模式会裁掉外框四角各 1 像素。
     */
    private static void fillManaBarLayer(GuiGraphicsExtractor g, int x, int y,
            int startOffset, int endOffset, int barWidth, int barHeight,
            int color, boolean yzuiStyle, boolean roundRight) {
        int start = Math.max(0, Math.min(barWidth, startOffset));
        int end = Math.max(start, Math.min(barWidth, endOffset));
        if (end <= start) {
            return;
        }

        if (!yzuiStyle || barWidth < 3 || barHeight < 3) {
            g.fill(x + start, y, x + end, y + barHeight, color);
            return;
        }

        boolean roundLeft = start == 0;
        int bodyStart = start + (roundLeft ? 1 : 0);
        int bodyEnd = end - (roundRight ? 1 : 0);
        if (bodyEnd > bodyStart) {
            g.fill(x + bodyStart, y, x + bodyEnd, y + barHeight, color);
        }
        if (roundLeft) {
            g.fill(x, y + 1, x + 1, y + barHeight - 1, color);
        }
        if (roundRight) {
            g.fill(x + end - 1, y + 1, x + end, y + barHeight - 1, color);
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

    /**
     * 注册客户端魔力 HUD。
     */
    public static void register() {
        DebugLogger.info("ManaHudRenderer", "魔力条已启用 YZUI 状态栏缺角样式与居中布局");
    }
}
