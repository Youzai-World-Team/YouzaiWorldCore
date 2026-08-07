package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.client.hud.HotbarRenderer;

/**
 * 标记滚轮触发的热键栏切换方向，供 HotbarRenderer 在连续空间中追踪包装动画。
 * <ul>
 *   <li>+1 = 向前滚动（滚轮向下）</li>
 *   <li>-1 = 向后滚动（滚轮向上）</li>
 * </ul>
 */
@Mixin(MouseHandler.class)
public class ScrollHotbarInputMixin {

    @Unique
    private static int yzwc$preScrollSlot = 0;

    /** 在 onScroll 开头捕获当前槽位 */
    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"))
    private void yzwc$capturePreScroll(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            yzwc$preScrollSlot = player.getInventory().getSelectedSlot();
        }
    }

    /** 在 onScroll 末尾检测槽位变更并计算方向 */
    @Inject(method = "onScroll(JDD)V", at = @At("TAIL"))
    private void yzwc$checkScrollDirection(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        int slot = player.getInventory().getSelectedSlot();
        if (slot != yzwc$preScrollSlot) {
            // 模 9 距离：1 = 前滚, 8 = 后滚
            int diff = (slot - yzwc$preScrollSlot + 9) % 9;
            int dir = (diff == 1) ? 1 : -1;
            HotbarRenderer.markScrollInput(dir);
        }
    }
}
