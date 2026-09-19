package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.render.YzuiLoadingRenderer;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 只替换资源重载的视觉调用，保留原版进度采样、完成/错误回调和 Overlay 退出时机。 */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
    @Shadow private float currentProgress;
    @Shadow private long fadeOutStart;
    @Unique private boolean youzaiworldcore$logged;

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"), require = 2)
    private void youzaiworldcore$background(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        // 在原版 nextStratum 之后绘制，避免淡出时被下层页面盖住。
        YzuiLoadingRenderer.background(g, (color >>> 24) / 255f);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/ARGB;setVector4fFromARGB32(Lorg/joml/Vector4f;I)Lorg/joml/Vector4f;"))
    private Vector4f youzaiworldcore$clearColor(Vector4f vector, int color) {
        return ARGB.setVector4fFromARGB32(vector, YzuiTheme.palette().background());
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void youzaiworldcore$loadingCard(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier logo,
            int x, int y, float u, float v, int width, int height, int sourceWidth, int sourceHeight,
            int textureWidth, int textureHeight, int color) {
        if (!youzaiworldcore$logged) {
            youzaiworldcore$logged = true;
            DebugLogger.info("YzuiLoading", "资源加载主题卡片已就绪");
        }
        // 完成后明确显示 100%，不让平滑进度在淡出时停留于 99%。
        YzuiLoadingRenderer.render(g, logo, fadeOutStart >= 0 ? 1f : currentProgress, (color >>> 24) / 255f);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void youzaiworldcore$skipSecondLogo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier logo,
            int x, int y, float u, float v, int width, int height, int sourceWidth, int sourceHeight,
            int textureWidth, int textureHeight, int color) {
        // 主题卡片已绘制完整的官网品牌标志，跳过原版第二次绘制。
    }

    @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$replaceProgress(GuiGraphicsExtractor g, int left, int top, int right, int bottom,
            float alpha, CallbackInfo ci) {
        // 仅取消旧进度条绘制；外层 extractRenderState 与 tick 均继续执行。
        ci.cancel();
    }
}
