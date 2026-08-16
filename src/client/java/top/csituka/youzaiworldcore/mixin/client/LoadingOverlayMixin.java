package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.GradientBackgroundUtil;
import top.csituka.youzaiworldcore.client.render.LoadingCircleRenderer;

/**
 * 为 Mojang 加载页面和后续资源重载页面添加 135° 对角线渐变，
 * 并使渐变跟随加载页面的淡入/淡出动画（替换纯色品牌背景的淡出效果）；
 * 同时在启动进度条上方接入独立的 8-bit 加载圈组件。
 * <p>
 * 原版的淡出逻辑：
 * <ol>
 *   <li>加载完成后设置 {@code fadeOutStart}，{\@code fadeOutTime = (now - fadeOutStart) / 1000f}</li>
 *   <li>{\@code fadeOutTime >= 1.0} 时进入淡出阶段：
 *       alpha = 1.0 - clamp(fadeOutTime - 1.0, 0, 1)，从 1.0 渐变到 0.0</li>
 *   <li>{\@code fadeIn} 启用时：alpha = clamp(fadeInTime, 0.15, 1.0)，从 0.15 上升到 1.0</li>
 * </ol>
 */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {

    @Unique private static final int youzaiworldcore$LOADING_CIRCLE_GAP = 8;

    @Shadow private long fadeOutStart;
    @Shadow private long fadeInStart;
    @Shadow private boolean fadeIn;

    @Unique private LoadingCircleRenderer youzaiworldcore$loadingCircleRenderer;

    /**
     * 在渲染开头绘制带淡入/淡出 alpha 的渐变背景。
     * alpha 计算与 {@code extractRenderState} 中的品牌背景填充逻辑一致。
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void youzaiworldcore$drawGradientBackground(GuiGraphicsExtractor graphics,
                                                        int mouseX, int mouseY,
                                                        float partialTick, CallbackInfo ci) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int alpha = computeFadeAlpha();
        GradientBackgroundUtil.drawDiagonalGradient(graphics, width, height, alpha);
    }

    /**
     * 计算当前帧的淡出透明度（0-255），与 vanilla {@code extractRenderState} 的
     * 品牌背景填充 alpha 保持一致。
     */
    private int computeFadeAlpha() {
        long now = Util.getMillis();

        // 淡出阶段（加载完成后触发）
        if (this.fadeOutStart != -1L) {
            float fadeOutTime = (float) (now - this.fadeOutStart) / 1000.0f;
            if (fadeOutTime >= 1.0f) {
                // 淡出中：alpha 从 1.0 渐变到 0.0
                float progress = 1.0f - Mth.clamp(fadeOutTime - 1.0f, 0.0f, 1.0f);
                return Mth.ceil(progress * 255.0f);
            }
            // fadeOutTime < 1.0：延迟期，opacity 不变（与原版一致）
            return 255;
        }

        // 淡入阶段（第一次显示时的淡入效果）
        if (this.fadeIn && this.fadeInStart != -1L) {
            float fadeInTime = (float) (now - this.fadeInStart) / 500.0f;
            if (fadeInTime < 1.0f) {
                // 原版品牌背景用 clamp(fadeInTime, 0.15, 1.0)，但渐变我们允许从 0 开始更平滑
                float progress = Mth.clamp(fadeInTime, 0.0f, 1.0f);
                return Mth.ceil(progress * 255.0f);
            }
        }

        // 正常显示
        return 255;
    }

    /**
     * 在原版启动进度条上方绘制独立的 8-bit 加载圈组件。
     * 直接复用进度条传入的透明度，使两者在加载结束时同步淡出。
     */
    @Inject(method = "extractProgressBar", at = @At("TAIL"))
    private void youzaiworldcore$drawLoadingCircle(GuiGraphicsExtractor graphics,
                                                   int left, int top, int right, int bottom,
                                                   float alpha, CallbackInfo ci) {
        if (this.youzaiworldcore$loadingCircleRenderer == null) {
            this.youzaiworldcore$loadingCircleRenderer = new LoadingCircleRenderer();
        }

        int centerX = left + (right - left) / 2;
        int centerY = top - youzaiworldcore$LOADING_CIRCLE_GAP
                - LoadingCircleRenderer.VISUAL_RADIUS;
        this.youzaiworldcore$loadingCircleRenderer.render(graphics, centerX, centerY, alpha);
    }

    /**
     * 劫持 {@code fill()} 调用，跳过纯色品牌背景填充（让我们的渐变显示出来）。
     */
    @Redirect(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void youzaiworldcore$skipBrandFill(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        // no-op：不执行 fill，我们的渐变已在 HEAD 中绘制
    }

    /**
     * 劫持暗色模式下的 clearColor 设置，让它透明（我们的渐变会填充背景）。
     */
    @Redirect(
        method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;setVector4fFromARGB32(Lorg/joml/Vector4f;I)Lorg/joml/Vector4f;")
    )
    private Vector4f youzaiworldcore$makeClearColorTransparent(Vector4f vector4f, int color) {
        return vector4f.set(0, 0, 0, 0); // 透明黑，渐变背景会填满画面
    }
}
