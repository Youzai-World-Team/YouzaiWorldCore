package top.csituka.youzaiworldcore.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import top.csituka.youzaiworldcore.config.DoubleDoorsState;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 双开门功能事件处理器（精简版）。
 * <p>
 * 仅支持「同材质木门 / 栅栏门」的点击双开，按玩家独立开关。
 * 红石触发、村民 AI、活板门及连锁开门均不在本版本范围内。
 * </p>
 * <p>
 * 触发逻辑见 Mixin：玩家点击门时，先由 {@code useWithoutItem} 完成原版开关，
 * 再由本处理器把<b>相邻同材质</b>的配对门同步为与被点击门相同的开合状态。
 * </p>
 */
public class DoubleDoorsHandler {

    public static final String MODULE = "DoubleDoorsHandler";

    private DoubleDoorsHandler() {
    }

    // ===== 注册 =====

    /**
     * 注册回调。本类由 Mixin 直接调用，无需向 Fabric 事件系统注册。
     */
    public static void register() {
        DebugLogger.info(MODULE, "双开门处理器已就绪（点击触发，按玩家开关，仅木门/栅栏门）");
    }

    // ===== 接口方法（由 Mixin 调用）=====

    /**
     * 玩家点击门/栅栏门后调用。
     * 此时原版逻辑已将被点击门切换为 {@code targetOpen} 状态。
     *
     * @param level       世界
     * @param player     操作玩家（不可为 null）
     * @param blockPos   被点击方块位置（门可能为上半部，内部会归一化）
     * @param targetOpen 被点击门切换后的开合状态（与玩家点击意图一致）
     */
    public static void onDoorClick(Level level, @Nullable Player player,
                                   BlockPos blockPos, boolean targetOpen) {
        if (level.isClientSide()) {
            return;
        }
        if (player == null) {
            return;
        }
        if (player.isCrouching()) {
            // 潜行时禁用双开，仅保留原版单开行为
            DebugLogger.debug(MODULE, "onDoorClick: 玩家 %s 潜行中，跳过双开",
                    player.getName().getString());
            return;
        }
        if (!DoubleDoorsState.isEnabled(player.getUUID())) {
            DebugLogger.debug(MODULE, "onDoorClick: 玩家 %s 未启用双开门，跳过",
                    player.getName().getString());
            return;
        }

        BlockState clickState = level.getBlockState(blockPos);
        if (!isSupportedDoor(clickState)) {
            return;
        }
        if (!canOpenByHand(clickState)) {
            // 铁门等无法徒手开启的方块：原版点击不会切换，也不会走到这里
            return;
        }

        DebugLogger.debug(MODULE, "onDoorClick: pos=%s, player=%s, targetOpen=%s",
                blockPos, player.getName().getString(), targetOpen);

        processDoor(player, level, blockPos, clickState, targetOpen);
    }

    // ===== 类型判定 =====

    /**
     * 判断是否为受支持的门类型（木门 / 栅栏门，不含活板门与铁门）。
     * 铁门虽是 {@link DoorBlock}，但因无法徒手开启而被 {@link #canOpenByHand} 排除。
     */
    private static boolean isSupportedDoor(BlockState blockState) {
        Block block = blockState.getBlock();
        return block instanceof DoorBlock || block instanceof FenceGateBlock;
    }

    /** 判断门是否可以徒手开启（过滤铁门等不可徒手操作的方块） */
    private static boolean canOpenByHand(BlockState blockState) {
        Block block = blockState.getBlock();
        if (block instanceof DoorBlock doorBlock) {
            return doorBlock.type().canOpenByHand();
        }
        // 栅栏门始终可徒手开启
        return block instanceof FenceGateBlock;
    }

    // ===== 核心算法 =====

    /**
     * 把与被点击门相邻的同材质配对门同步为 {@code targetOpen} 状态。
     */
    private static void processDoor(@Nullable Player player, Level level,
                                   BlockPos blockPos, BlockState blockState,
                                   boolean targetOpen) {
        DebugLogger.entering(MODULE, "processDoor",
                "pos=" + blockPos + ", targetOpen=" + targetOpen);

        Block block = blockState.getBlock();

        // 门（DoorBlock）可能有上下两半，统一到下半部分以便配对
        if (block instanceof DoorBlock) {
            if (blockState.getValue(DoorBlock.HALF).equals(DoubleBlockHalf.UPPER)) {
                blockPos = blockPos.below().immutable();
                blockState = level.getBlockState(blockPos);
                DebugLogger.debug(MODULE, "processDoor: 归一化到下半部分 %s", blockPos);
            }
        }

        if (!blockState.hasProperty(BlockStateProperties.OPEN)) {
            DebugLogger.debug(MODULE, "processDoor: 方块无 OPEN 属性，返回");
            DebugLogger.exiting(MODULE, "processDoor", "false (no OPEN)");
            return;
        }

        // 栅栏门需要保存朝向，使相邻栅栏门开合方向一致
        Direction facing = null;
        if (block instanceof FenceGateBlock) {
            facing = blockState.getValue(FenceGateBlock.FACING);
        }

        // 门为双格高度（已归一化到下半部，仅需水平搜索）；
        // 栅栏门单格，需同时搜索上下一层以覆盖相邻配对
        int yOffset = (block instanceof DoorBlock) ? 0 : 1;

        // 在同类型、同材质（显示名相同）的相邻门中搜索配对
        Component doorName = block.getName();
        List<BlockPos> partnerList = findPartnerDoors(
                level, blockPos, block, doorName, yOffset);

        if (partnerList.isEmpty()) {
            DebugLogger.debug(MODULE, "processDoor: 未找到配对门，返回");
            DebugLogger.exiting(MODULE, "processDoor", "false (no partner)");
            return;
        }

        DebugLogger.info(MODULE, "processDoor: 找到 %d 个配对门，统一设置 OPEN=%s",
                partnerList.size(), targetOpen);

        for (BlockPos partnerPos : partnerList) {
            BlockState partnerState = level.getBlockState(partnerPos);
            Block partnerBlock = partnerState.getBlock();

            if (partnerBlock instanceof DoorBlock) {
                level.setBlock(partnerPos,
                        partnerState.setValue(DoorBlock.OPEN, targetOpen), 10);
                DebugLogger.debug(MODULE, "processDoor: 同步门 %s -> OPEN=%s", partnerPos, targetOpen);
            } else if (partnerBlock instanceof FenceGateBlock) {
                Direction partnerFacing = partnerState.getValue(FenceGateBlock.FACING);
                // 只在朝向相同或相反的栅栏门之间同步（保持开合方向一致）
                if (partnerFacing.equals(facing) || partnerFacing.getOpposite().equals(facing)) {
                    level.setBlock(partnerPos,
                            partnerState.setValue(FenceGateBlock.OPEN, targetOpen)
                                    .setValue(FenceGateBlock.FACING, facing),
                            10);
                    DebugLogger.debug(MODULE, "processDoor: 同步栅栏门 %s -> OPEN=%s, FACING=%s",
                            partnerPos, targetOpen, facing);
                }
            }
        }

        // 玩家操作时触发挥手动效
        if (player != null) {
            player.swing(InteractionHand.MAIN_HAND);
        }

        DebugLogger.exiting(MODULE, "processDoor", "true (synced)");
    }

    /**
     * 在以 {@code origin} 为中心的 3×3×(1+yOffset×2) 范围内搜索相邻同类型、同材质的配对门。
     * <p>
     * 不做递归（仅双开：找到直接相邻的配对门即可）。
     * 配对条件：① 同属 {@link DoorBlock} 或同属 {@link FenceGateBlock}；
     * ② 拥有 {@code OPEN} 属性；③ 显示名（即材质/类型）相同。
     * </p>
     */
    private static List<BlockPos> findPartnerDoors(Level level, BlockPos origin,
                                                   Block block, Component doorName,
                                                   int yOffset) {
        List<BlockPos> result = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -yOffset; y <= yOffset; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue; // 跳过自身
                    }
                    BlockPos pos = origin.offset(x, y, z).immutable();
                    BlockState state = level.getBlockState(pos);
                    Block b = state.getBlock();

                    boolean sameType = (block instanceof DoorBlock && b instanceof DoorBlock)
                            || (block instanceof FenceGateBlock && b instanceof FenceGateBlock);
                    if (!sameType) {
                        continue;
                    }
                    if (!state.hasProperty(BlockStateProperties.OPEN)) {
                        continue;
                    }
                    // 同材质判定：显示名相同
                    if (!b.getName().equals(doorName)) {
                        continue;
                    }
                    result.add(pos);
                    DebugLogger.debug(MODULE, "findPartnerDoors: 找到配对门 %s", pos);
                }
            }
        }
        return result;
    }
}
