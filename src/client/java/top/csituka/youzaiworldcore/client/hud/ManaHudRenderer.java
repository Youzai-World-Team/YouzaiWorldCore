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

        int mana = ManaManager.getClientMana();
        boolean holdingStaff = isHoldingAnyStaff(client);
        boolean shouldShow = holdingStaff || mana < 100;

        // ── 蓝条动画状态 ──
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
                // 仍然往下走，让蓄力条动画能在蓝条隐藏时继续播放
            }
        }

        int alpha = Math.min(255, Math.max(0, (int) (animProgress * 255)));
        int slideOffset = (int) ((1.0f - animProgress) * SLIDE_DISTANCE);

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // ── 渲染魔力条 ──
        if (animState != AnimState.HIDDEN) {
            renderManaBar(graphics, screenWidth, screenHeight, slideOffset, alpha);
        }

        // ── 渲染蓄力条（独立于蓝条动画） ──
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
                                       int slideOffset, int alpha) {
        int mana = ManaManager.getClientMana();
        int x = 4;
        int y = screenHeight - 10 + slideOffset;

        long elapsed = System.currentTimeMillis() - ManaManager.getLastInsufficientManaTime();
        boolean flashing = elapsed < 1500;
        boolean flashOn = flashing && ((elapsed / 200) % 2 == 0);

        // 背景
        int bgColor = packARGB(0x22, 0x22, 0x22, alpha);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, bgColor);

        // 填充
        int fillWidth = (int) ((mana / 100.0f) * BAR_WIDTH);
        int fillColor;
        if (flashing && flashOn) {
            fillColor = packARGB(0xFF, 0x44, 0x44, alpha);
        } else if (flashing) {
            fillColor = packARGB(0x88, 0x22, 0x22, alpha);
        } else {
            fillColor = packColorWithAlpha(getManaColor(mana), alpha);
        }
        graphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, fillColor);

        // 文字
        int textY = y - 10;

        if (flashing) {
            String manaText = mana + " / 100";
            int manaWidth = Minecraft.getInstance().font.width(manaText);
            graphics.text(Minecraft.getInstance().font, manaText,
                    x, textY,
                    packARGB(0xFF, 0xFF, 0xFF, alpha), false);
            graphics.text(Minecraft.getInstance().font, "魔力不足",
                    x + manaWidth + 4, textY,
                    packARGB(0xFF, 0x55, 0x55, alpha), false);
        } else {
            String text = mana + " / 100";
            graphics.text(Minecraft.getInstance().font, text,
                    x + 1, textY + 1,
                    packARGB(0x00, 0x00, 0x00, alpha), false);
            graphics.text(Minecraft.getInstance().font, text,
                    x, textY,
                    packARGB(0xFF, 0xFF, 0xFF, alpha), false);
        }
    }

    // ═══════════════════════════════════════
    //  蓄力条渲染（带长度动画）
    // ═══════════════════════════════════════

    /** 动画插值后的蓄力进度（0~1） */
    private static float chargeDisplayProgress = 0.0f;
    /** 上次帧是否在蓄力（用于检测开始/结束） */
    private static boolean wasCharging = false;

    private static final int CHARGE_BAR_WIDTH = 40;
    private static final int CHARGE_BAR_HEIGHT = 3;
    /** 蓄力增长：指数平滑因子（每帧 8%，等效约 580ms 填满） */
    private static final float CHARGE_GROW_FACTOR = 0.08f;
    /** 蓄力收缩：指数平滑因子（等效约 380ms 缩完） */
    private static final float CHARGE_SHRINK_FACTOR = 0.12f;

    private static void renderChargeBar(GuiGraphicsExtractor graphics, Minecraft client,
                                         int screenWidth, int screenHeight, float deltaMs) {
        boolean charging = client.player.isUsingItem()
                && client.player.getUseItem().getItem() instanceof FlameStaffItem;

        // 检测蓄力开始 → 重置动画起点
        if (charging && !wasCharging) {
            chargeDisplayProgress = 0.0f;
        }
        wasCharging = charging;

        float targetProgress = charging
                ? Math.min(1.0f, Math.min(FlameStaffItem.MAX_CHARGE_TICKS, client.player.getTicksUsingItem())
                        / (float) FlameStaffItem.MAX_CHARGE_TICKS)
                : 0.0f;

        // 指数平滑（帧率无关）
        float factor = charging ? CHARGE_GROW_FACTOR : CHARGE_SHRINK_FACTOR;
        float t = 1.0f - (float) Math.pow(1.0 - factor, deltaMs / 16.67);
        chargeDisplayProgress += (targetProgress - chargeDisplayProgress) * t;

        // 微小值截断
        if (Math.abs(chargeDisplayProgress) < 0.001f) chargeDisplayProgress = 0.0f;

        if (chargeDisplayProgress <= 0.001f) return;

        int barX = (screenWidth - CHARGE_BAR_WIDTH) / 2;
        int barY = screenHeight / 2 + 15;

        // 背景
        graphics.fill(barX, barY,
                barX + CHARGE_BAR_WIDTH, barY + CHARGE_BAR_HEIGHT,
                packARGB(0x33, 0x33, 0x33, 0xFF));

        // 填充（红→橙渐变）
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
