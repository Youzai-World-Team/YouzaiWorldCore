package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.commands.arguments.ObjectiveArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ObjectiveArgument.class)
public abstract class ObjectiveArgumentPoolMixin {
    @ModifyExpressionValue(method = "getObjective", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private static ServerScoreboard youzaiworldcore$contextScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) CommandContext<CommandSourceStack> context) {
        return context.getSource().getLevel().getScoreboard();
    }

    @ModifyExpressionValue(method = "listSuggestions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private ServerScoreboard youzaiworldcore$suggestScoreboard(ServerScoreboard original,
            @Local(ordinal = 0) CommandSourceStack source) {
        return source.getLevel().getScoreboard();
    }
}
