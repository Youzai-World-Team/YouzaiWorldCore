package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.hud.ArmorBarRenderer;
import top.csituka.youzaiworldcore.client.hud.FoodBarRenderer;
import top.csituka.youzaiworldcore.client.hud.HealthBarRenderer;
import top.csituka.youzaiworldcore.client.hud.OxygenBarRenderer;

/**
 * YZUI 血条/饥饿条/盔甲条/氧气条替换 Mixin。
 *
 * <p>
 * 当 YZUI 启用时，取消以下原版渲染：
 * </p>
 * <ul>
 * <li>{@link Hud#extractPlayerHealth} — 爱心血条</li>
 * <li>{@link Hud#extractFood} — 鸡腿饥饿值</li>
 * <li>{@link Hud#extractArmor} — 盔甲图标</li>
 * <li>{@link Hud#extractAirBubbles} — 氧气气泡</li>
 * </ul>
 *
 * <p>
 * 替换为两行并排长条进度条：
 * </p>
 * 
 * <pre>
 * 第二行: [ARMOR BAR]          [OXYGEN BAR]   (仅在对应值非默认时显示)
 * 第一行: [HEALTH BAR]    gap    [FOOD BAR]    (始终显示)
 * </pre>
 *
 * <p>
 * 创造模式 / 旁观模式隐藏所有条。
 * </p>
 */
@SuppressWarnings("null")
@Mixin(Hud.class)
public abstract class HealthBarMixin {

    /** 两行之间的垂直间距（≥ 文字高度 + 行高，避免 text 与第二行条重叠） */
    private static final int ROW_GAP = 12;

    /** 布局缓存：仅在窗口尺寸变化时重新计算 */
    private static int cachedSw = -1;
    private static int cachedSh = -1;
    private static int cachedStartX;
    private static int cachedRow1Y;
    private static int cachedRow2Y;
    private static int cachedHealthX;
    private static int cachedFoodX;

    // ===== 取消原版渲染 =====

    @Inject(method = "extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void yzwc$onExtractPlayerHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui())
            ci.cancel();
    }

    @Inject(method = "extractFood(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/world/entity/player/Player;II)V", at = @At("HEAD"), cancellable = true)
    private void yzwc$onExtractFood(GuiGraphicsExtractor graphics,
            Player player, int i, int j, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui())
            ci.cancel();
    }

    @Inject(method = "extractArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/world/entity/player/Player;IIII)V", at = @At("HEAD"), cancellable = true)
    private static void yzwc$onExtractArmor(GuiGraphicsExtractor graphics,
            Player player, int i, int j, int k, int l, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui())
            ci.cancel();
    }

    @Inject(method = "extractAirBubbles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/world/entity/player/Player;III)V", at = @At("HEAD"), cancellable = true)
    private void yzwc$onExtractAirBubbles(GuiGraphicsExtractor graphics,
            Player player, int i, int j, int k, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui())
            ci.cancel();
    }

    /**
     * 替换原版物品名称渲染：仅生存模式显示，加入深色圆角背景，文字与背景同步消失。
     *
     * <p>
     * 取消原版 {@code extractSelectedItemName}，由本方法完全接管：
     * <ul>
     * <li>仅在生存/冒险模式显示（判定 {@code !creative && !spectator}）</li>
     * <li>物品切换后 60 tick（约 3 秒）后渐隐消失</li>
     * <li>15% 黑色不透明度圆角背景 + 白色文字</li>
     * <li>背景与文字同时出现、同时消失</li>
     * </ul>
     * </p>
     */
    @Unique
    private static ItemStack yzwc$lastHighlightedItem = ItemStack.EMPTY;
    @Unique
    private static int yzwc$itemHighlightStartTick = 0;
    @Unique
    private static final int HIGHLIGHT_DURATION_TICKS = 60; // 3 秒
    @Unique
    private static final int FADE_TICKS = 20; // 最后 20 tick 渐隐

    @Inject(method = "extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true)
    private void yzwc$onSelectedItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui())
            return;

        Minecraft client = Minecraft.getInstance();
        Hud hud = client.gui.hud;
        Player player = client.player;
        if (player == null)
            return;

        // 仅在生存/冒险模式显示（原版在创造模式也有，但用户要求仅生存模式可见）
        if (player.isCreative() || player.isSpectator())
            return;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            // 切回空手时清除上次记录，再切回同一物品时重新计时
            yzwc$lastHighlightedItem = ItemStack.EMPTY;
            yzwc$itemHighlightStartTick = 0;
            return;
        }

        // 取消原版渲染
        ci.cancel();

        // 检测物品是否变更
        int guiTicks = hud.getGuiTicks();
        if (!ItemStack.isSameItemSameComponents(yzwc$lastHighlightedItem, held)) {
            yzwc$lastHighlightedItem = held.copy();
            yzwc$itemHighlightStartTick = guiTicks;
        }

        // 超时判定
        int elapsed = guiTicks - yzwc$itemHighlightStartTick;
        if (elapsed > HIGHLIGHT_DURATION_TICKS)
            return;

        // 渐隐 alpha
        int alpha = 255;
        if (elapsed > HIGHLIGHT_DURATION_TICKS - FADE_TICKS) {
            alpha = (int) (255 * (HIGHLIGHT_DURATION_TICKS - elapsed) / (float) FADE_TICKS);
        }

        Font font = client.font;
        Component name = held.getHoverName();
        String text = name.getString();
        int textWidth = font.width(text);
        int fontHeight = font.lineHeight;

        int sw = graphics.guiWidth();
        int sh = graphics.guiHeight();

        // 物品名称位置（水平居中，距离底部 59px）
        int textX = (sw - textWidth) / 2;
        int textY = sh - 59;

        // === 背景圆角矩形 ===
        int pad = 4;
        int bgX = textX - pad;
        int bgY = textY - pad;
        int bgW = textWidth + pad * 2;
        int bgH = fontHeight + pad * 2;
        // 15% 黑色 + 当前 alpha
        int bgAlpha = (int) (0.15f * alpha);
        int bgColor = (bgAlpha << 24); // 0x00BBGGRR 格式
        // 主体
        graphics.fill(bgX, bgY + 2, bgX + bgW, bgY + bgH - 2, bgColor);
        graphics.fill(bgX + 2, bgY, bgX + bgW - 2, bgY + bgH, bgColor);
        // 四角
        graphics.fill(bgX + 1, bgY + 1, bgX + 2, bgY + 2, bgColor);
        graphics.fill(bgX + bgW - 2, bgY + 1, bgX + bgW - 1, bgY + 2, bgColor);
        graphics.fill(bgX + 1, bgY + bgH - 2, bgX + 2, bgY + bgH - 1, bgColor);
        graphics.fill(bgX + bgW - 2, bgY + bgH - 2, bgX + bgW - 1, bgY + bgH - 1, bgColor);

        // === 文字（带渐隐） ===
        int textColor = (alpha << 24) | 0xFFFFFF;
        graphics.text(font, text, textX + 1, textY + 1, 0xFF000000, false); // 阴影
        graphics.text(font, text, textX, textY, textColor, false);
    }

    // ===== 渲染自定义条 =====
    //
    // 注：注入点使用 HEAD 而非 RETURN，确保 HUD 条的渲染层级低于物品名称等后续元素。
    // 物品名称（extractSelectedItemName）在此之后才执行，自然覆盖在条之上。

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/client/DeltaTracker;)V", at = @At("HEAD"))
    private void yzwc$onRenderBars(GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!yzwc$shouldApplyYzui()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Player player = client.player;

        if (player == null || !player.isAlive()
                || player.isCreative() || player.isSpectator()) {
            return;
        }

        int sw = graphics.guiWidth();
        int sh = graphics.guiHeight();

        // 缓存布局计算：窗口尺寸不变时复用（仅在缩放/全屏切换时变化）
        if (sw != cachedSw || sh != cachedSh) {
            cachedSw = sw;
            cachedSh = sh;

            int bw = HealthBarRenderer.BAR_WIDTH;
            int bg = HealthBarRenderer.BAR_GAP;
            int bh = HealthBarRenderer.BAR_HEIGHT;
            int yOffset = HealthBarRenderer.Y_OFFSET_FROM_BOTTOM;

            int totalWidth = bw * 2 + bg;
            cachedStartX = (sw - totalWidth) / 2;
            cachedRow1Y = sh - yOffset;
            cachedRow2Y = cachedRow1Y - bh - ROW_GAP;
            cachedHealthX = cachedStartX;
            cachedFoodX = cachedStartX + bw + bg;
        }
        int row1Y = cachedRow1Y;
        int row2Y = cachedRow2Y;
        int healthX = cachedHealthX;
        int foodX = cachedFoodX;

        // === 第二行（盔甲、氧气 — 仅在有值时显示） ===
        boolean armorVisible = player.getArmorValue() > 0;
        boolean oxygenVisible = player.getAirSupply() < player.getMaxAirSupply();

        if (armorVisible || oxygenVisible) {
            if (armorVisible) {
                ArmorBarRenderer.render(graphics, healthX, row2Y);
            }
            if (oxygenVisible) {
                OxygenBarRenderer.render(graphics, foodX, row2Y);
            }
        }

        // === 第一行（血条、食物条 — 始终显示） ===
        HealthBarRenderer.render(graphics, healthX, row1Y);
        FoodBarRenderer.render(graphics, foodX, row1Y);
    }

    // ===== YZUI 开关判断 =====

    @Unique
    private static boolean yzwc$shouldApplyYzui() {
        if (ClientExternalSettings.isYzuiEnabled()) {
            return true;
        }
        var screen = Minecraft.getInstance().gui.screen();
        return screen != null
                && screen.getClass().getName().startsWith("top.csituka.youzaiworldcore");
    }
}
