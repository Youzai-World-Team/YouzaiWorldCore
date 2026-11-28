package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntitySelectorOptions.class)
public abstract class EntitySelectorOptionsPoolMixin {
    @ModifyExpressionValue(method = "lambda$bootStrap$47", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private static ServerScoreboard youzaiworldcore$entityScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) Entity entity) {
        return entity.level() instanceof ServerLevel level ? level.getScoreboard() : original;
    }
}
