package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolScoreboards;

@Mixin(ServerLevel.class)
public abstract class ServerLevelScoreboardMixin {
    @WrapOperation(method = "getScoreboard()Lnet/minecraft/server/ServerScoreboard;", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;getScoreboard()Lnet/minecraft/server/ServerScoreboard;"))
    private ServerScoreboard youzaiworldcore$selectPoolScoreboard(MinecraftServer server,
            Operation<ServerScoreboard> original) {
        return ((PoolScoreboards.ServerAccess) server).youzaiworldcore$getPoolScoreboards()
                .get((ServerLevel) (Object) this);
    }
}
