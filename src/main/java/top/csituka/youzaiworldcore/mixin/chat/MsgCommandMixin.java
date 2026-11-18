package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.MsgCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** /tell、/msg、/w 只替换来源前缀，双方投递与正文过滤使用原版实现。 */
@Mixin(MsgCommand.class)
public abstract class MsgCommandMixin {
    @ModifyVariable(method = "sendMessage", at = @At("HEAD"), argsOnly = true)
    private static PlayerChatMessage youzaiworldcore$preservePrivateText(PlayerChatMessage message) {
        return message.withUnsignedContent(Component.literal(message.signedContent()));
    }

    @Redirect(method = "sendMessage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/ChatType;bind(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/commands/CommandSourceStack;)Lnet/minecraft/network/chat/ChatType$Bound;"))
    private static ChatType.Bound youzaiworldcore$privateHeader(ResourceKey<ChatType> original,
            CommandSourceStack source) {
        DebugLogger.debug("ChatMessages", "私信前缀：发送者=%s", source.getTextName());
        return ChatType.bind(ChatFormatHelper.MESSAGE_TYPE_ID, source.getServer().registryAccess(),
                ChatFormatHelper.formatPrivateHeader(source));
    }
}
