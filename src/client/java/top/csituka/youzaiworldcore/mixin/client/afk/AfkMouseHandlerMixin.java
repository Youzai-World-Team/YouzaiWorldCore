package top.csituka.youzaiworldcore.mixin.client.afk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.afk.AfkInputTracker;

/**
 * AFK 输入检测：鼠标通道。
 * <p>
 * 在 {@link MouseHandler#onButton}（点击）、{@link MouseHandler#onScroll}
 * （滚轮）、{@link MouseHandler#onMove}（移动）的 HEAD 注入，窗口聚焦时记录
 * 一次输入活动。onMove 在菜单（背包 / 聊天 / 暂停）中移动鼠标同样触发，
 * 均视为玩家活动。
 * </p>
 */
@Mixin(MouseHandler.class)
public class AfkMouseHandlerMixin {

    @Inject(
            method = "onButton(JLnet/minecraft/client/input/MouseButtonInfo;I)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onButton(long window, MouseButtonInfo button,
            int action, CallbackInfo ci) {
        if (Minecraft.getInstance().getWindow().isFocused()) {
            AfkInputTracker.markInput();
        }
    }

    @Inject(
            method = "onScroll(JDD)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onScroll(long window, double horizontal,
            double vertical, CallbackInfo ci) {
        if (Minecraft.getInstance().getWindow().isFocused()) {
            AfkInputTracker.markInput();
        }
    }

    @Inject(
            method = "onMove(JDD)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$onMove(long window, double x, double y,
            CallbackInfo ci) {
        if (Minecraft.getInstance().getWindow().isFocused()) {
            AfkInputTracker.markInput();
        }
    }
}
