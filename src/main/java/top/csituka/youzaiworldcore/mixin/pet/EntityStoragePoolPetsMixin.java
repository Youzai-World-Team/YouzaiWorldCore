package top.csituka.youzaiworldcore.mixin.pet;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.LinkedHashSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolPets;
import top.csituka.youzaiworldcore.pet.PetUtils;

@Mixin(EntityStorage.class)
public abstract class EntityStoragePoolPetsMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @SuppressWarnings("null")
    @Inject(method = "loadEntities", at = @At("RETURN"), cancellable = true)
    private void youzaiworldcore$skipArchivedCopies(ChunkPos pos,
            CallbackInfoReturnable<CompletableFuture<ChunkEntities<Entity>>> cir) {
        cir.setReturnValue(cir.getReturnValue().thenApply(chunk -> {
            if (chunk.isEmpty()) {
                return chunk;
            }
            var roots = new LinkedHashSet<Entity>();
            chunk.getEntities().forEach(root -> {
                roots.add(root);
                for (Entity entity : root.getSelfAndPassengers().toList()) {
                    if (entity instanceof Mob pet && PetUtils.petType(pet) != null && !PoolPets.canLoad(level, pet)) {
                        roots.addAll(pet.getPassengers());
                        pet.ejectPassengers();
                        pet.stopRiding();
                        roots.remove(pet);
                    }
                }
            });
            return new ChunkEntities<>(pos, java.util.List.copyOf(roots));
        }));
    }

    @WrapOperation(method = "storeEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> youzaiworldcore$confirmPetWrite(SimpleRegionStorage storage, ChunkPos pos,
            CompoundTag data, Operation<CompletableFuture<Void>> original) {
        var result = original.call(storage, pos, data);
        PoolPets.afterEntityWrite(level.getServer(), data, result, () -> storage.synchronize(true));
        return result;
    }
}
