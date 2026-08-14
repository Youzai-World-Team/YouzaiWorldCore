package top.csituka.youzaiworldcore.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.jspecify.annotations.Nullable;

/**
 * {@link PlayerChatMessage} 的扩展接口（Mixin 附加数据）。
 * <p>
 * 仿 Styled Chat 的 {@code ExtPlayerChatMessage}：在不改动原版消息签名的前提下，
 * 把「格式化后的完整消息」作为 {@code override} 参数挂到消息对象上，
 * 供发送阶段（{@code OutgoingChatMessage.create}）取出并替换为自定义发送消息。
 * </p>
 */
public interface ExtPlayerChatMessage {

    static Component getArg(PlayerChatMessage message, String name) {
        return ((ExtPlayerChatMessage) (Object) message).youzaiworldcore_getArg(name);
    }

    static void setArg(PlayerChatMessage message, String name, Component value) {
        ((ExtPlayerChatMessage) (Object) message).youzaiworldcore_setArg(name, value);
    }

    /** 取消息的原始（未过滤）文本内容 */
    String youzaiworldcore_getOriginal();

    void youzaiworldcore_setArg(String name, Component arg);

    @Nullable
    Component youzaiworldcore_getArg(String name);
}
