package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.YzuiBackdrop;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.render.YzuiViewport;

/** 页面内容、提示框和输入使用同一个变换；背景固定在物理 GUI 坐标中。 */
@Mixin(Screen.class)
public abstract class GuiAnimationScreenMixin {
    @Inject(method = { "init(II)V", "resize(II)V" }, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/Screen;height:I", opcode = 181, shift = At.Shift.AFTER))
    private void youzaiworldcore$configureViewport(int width, int height, CallbackInfo ci) {
        YzuiViewport.configure((Screen) (Object) this, width, height);
    }

    @WrapMethod(method = "extractRenderStateWithTooltipAndSubtitles")
    private void youzaiworldcore$renderPage(GuiGraphicsExtractor g, int mouseX, int mouseY,
            float partialTick, Operation<Void> original) {
        Screen screen = (Screen) (Object) this;
        var frame = GuiAnimationController.sampleFrame(screen);
        float viewportScale = YzuiViewport.scale(screen);
        int localX = (int) Math.floor(frame.toLocalX(mouseX / viewportScale, screen.width));
        int localY = (int) Math.floor(frame.toLocalY(mouseY / viewportScale, screen.height));

        g.pose().pushMatrix();
        g.pose().scale(viewportScale, viewportScale);
        g.pose().translate(frame.translateX(screen.width), frame.translateY(screen.height));
        g.pose().scale(frame.scale(), frame.scale());
        var previousContent = GuiAnimationController.pushContent(frame);
        Screen previousViewport = YzuiViewport.beginRendering(YzuiTheme.isCustomScreen(screen) ? screen : null);
        try {
            original.call(g, localX, localY, partialTick);
        } finally {
            YzuiViewport.beginRendering(previousViewport);
            GuiAnimationController.restoreContent(previousContent);
            g.pose().popMatrix();
        }
    }

    @Redirect(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void youzaiworldcore$themeBackground(Screen screen, GuiGraphicsExtractor g, int x, int y, float tick) {
        g.pose().pushMatrix();
        g.pose().identity();
        Screen previousViewport = YzuiViewport.beginRendering(null);
        var previousContent = GuiAnimationController.suspendContent(false);
        try {
            if (YzuiTheme.isCustomScreen(screen)) YzuiBackdrop.render(g, screen, tick);
            else screen.extractBackground(g, x, y, tick);
        } finally {
            GuiAnimationController.restoreContent(previousContent);
            YzuiViewport.beginRendering(previousViewport);
            g.pose().popMatrix();
        }
    }

    @Redirect(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void youzaiworldcore$themeContent(Screen screen, GuiGraphicsExtractor g, int x, int y, float tick) {
        YzuiTheme.screenCard(g, screen);
        screen.extractRenderState(g, x, y, tick);
    }
}