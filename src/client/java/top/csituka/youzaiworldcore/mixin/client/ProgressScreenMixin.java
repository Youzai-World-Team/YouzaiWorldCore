package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.GradientBackgroundUtil;

/**
 * 为资源重载界面（ProgressScreen）添加 135° 对角线渐变背景。
 */
@Mixin(ProgressScreen.class)
public class ProgressScreenMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void youzaiworldcore$drawGradientBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        GradientBackgroundUtil.drawDiagonalGradient(graphics, width, height, 255);
    }
}
