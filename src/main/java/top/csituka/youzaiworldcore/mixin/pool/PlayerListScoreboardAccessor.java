package top.csituka.youzaiworldcore.mixin.pool;

import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PlayerList.class)
public interface PlayerListScoreboardAccessor {
    @Invoker("updateEntireScoreboard")
    void youzaiworldcore$sendScoreboard(ServerScoreboard board, ServerPlayer player);
}
