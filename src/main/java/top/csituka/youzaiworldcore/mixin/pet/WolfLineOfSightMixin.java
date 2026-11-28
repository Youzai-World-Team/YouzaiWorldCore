package top.csituka.youzaiworldcore.mixin.pet;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.pet.PetInternalTags;
import top.csituka.youzaiworldcore.util.LoadedChunks;

@Mixin(LivingEntity.class)
public abstract class WolfLineOfSightMixin {
    @SuppressWarnings("null")
    @WrapOperation(method = "hasLineOfSight(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;D)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private BlockHitResult yzwc$clipPetSight(Level level, ClipContext context, Operation<BlockHitResult> original) {
        if (level instanceof ServerLevel serverLevel && (Object) this instanceof Wolf wolf
                && wolf.entityTags().contains(PetInternalTags.TAG_PET_MARKER)) {
            return LoadedChunks.collisionView(serverLevel).clip(context);
        }
        return original.call(level, context);
    }
}
