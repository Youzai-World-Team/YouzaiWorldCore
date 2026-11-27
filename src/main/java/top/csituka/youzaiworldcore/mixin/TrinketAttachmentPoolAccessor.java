package top.csituka.youzaiworldcore.mixin;

import eu.pb4.trinkets.impl.LivingEntityTrinketAttachment;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LivingEntityTrinketAttachment.class, remap = false)
public interface TrinketAttachmentPoolAccessor {
    @Accessor("delayedDropped")
    NonNullList<ItemStack> youzaiworldcore$getDelayedDropped();
}
