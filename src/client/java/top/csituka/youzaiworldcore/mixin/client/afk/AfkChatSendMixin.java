package top.csituka.youzaiworldcore.mixin.client.afk;

import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.afk.AfkInputTracker;

/**
 * 实际发送聊天/命令时才计入输入。心跳在命令包之前发出，
 * 服务端执行 /yzwc afk 时已经知道本次输入序号，不依赖网络延迟或固定宽限期。
 */
@Mixin(ClientPacketListener.class)
public abstract class AfkChatSendMixin {
    @Inject(method = {"sendChat", "sendCommand"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void youzaiworldcore$beforeChatPacket(String message, CallbackInfo ci) {
        AfkInputTracker.onChatSent();
    }
}
