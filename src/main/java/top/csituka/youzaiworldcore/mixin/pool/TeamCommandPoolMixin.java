package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.commands.TeamCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeamCommand.class)
public abstract class TeamCommandPoolMixin {
    @ModifyExpressionValue(method = {"leaveTeam", "joinTeam", "emptyTeam", "deleteTeam", "listTeams",
            "createTeam(Lnet/minecraft/commands/CommandSourceStack;Ljava/lang/String;Lnet/minecraft/network/chat/Component;)I"},
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"), require = 6)
    private static ServerScoreboard youzaiworldcore$sourceScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) CommandSourceStack source) {
        return source.getLevel().getScoreboard();
    }
}
