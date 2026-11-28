package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.commands.ExecuteCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandPoolMixin {
    @ModifyExpressionValue(method = "storeValue", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private static ServerScoreboard youzaiworldcore$sourceScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) CommandSourceStack source) {
        return source.getLevel().getScoreboard();
    }

    @ModifyExpressionValue(method = {
            "checkScore(Lcom/mojang/brigadier/context/CommandContext;Lnet/minecraft/server/commands/ExecuteCommand$IntBiPredicate;)Z",
            "checkScore(Lcom/mojang/brigadier/context/CommandContext;Lnet/minecraft/advancements/predicates/MinMaxBounds$Ints;)Z"},
            at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"), require = 2)
    private static ServerScoreboard youzaiworldcore$contextScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) CommandContext<CommandSourceStack> context) {
        return context.getSource().getLevel().getScoreboard();
    }
}
