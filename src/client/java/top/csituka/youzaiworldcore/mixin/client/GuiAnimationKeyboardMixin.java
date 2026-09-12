package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/** 只拦截正在退出的页面输入，保留原版键盘状态更新与全局快捷键。 */
@Mixin(KeyboardHandler.class)
public class GuiAnimationKeyboardMixin {
    @WrapOperation(method = "keyPress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean youzaiworldcore$blockExitKey(Screen screen, KeyEvent event, Operation<Boolean> original) {
        return GuiAnimationController.isExiting(screen) || original.call(screen, event);
    }

    @WrapOperation(method = "keyPress", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;keyReleased(Lnet/minecraft/client/input/KeyEvent;)Z"))
    private boolean youzaiworldcore$blockExitRelease(Screen screen, KeyEvent event, Operation<Boolean> original) {
        return GuiAnimationController.isExiting(screen) || original.call(screen, event);
    }

    @WrapOperation(method = "charTyped", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;charTyped(Lnet/minecraft/client/input/CharacterEvent;)Z"))
    private boolean youzaiworldcore$blockExitCharacter(Screen screen, CharacterEvent event, Operation<Boolean> original) {
        return GuiAnimationController.isExiting(screen) || original.call(screen, event);
    }
}
