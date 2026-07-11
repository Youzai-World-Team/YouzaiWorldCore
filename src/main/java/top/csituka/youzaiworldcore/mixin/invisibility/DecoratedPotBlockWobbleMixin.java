package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

/**
 * 隐身玩家陶罐摇动动画屏蔽 Mixin。
 * <p>
 * 当隐身玩家与陶罐交互（点击陶罐）时，会触发 {@code wobble} 动画。
 * 该动画通过 {@code level.blockEvent} 广播 {@code ClientboundBlockEventPacket} 给附近所有玩家。
 * 本 Mixin 在 {@code wobble} 中检查是否有隐身玩家在附近，
 * 若有则取消块事件广播，从而隐藏摇动动画对其他玩家的显示。
 * </p>
 */
@Mixin(DecoratedPotBlockEntity.class)
public abstract class DecoratedPotBlockWobbleMixin {

    /** 判定隐身玩家是否触发摇动的邻近距离平方（3.0 格半径）。 */
    @Unique
    private static final double WOBBLE_PROXIMITY_THRESHOLD_SQ = 9.0; // 3.0^2

    /**
     * 在 {@code wobble} 中检查附近是否有隐身玩家，若有则取消广播。
     */
    @Inject(method = "wobble", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onWobble(
            DecoratedPotBlockEntity.WobbleStyle style,
            CallbackInfo ci
    ) {
        DecoratedPotBlockEntity self = (DecoratedPotBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        if (!(self.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 检查附近是否有隐身玩家
        for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
            if (!InvisibilityManager.isInvisible(player)) {
                continue;
            }
            double dx = player.getX() - (pos.getX() + 0.5);
            double dy = player.getY() - (pos.getY() + 0.5);
            double dz = player.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= WOBBLE_PROXIMITY_THRESHOLD_SQ) {
                ci.cancel();
                return;
            }
        }
    }
}
