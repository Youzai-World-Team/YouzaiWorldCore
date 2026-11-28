package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.commands.arguments.TeamArgument;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeamArgument.class)
public abstract class TeamArgumentPoolMixin {
    @ModifyExpressionValue(method = "getTeam", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private static ServerScoreboard youzaiworldcore$contextScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) CommandContext<CommandSourceStack> context) {
        return context.getSource().getLevel().getScoreboard();
    }
}
