package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/**
 * 让页面中的全屏半透明黑色遮罩保持固定，仅随页面切换淡入淡出。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiAnimationGuiGraphicsExtractorMixin {

    @Redirect(
            method = "fill(IIIII)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V"
            )
    )
    private void youzaiworldcore$stabilizeFullscreenOverlay(
            GuiGraphicsExtractor graphics, RenderPipeline pipeline,
            int x1, int y1, int x2, int y2, int color) {
        if (!GuiAnimationController.isFull()
                || !youzaiworldcore$isFullscreenBlackOverlay(graphics, x1, y1, x2, y2, color)) {
            graphics.fill(pipeline, x1, y1, x2, y2, color);
            return;
        }

        int originalAlpha = color >>> 24;
        int animatedAlpha = Math.round(originalAlpha * GuiAnimationController.getScreenOpacity());
        int animatedColor = (animatedAlpha << 24) | (color & 0x00FFFFFF);

        int compensation = Math.round(GuiAnimationController.getContentTransformDisplacement());
        graphics.fill(pipeline, x1, y1 - compensation, x2, y2 - compensation, animatedColor);
    }

    private static boolean youzaiworldcore$isFullscreenBlackOverlay(
            GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int alpha = color >>> 24;
        if (alpha <= 0 || alpha >= 255 || (color & 0x00FFFFFF) != 0) {
            return false;
        }

        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        return left <= 0 && top <= 0
                && right >= graphics.guiWidth() && bottom >= graphics.guiHeight();
    }
}
