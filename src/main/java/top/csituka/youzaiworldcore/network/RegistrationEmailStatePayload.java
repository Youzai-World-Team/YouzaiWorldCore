package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 服务端同步邮箱注册步骤、结果及倒计时。 */
public record RegistrationEmailStatePayload(
        State state,
        String sessionId,
        String message,
        int expiresInSeconds,
        int resendAfterSeconds) implements CustomPacketPayload {

    public enum State {
        REQUIRED,
        CODE_SENT,
        ERROR,
        COMPLETED,
        EXPIRED
    }

    public static final Type<RegistrationEmailStatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "registration_email_state"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, RegistrationEmailStatePayload> STREAM_CODEC = StreamCodec
            .of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.state);
                        buf.writeUtf(payload.sessionId, 128);
                        buf.writeUtf(payload.message, 512);
                        buf.writeVarInt(payload.expiresInSeconds);
                        buf.writeVarInt(payload.resendAfterSeconds);
                    },
                    buf -> new RegistrationEmailStatePayload(
                            buf.readEnum(State.class),
                            buf.readUtf(128),
                            buf.readUtf(512),
                            buf.readVarInt(),
                            buf.readVarInt()));

    public RegistrationEmailStatePayload {
        if (state == null)
            throw new IllegalArgumentException("邮箱注册状态不能为空");
        if (sessionId == null || sessionId.length() > 128) {
            throw new IllegalArgumentException("邮箱注册会话 ID 无效");
        }
        if (message == null || message.length() > 512) {
            throw new IllegalArgumentException("邮箱注册状态消息无效");
        }
        if (expiresInSeconds < 0 || expiresInSeconds > 86_400
                || resendAfterSeconds < 0 || resendAfterSeconds > 86_400) {
            throw new IllegalArgumentException("邮箱注册倒计时无效");
        }
    }

    public static RegistrationEmailStatePayload required(String sessionId, int expiresInSeconds) {
        return new RegistrationEmailStatePayload(State.REQUIRED, sessionId, "", expiresInSeconds, 0);
    }

    public static RegistrationEmailStatePayload codeSent(
            String sessionId, String message, int expiresInSeconds, int resendAfterSeconds) {
        return new RegistrationEmailStatePayload(
                State.CODE_SENT, sessionId, message, expiresInSeconds, resendAfterSeconds);
    }

    public static RegistrationEmailStatePayload error(
            String sessionId, String message, int resendAfterSeconds) {
        return new RegistrationEmailStatePayload(State.ERROR, sessionId, message, 0, resendAfterSeconds);
    }

    public static RegistrationEmailStatePayload completed(String sessionId) {
        return new RegistrationEmailStatePayload(State.COMPLETED, sessionId, "", 0, 0);
    }

    public static RegistrationEmailStatePayload expired(String sessionId, String message) {
        return new RegistrationEmailStatePayload(State.EXPIRED, sessionId, message, 0, 0);
    }

    @SuppressWarnings("null")
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
