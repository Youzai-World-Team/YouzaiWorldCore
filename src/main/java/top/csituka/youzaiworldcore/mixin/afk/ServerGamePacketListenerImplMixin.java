package top.csituka.youzaiworldcore.mixin.afk;

import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
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
 * <p>数据包入口可能在网络线程；先排入主线程，再由原版排队执行消息或命令，
 * 保证切换 AFK 前记录活动，不会在命令完成后补写自身的活动事件。</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    private static final String MODULE = "AfkMixin.ServerGamePacketListenerImpl";

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"))
    private void youzaiworldcore$onChat(ServerboundChatPacket packet, CallbackInfo ci) {
        markActivity("聊天");
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"))
    private void youzaiworldcore$onCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        markActivity("命令");
    }

    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"))
    private void youzaiworldcore$onSignedCommand(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
        markActivity("带签名命令");
    }

    private void markActivity(String source) {
        if (player == null || player.level().getServer() == null) {
            return;
        }
        var server = player.level().getServer();
        server.execute(() -> {
            if (!player.hasDisconnected()) {
                AfkManager.onChatActivity(player, server.getTickCount());
                DebugLogger.trace(MODULE, "%s 收到客户端%s数据包，已记录 AFK 活动",
                        player.getName().getString(), source);
            }
        });
    }
}
