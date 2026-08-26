package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/** 将鼠标坐标反向补偿页面位移动画，确保动画期间按钮仍与视觉位置一致。 */
@Mixin(MouseHandler.class)
public class GuiAnimationMouseHandlerMixin {

    @Redirect(method = { "onButton", "onScroll",
            "handleAccumulatedMovement" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D"))
    private double youzaiworldcore$adjustInputY(MouseHandler handler, @NonNull Window window) {
        return handler.getScaledYPos(window) - youzaiworldcore$inputOffset();
    }

    private static double youzaiworldcore$inputOffset() {
        var screen = Minecraft.getInstance().gui.screen();
        return GuiAnimationController.getInputYOffset(screen);
    }
}
