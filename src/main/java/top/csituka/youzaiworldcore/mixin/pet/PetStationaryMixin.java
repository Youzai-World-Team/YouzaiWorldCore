package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.pet.PetStationaryAccess;
import top.csituka.youzaiworldcore.util.LoadedChunks;

@Mixin(Entity.class)
public abstract class PetStationaryMixin implements PetStationaryAccess {
    @Unique
    private static final double YZWC_PET_RANGE = 5.0;
    @Unique
    private @Nullable Vec3 yzwc$station;
    @Unique
    private boolean yzwc$adjustingPosition;

    @Override
    public void yzwc$setStation(@Nullable Vec3 center) {
        yzwc$station = center;
    }

    @Override
    public boolean yzwc$isStationary() {
        return yzwc$station != null;
    }

    @SuppressWarnings("null")
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 yzwc$limitPetMovement(Vec3 movement) {
        if (yzwc$station == null) {
            return movement;
        }
        Vec3 position = ((Entity) (Object) this).position();
        return yzwc$stationaryPosition(position.add(movement)).subtract(position);
    }

    // Riding, leashes and raw position updates must obey the same boundary as
    // navigation.
    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void yzwc$limitPetPosition(double x, double y, double z, CallbackInfo ci) {
        if (yzwc$station == null || yzwc$adjustingPosition) {
            return;
        }
        Vec3 safe = yzwc$stationaryPosition(new Vec3(x, y, z));
        if (safe.x != x || safe.y != y || safe.z != z) {
            yzwc$adjustingPosition = true;
            try {
                ((Entity) (Object) this).setPosRaw(safe.x, safe.y, safe.z);
            } finally {
                yzwc$adjustingPosition = false;
            }
            ci.cancel();
        }
    }

    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    private void yzwc$keepPetAtStation(TeleportTransition transition, CallbackInfoReturnable<Entity> ci) {
        if (yzwc$isStationary()) {
            ci.setReturnValue(null);
        }
    }

    @Unique
    private Vec3 yzwc$stationaryPosition(Vec3 requested) {
        Entity entity = (Entity) (Object) this;
        Vec3 center = yzwc$station;
        if (center == null) {
            return requested;
        }
        Vec3 offset = requested.subtract(center);
        Vec3 allowed = offset.lengthSqr() <= YZWC_PET_RANGE * YZWC_PET_RANGE ? requested
                : center.add(offset.normalize().scale(Math.nextDown(YZWC_PET_RANGE)));
        if (entity.level() instanceof ServerLevel level
                && LoadedChunks.get(level, BlockPos.containing(allowed)) == null) {
            return entity.position();
        }
        return allowed;
    }
}
