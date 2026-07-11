package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

/**
 * 隐身玩家木桶动画/声音屏蔽 Mixin。
 * <p>
 * 木桶的动画机制与箱子不同——它通过 {@code updateBlockState} 修改 {@code OPEN} 方块属性
 * 来触发客户端模型变化，而非使用 {@code blockEvent}。因此需要直接 Mixin
 * {@code updateBlockState} 和 {@code playSound} 方法。
 * </p>
 */
@Mixin(BarrelBlockEntity.class)
public abstract class BarrelBlockEntityAnimationMixin {

    // ==================== startOpen ====================

    /**
     * 在 {@code startOpen} 中记录隐身玩家与木桶的交互关系。
     */
    @Inject(method = "startOpen", at = @At("HEAD"))
    private void youzaiworldcore$onStartOpen(ContainerUser user, CallbackInfo ci) {
        BarrelBlockEntity self = (BarrelBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        if (user instanceof ServerPlayer player) {
            if (InvisibilityManager.isInvisible(player)) {
                InvisibilityManager.markContainerInteraction(pos);
            } else {
                InvisibilityManager.clearContainerInteraction(pos);
            }
        }
    }

    // ==================== updateBlockState ====================

    /**
     * 在 {@code updateBlockState} 中拦截方块状态更新。
     * 如果该木桶被隐身玩家交互，取消状态更新广播，
     * 从而阻止其他玩家看到木桶的开启/关闭动画。
     */
    @Inject(method = "updateBlockState", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onUpdateBlockState(BlockState state, boolean open, CallbackInfo ci) {
        BarrelBlockEntity self = (BarrelBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        if (InvisibilityManager.isContainerInteractionBlocked(pos)) {
            ci.cancel();
        }

        // 木桶关闭时清理标记
        if (!open) {
            InvisibilityManager.clearContainerInteraction(pos);
        }
    }

    // ==================== playSound ====================

    /**
     * 在 {@code playSound} 中拦截声音播放。
     * 如果该木桶被隐身玩家交互，取消声音播放。
     */
    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onPlaySound(BlockState state, SoundEvent sound, CallbackInfo ci) {
        BarrelBlockEntity self = (BarrelBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        if (InvisibilityManager.isContainerInteractionBlocked(pos)) {
            ci.cancel();
        }
    }
}
