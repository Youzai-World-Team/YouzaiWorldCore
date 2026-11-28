package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.commands.ScoreboardCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScoreboardCommand.class)
public abstract class ScoreboardCommandPoolMixin {
    @ModifyExpressionValue(method = {"suggestTriggers", "getScore", "performOperation",
            "enableTrigger", "resetScores", "resetScore", "setScore", "setScoreDisplay", "setScoreNumberFormat", "addScore", "removeScore",
            "listTrackedPlayers", "listTrackedPlayerScores", "clearDisplaySlot", "setDisplaySlot", "removeObjective", "addObjective", "listObjectives"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"), require = 18)
    private static ServerScoreboard youzaiworldcore$sourceScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) CommandSourceStack source) {
        return source.getLevel().getScoreboard();
    }
}
