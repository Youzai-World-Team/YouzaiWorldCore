package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 农作物收获经验掉落事件处理器。
 * <p>
 * 当玩家破坏完全成熟的农作物时，额外掉落经验球。
 * 支持作物：小麦、胡萝卜、土豆、甜菜根、可可豆、下界疣、西瓜、南瓜。
 * </p>
 */
@SuppressWarnings("null")
public class CropXpHandler {

    private static final CropXpHandler INSTANCE = new CropXpHandler();
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/CropXp");

    /** 每次成熟作物收获掉落的经验值 */
    private static final int XP_PER_CROP = 1;

    private CropXpHandler() {
    }

    /**
     * {@link PlayerBlockBreakEvents#AFTER} 回调。
     */
    private void onBlockBreak(Level level, Player player, BlockPos pos, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        Block block = state.getBlock();

        // 检查是否为成熟作物
        if (!isFullyMature(state, block)) {
            return;
        }

        // 掉落经验球
        ServerLevel serverLevel = (ServerLevel) level;
        ExperienceOrb xpOrb = new ExperienceOrb(serverLevel,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                XP_PER_CROP);
        serverLevel.addFreshEntity(xpOrb);

        LOGGER.debug("作物收获经验掉落: {} at {} by {}",
                block.getName().getString(), pos, player.getName().getString());
    }

    /**
     * 判断方块状态是否为完全成熟的作物。
     * <ul>
     * <li>{@link CropBlock} 及其子类（小麦、胡萝卜、土豆、甜菜根）使用 {@code isMaxAge}</li>
     * <li>{@link NetherWartBlock} 检查 AGE == MAX_AGE</li>
     * <li>{@link CocoaBlock} 检查 AGE == MAX_AGE</li>
     * <li>西瓜 / 南瓜：始终视为成熟</li>
     * </ul>
     */
    private static boolean isFullyMature(BlockState state, Block block) {
        if (block instanceof CropBlock cropBlock) {
            return cropBlock.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) == NetherWartBlock.MAX_AGE;
        }
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) == CocoaBlock.MAX_AGE;
        }
        if (block == Blocks.MELON || block == Blocks.PUMPKIN) {
            return true;
        }
        return false;
    }

    /**
     * 注册事件处理器。
     */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(INSTANCE::onBlockBreak);
        LOGGER.info("农作物收获经验掉落事件处理器已注册");
    }
}
