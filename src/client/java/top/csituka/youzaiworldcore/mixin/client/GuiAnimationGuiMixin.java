package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/** 在原版 Gui 屏幕字段写入前后接入统一的页面切换状态机。 */
@Mixin(Gui.class)
public abstract class GuiAnimationGuiMixin {

    @Shadow
    private Screen screen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$interceptScreenChange(Screen newScreen, CallbackInfo ci) {
        if (GuiAnimationController.interceptScreenChange((Gui) (Object) this, newScreen)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "setScreen",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/gui/Gui;screen:Lnet/minecraft/client/gui/screens/Screen;",
                    opcode = 181,
                    shift = At.Shift.AFTER
            )
    )
    private void youzaiworldcore$recordScreenChange(Screen newScreen, CallbackInfo ci) {
        GuiAnimationController.onScreenChanged(this.screen);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void youzaiworldcore$tickAnimation(
            net.minecraft.client.DeltaTracker deltaTracker, boolean renderLevel, boolean renderGui, CallbackInfo ci) {
        GuiAnimationController.tick();
    }
}
