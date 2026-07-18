package top.csituka.youzaiworldcore.mixin.doubledoors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.event.DoubleDoorsHandler;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 栅栏门（FenceGateBlock）的双开 Mixin。
 * <ul>
 *   <li>注入 {@code useWithoutItem} 的 RETURN，捕获玩家点击交互</li>
 *   <li>注入 {@code neighborChanged} 的 HEAD+RETURN，捕获红石信号变化</li>
 * </ul>
 */
@Mixin(value = FenceGateBlock.class, priority = 1001)
public class FenceGateBlockMixin {

    private static final String MODULE = "FenceGateBlockMixin";

    /** 保存邻居变化前的 OPEN 状态，用于判断是否实际发生了变化 */
    private static final ThreadLocal<Boolean> prevOpenState = new ThreadLocal<>();

    /**
     * 玩家点击栅栏门时触发。
     * 注入在 {@code useWithoutItem} 的 RETURN 处。
     */
    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void youzaiworldcore$onUseWithoutItem(BlockState blockState, Level level, BlockPos blockPos,
                                                    Player player, BlockHitResult blockHitResult,
                                                    CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) {
            return;
        }
        DebugLogger.debug(MODULE, "useWithoutItem@RETURN: pos=%s, player=%s",
                blockPos, player.getName().getString());
        DoubleDoorsHandler.onDoorClick(level, player, blockPos, blockHitResult);
    }

    /**
     * 红石信号更新导致邻居变化时，在 {@code neighborChanged} 的 HEAD 处
     * 保存当前 OPEN 状态以在 RETURN 处判断是否实际变化。
     */
    @Inject(method = "neighborChanged", at = @At("HEAD"))
    private void youzaiworldcore$beforeNeighborChanged(BlockState state, Level level, BlockPos pos,
                                                        Block block, Orientation orientation,
                                                        boolean isMoving,
                                                        CallbackInfo ci) {
        if (level.isClientSide()) {
            prevOpenState.remove();
            return;
        }
        prevOpenState.set(state.getValue(BlockStateProperties.OPEN));
    }

    /**
     * 红石信号更新后，在 {@code neighborChanged} 的 RETURN 处判断门状态是否变化。
     */
    @Inject(method = "neighborChanged", at = @At("RETURN"))
    private void youzaiworldcore$afterNeighborChanged(BlockState state, Level level, BlockPos pos,
                                                       Block block, Orientation orientation,
                                                       boolean isMoving,
                                                       CallbackInfo ci) {
        if (level.isClientSide()) {
            prevOpenState.remove();
            return;
        }
        Boolean prev = prevOpenState.get();
        if (prev == null) {
            prevOpenState.remove();
            return;
        }
        boolean current = state.getValue(BlockStateProperties.OPEN);
        if (prev != current) {
            DebugLogger.debug(MODULE, "neighborChanged@RETURN: pos=%s, open=%s->%s", pos, prev, current);
            DoubleDoorsHandler.onNeighborChanged(level, pos, state);
        }
        prevOpenState.remove();
    }
}
