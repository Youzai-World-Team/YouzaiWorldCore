package top.csituka.youzaiworldcore.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.FlameStaffItem;
import top.csituka.youzaiworldcore.mana.ManaManager;

@SuppressWarnings("null")
public class ManaHudRenderer {

    // ─── 蓝条尺寸 ───
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 4;

    // ─── 滑入/滑出动画 ───
    private static final int SHOW_DURATION_MS = 300;
    private static final int HIDE_DURATION_MS = 200;
    private static final int SLIDE_DISTANCE = 15;

    private enum AnimState { HIDDEN, SHOWING, HIDEING, VISIBLE }
    private static AnimState animState = AnimState.HIDDEN;
    private static float animProgress = 0.0f;
    private static long prevFrameTime = 0;

    // ─── 魔力消耗缓动 ───
    /** 当前显示的魔力值（包含黄色残影部分），用于缓动消失 */
    private static float displayMana = 100.0f;
    /** 上次魔力减少的时间戳 */
    private static long lastManaDecreaseTime = 0;
    private static final int MANA_LOSS_DELAY_MS = 200;  // 减少后延迟 200ms 开始收缩
    private static final int MANA_LOSS_SHRINK_MS = 400; // 收缩持续 400ms

    // ═══════════════════════════════════════
    //  主渲染入口
    // ═══════════════════════════════════════

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        long now = System.currentTimeMillis();
        if (prevFrameTime == 0) prevFrameTime = now;
        float deltaMs = now - prevFrameTime;
        prevFrameTime = now;
        if (deltaMs > 100) deltaMs = 16;

        int actualMana = ManaManager.getClientMana();
        boolean holdingStaff = isHoldingAnyStaff(client);
        boolean shouldShow = holdingStaff || actualMana < 100;

        // ── 蓝条显隐动画 ──
        if (shouldShow && animState == AnimState.HIDDEN) {
            animState = AnimState.SHOWING;
            animProgress = 0.0f;
        } else if (!shouldShow && animState == AnimState.VISIBLE) {
            animState = AnimState.HIDEING;
        }

        if (animState == AnimState.SHOWING) {
            animProgress += deltaMs / SHOW_DURATION_MS;
            if (animProgress >= 1.0f) {
                animProgress = 1.0f;
                animState = AnimState.VISIBLE;
            }
        } else if (animState == AnimState.HIDEING) {
            animProgress -= deltaMs / HIDE_DURATION_MS;
            if (animProgress <= 0.0f) {
                animProgress = 0.0f;
                animState = AnimState.HIDDEN;
            }
        }

        // ── 魔力消耗缓动更新 ──
        if (actualMana < displayMana) {
            // 魔力减少
            if (lastManaDecreaseTime == 0) lastManaDecreaseTime = now;
            long elapsed = now - lastManaDecreaseTime;
            if (elapsed > MANA_LOSS_DELAY_MS) {
                float t = Math.min(1.0f, (elapsed - MANA_LOSS_DELAY_MS) / (float) MANA_LOSS_SHRINK_MS);
                displayMana = actualMana + (displayMana - actualMana) * (1.0f - t);
                if (displayMana <= actualMana + 0.5f) displayMana = actualMana;
            }
        } else if (actualMana > displayMana) {
            // 魔力恢复 → 瞬间对齐
            displayMana = actualMana;
            lastManaDecreaseTime = 0;
        } else {
            lastManaDecreaseTime = 0;
        }

        int alpha = Math.min(255, Math.max(0, (int) (animProgress * 255)));
        int slideOffset = (int) ((1.0f - animProgress) * SLIDE_DISTANCE);

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // ── 渲染魔力条 ──
        if (animState != AnimState.HIDDEN) {
            renderManaBar(graphics, screenWidth, screenHeight, slideOffset, alpha, actualMana);
        }

        // ── 渲染蓄力条 ──
        renderChargeBar(graphics, client, screenWidth, screenHeight, deltaMs);
    }

    // ═══════════════════════════════════════

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

    // ═══════════════════════════════════════
    //  魔力条渲染
    // ═══════════════════════════════════════

    private static void renderManaBar(GuiGraphicsExtractor graphics,
                                       int screenWidth, int screenHeight,
                                       int slideOffset, int alpha, int actualMana) {
        int x = 4;
        int y = screenHeight - 10 + slideOffset;

        long elapsed = System.currentTimeMillis() - ManaManager.getLastInsufficientManaTime();
        boolean flashing = elapsed < 1500;
        boolean flashOn = flashing && ((elapsed / 200) % 2 == 0);

        // 背景
        int bgColor = packARGB(0x22, 0x22, 0x22, alpha);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, bgColor);

        int actualWidth = (int) ((actualMana / 100.0f) * BAR_WIDTH);
        int displayWidth = (int) ((displayMana / 100.0f) * BAR_WIDTH);

        // 黄色消耗残影（在蓝色上方，比蓝色宽的部分）
        if (displayWidth > actualWidth) {
            // 黄色部分透明度也随 alpha 渐入渐出
            int yellowAlpha = alpha;
            // 残影尾部渐变透明（如果正在收缩中）
            if (displayMana > actualMana) {
                float shrinkRatio = (displayMana - actualMana) / (100.0f - actualMana);
                yellowAlpha = (int) (alpha * Math.max(0.3f, 1.0f - shrinkRatio * 0.5f));
            }
            graphics.fill(x + actualWidth, y,
                    x + displayWidth, y + BAR_HEIGHT,
                    packARGB(0xFF, 0xD7, 0x00, yellowAlpha)); // 金黄色
        }

        // 蓝色实际魔力填充
        int fillColor;
        if (flashing && flashOn) {
            fillColor = packARGB(0xFF, 0x44, 0x44, alpha);
        } else if (flashing) {
            fillColor = packARGB(0x88, 0x22, 0x22, alpha);
        } else {
            fillColor = packColorWithAlpha(getManaColor(actualMana), alpha);
        }
        if (actualWidth > 0) {
            graphics.fill(x, y, x + actualWidth, y + BAR_HEIGHT, fillColor);
        }

        // 文字
        int textY = y - 10;

        if (flashing) {
            String manaText = actualMana + " / 100";
            int manaWidth = Minecraft.getInstance().font.width(manaText);
            graphics.text(Minecraft.getInstance().font, manaText,
                    x, textY,
                    packARGB(0xFF, 0xFF, 0xFF, alpha), false);
            graphics.text(Minecraft.getInstance().font, "魔力不足",
                    x + manaWidth + 4, textY,
                    packARGB(0xFF, 0x55, 0x55, alpha), false);
        } else {
            String text = actualMana + " / 100";
            graphics.text(Minecraft.getInstance().font, text,
                    x + 1, textY + 1,
                    packARGB(0x00, 0x00, 0x00, alpha), false);
            graphics.text(Minecraft.getInstance().font, text,
                    x, textY,
                    packARGB(0xFF, 0xFF, 0xFF, alpha), false);
        }
    }

    // ═══════════════════════════════════════
    //  蓄力条渲染
    // ═══════════════════════════════════════

    private static float chargeDisplayProgress = 0.0f;
    private static boolean wasCharging = false;

    private static final int CHARGE_BAR_WIDTH = 40;
    private static final int CHARGE_BAR_HEIGHT = 3;
    private static final float CHARGE_GROW_FACTOR = 0.08f;
    private static final float CHARGE_SHRINK_FACTOR = 0.12f;

    private static void renderChargeBar(GuiGraphicsExtractor graphics, Minecraft client,
                                         int screenWidth, int screenHeight, float deltaMs) {
        boolean charging = client.player.isUsingItem()
                && client.player.getUseItem().getItem() instanceof FlameStaffItem;

        if (charging && !wasCharging) {
            chargeDisplayProgress = 0.0f;
        }
        wasCharging = charging;

        float targetProgress = charging
                ? Math.min(1.0f, Math.min(FlameStaffItem.MAX_CHARGE_TICKS, client.player.getTicksUsingItem())
                        / (float) FlameStaffItem.MAX_CHARGE_TICKS)
                : 0.0f;

        float factor = charging ? CHARGE_GROW_FACTOR : CHARGE_SHRINK_FACTOR;
        float t = 1.0f - (float) Math.pow(1.0 - factor, deltaMs / 16.67);
        chargeDisplayProgress += (targetProgress - chargeDisplayProgress) * t;

        if (Math.abs(chargeDisplayProgress) < 0.001f) chargeDisplayProgress = 0.0f;
        if (chargeDisplayProgress <= 0.001f) return;

        int barX = (screenWidth - CHARGE_BAR_WIDTH) / 2;
        int barY = screenHeight / 2 + 15;

        graphics.fill(barX, barY,
                barX + CHARGE_BAR_WIDTH, barY + CHARGE_BAR_HEIGHT,
                packARGB(0x33, 0x33, 0x33, 0xFF));

        int fillW = (int) (chargeDisplayProgress * CHARGE_BAR_WIDTH);
        int chargeColor = packARGB(
                0xFF,
                Math.max(0, 0xFF - (int) (chargeDisplayProgress * 0xAA)),
                0x00,
                0xFF
        );
        if (fillW > 0) {
            graphics.fill(barX, barY,
                    barX + fillW, barY + CHARGE_BAR_HEIGHT,
                    chargeColor);
        }
    }

    // ═══════════════════════════════════════
    //  颜色工具
    // ═══════════════════════════════════════

    private static int getManaColor(int mana) {
        if (mana >= 70) return 0xFF00BFFF;
        else if (mana >= 30) return 0xFF1E90FF;
        else return 0xFF4169E1;
    }

    private static int packColorWithAlpha(int argb, int alpha) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return packARGB(r, g, b, alpha);
    }

    private static int packARGB(int r, int g, int b, int alpha) {
        return (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static void register() {
    }
}
