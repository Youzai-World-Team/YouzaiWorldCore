package top.csituka.youzaiworldcore.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignApplicator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import top.csituka.youzaiworldcore.sign.FlashingSign;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 闪烁墨染：让原版告示牌文字按 20 tick 周期闪烁。
 * <p>
 * 原版 {@link net.minecraft.world.level.block.SignBlock} 会在调用本类前检查
 * 涂蜡、权限、距离和其他玩家编辑状态，因此这里仅处理实际状态变更。
 */
@SuppressWarnings("null")
public class FlashingInkSacItem extends Item implements SignApplicator {

    private static final String MODULE = "FlashingInkSacItem";

    public FlashingInkSacItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean tryApplyToSign(Level level, SignBlockEntity sign, boolean front,
            ItemStack stack, Player player) {
        if (!(sign instanceof FlashingSign flashingSign)
                || sign.isWaxed()
                || !player.mayBuild()
                || (sign.getPlayerWhoMayEdit() != null
                        && !sign.getPlayerWhoMayEdit().equals(player.getUUID()))
                || flashingSign.youzaiworldcore$isFlashing(front)) {
            return false;
        }

        if (!flashingSign.youzaiworldcore$setFlashing(front, true)) {
            return false;
        }

        level.playSound(null, sign.getBlockPos(), SoundEvents.GLOW_INK_SAC_USE,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        DebugLogger.info(MODULE, "已启用原版告示牌闪烁：pos=%s, front=%s",
                sign.getBlockPos().toShortString(), front);
        return true;
    }
}
