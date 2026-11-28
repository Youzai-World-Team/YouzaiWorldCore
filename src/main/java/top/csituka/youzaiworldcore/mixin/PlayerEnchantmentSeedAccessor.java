package top.csituka.youzaiworldcore.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Player.class)
public interface PlayerEnchantmentSeedAccessor {
    @Accessor("enchantmentSeed")
    void youzaiworldcore$setEnchantmentSeed(int seed);
}
