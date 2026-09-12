package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.platform.Window;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.render.YzuiViewport;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** 用最后显示的一帧反算位移、缩放和视口，保持点击、滚动与拖拽和画面一致。 */
@Mixin(MouseHandler.class)
public class GuiAnimationMouseHandlerMixin {

    @Redirect(method = { "onButton", "onScroll",
            "handleAccumulatedMovement" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D"))
    private double youzaiworldcore$adjustInputY(MouseHandler handler, @NonNull Window window) {
        var screen = Minecraft.getInstance().gui.screen();
        double y = YzuiViewport.toLogical(screen, handler.getScaledYPos(window));
        return screen == null ? y : GuiAnimationController.inputFrame(screen).toLocalY(y, screen.height);
    }

    @Redirect(method = { "onButton", "onScroll", "handleAccumulatedMovement" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D"))
    private double youzaiworldcore$adjustInputX(MouseHandler handler, Window window) {
        var screen = Minecraft.getInstance().gui.screen();
        double x = YzuiViewport.toLogical(screen, handler.getScaledXPos(window));
        return screen == null ? x : GuiAnimationController.inputFrame(screen).toLocalX(x, screen.width);
    }

    @Redirect(method = "handleAccumulatedMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z"))
    private boolean youzaiworldcore$scaleDrag(Screen screen, MouseButtonEvent event, double dx, double dy) {
        if (GuiAnimationController.isExiting(screen)) return true;
        float scale = YzuiViewport.scale(screen) * GuiAnimationController.inputFrame(screen).scale();
        return screen.mouseDragged(event, dx / scale, dy / scale);
    }

    @WrapOperation(method = "onButton", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean youzaiworldcore$blockExitClick(Screen screen, MouseButtonEvent event, boolean click,
                                                 Operation<Boolean> original) {
        return GuiAnimationController.isExiting(screen) || original.call(screen, event, click);
    }

    @WrapOperation(method = "onButton", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"))
    private boolean youzaiworldcore$blockExitRelease(Screen screen, MouseButtonEvent event,
                                                   Operation<Boolean> original) {
        return GuiAnimationController.isExiting(screen) || original.call(screen, event);
    }

    @WrapOperation(method = "onScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;mouseScrolled(DDDD)Z"))
    private boolean youzaiworldcore$blockExitScroll(Screen screen, double x, double y, double dx, double dy,
                                                  Operation<Boolean> original) {
        return GuiAnimationController.isExiting(screen) || original.call(screen, x, y, dx, dy);
    }
}
