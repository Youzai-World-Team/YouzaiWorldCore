package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.chat.ChatMessageStyle;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Collection;
import java.util.stream.Collectors;

/** 私信使用统一前缀和独立发送回执；自发自收正文只投递一次。 */
@Mixin(MsgCommand.class)
public abstract class MsgCommandMixin {
    @ModifyVariable(method = "sendMessage", at = @At("HEAD"), argsOnly = true)
    private static PlayerChatMessage youzaiworldcore$preservePrivateText(PlayerChatMessage message) {
        return message.withUnsignedContent(Component.literal(message.signedContent()));
    }

    @SuppressWarnings("null")
    @Redirect(method = "sendMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/ChatType;bind(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/commands/CommandSourceStack;)Lnet/minecraft/network/chat/ChatType$Bound;"))
    private static ChatType.Bound youzaiworldcore$privateHeader(ResourceKey<ChatType> original,
            CommandSourceStack source) {
        DebugLogger.SERVER.debug("ChatMessages", "私信前缀：发送者={}", source.getTextName());
        return ChatType.bind(ChatFormatHelper.MESSAGE_TYPE_ID, source.getServer().registryAccess(),
                ChatFormatHelper.formatPrivateHeader(source));
    }

    /** 原版每个收件人都会产生回执；自发自收时收件路径已经给发送者投递。 */
    @SuppressWarnings("null")
    @Redirect(method = "sendMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/CommandSourceStack;sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V"))
    private static void youzaiworldcore$skipSelfFeedback(CommandSourceStack feedbackSource,
            OutgoingChatMessage message, boolean filtered, ChatType.Bound parameters,
            CommandSourceStack commandSource, Collection<ServerPlayer> targets, PlayerChatMessage originalMessage) {
        ServerPlayer sender = commandSource.getPlayer();
        if (sender != null && targets.stream().anyMatch(target -> target.getUUID().equals(sender.getUUID()))) {
            DebugLogger.SERVER.debug("ChatMessages", "自发自收私信省略额外回执：{}", sender.getScoreboardName());
            return;
        }
        feedbackSource.sendChatMessage(message, filtered, parameters);
    }

    /** 投递完成后向发送者确认收件人；完全过滤的目标不列入成功回执。 */
    @Inject(method = "sendMessage", at = @At("RETURN"))
    private static void youzaiworldcore$sendPrivateReceipt(CommandSourceStack source,
            Collection<ServerPlayer> targets, PlayerChatMessage message, CallbackInfo ci) {
        if (source.isSilent()) return;
        String recipients = targets.stream()
                .filter(target -> !source.shouldFilterMessageTo(target) || !message.isFullyFiltered())
                .map(ServerPlayer::getScoreboardName).distinct().collect(Collectors.joining(", "));
        if (recipients.isEmpty()) return;
        source.sendSystemMessage(ChatMessageStyle.privateSent(ChatFormatHelper.formatPrivateHeader(source),
                Component.literal(recipients)));
        DebugLogger.SERVER.debug("ChatMessages", "私信发送回执：发送者={}，收件人={}", source.getTextName(), recipients);
    }
}
