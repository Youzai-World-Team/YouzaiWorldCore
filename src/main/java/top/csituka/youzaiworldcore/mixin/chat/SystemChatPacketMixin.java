package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import top.csituka.youzaiworldcore.chat.ChatMessageStyle;

/** 给服务端聊天栏系统消息补上游戏或模组来源，动作栏消息不进入聊天排版。 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class SystemChatPacketMixin {
    @SuppressWarnings("null")
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> youzaiworldcore$formatSystemPacket(Packet<?> packet) {
        if (!(packet instanceof ClientboundSystemChatPacket chat) || chat.overlay())
            return packet;
        var content = ChatMessageStyle.system(chat.content(), null);
        return content == chat.content() ? packet : new ClientboundSystemChatPacket(content, false);
    }
}
