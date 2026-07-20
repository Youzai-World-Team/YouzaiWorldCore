package top.csituka.youzaiworldcore.mixin.doubledoors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.event.DoubleDoorsHandler;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 栅栏门（FenceGateBlock）双开 Mixin。
 * <p>
 * 仅在玩家点击（{@code useWithoutItem}）时触发：在 HEAD 记录点击前的 OPEN 状态，
 * 在 RETURN 比对点击后的新状态；仅当<b>确实发生了切换</b>时，
 * 把新开合状态交给 {@link DoubleDoorsHandler} 同步相邻同材质栅栏门。
 * </p>
 * <p>
 * 红石信号触发不在本精简版本范围内（已移除 {@code neighborChanged} 注入）。
 * </p>
 */
@Mixin(value = FenceGateBlock.class, priority = 1001)
public class FenceGateBlockMixin {

    private static final String MODULE = "FenceGateBlockMixin";

    /** 保存点击前（原版切换前）的 OPEN 状态，用于判断是否发生了切换 */
    private static final ThreadLocal<Boolean> preOpen = new ThreadLocal<>();

    @SuppressWarnings("null")
    @Inject(method = "useWithoutItem", at = @At("HEAD"))
    private void youzaiworldcore$head(BlockState blockState, Level level, @NotNull BlockPos blockPos,
                                        Player player, BlockHitResult blockHitResult,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) {
            preOpen.remove();
            return;
        }
        preOpen.set(level.getBlockState(blockPos).getValue(BlockStateProperties.OPEN));
    }

    @SuppressWarnings("null")
    @Inject(method = "useWithoutItem", at = @At("RETURN"))
    private void youzaiworldcore$return(BlockState blockState, Level level, @NotNull BlockPos blockPos,
                                          Player player, BlockHitResult blockHitResult,
                                          CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) {
            preOpen.remove();
            return;
        }
        Boolean pre = preOpen.get();
        preOpen.remove();
        if (pre == null) {
            return;
        }
        boolean post = level.getBlockState(blockPos).getValue(BlockStateProperties.OPEN);
        if (post == pre) {
            // 未发生切换（如被红石供能锁定），不触发双开
            DebugLogger.debug(MODULE, "useWithoutItem: 状态未变化，跳过 pos=%s", blockPos);
            return;
        }
        DebugLogger.debug(MODULE, "useWithoutItem: pos=%s, targetOpen=%s", blockPos, post);
        DoubleDoorsHandler.onDoorClick(level, player, blockPos, post);
    }
}
