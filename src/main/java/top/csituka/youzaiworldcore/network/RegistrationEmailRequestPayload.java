package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 客户端提交邮箱地址或邮箱验证码的注册请求。 */
public record RegistrationEmailRequestPayload(Action action, String sessionId, String value)
        implements CustomPacketPayload {

    public enum Action {
        SEND_CODE,
        VERIFY_CODE
    }

    public static final Type<RegistrationEmailRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "registration_email_request"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, RegistrationEmailRequestPayload> STREAM_CODEC = StreamCodec
            .of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.action);
                        buf.writeUtf(payload.sessionId, 128);
                        buf.writeUtf(payload.value, 254);
                    },
                    buf -> new RegistrationEmailRequestPayload(
                            buf.readEnum(Action.class), buf.readUtf(128), buf.readUtf(254)));

    public RegistrationEmailRequestPayload {
        if (action == null)
            throw new IllegalArgumentException("邮箱注册动作不能为空");
        if (sessionId == null || sessionId.isBlank() || sessionId.length() > 128) {
            throw new IllegalArgumentException("邮箱注册会话 ID 无效");
        }
        if (value == null || value.length() > 254) {
            throw new IllegalArgumentException("邮箱注册字段长度无效");
        }
    }

    @SuppressWarnings("null")
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
