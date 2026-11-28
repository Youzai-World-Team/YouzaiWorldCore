package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayer.class)
public interface PetShoulderAccessor {
    @Invoker("setShoulderEntityLeft")
    void yzwc$setPetShoulderLeft(CompoundTag data);

    @Invoker("setShoulderEntityRight")
    void yzwc$setPetShoulderRight(CompoundTag data);
}
