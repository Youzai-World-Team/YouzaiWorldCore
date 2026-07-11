package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

/**
 * 隐身玩家容器块事件广播屏蔽 Mixin。
 * <p>
 * 拦截 {@link ServerLevel#blockEvent} 方法，当该位置在隐身玩家容器交互跟踪记录中时，
 * 取消块事件入队，从而阻止 {@link net.minecraft.network.protocol.game.ClientboundBlockEventPacket}
 * 广播给附近玩家。
 * </p>
 * <p>
 * 此 Mixin 作为兜底方案，覆盖末影箱、潜影盒等容器的 blockEvent 调用。
 * 对于已通过 {@code signalOpenCount} 拦截的箱子/陷阱箱，此 Mixin 不会产生额外影响。
 * </p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelBlockEventMixin {

    /**
     * 在 {@code blockEvent} 中加入队列前检查。
     * 如果位置在隐身容器交互记录中，阻止该块事件入队。
     */
    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onBlockEvent(
            BlockPos pos,
            Block block,
            int type,
            int data,
            CallbackInfo ci
    ) {
        // 只拦截容器动画事件（type == 1: 箱盖/潜影盒/末影箱开合动画）
        if (type == 1 && InvisibilityManager.isContainerInteractionBlocked(pos)) {
            ci.cancel();
        }
    }
}
