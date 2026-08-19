package top.csituka.youzaiworldcore.mixin.sign;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.InkSacItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.sign.FlashingSign;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 让普通墨囊在原版告示牌上同时取消闪烁状态。 */
@Mixin(InkSacItem.class)
public abstract class InkSacItemMixin {

    @Inject(method = "tryApplyToSign", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$removeFlashing(Level level, SignBlockEntity sign, boolean front,
            ItemStack stack, Player player, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (!(sign instanceof FlashingSign flashingSign)
                || !flashingSign.youzaiworldcore$isFlashing(front)) {
            return;
        }

        flashingSign.youzaiworldcore$setFlashing(front, false);
        DebugLogger.info("InkSacItem", "普通墨囊取消告示牌闪烁：pos=%s, front=%s",
                sign.getBlockPos().toShortString(), front);
        // 有荧光文字时继续执行原版逻辑，让它同时取消发光；没有荧光文字时，
        // 原版逻辑会返回 false，此处直接完成一次有效的「取消闪烁」操作。
        if (!sign.getText(front).hasGlowingText()) {
            level.playSound(null, sign.getBlockPos(), SoundEvents.INK_SAC_USE,
                    SoundSource.BLOCKS, 1.0f, 1.0f);
            callbackInfo.setReturnValue(true);
        }
    }
}
