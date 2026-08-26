package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 服务端同步邮箱找回密码的结果与倒计时。 */
public record PasswordResetStatePayload(
        State state,
        String sessionId,
        String message,
        int expiresInSeconds,
        int resendAfterSeconds) implements CustomPacketPayload {

    public enum State {
        CODE_SENT,
        ERROR,
        COMPLETED,
        EXPIRED
    }

    public static final Type<PasswordResetStatePayload> ID = new Type<>(Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "password_reset_state"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, PasswordResetStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.state);
                buf.writeUtf(payload.sessionId, 128);
                buf.writeUtf(payload.message, 512);
                buf.writeVarInt(payload.expiresInSeconds);
                buf.writeVarInt(payload.resendAfterSeconds);
            },
            buf -> new PasswordResetStatePayload(
                    buf.readEnum(State.class),
                    buf.readUtf(128),
                    buf.readUtf(512),
                    buf.readVarInt(),
                    buf.readVarInt()));

    public PasswordResetStatePayload {
        if (state == null)
            throw new IllegalArgumentException("找回密码状态不能为空");
        if (sessionId == null || sessionId.length() > 128) {
            throw new IllegalArgumentException("找回密码会话 ID 无效");
        }
        if (message == null || message.length() > 512) {
            throw new IllegalArgumentException("找回密码状态消息无效");
        }
        if (expiresInSeconds < 0 || expiresInSeconds > 86_400
                || resendAfterSeconds < 0 || resendAfterSeconds > 86_400) {
            throw new IllegalArgumentException("找回密码倒计时无效");
        }
    }

    public static PasswordResetStatePayload codeSent(
            String sessionId, String message, int expiresInSeconds, int resendAfterSeconds) {
        return new PasswordResetStatePayload(
                State.CODE_SENT, sessionId, message, expiresInSeconds, resendAfterSeconds);
    }

    public static PasswordResetStatePayload error(
            String sessionId, String message, int resendAfterSeconds) {
        return new PasswordResetStatePayload(State.ERROR, sessionId, message, 0, resendAfterSeconds);
    }

    public static PasswordResetStatePayload completed(String sessionId, String message) {
        return new PasswordResetStatePayload(State.COMPLETED, sessionId, message, 0, 0);
    }

    public static PasswordResetStatePayload expired(String sessionId, String message) {
        return new PasswordResetStatePayload(State.EXPIRED, sessionId, message, 0, 0);
    }

    @SuppressWarnings("null")
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
