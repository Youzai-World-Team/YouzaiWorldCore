package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.commands.TriggerCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TriggerCommand.class)
public abstract class TriggerCommandPoolMixin {
    @ModifyExpressionValue(method = {"suggestObjectives", "addValue", "setValue", "simpleTrigger"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"), require = 4)
    private static ServerScoreboard youzaiworldcore$sourceScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) CommandSourceStack source) {
        return source.getLevel().getScoreboard();
    }
}
