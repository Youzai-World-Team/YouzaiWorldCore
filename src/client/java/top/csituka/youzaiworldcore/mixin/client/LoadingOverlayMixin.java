package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.GradientBackgroundUtil;

/**
 * 为 Mojang 加载页面背景添加 135° 对角线渐变。
 * 用 {@link GradientBackgroundUtil#drawDiagonalGradient} 替代原有的纯色背景。
 */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {

    /**
     * 在渲染开头绘制渐变背景。
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void youzaiworldcore$drawGradientBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        GradientBackgroundUtil.drawDiagonalGradient(graphics, width, height, 255);
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
