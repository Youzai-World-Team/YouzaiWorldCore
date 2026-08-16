package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.chat.ExtPlayerChatMessage;
import top.csituka.youzaiworldcore.config.ChatFormatSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 私聊消息格式化（仿 Styled Chat 的 MsgCommandMixin）。
 * <p>
 * {@code /msg} 发送时：
 * <ol>
 *   <li>HEAD 注入：把消息内容格式化结果存入 {@code base_input} 参数；</li>
 *   <li>重定向 {@code CommandSourceStack.sendChatMessage}：去掉原版发送者反馈
 *       （由发送者分支统一重发）；</li>
 *   <li>重定向 {@code ServerPlayer.sendChatMessage}：发送者视角用「发出」模板、
 *       接收者视角用「收到」模板，均以自定义 ChatType 发送。</li>
 * </ol>
 * </p>
 */
@SuppressWarnings("null")
@Mixin(MsgCommand.class)
public abstract class MsgCommandMixin {

    private static final String MODULE = "ChatMixin.MsgCommand";

    @Inject(method = "sendMessage", at = @At("HEAD"))
    private static void youzaiworldcore$setBaseInput(CommandSourceStack source, Collection<ServerPlayer> targets,
                                                     PlayerChatMessage signedMessage, CallbackInfo ci) {
        if (!ChatFormatSettings.isEnabled()) {
            return;
        }
        try {
            ChatFormatHelper.server = source.getServer();
            ServerPlayer sender = source.getPlayer();
            Component baseInput = sender != null
                    ? ChatFormatHelper.formatMessageContent(sender, signedMessage.signedContent())
                    : Component.literal(signedMessage.signedContent());
            ExtPlayerChatMessage.setArg(signedMessage, "base_input", baseInput);
            if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
                DebugLogger.trace(MODULE, "base_input set for /msg by {}", source.getTextName());
            }
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "setBaseInput", e);
        }
    }

    /** 原版发送者反馈由 {@link #youzaiworldcore$formatPrivateMessage} 统一重发 */
    @Redirect(
            method = "sendMessage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/commands/CommandSourceStack;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V")
    )
    private static void youzaiworldcore$noopSenderFeedback(CommandSourceStack instance, OutgoingChatMessage message,
                                                           boolean bl, ChatType.Bound parameters) {
        // noop
    }

    @Redirect(
            method = "sendMessage",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V")
    )
    private static void youzaiworldcore$formatPrivateMessage(ServerPlayer receiver, OutgoingChatMessage message,
                                                             boolean bl, ChatType.Bound parameters) {
        try {
            var chatServer = ChatFormatHelper.server;
            if (!ChatFormatSettings.isEnabled() || chatServer == null
                    || !(message instanceof OutgoingChatMessage.Player playerMessage)) {
                receiver.sendChatMessage(message, bl, parameters);
                return;
            }

            PlayerChatMessage raw = playerMessage.message();
            Component baseInput = ExtPlayerChatMessage.getArg(raw, "base_input");
            if (baseInput == ChatFormatHelper.EMPTY_TEXT) {
                receiver.sendChatMessage(message, bl, parameters);
                return;
            }

            var registry = chatServer.registryAccess();
            Component senderName = parameters.name();
            Component receiverName = receiver.getDisplayName();

            // 发送者视角：套「发出」模板发给发送者本人
            ServerPlayer sender = chatServer.getPlayerList().getPlayer(raw.sender());
            if (sender != null) {
                Component sent = ChatFormatHelper.formatPrivateMessageSent(
                        sender.createCommandSourceStack(), senderName, receiverName, baseInput);
                sender.sendChatMessage(message, bl, ChatType.bind(ChatFormatHelper.MESSAGE_TYPE_ID, registry, sent));
            }

            // 接收者视角：套「收到」模板发给接收者
            Component received = ChatFormatHelper.formatPrivateMessageReceived(
                    receiver.createCommandSourceStack(), senderName, receiverName, baseInput);
            receiver.sendChatMessage(message, bl, ChatType.bind(ChatFormatHelper.MESSAGE_TYPE_ID, registry, received));

            if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
                DebugLogger.debug(MODULE, "formatted /msg sender={} receiver={}", senderName.getString(),
                        receiverName.getString());
            }
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "formatPrivateMessage", e);
            receiver.sendChatMessage(message, bl, parameters);
        }
    }
}
