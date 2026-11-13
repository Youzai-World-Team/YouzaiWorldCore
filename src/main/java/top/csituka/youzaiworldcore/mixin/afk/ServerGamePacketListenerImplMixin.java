package top.csituka.youzaiworldcore.mixin.afk;

import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.afk.AfkManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 捕获客户端发出的聊天和命令数据包，作为 AFK 的明确活动信号。
 * <p>聊天和命令入口可能在网络线程，必须先经 PacketUtils 转交到与客户端心跳
 * 相同的主线程数据包队列，再同步记录活动。不能另用 server.execute 排队，
 * 否则命令可能越过它自己的心跳，导致手动 AFK 被误恢复。</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    private static final String MODULE = "AfkMixin.ServerGamePacketListenerImpl";

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"))
    private void youzaiworldcore$onChat(ServerboundChatPacket packet, CallbackInfo ci) {
        markActivity(packet, "聊天");
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"))
    private void youzaiworldcore$onCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        markActivity(packet, "命令");
    }

    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"))
    private void youzaiworldcore$onSignedCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
        markActivity(packet, "带签名命令");
    }

    private void markActivity(Packet<ServerGamePacketListener> packet, String source) {
        if (player == null || player.level().getServer() == null) {
            return;
        }
        PacketUtils.ensureRunningOnSameThread(packet,
                (ServerGamePacketListenerImpl) (Object) this, player.level());
        var server = player.level().getServer();
        if (!player.hasDisconnected()) {
            AfkManager.onChatActivity(player, server.getTickCount());
            DebugLogger.trace(MODULE, "%s 收到客户端%s数据包，已记录 AFK 活动",
                    player.getName().getString(), source);
        }
    }
}
