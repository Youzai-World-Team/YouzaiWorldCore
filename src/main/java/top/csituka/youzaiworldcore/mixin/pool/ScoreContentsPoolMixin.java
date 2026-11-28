package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.network.chat.contents.ScoreContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ScoreContents.class)
public abstract class ScoreContentsPoolMixin {
    @ModifyExpressionValue(method = "getScore", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private ServerScoreboard youzaiworldcore$sourceScoreboard(ServerScoreboard original,
            @Local(argsOnly = true) CommandSourceStack source) {
        return source.getLevel().getScoreboard();
    }
}
