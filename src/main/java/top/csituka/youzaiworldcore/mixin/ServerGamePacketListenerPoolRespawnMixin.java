package top.csituka.youzaiworldcore.mixin;

import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolRespawn;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerPoolRespawnMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleClientCommand", at = @At("TAIL"))
    private void youzaiworldcore$finishPoolRespawn(ServerboundClientCommandPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
            PoolRespawn.finishPendingRespawn(player);
        }
    }
}
