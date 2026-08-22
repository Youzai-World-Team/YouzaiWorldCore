package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 客户端提交已登录账户的管理操作，敏感字段不会进入聊天命令。 */
public record AccountManagementRequestPayload(
        Action action,
        String currentPassword,
        String newPassword,
        String email,
        String sessionId,
        String code
) implements CustomPacketPayload {

    public enum Action {
        LOAD,
        CHANGE_PASSWORD,
        SEND_EMAIL_CODE,
        VERIFY_EMAIL_CODE,
        DEACTIVATE
    }

    public static final Type<AccountManagementRequestPayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "account_management_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AccountManagementRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.action);
                        buf.writeUtf(payload.currentPassword, 128);
                        buf.writeUtf(payload.newPassword, 128);
                        buf.writeUtf(payload.email, 254);
                        buf.writeUtf(payload.sessionId, 128);
                        buf.writeUtf(payload.code, 6);
                    },
                    buf -> new AccountManagementRequestPayload(
                            buf.readEnum(Action.class),
                            buf.readUtf(128),
                            buf.readUtf(128),
                            buf.readUtf(254),
                            buf.readUtf(128),
                            buf.readUtf(6)));

    public AccountManagementRequestPayload {
        if (action == null) throw new IllegalArgumentException("账户管理动作不能为空");
        if (currentPassword == null || currentPassword.length() > 128) {
            throw new IllegalArgumentException("当前密码长度无效");
        }
        if (newPassword == null || newPassword.length() > 128) {
            throw new IllegalArgumentException("新密码长度无效");
        }
        if (email == null || email.length() > 254) {
            throw new IllegalArgumentException("换绑邮箱长度无效");
        }
        if (sessionId == null || sessionId.length() > 128) {
            throw new IllegalArgumentException("换绑邮箱会话 ID 无效");
        }
        if (code == null || code.length() > 6) {
            throw new IllegalArgumentException("换绑邮箱验证码长度无效");
        }
        switch (action) {
            case LOAD -> {
            }
            case CHANGE_PASSWORD -> {
                if (currentPassword.isEmpty() || newPassword.isEmpty()) {
                    throw new IllegalArgumentException("修改密码字段不能为空");
                }
            }
            case SEND_EMAIL_CODE -> {
                if (currentPassword.isEmpty() || email.isBlank()) {
                    throw new IllegalArgumentException("换绑邮箱字段不能为空");
                }
            }
            case VERIFY_EMAIL_CODE -> {
                if (sessionId.isBlank() || code.isBlank()) {
                    throw new IllegalArgumentException("换绑邮箱校验字段不能为空");
                }
            }
            case DEACTIVATE -> {
                if (currentPassword.isEmpty()) {
                    throw new IllegalArgumentException("注销账户密码不能为空");
                }
            }
        }
    }

    public static AccountManagementRequestPayload load() {
        return new AccountManagementRequestPayload(Action.LOAD, "", "", "", "", "");
    }

    public static AccountManagementRequestPayload changePassword(
            String currentPassword, String newPassword) {
        return new AccountManagementRequestPayload(
                Action.CHANGE_PASSWORD, currentPassword, newPassword, "", "", "");
    }

    public static AccountManagementRequestPayload sendEmailCode(
            String currentPassword, String email) {
        return new AccountManagementRequestPayload(
                Action.SEND_EMAIL_CODE, currentPassword, "", email, "", "");
    }

    public static AccountManagementRequestPayload verifyEmailCode(String sessionId, String code) {
        return new AccountManagementRequestPayload(
                Action.VERIFY_EMAIL_CODE, "", "", "", sessionId, code);
    }

    public static AccountManagementRequestPayload deactivate(String currentPassword) {
        return new AccountManagementRequestPayload(
                Action.DEACTIVATE, currentPassword, "", "", "", "");
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
