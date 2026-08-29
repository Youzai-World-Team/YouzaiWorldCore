package top.csituka.youzaiworldcore.mixin.afk;

import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
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
 * <p>在数据包入口记录活动，可覆盖不产生广播消息的普通命令。</p>
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

    private void markActivity(String source) {
        if (player == null || player.level().getServer() == null) {
            return;
        }
        long serverTick = player.level().getServer().getTickCount();
        AfkManager.onChatActivity(player, serverTick);
        DebugLogger.trace(MODULE, "%s 收到客户端%s数据包，记录 AFK 活动 tick=%d",
                player.getName().getString(), source, serverTick);
    }
}
