package top.csituka.youzaiworldcore.mixin.seat;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link Entity} 的私有字段 {@code vehicle} 供 {@code SeatEntity.mountPlayer()} 使用。
 */
@Mixin(Entity.class)
public interface EntityVehicleAccessor {

    @Accessor("vehicle")
    Entity youzaiworldcore$getVehicle();

    @Accessor("vehicle")
    void youzaiworldcore$setVehicle(Entity vehicle);
}
