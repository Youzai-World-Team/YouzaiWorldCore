package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.pet.PetEventHandler;

/**
 * Mixin — 拦截 {@link TamableAnimal#tame(Player)} 以在驯服成功后注册宠物。
 * <p>
 * 仅在目标实体为 {@link Wolf} 时执行宠物注册逻辑。
 * </p>
 */
@Mixin(TamableAnimal.class)
public abstract class WolfTameMixin {

    @Inject(method = "tame", at = @At("RETURN"))
    private void onPostTame(Player player, CallbackInfo ci) {
        TamableAnimal self = (TamableAnimal) (Object) this;

        if (!(self instanceof Wolf wolf)) {
            return;
        }

        if (wolf.level().isClientSide()) {
            return;
        }

        if (!wolf.isTame()) {
            return;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        PetEventHandler.onTame(wolf, serverPlayer, (ServerLevel) wolf.level());
    }
}
