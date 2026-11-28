package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackPoolMixin {
    @Shadow public abstract ServerLevel getLevel();

    @ModifyExpressionValue(method = "getAllTeams", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private ServerScoreboard youzaiworldcore$teamSuggestions(ServerScoreboard original) {
        return getLevel().getScoreboard();
    }
}
