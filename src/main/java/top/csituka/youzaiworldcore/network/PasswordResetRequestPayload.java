package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 客户端提交找回密码邮箱、验证码与新密码。 */
public record PasswordResetRequestPayload(
        Action action,
        String sessionId,
        String email,
        String code,
        String newPassword) implements CustomPacketPayload {

    public enum Action {
        SEND_CODE,
        RESET_PASSWORD
    }

    public static final Type<PasswordResetRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "password_reset_request"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, PasswordResetRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action);
                buf.writeUtf(payload.sessionId, 128);
                buf.writeUtf(payload.email, 254);
                buf.writeUtf(payload.code, 6);
                buf.writeUtf(payload.newPassword, 128);
            },
            buf -> new PasswordResetRequestPayload(
                    buf.readEnum(Action.class),
                    buf.readUtf(128),
                    buf.readUtf(254),
                    buf.readUtf(6),
                    buf.readUtf(128)));

    public PasswordResetRequestPayload {
        if (action == null)
            throw new IllegalArgumentException("找回密码动作不能为空");
        if (sessionId == null || sessionId.length() > 128) {
            throw new IllegalArgumentException("找回密码会话 ID 无效");
        }
        if (email == null || email.length() > 254) {
            throw new IllegalArgumentException("找回密码邮箱长度无效");
        }
        if (code == null || code.length() > 6) {
            throw new IllegalArgumentException("找回密码验证码长度无效");
        }
        if (newPassword == null || newPassword.length() > 128) {
            throw new IllegalArgumentException("找回密码的新密码长度无效");
        }
        if (action == Action.SEND_CODE && email.isBlank()) {
            throw new IllegalArgumentException("找回密码邮箱不能为空");
        }
        if (action == Action.RESET_PASSWORD
                && (sessionId.isBlank() || code.isBlank() || newPassword.isEmpty())) {
            throw new IllegalArgumentException("找回密码校验字段不能为空");
        }
    }

    public static PasswordResetRequestPayload sendCode(String sessionId, String email) {
        return new PasswordResetRequestPayload(Action.SEND_CODE, sessionId, email, "", "");
    }

    public static PasswordResetRequestPayload resetPassword(
            String sessionId, String code, String newPassword) {
        return new PasswordResetRequestPayload(
                Action.RESET_PASSWORD, sessionId, "", code, newPassword);
    }

    @SuppressWarnings("null")
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
