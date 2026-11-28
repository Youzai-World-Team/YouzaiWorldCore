package top.csituka.youzaiworldcore.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolScoreboards;

/** 在原版开始换维度前保存源池，而非在到达事件中保存。 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPoolTeleportMixin {
    @WrapMethod(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;")
    private ServerPlayer youzaiworldcore$switchPool(TeleportTransition transition, Operation<ServerPlayer> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        return DimensionPoolManager.onTeleport(player, transition, () -> original.call(transition));
    }

    @WrapOperation(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/server/level/ServerPlayer;setServerLevel(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void youzaiworldcore$switchScoreboard(ServerPlayer player, ServerLevel destination, Operation<Void> original) {
        ServerLevel origin = player.level();
        original.call(player, destination);
        PoolScoreboards.sync(player, origin, destination);
    }

    @ModifyExpressionValue(method = "createCommonSpawnInfo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getLastDeathLocation()Ljava/util/Optional;"))
    private Optional<GlobalPos> youzaiworldcore$poolDeathLocation(Optional<GlobalPos> location,
            @Local(argsOnly = true) ServerLevel destination) {
        return DimensionPoolSettings.deathLocationInPool(location, destination.dimension());
    }
}
