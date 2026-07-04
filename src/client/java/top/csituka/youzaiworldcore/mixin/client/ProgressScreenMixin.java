package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.GradientBackgroundUtil;

/**
 * 为资源重载界面（ProgressScreen）的内容层绘制 135° 对角线渐变背景。
 * <p>
 * 背景层由 {@link ScreenMixinForProgressBg} 拦截处理。
 */
@Mixin(ProgressScreen.class)
public class ProgressScreenMixin {

    /**
     * 在 extractRenderState 头部绘制渐变，覆盖渲染管线中背景层之后的 UI 内容层，
     * 确保渐变在所有 UI 元素之下正确显示。
     */
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void youzaiworldcore$drawGradientBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        GradientBackgroundUtil.drawDiagonalGradient(graphics, width, height, 255);
    }
}
