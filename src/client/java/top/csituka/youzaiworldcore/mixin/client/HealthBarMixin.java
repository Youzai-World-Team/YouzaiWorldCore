package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
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
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 血条/饥饿条/盔甲条/氧气条替换 Mixin。
 *
 * <p>当 YZUI 启用时，取消以下原版渲染：</p>
 * <ul>
 *   <li>{@link Hud#extractPlayerHealth} — 爱心血条</li>
 *   <li>{@link Hud#extractFood} — 鸡腿饥饿值</li>
 *   <li>{@link Hud#extractArmor} — 盔甲图标</li>
 *   <li>{@link Hud#extractAirBubbles} — 氧气气泡</li>
 * </ul>
 *
 * <p>替换为两行并排长条进度条：</p>
 * <pre>
 * 第二行: [ARMOR BAR]          [OXYGEN BAR]   (仅在对应值非默认时显示)
 * 第一行: [HEALTH BAR]    gap    [FOOD BAR]    (始终显示)
 * </pre>
 *
 * <p>创造模式 / 旁观模式隐藏所有条。</p>
 */
@SuppressWarnings("null")
@Mixin(Hud.class)
public abstract class HealthBarMixin {

    private static final String LOG_TAG = "HealthBarMixin";
    /** 两行之间的垂直间距（≥ 文字高度 + 行高，避免 text 与第二行条重叠） */
    private static final int ROW_GAP = 12;

    // ===== 取消原版渲染 =====

    @Inject(method = "extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$onExtractPlayerHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui()) ci.cancel();
    }

    @Inject(method = "extractFood(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/world/entity/player/Player;II)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$onExtractFood(GuiGraphicsExtractor graphics,
            Player player, int i, int j, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui()) ci.cancel();
    }

    @Inject(method = "extractArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/world/entity/player/Player;IIII)V",
            at = @At("HEAD"), cancellable = true)
    private static void yzwc$onExtractArmor(GuiGraphicsExtractor graphics,
            Player player, int i, int j, int k, int l, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui()) ci.cancel();
    }

    @Inject(method = "extractAirBubbles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/world/entity/player/Player;III)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$onExtractAirBubbles(GuiGraphicsExtractor graphics,
            Player player, int i, int j, int k, CallbackInfo ci) {
        if (yzwc$shouldApplyYzui()) ci.cancel();
    }

    // ===== 渲染自定义条 =====

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/client/DeltaTracker;)V", at = @At("RETURN"))
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

        int bw = HealthBarRenderer.BAR_WIDTH;
        int bg = HealthBarRenderer.BAR_GAP;
        int bh = HealthBarRenderer.BAR_HEIGHT;
        int yOffset = HealthBarRenderer.Y_OFFSET_FROM_BOTTOM;

        // 计算第一行（主行）位置
        int totalWidth = bw * 2 + bg;
        int startX = (sw - totalWidth) / 2;
        int row1Y = sh - yOffset;               // 第一行（血条、食物条）
        int row2Y = row1Y - bh - ROW_GAP;       // 第二行（盔甲条、氧气条）

        int healthX = startX;
        int foodX = startX + bw + bg;

        DebugLogger.debug(LOG_TAG,
                "渲染四行: row1Y=%d, row2Y=%d, healthX=%d, foodX=%d, sw=%d, sh=%d",
                row1Y, row2Y, healthX, foodX, sw, sh);

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
