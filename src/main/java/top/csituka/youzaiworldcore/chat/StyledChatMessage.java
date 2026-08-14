package top.csituka.youzaiworldcore.chat;

import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

/**
 * 自定义出站聊天消息（仿 Styled Chat 的 {@code StyledChatSentMessage}）。
 * <p>
 * 当 {@link PlayerChatMessage} 上挂有 {@code override} 参数时，
 * {@code OutgoingChatMessage.create} 会返回本实现，把「按模板格式化后的完整消息」
 * 用自定义 ChatType 原样发送给接收者，替代原版 {@code <玩家名> 消息} 的渲染。
 * </p>
 * <p>
 * 模板颜色<b>始终强制生效</b>（无视客户端「聊天颜色」设置）。
 * </p>
 */
@SuppressWarnings("null")
public record StyledChatMessage(PlayerChatMessage message, Component override, ChatType.Bound parameters)
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

        // 直接发送模板渲染结果，模板颜色对所有玩家强制生效
        receiver.connection.sendPlayerChatMessage(filtered, this.parameters);
    }
}
