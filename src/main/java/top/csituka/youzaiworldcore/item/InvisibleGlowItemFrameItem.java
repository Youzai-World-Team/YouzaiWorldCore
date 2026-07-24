package top.csituka.youzaiworldcore.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 隐形发光物品展示框物品。
 * <p>
 * 使用方式与发光物品展示框相同，但放置后展示框实体为隐形且发光状态。
 * 合成配方：隐形物品展示框 + 荧石粉。
 * </p>
 */
@SuppressWarnings("null")
public class InvisibleGlowItemFrameItem extends Item {

    public InvisibleGlowItemFrameItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        DebugLogger.entering("InvisibleGlowItemFrameItem", "useOn");

        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        BlockPos placePos = clickedPos.relative(clickedFace);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        // ===== 检查放置位置是否可通行 =====
        BlockState placeState = level.getBlockState(placePos);
        if (!placeState.isAir() && !placeState.canBeReplaced()) {
            DebugLogger.branch("InvisibleGlowItemFrameItem", "place pos is replaceable", false,
                    "block=" + placeState.getBlock());
            return InteractionResult.FAIL;
        }

        // ===== 计算展示框的附着位置 =====
        BlockPos framePos = placePos;
        Direction attachmentDir = clickedFace;

        // ===== 创建发光物品展示框实体 =====
        GlowItemFrame frame = new GlowItemFrame(level, framePos, attachmentDir);

        // ===== 检查实体是否能附着（survives）=====
        if (!frame.survives()) {
            DebugLogger.branch("InvisibleGlowItemFrameItem", "frame survives", false,
                    "pos=" + framePos + ", dir=" + attachmentDir);
            DebugLogger.exiting("InvisibleGlowItemFrameItem", "useOn", "FAIL (cannot attach)");
            return InteractionResult.FAIL;
        }

        // ===== 检查碰撞（与其他展示框/实体不重叠）=====
        AABB frameBox = frame.getBoundingBox();
        if (!level.getEntities(frame, frameBox).isEmpty()) {
            DebugLogger.branch("InvisibleGlowItemFrameItem", "no collision with other entities", false,
                    "bbox=" + frameBox);
            DebugLogger.exiting("InvisibleGlowItemFrameItem", "useOn", "FAIL (collision)");
            return InteractionResult.FAIL;
        }

        // ===== 服务端执行 =====
        if (level.isClientSide()) {
            DebugLogger.branch("InvisibleGlowItemFrameItem", "is server side", false, "客户端 SUCCESS");
            return InteractionResult.SUCCESS;
        }

        // 设置展示框为隐形
        frame.setInvisible(true);

        // 添加到世界
        boolean added = level.addFreshEntity(frame);
        if (!added) {
            DebugLogger.warn("InvisibleGlowItemFrameItem", "无法将隐形发光展示框添加到世界，pos=%s", framePos);
            DebugLogger.exiting("InvisibleGlowItemFrameItem", "useOn", "FAIL (add entity failed)");
            return InteractionResult.FAIL;
        }

        // 播放放置音效（使用 GlowItemFrame 自身的 getPlaceSound()）
        frame.playPlacementSound();

        // ===== 消耗物品（非创造模式）=====
        if (player != null && !player.isCreative()) {
            stack.shrink(1);
            player.getInventory().setChanged();
        }

        DebugLogger.info("InvisibleGlowItemFrameItem", "隐形发光展示框已放置：玩家=%s, pos=%s, dir=%s",
                player != null ? player.getName().getString() : "?",
                framePos, attachmentDir);
        DebugLogger.exiting("InvisibleGlowItemFrameItem", "useOn", "SUCCESS");
        return InteractionResult.SUCCESS;
    }
}
