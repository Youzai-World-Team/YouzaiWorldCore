package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractHorse.class)
public interface HorseOwnerAccessor {
    @Accessor("owner")
    void yzwc$setPetOwner(@Nullable EntityReference<LivingEntity> owner);
}
