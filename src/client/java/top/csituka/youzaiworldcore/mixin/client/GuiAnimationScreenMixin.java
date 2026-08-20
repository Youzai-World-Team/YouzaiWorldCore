package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/** 为原版和模组的所有 Screen 统一压入页面进入/退出变换。 */
@Mixin(Screen.class)
public abstract class GuiAnimationScreenMixin {

    @Unique
    private boolean youzaiworldcore$animationTransformApplied;

    @ModifyVariable(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1
    )
    private int youzaiworldcore$adjustRenderMouseY(int mouseY) {
        Screen screen = (Screen) (Object) this;
        return mouseY - (int) Math.round(GuiAnimationController.getInputYOffset(screen));
    }

    @Inject(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void youzaiworldcore$beginAnimation(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        float displacement = GuiAnimationController.getDisplacement(screen, screen.height);
        if (Math.abs(displacement) < 0.01F) {
            return;
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, displacement);
        this.youzaiworldcore$animationTransformApplied = true;
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void youzaiworldcore$endAnimation(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!this.youzaiworldcore$animationTransformApplied) {
            return;
        }
        graphics.pose().popMatrix();
        this.youzaiworldcore$animationTransformApplied = false;
    }
}
