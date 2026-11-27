package top.csituka.youzaiworldcore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolRespawn;

@Mixin(PlayerList.class)
public abstract class PlayerListPoolRespawnMixin {
    @WrapOperation(method = "respawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;findRespawnPositionAndUseSpawnBlock(ZLnet/minecraft/world/level/portal/TeleportTransition$PostTeleportTransition;)Lnet/minecraft/world/level/portal/TeleportTransition;"))
    private TeleportTransition youzaiworldcore$stagePoolTransfer(ServerPlayer player, boolean useSpawnBlock,
            TeleportTransition.PostTeleportTransition postTransition, Operation<TeleportTransition> original) {
        TeleportTransition target = original.call(player, useSpawnBlock, postTransition);
        return useSpawnBlock ? target : PoolRespawn.stageLivingTransfer(player, target);
    }
}
