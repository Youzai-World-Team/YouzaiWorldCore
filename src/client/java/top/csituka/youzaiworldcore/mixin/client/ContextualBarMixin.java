package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import net.minecraft.client.gui.contextualbar.JumpableVehicleBar;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.hud.HealthBarRenderer;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 上下文栏（经验条/定位条/跳跃条）样式替换 Mixin。
 *
 * <p>
 * 将原版精灵表(sprite)驱动的上下文栏渲染替换为 YZUI 风格的圆角填充条。
 * 取消各栏的 {@code extractBackground}，替换为 YZUI 背景+填充绘制，
 * 同时保留 {@code LocatorBar.extractRenderState} 的航点指示器渲染。
 * </p>
 */
@SuppressWarnings("null")
@Mixin({ ExperienceBar.class, LocatorBar.class, JumpableVehicleBar.class })
public abstract class ContextualBarMixin {

    private static final String LOG_TAG = "ContextualBarMixin";
    /** 上下文栏宽度（与原版 {@link ContextualBar#WIDTH} 一致） */
    private static final int BAR_WIDTH = 182;
    /** YZUI 风格背景色 */
    private static final int BG_COLOR = 0xAA333333;
    /** 经验条填充色（绿色系） */
    private static final int COLOR_EXP_FILL = 0xFF88FF88;
    /** 跳跃条填充色（蓝色系） */
    private static final int COLOR_JUMP_FILL = 0xFF66AAFF;
    /** 跳跃冷却色（暗红） */
    private static final int COLOR_JUMP_COOLDOWN = 0xFF883333;

    /**
     * 取消原版 {@code extractBackground} 绘制，替换为 YZUI 风格。
     *
     * <p>
     * 对于 {@link ExperienceBar} 和 {@link JumpableVehicleBar}，
     * 额外绘制进度填充条；对于 {@link LocatorBar} 仅绘制圆角背景
     * （航点由 {@code extractRenderState} 绘制在背景之上）。
     * </p>
     */
    @Inject(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;"
            + "Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void yzwc$onExtractBackground(GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ClientExternalSettings.isYzuiEnabled()) return;

        Minecraft client = Minecraft.getInstance();
        ContextualBar bar = (ContextualBar) (Object) this;

        int left = bar.left(client.getWindow());
        int top = bar.top(client.getWindow());

        // 绘制圆角背景
        HealthBarRenderer.fillBarBg(graphics, left, top, BAR_WIDTH, BG_COLOR);

        if (bar instanceof ExperienceBar) {
            drawExpProgress(graphics, left, top, client.player);
        } else if (bar instanceof JumpableVehicleBar) {
            drawJumpProgress(graphics, left, top, client.player);
        }
        // LocatorBar: 仅绘制背景，航点由 extractRenderState 绘制

        ci.cancel();
    }

    /**
     * 绘制经验条进度填充（绿色）及经验数值文字。
     *
     * <p>
     * 在进度条上方绘制左对齐的当前等级内经验值和右对齐的升级所需经验值。
     * </p>
     */
    private static void drawExpProgress(GuiGraphicsExtractor graphics,
            int left, int top, Player player) {
        if (player == null) return;

        int xpNeeded = player.getXpNeededForNextLevel();
        if (xpNeeded <= 0) return;

        float progress = player.experienceProgress;
        int progressWidth = (int) (progress * BAR_WIDTH);
        if (progressWidth > 0) {
            HealthBarRenderer.fillBarFill(graphics, left, top, progressWidth, BAR_WIDTH, COLOR_EXP_FILL);
        }

        // 经验数值文字：与血条/饥饿条文本同位置（居中在各自的 85px 区域内）
        int sw = graphics.guiWidth();
        int yzuiBarWidth = HealthBarRenderer.BAR_WIDTH; // 85
        int yzuiGap = HealthBarRenderer.BAR_GAP;        // 8
        int yzuiTotal = yzuiBarWidth * 2 + yzuiGap;     // 178
        int startX = (sw - yzuiTotal) / 2;

        Font font = Minecraft.getInstance().font;
        int currentXp = (int) (progress * xpNeeded);
        int textY = top - 2; // 条上方 2px（基线）
        int shadowOffset = 1;

        String currentText = String.valueOf(currentXp);
        String neededText = String.valueOf(xpNeeded);

        // 左经验值：居中在血条区域（healthX + 85/2）
        int healthCenterX = startX + yzuiBarWidth / 2;
        int currentWidth = font.width(currentText);
        int currentX = healthCenterX - currentWidth / 2;
        graphics.text(font, currentText, currentX + shadowOffset, textY + shadowOffset,
                0xFF000000, false);
        graphics.text(font, currentText, currentX, textY, 0xFFFFFFFF, false);

        // 右经验值：居中在饥饿条区域（foodX + 85/2）
        int foodCenterX = startX + yzuiBarWidth + yzuiGap + yzuiBarWidth / 2;
        int neededWidth = font.width(neededText);
        int neededX = foodCenterX - neededWidth / 2;
        graphics.text(font, neededText, neededX + shadowOffset, textY + shadowOffset,
                0xFF000000, false);
        graphics.text(font, neededText, neededX, textY, 0xFFFFFFFF, false);

        DebugLogger.debug(LOG_TAG,
                "经验条: progress=%.3f, current=%d, needed=%d, width=%d/%d",
                progress, currentXp, xpNeeded, progressWidth, BAR_WIDTH);
    }

    /**
     * 绘制跳跃条进度/冷却填充（蓝色/暗红）。
     */
    private static void drawJumpProgress(GuiGraphicsExtractor graphics,
            int left, int top, Player player) {
        if (!(player instanceof LocalPlayer localPlayer)) return;

        PlayerRideableJumping vehicle = localPlayer.jumpableVehicle();
        if (vehicle == null) return;

        if (vehicle.getJumpCooldown() > 0) {
            // 冷却中：暗红全条
            HealthBarRenderer.fillBarFill(graphics, left, top, BAR_WIDTH, BAR_WIDTH, COLOR_JUMP_COOLDOWN);
            DebugLogger.debug(LOG_TAG, "跳跃条: cooldown=%d", vehicle.getJumpCooldown());
        } else {
            // 蓄力进度：蓝色
            float scale = localPlayer.getJumpRidingScale();
            if (scale > 0.0f) {
                int progressWidth = Mth.lerpDiscrete(scale, 0, BAR_WIDTH);
                if (progressWidth > 0) {
                    HealthBarRenderer.fillBarFill(graphics, left, top, progressWidth, BAR_WIDTH, COLOR_JUMP_FILL);
                }
                DebugLogger.debug(LOG_TAG, "跳跃条: scale=%.3f, width=%d", scale, progressWidth);
            }
        }
    }
}
