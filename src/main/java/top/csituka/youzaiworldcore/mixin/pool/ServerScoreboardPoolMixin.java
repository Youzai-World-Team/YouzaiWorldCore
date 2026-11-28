package top.csituka.youzaiworldcore.mixin.pool;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolScoreboards;

@Mixin(ServerScoreboard.class)
public abstract class ServerScoreboardPoolMixin implements PoolScoreboards.Access {
    @Unique
    private @Nullable String youzaiworldcore$pool;

    @Override
    public void youzaiworldcore$setScoreboardPool(String pool) {
        youzaiworldcore$pool = pool;
    }

    @SuppressWarnings("null")
    @WrapOperation(method = { "onScoreChanged", "onPlayerRemoved", "onPlayerScoreRemoved", "setDisplayObjective",
            "addPlayerToTeam", "removePlayerFromTeam", "onObjectiveChanged", "onTeamAdded", "onTeamChanged",
            "onTeamRemoved" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void youzaiworldcore$broadcastToPool(PlayerList players, Packet<?> packet, Operation<Void> original) {
        for (ServerPlayer player : players.getPlayers()) {
            if (PoolScoreboards.receives(player, youzaiworldcore$pool)) {
                player.connection.send(packet);
            }
        }
    }

    @WrapOperation(method = { "startTrackingObjective",
            "stopTrackingObjective" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;getPlayers()Ljava/util/List;"))
    private List<ServerPlayer> youzaiworldcore$trackForPool(PlayerList players,
            Operation<List<ServerPlayer>> original) {
        return original.call(players).stream()
                .filter(player -> PoolScoreboards.receives(player, youzaiworldcore$pool)).toList();
    }
}
