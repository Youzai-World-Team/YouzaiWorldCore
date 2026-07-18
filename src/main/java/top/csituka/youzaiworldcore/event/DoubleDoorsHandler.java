package top.csituka.youzaiworldcore.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import top.csituka.youzaiworldcore.config.DoubleDoorsConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 双开门/双活板门/双栅栏门功能的事件处理器。
 * <p>
 * 实现逻辑移植自 Serilum 的 Double Doors（已取得作者许可，无需署名）。
 * 核心策略：玩家点击或红石/村民触发门状态变化时，通过递归搜索寻找
 * 同类型、同名称的相邻方块，统一设置其 OPEN 状态。
 * </p>
 */
public class DoubleDoorsHandler {

    public static final String MODULE = "DoubleDoorsHandler";

    /** 重入保护标志：防止连锁开门过程中循环触发 */
    private static boolean processing = false;

    private DoubleDoorsHandler() {
    }

    // ===== 注册 =====

    /**
     * 注册事件回调。
     * 注意：本类不直接注册到 Fabric API 的事件系统，
     * 而是通过 Mixin 回调调用 {@link #onDoorClick}、
     * {@link #onSetOpen}、{@link #onNeighborChanged} 方法。
     */
    public static void register() {
        DebugLogger.info(MODULE, "双开门处理器已就绪（由 Mixin 回调驱动）");
    }

    // ===== 接口方法（由 Mixin 调用）=====

    /**
     * 玩家点击门/栅栏门/活板门时调用。
     * 从 {@code useWithoutItem} 的 Mixin 回调。
     */
    public static void onDoorClick(Level level, @Nullable Player player,
                                    BlockPos blockPos, BlockHitResult blockHitResult) {
        if (level.isClientSide()) {
            return;
        }
        if (player != null && player.isCrouching()) {
            // 潜行时禁用双开，使用原版单开行为
            return;
        }

        BlockState clickState = level.getBlockState(blockPos);
        if (!isDoorBlock(clickState)) {
            return;
        }
        if (!canOpenByHand(clickState)) {
            // 铁门等无法徒手开启的方块，点击动作本身已由 useWithoutItem 处理
            return;
        }

        DebugLogger.debug(MODULE, "onDoorClick: pos=%s, player=%s", blockPos, player != null ? player.getName().getString() : "null");

        try {
            processing = true;
            processDoor(player, level, blockPos, clickState, null);
        } finally {
            processing = false;
        }
    }

    /**
     * 红石更新导致门（DoorBlock）状态变化时调用。
     * 从 {@code setOpen} 方法 RETURN 的 Mixin 回调。
     *
     * @param level  世界
     * @param blockPos 门的位置
     * @param openState 当前的 BlockState（已包含更新后的 OPEN 值）
     */
    public static void onSetOpen(Level level, BlockPos blockPos, BlockState openState) {
        if (level.isClientSide()) {
            return;
        }
        if (processing) {
            return;
        }
        if (!isDoorBlock(openState)) {
            return;
        }

        DebugLogger.debug(MODULE, "onSetOpen: pos=%s", blockPos);

        try {
            processing = true;
            boolean isOpen = openState.getValue(BlockStateProperties.OPEN);
            processDoor(null, level, blockPos, openState, isOpen);
        } finally {
            processing = false;
        }
    }

    /**
     * 红石更新导致栅栏门/活板门状态变化时调用。
     * 从 {@code neighborChanged} 方法 RETURN 的 Mixin 回调。
     *
     * @param level  世界
     * @param blockPos 方块位置
     * @param newState 当前的 BlockState
     */
    public static void onNeighborChanged(Level level, BlockPos blockPos, BlockState newState) {
        if (level.isClientSide()) {
            return;
        }
        if (processing) {
            return;
        }
        if (!isDoorBlock(newState)) {
            return;
        }

        DebugLogger.debug(MODULE, "onNeighborChanged: pos=%s", blockPos);

        try {
            processing = true;
            boolean isOpen = newState.getValue(BlockStateProperties.OPEN);
            processDoor(null, level, blockPos, newState, isOpen);
        } finally {
            processing = false;
        }
    }

    // ===== 核心算法 =====

    /**
     * 判断指定方块是否为受支持的门类方块，并检查对应配置是否启用。
     */
    public static boolean isDoorBlock(BlockState blockState) {
        Block block = blockState.getBlock();
        return (block instanceof DoorBlock && DoubleDoorsConfig.isEnableDoors())
                || (block instanceof TrapDoorBlock && DoubleDoorsConfig.isEnableTrapdoors())
                || (block instanceof FenceGateBlock && DoubleDoorsConfig.isEnableFenceGates());
    }

    /**
     * 判断指定门方块是否可徒手操作（即不需要红石信号）。
     * 仅 {@code onDoorClick} 路径使用，用于过滤不可徒手操作的方块类型。
     */
    private static boolean canOpenByHand(BlockState blockState) {
        Block block = blockState.getBlock();
        if (block instanceof DoorBlock doorBlock) {
            return doorBlock.type().canOpenByHand();
        }
        // 栅栏门和活板门在 Minecraft 中始终可徒手操作
        return true;
    }

    /**
     * 处理双开门逻辑：找到所有相连的同类方块并统一打开/关闭。
     *
     * @param player    操作玩家（可能为 null，红石/村民触发时）
     * @param level     世界
     * @param blockPos  点击/变化的方块位置
     * @param blockState 当前的方块状态
     * @param isOpen    目标开关状态（null 表示切换——读取当前状态的 OPEN 值再取反）
     * @return 是否有其他方块被同步操作（返回 {@code true} 表示找到了配对门）
     */
    private static boolean processDoor(@Nullable Player player, Level level,
                                        BlockPos blockPos, BlockState blockState,
                                        @Nullable Boolean isOpen) {
        DebugLogger.entering(MODULE, "processDoor", "pos=" + blockPos + ", isOpen=" + isOpen);

        Block block = blockState.getBlock();

        // 门（DoorBlock）可能有上下两半，统一到下半部分
        if (block instanceof DoorBlock) {
            if (blockState.getValue(DoorBlock.HALF).equals(DoubleBlockHalf.UPPER)) {
                blockPos = blockPos.below().immutable();
                blockState = level.getBlockState(blockPos);
                DebugLogger.debug(MODULE, "processDoor: 归一化到下半部分 %s", blockPos);
            }
        }

        // 验证方块是否有 OPEN 属性
        if (!blockState.hasProperty(BlockStateProperties.OPEN)) {
            DebugLogger.debug(MODULE, "processDoor: 方块无 OPEN 属性，返回");
            DebugLogger.exiting(MODULE, "processDoor", "false (no OPEN)");
            return false;
        }

        // 计算目标状态
        if (isOpen == null) {
            // 切换：取当前 OPEN 状态的相反值
            isOpen = !blockState.getValue(BlockStateProperties.OPEN);
            DebugLogger.debug(MODULE, "processDoor: 切换模式，目标 isOpen=%s", isOpen);
        }

        // 栅栏门需要保存朝向，使相邻栅栏门的开门方向一致
        Direction facing = null;
        if (block instanceof FenceGateBlock) {
            facing = blockState.getValue(FenceGateBlock.FACING);
        }

        // 搜索 Y 偏移：门（DoorBlock）有双格高度，已经归一化到下半部；
        // 栅栏门/活板门只有一格，需同时搜索上下一层
        int yOffset = (block instanceof DoorBlock) ? 0 : 1;

        // 递归搜索所有相连的同名方块
        List<BlockPos> posToOpenList = recursivelyOpenDoors(
                new ArrayList<>(List.of(blockPos.immutable())),
                new ArrayList<>(),
                level, blockPos, blockPos, block, yOffset
        );

        if (posToOpenList.size() <= 1) {
            DebugLogger.debug(MODULE, "processDoor: 未找到配对方块，返回");
            DebugLogger.exiting(MODULE, "processDoor", "false (no partner)");
            return false;
        }

        DebugLogger.info(MODULE, "processDoor: 找到 %d 个方块需要同步", posToOpenList.size());

        // 遍历并同步所有找到的方块
        for (BlockPos toOpenBlockPos : posToOpenList) {
            if (toOpenBlockPos.equals(blockPos)) {
                continue; // 跳过触发源方块（其状态已由原版逻辑或调用方处理）
            }

            BlockState oBlockState = level.getBlockState(toOpenBlockPos);
            Block oBlock = oBlockState.getBlock();

            if (block instanceof DoorBlock) {
                if (!DoubleDoorsConfig.isEnableDoors()) {
                    continue;
                }
                level.setBlock(toOpenBlockPos,
                        oBlockState.setValue(DoorBlock.OPEN, isOpen),
                        10);
                DebugLogger.debug(MODULE, "processDoor: 同步门 %s -> OPEN=%s", toOpenBlockPos, isOpen);
            } else if (block instanceof TrapDoorBlock) {
                if (!DoubleDoorsConfig.isEnableTrapdoors()) {
                    continue;
                }
                level.setBlock(toOpenBlockPos,
                        oBlockState.setValue(BlockStateProperties.OPEN, isOpen),
                        10);
                DebugLogger.debug(MODULE, "processDoor: 同步活板门 %s -> OPEN=%s", toOpenBlockPos, isOpen);
            } else if (block instanceof FenceGateBlock) {
                if (!DoubleDoorsConfig.isEnableFenceGates()) {
                    continue;
                }
                Direction oFacing = oBlockState.getValue(FenceGateBlock.FACING);
                // 只在朝向相同或相反的栅栏门之间同步（保持开门方向一致）
                if (oFacing.equals(facing) || oFacing.getOpposite().equals(facing)) {
                    level.setBlock(toOpenBlockPos,
                            oBlockState.setValue(DoorBlock.OPEN, isOpen)
                                    .setValue(FenceGateBlock.FACING, facing),
                            10);
                    DebugLogger.debug(MODULE, "processDoor: 同步栅栏门 %s -> OPEN=%s, FACING=%s",
                            toOpenBlockPos, isOpen, facing);
                }
            }
        }

        // 玩家操作时触发挥手动效
        if (player != null) {
            player.swing(InteractionHand.MAIN_HAND);
        }

        DebugLogger.exiting(MODULE, "processDoor", "true (synced)");
        return true;
    }

    /**
     * 递归搜索所有相连的同类门方块。
     *
     * @param posToOpenList  已找到的需要同步的方块列表
     * @param ignoreOpenList 已检查过的非门方块（暂未使用）
     * @param level          世界
     * @param originalBlockPos 原始触发位置（用于距离限制）
     * @param blockPos       当前搜索位置
     * @param block          原始方块实例
     * @param yOffset        垂直搜索范围（门为 0，栅栏门/活板门为 1）
     * @return 所有需要同步的方块位置列表
     */
    private static List<BlockPos> recursivelyOpenDoors(
            List<BlockPos> posToOpenList,
            List<BlockPos> ignoreOpenList,
            Level level,
            BlockPos originalBlockPos,
            BlockPos blockPos,
            Block block,
            int yOffset) {

        DebugLogger.entering(MODULE, "recursivelyOpenDoors",
                "pos=" + blockPos + ", origin=" + originalBlockPos + ", yOffset=" + yOffset);

        // 搜索 3×3×（1+yOffset×2）范围
        for (int x = -1; x <= 1; x++) {
            for (int y = -yOffset; y <= yOffset; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue; // 跳过自身
                    }

                    BlockPos bpa = blockPos.offset(x, y, z).immutable();

                    if (posToOpenList.contains(bpa)) {
                        continue; // 已在列表中
                    }

                    // 距离限制（欧氏平方距离）
                    int maxDist = DoubleDoorsConfig.getRecursiveOpeningMaxBlocksDistance();
                    double distSq = originalBlockPos.distSqr(bpa);
                    if (distSq > (double) maxDist * maxDist) {
                        continue;
                    }

                    BlockState oBlockState = level.getBlockState(bpa);
                    if (!isDoorBlock(oBlockState)) {
                        continue;
                    }

                    // 按显示名称匹配同类方块（原版行为：同材质/类型门的翻译键相同）
                    if (oBlockState.getBlock().getName().equals(block.getName())) {
                        posToOpenList.add(bpa.immutable());
                        DebugLogger.debug(MODULE, "recursivelyOpenDoors: 找到匹配方块 %s (距离=%d)",
                                bpa, (int) Math.sqrt(distSq));

                        if (DoubleDoorsConfig.isEnableRecursiveOpening()) {
                            recursivelyOpenDoors(posToOpenList, ignoreOpenList,
                                    level, originalBlockPos, bpa, block, yOffset);
                        }
                    }
                }
            }
        }

        DebugLogger.exiting(MODULE, "recursivelyOpenDoors", "找到 " + posToOpenList.size() + " 个方块");
        return posToOpenList;
    }
}
