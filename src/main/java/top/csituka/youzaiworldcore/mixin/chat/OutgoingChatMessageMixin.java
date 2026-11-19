package top.csituka.youzaiworldcore.mixin.chat;

import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.chat.ChatFormatHelper;
import top.csituka.youzaiworldcore.chat.ExtPlayerChatMessage;
import top.csituka.youzaiworldcore.chat.StyledChatMessage;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 出站聊天消息替换（仿 Styled Chat 的 OutgoingChatMessageMixin）。
 * <p>
 * 当消息上挂有 {@code chat_header}（由广播入口设置）时，
 * 把 {@code OutgoingChatMessage.create} 的结果替换为 {@link StyledChatMessage}，
 * 自定义 ChatType 分别渲染前缀和经过原版过滤的正文。
 * </p>
 * <p>
 * 注意：本类为 <b>interface mixin</b>，禁止声明非 {@code @Shadow} 字段
 * （interface 字段会被视为常量而非注入字段，导致 Mixin 变换失败）。
 * </p>
 */
@Mixin(OutgoingChatMessage.class)
public interface OutgoingChatMessageMixin {

    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static void youzaiworldcore$patchStyle(PlayerChatMessage message,
                                                   CallbackInfoReturnable<OutgoingChatMessage> cir) {
        try {
            var header = ((ExtPlayerChatMessage) (Object) message).youzaiworldcore_getArg(ChatFormatHelper.HEADER_ARG);
            if (header != null && header != ChatFormatHelper.EMPTY_TEXT && ChatFormatHelper.server != null) {
                cir.setReturnValue(new StyledChatMessage(
                        message, header, ChatFormatHelper.createParameters(header)));
                DebugLogger.SERVER.trace("ChatMixin.OutgoingChatMessage", "OutgoingChatMessage replaced with styled message");
            }
        } catch (Exception e) {
            // 兜底：异常时走原版发送，不影响聊天
            DebugLogger.SERVER.exceptionSummary("ChatMixin.OutgoingChatMessage", "create", e.getMessage());
        }
    }
}
