package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetEventHandler;

/**
 * Mixin — 拦截 {@link Wolf#getBreedOffspring(ServerLevel, AgeableMob)}
 * 以在幼狼生成时执行宠物繁殖继承逻辑。
 * <p>
 * 通过父狼的 {@link net.minecraft.world.entity.animal.Animal#getLoveCause()} 获取触发繁殖的玩家。
 * </p>
 */
@Mixin(Wolf.class)
public abstract class WolfBreedMixin {

    @Inject(method = "getBreedOffspring", at = @At("RETURN"))
    private void onGetBreedOffspring(ServerLevel level, AgeableMob otherParent,
                                     CallbackInfoReturnable<AgeableMob> cir) {
        AgeableMob baby = cir.getReturnValue();
        if (!(baby instanceof Wolf babyWolf) || !(otherParent instanceof Wolf otherWolf)) {
            return;
        }

        Wolf self = (Wolf) (Object) this;

        // 获取触发繁殖的玩家（由 Animal.setInLove(Player) 设置）
        ServerPlayer breeder = self.getLoveCause();
        if (breeder == null) {
            // 尝试从另一个父本获取
            breeder = otherWolf.getLoveCause();
        }
        if (breeder == null) {
            return; // 没有触发玩家，跳过
        }

        PetEventHandler.onBreed(self, otherWolf, babyWolf, breeder, level);
    }
}
