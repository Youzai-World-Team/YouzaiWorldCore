package top.csituka.youzaiworldcore.chat;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

/**
 * 自定义出站聊天消息（仿 Styled Chat 的 {@code StyledChatSentMessage}）。
 * <p>
 * 当玩家消息带有 {@code chat_header} 时，使用统一来源前缀。
 * 前缀和正文分别由 ChatType 传递，正文继续使用原版过滤及签名。
 * </p>
 * <p>
 * 玩家身份前缀由服务端生成，原始消息签名始终保留。
 * </p>
 */
@SuppressWarnings("null")
public record StyledChatMessage(PlayerChatMessage message, Component header, ChatType.Bound parameters)
        implements OutgoingChatMessage {

    @Override
    public Component content() {
        return this.message.decoratedContent();
    }

    @Override
    public void sendToPlayer(ServerPlayer receiver, boolean filterMaskEnabled, ChatType.Bound params) {
        PlayerChatMessage filtered = this.message.filter(filterMaskEnabled);

        // 消息被完全过滤（聊天内容全被屏蔽）时不发送，保持原版行为
        if (filtered.isFullyFiltered()) {
            return;
        }

        // 正文保持独立，部分过滤也不会被前缀里的未过滤文本覆盖。
        receiver.connection.sendPlayerChatMessage(filtered, this.parameters);
    }
}
