package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

/**
 * 隐身玩家容器动画屏蔽 Mixin（箱子/陷阱箱）。
 * <p>
 * 当隐身玩家与箱子等容器交互时（如打开箱子），禁止容器动画（箱盖开合）和声音
 * 传播给其他玩家。隐身玩家自身仍可以正常看到动画并与容器交互。
 * </p>
 * <h3>实现原理</h3>
 * <ul>
 *   <li>在 {@link ChestBlockEntity#startOpen} 中记录隐身玩家打开的容器位置到
 *       {@link InvisibilityManager} 的跟踪集</li>
 *   <li>在 {@link ChestBlockEntity#signalOpenCount} 中拦截 blockEvent 调用，阻止动画包广播</li>
 *   <li>在 {@link ChestBlockEntity#playSound} 中拦截声音播放，阻止声音广播</li>
 * </ul>
 */
@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityAnimationMixin {

    // ==================== startOpen ====================

    /**
     * 在 {@code startOpen} 中记录隐身玩家与容器的交互关系。
     * 如果是隐身玩家打开：标记到 InvisibilityManager 的跟踪集。
     * 如果是非隐身玩家打开：清除该位置的跟踪标记。
     */
    @Inject(method = "startOpen", at = @At("HEAD"))
    private void youzaiworldcore$onStartOpen(ContainerUser user, CallbackInfo ci) {
        ChestBlockEntity self = (ChestBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        if (user instanceof ServerPlayer player) {
            if (InvisibilityManager.isInvisible(player)) {
                InvisibilityManager.markContainerInteraction(pos);
            } else {
                // 如果有显形玩家打开了该容器，清除隐身标记，允许动画正常广播
                InvisibilityManager.clearContainerInteraction(pos);
            }
        }
    }

    // ==================== signalOpenCount ====================

    /**
     * 在 {@code signalOpenCount} 中拦截 blockEvent 调用。
     * 如果容器是由隐身玩家打开的，取消 blockEvent 调用，
     * 从而阻止 {@link net.minecraft.network.protocol.game.ClientboundBlockEventPacket}
     * 广播给其他玩家。
     */
    @Inject(method = "signalOpenCount", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onSignalOpenCount(
            Level level,
            BlockPos pos,
            BlockState state,
            int oldCount,
            int newCount,
            CallbackInfo ci
    ) {
        // 如果此容器在隐身玩家交互记录中，阻止动画广播
        if (InvisibilityManager.isContainerInteractionBlocked(pos)) {
            ci.cancel();
        }

        // 当容器完全关闭时，清理记录
        if (newCount == 0) {
            InvisibilityManager.clearContainerInteraction(pos);
        }
    }

    // ==================== playSound ====================

    /**
     * 在 {@code playSound} 中拦截声音播放。
     * 如果容器是由隐身玩家打开的，取消声音播放调用，
     * 从而阻止容器开合声音传播给其他玩家。
     * <p>
     * {@code playSound} 是私有静态方法，由 {@code ContainerOpenersCounter} 匿名内部类的
     * {@code onOpen} / {@code onClose} 调用。
     * </p>
     */
    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private static void youzaiworldcore$onPlaySound(
            Level level,
            BlockPos pos,
            BlockState state,
            SoundEvent sound,
            CallbackInfo ci
    ) {
        if (InvisibilityManager.isContainerInteractionBlocked(pos)) {
            ci.cancel();
        }
    }
}
