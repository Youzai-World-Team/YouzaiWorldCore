package top.csituka.youzaiworldcore.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 隐形物品展示框物品。
 * <p>
 * 使用方式与普通物品展示框相同，但放置后展示框实体为隐形状态。
 * 合成配方：普通物品展示框 + 幻翼膜。
 * </p>
 */
@SuppressWarnings("null")
public class InvisibleItemFrameItem extends Item {

    public InvisibleItemFrameItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        DebugLogger.entering("InvisibleItemFrameItem", "useOn");

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos placePos = clickedPos.relative(clickedFace);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // ===== 检查放置位置是否可通行 =====
        BlockState placeState = level.getBlockState(placePos);
        if (!placeState.isAir() && !placeState.canBeReplaced()) {
            DebugLogger.branch("InvisibleItemFrameItem", "place pos is replaceable", false,
                    "block=" + placeState.getBlock());
            // 如果位置被方块占据，尝试在相邻的可替换方块上放置
            return InteractionResult.FAIL;
        }

        // ===== 计算展示框的附着位置 =====
        // 展示框的附着面即点击的面，位置在点击面外侧的空气处
        BlockPos framePos = placePos;
        Direction attachmentDir = clickedFace;

        // ===== 创建物品展示框实体 =====
        ItemFrame frame = new ItemFrame(level, framePos, attachmentDir);

        // ===== 检查实体是否能附着（survives）=====
        if (!frame.survives()) {
            DebugLogger.branch("InvisibleItemFrameItem", "frame survives", false,
                    "pos=" + framePos + ", dir=" + attachmentDir);
            DebugLogger.exiting("InvisibleItemFrameItem", "useOn", "FAIL (cannot attach)");
            return InteractionResult.FAIL;
        }

        // ===== 检查碰撞（与其他展示框/实体不重叠）=====
        AABB frameBox = frame.getBoundingBox();
        if (!level.getEntities(frame, frameBox).isEmpty()) {
            DebugLogger.branch("InvisibleItemFrameItem", "no collision with other entities", false,
                    "bbox=" + frameBox);
            DebugLogger.exiting("InvisibleItemFrameItem", "useOn", "FAIL (collision)");
            return InteractionResult.FAIL;
        }

        // ===== 服务端执行 =====
        if (level.isClientSide()) {
            DebugLogger.branch("InvisibleItemFrameItem", "is server side", false, "客户端 SUCCESS");
            return InteractionResult.SUCCESS;
        }

        // 设置展示框为隐形
        frame.setInvisible(true);

        // 添加到世界
        boolean added = level.addFreshEntity(frame);
        if (!added) {
            DebugLogger.warn("InvisibleItemFrameItem", "无法将隐形展示框添加到世界，pos=%s", framePos);
            DebugLogger.exiting("InvisibleItemFrameItem", "useOn", "FAIL (add entity failed)");
            return InteractionResult.FAIL;
        }

        // 播放放置音效
        level.playSound(null, framePos, SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS,
                1.0F, 1.0F);

        // ===== 消耗物品（非创造模式）=====
        if (player != null && !player.isCreative()) {
            stack.shrink(1);
            player.getInventory().setChanged();
        }

        DebugLogger.info("InvisibleItemFrameItem", "隐形展示框已放置：玩家=%s, pos=%s, dir=%s",
                player != null ? player.getName().getString() : "?",
                framePos, attachmentDir);
        DebugLogger.exiting("InvisibleItemFrameItem", "useOn", "SUCCESS");
        return InteractionResult.SUCCESS;
    }
}
