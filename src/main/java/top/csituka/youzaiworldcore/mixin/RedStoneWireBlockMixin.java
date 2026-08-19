package top.csituka.youzaiworldcore.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.block.NotGateRedstoneRepeaterBlock;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 限制红石线与非门红石中继器的连接方向。
 * <p>
 * 26.2 移除了 {@code Block.canConnectRedstone} 钩子：红石线
 * {@code RedStoneWireBlock.shouldConnectTo(state, direction)} 对信号源方块
 * （{@code isSignalSource() == true}）无条件返回 {@code true}，
 * 导致红石线会从<b>所有水平方向</b>连向本元件（左右两侧也被接线）。
 * <p>
 * 本 Mixin 在 {@code shouldConnectTo(BlockState, Direction)} 的 {@code RETURN} 处拦截：
 * <ul>
 *   <li>{@code direction == null}（红石线向上/向下连接判断）：返回 {@code false}，
 *       元件不向垂直方向输出，也不需要被红石线垂直连接。</li>
 *   <li>{@code direction != null}（水平方向）：仅当 {@code direction} 等于元件的
 *       {@code FACING}（输出端，贴图上方）或 {@code FACING.getOpposite()}（输入端，
 *       贴图下方）时返回 {@code true}；左右两侧（{@code getClockWise} /
 *       {@code getCounterClockWise}）返回 {@code false}，不连接红石线。</li>
 * </ul>
 * <p>
 * 放在 <b>main</b> mixin 列表：连接状态（{@code RedstoneSide}）在服务端用于信号评估、
 * 在客户端用于接线渲染，双端都要生效。
 */
@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {

    /**
     * 拦截红石线的水平/垂直连接判定。
     *
     * @param state     相邻方块状态（可能是非门红石中继器）
     * @param direction 红石线到该方块的方向；{@code null} 表示无方向查询（上下连接判断）
     * @param cir       Mixin 回调，可改写返回值
     */
    @Inject(method = "shouldConnectTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
            at = @At("RETURN"), cancellable = true)
    private static void yzwc$restrictNotGateConnection(BlockState state, Direction direction,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (!(state.getBlock() instanceof NotGateRedstoneRepeaterBlock)) {
            return;
        }

        if (direction == null) {
            // 红石线向上/向下连接判断：元件只水平输入/输出，垂直方向不连接。
            cir.setReturnValue(false);
            return;
        }

        // 水平方向：仅输入端（贴图下方 = FACING 反方向）与输出端（贴图上方 = FACING）连接，
        // 左右两侧不连接红石粉。
        Direction facing = state.getValue(NotGateRedstoneRepeaterBlock.FACING);
        boolean connect = direction == facing || direction == facing.getOpposite();

        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug("RedStoneWireBlockMixin",
                    "yzwc$restrictNotGateConnection: facing=%s, direction=%s, connect=%s",
                    facing, direction, connect);
        }

        cir.setReturnValue(connect);
    }
}
