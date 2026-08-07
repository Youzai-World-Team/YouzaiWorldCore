package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.config.FunctionToggleManager;

/**
 * 梯子向下延展事件处理器。
 * <p>
 * 玩家手持梯子物品并潜行时，右键点击已有的梯子方块，
 * 自动向下延伸放置梯子（仅服务端处理）。
 * 支持空中悬空延展：若下方无支撑方块，梯子仍可放置。
 * </p>
 */
@SuppressWarnings("null")
public class LadderExtendHandler {

    private static final LadderExtendHandler INSTANCE = new LadderExtendHandler();
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/LadderExtend");
    private static final int MAX_EXTEND = 20;

    private LadderExtendHandler() {
    }

    /**
     * {@link UseBlockCallback} 回调。
     */
    private InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
            BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (!FunctionToggleManager.isEnabled(player.getUUID(), FunctionToggleManager.KEY_LADDER)) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!player.isCrouching()) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);

        // 仅对梯子类型的方块生效
        if (!(state.getBlock() instanceof LadderBlock)) {
            return InteractionResult.PASS;
        }

        // 检查手持物品是否为梯子
        ItemStack heldItem = player.getItemInHand(hand);
        if (!heldItem.is(Items.LADDER)) {
            return InteractionResult.PASS;
        }

        // 确定梯子朝向
        Direction facing = state.getValue(LadderBlock.FACING);

        // 向下延伸梯子
        int placed = 0;
        for (int i = 1; i <= MAX_EXTEND; i++) {
            BlockPos belowPos = pos.below(i);

            // 超出世界底部则停止
            if (belowPos.getY() < level.getMinY()) {
                break;
            }

            BlockState belowState = level.getBlockState(belowPos);

            // 目标位置必须为空气或可替换方块
            if (!belowState.isAir() && !belowState.canBeReplaced()) {
                break;
            }

            // 消耗物品（生存模式）
            if (!player.getAbilities().instabuild) {
                if (heldItem.getCount() <= 0) {
                    break;
                }
                heldItem.shrink(1);
            }

            // 放置梯子
            BlockState ladderState = Blocks.LADDER.defaultBlockState()
                    .setValue(LadderBlock.FACING, facing);
            level.setBlock(belowPos, ladderState, Block.UPDATE_ALL_IMMEDIATE);

            // 播放放置音效
            level.playSound(null, belowPos, SoundEvents.LADDER_PLACE,
                    SoundSource.BLOCKS, 1.0f, 1.0f);

            placed++;
        }

        if (placed > 0) {
            LOGGER.info("梯子向下延展 {} 格，位置: {}", placed, pos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    /**
     * 注册事件处理器。
     */
    public static void register() {
        UseBlockCallback.EVENT.register(INSTANCE::onUseBlock);
        LOGGER.info("梯子向下延展事件处理器已注册");
    }
}
