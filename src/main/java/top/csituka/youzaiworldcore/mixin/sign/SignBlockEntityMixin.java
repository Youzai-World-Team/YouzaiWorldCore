package top.csituka.youzaiworldcore.mixin.sign;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.sign.FlashingSign;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 为原版告示牌补充正面 / 背面的闪烁持久化状态。 */
@Mixin(SignBlockEntity.class)
public abstract class SignBlockEntityMixin implements FlashingSign {

    @Unique
    private boolean youzaiworldcore$frontFlashing;

    @Unique
    private boolean youzaiworldcore$backFlashing;

    @Override
    public boolean youzaiworldcore$isFlashing(boolean front) {
        return front ? youzaiworldcore$frontFlashing : youzaiworldcore$backFlashing;
    }

    @SuppressWarnings("null")
    @Override
    public boolean youzaiworldcore$setFlashing(boolean front, boolean flashing) {
        boolean old = youzaiworldcore$isFlashing(front);
        if (old == flashing) {
            return false;
        }

        if (front) {
            youzaiworldcore$frontFlashing = flashing;
        } else {
            youzaiworldcore$backFlashing = flashing;
        }

        SignBlockEntity sign = (SignBlockEntity) (Object) this;
        sign.setChanged();
        if (sign.getLevel() != null && !sign.getLevel().isClientSide()) {
            sign.getLevel().sendBlockUpdated(sign.getBlockPos(), sign.getBlockState(),
                    sign.getBlockState(), Block.UPDATE_ALL);
        }
        DebugLogger.stateChange("SignBlockEntity", "sign@" + sign.getBlockPos().toShortString(),
                front ? "frontFlashing" : "backFlashing", old, flashing);
        return true;
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void youzaiworldcore$saveFlashing(ValueOutput output, CallbackInfo callbackInfo) {
        output.putBoolean("YouzaiWorldCoreFrontFlashing", youzaiworldcore$frontFlashing);
        output.putBoolean("YouzaiWorldCoreBackFlashing", youzaiworldcore$backFlashing);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void youzaiworldcore$loadFlashing(ValueInput input, CallbackInfo callbackInfo) {
        youzaiworldcore$frontFlashing = input.getBooleanOr("YouzaiWorldCoreFrontFlashing", false);
        youzaiworldcore$backFlashing = input.getBooleanOr("YouzaiWorldCoreBackFlashing", false);
    }
}
