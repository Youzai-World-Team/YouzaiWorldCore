package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.config.ChatFormatSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 玩家聊天广播入口注入（仿 Styled Chat 的 ServerGamePacketListenerImplMixin）。
 * <ol>
 *   <li>重定向 {@code ChatDecorator.decorate}：保留聊天原文；</li>
 *   <li>{@code broadcastChatMessage} 入口：生成等级、称号与玩家代号前缀，
 *       供 {@code OutgoingChatMessage.create} 替换发送。</li>
 * </ol>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    private static final String MODULE = "ChatMixin.ServerGamePacketListenerImpl";

    @Shadow
    public ServerPlayer player;

    @Redirect(
            method = "lambda$handleChat$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;")
    )
    private Component youzaiworldcore$replaceDecorator(ChatDecorator instance, ServerPlayer player, Component text) {
        if (player == null) {
            return text;
        }
        String input = text.getString();
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace(MODULE, "replaceDecorator player={} text={}", player.getName().getString(), input);
        }
        return ChatFormatHelper.formatMessageContent(player, input);
    }

    @Inject(method = "broadcastChatMessage", at = @At("HEAD"))
    private void youzaiworldcore$setFormattedMessage(PlayerChatMessage signedMessage, CallbackInfo ci) {
        ChatFormatHelper.modifyForSending(signedMessage, this.player);
    }

    @ModifyArg(
            method = "removePlayerFromWorld",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private Component youzaiworldcore$replaceLeaveMessage(Component text) {
        if (!ChatFormatSettings.isEnabled()) {
            return text;
        }
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace(MODULE, "replaceLeaveMessage player={}", this.player.getName().getString());
        }
        return ChatFormatHelper.formatLeft(this.player);
    }
}
