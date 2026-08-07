package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.hud.HotbarRenderer;

/**
 * 标记滚轮触发的热键栏切换，用于 HotbarRenderer 区分滚轮包装动画
 * 与数字键直接跳转。
 */
@Mixin(MouseHandler.class)
public class ScrollHotbarInputMixin {

    @Inject(method = "onScroll(JDD)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void yzwc$onScrollHotbarSwitch(CallbackInfo ci) {
        HotbarRenderer.markScrollInput();
    }
}
