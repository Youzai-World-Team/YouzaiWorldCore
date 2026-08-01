package top.csituka.youzaiworldcore.mixin.client.afk;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.afk.AfkInputTracker;

/**
 * AFK 输入检测：键盘通道。
 * <p>
 * 在 {@link KeyboardHandler#keyPress} 与 {@link KeyboardHandler#charTyped}
 * 的 HEAD 注入，窗口聚焦时记录一次输入活动。keyPress 仅在按键按下 / 释放时
 * 回调（持续按住不重复触发），因此「按住 W 挂机」不会被误判为活动——这正是
 * 客户端精确检测相对服务端近似检测的核心优势。charTyped 覆盖打字输入
 * （聊天框 / 搜索框等）。
 * </p>
 */
@Mixin(KeyboardHandler.class)
public class AfkKeyboardHandlerMixin {

    @Inject(
            method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onKeyPress(long window, int keyOrAction,
            KeyEvent event, CallbackInfo ci) {
        if (Minecraft.getInstance().getWindow().isFocused()) {
            AfkInputTracker.markInput();
        }
    }

    @Inject(
            method = "charTyped(JLnet/minecraft/client/input/CharacterEvent;)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onCharTyped(long window,
            CharacterEvent event, CallbackInfo ci) {
        if (Minecraft.getInstance().getWindow().isFocused()) {
            AfkInputTracker.markInput();
        }
    }
}
