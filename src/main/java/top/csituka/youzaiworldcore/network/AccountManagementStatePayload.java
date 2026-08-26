package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 服务端同步账户管理页面所需的最小账户状态与操作结果。 */
public record AccountManagementStatePayload(
        State state,
        String message,
        String email,
        String sessionId,
        int expiresInSeconds,
        int resendAfterSeconds) implements CustomPacketPayload {

    public enum State {
        LOADED,
        PASSWORD_CHANGED,
        EMAIL_CODE_SENT,
        EMAIL_CHANGED,
        DEACTIVATED,
        ERROR,
        EXPIRED
    }

    public static final Type<AccountManagementStatePayload> ID = new Type<>(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "account_management_state"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, AccountManagementStatePayload> STREAM_CODEC = StreamCodec
            .of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.state);
                        buf.writeUtf(payload.message, 512);
                        buf.writeUtf(payload.email, 254);
                        buf.writeUtf(payload.sessionId, 128);
                        buf.writeVarInt(payload.expiresInSeconds);
                        buf.writeVarInt(payload.resendAfterSeconds);
                    },
                    buf -> new AccountManagementStatePayload(
                            buf.readEnum(State.class),
                            buf.readUtf(512),
                            buf.readUtf(254),
                            buf.readUtf(128),
                            buf.readVarInt(),
                            buf.readVarInt()));

    public AccountManagementStatePayload {
        if (state == null)
            throw new IllegalArgumentException("账户管理状态不能为空");
        if (message == null || message.length() > 512) {
            throw new IllegalArgumentException("账户管理状态消息无效");
        }
        if (email == null || email.length() > 254) {
            throw new IllegalArgumentException("账户管理邮箱状态无效");
        }
        if (sessionId == null || sessionId.length() > 128) {
            throw new IllegalArgumentException("账户管理会话 ID 无效");
        }
        if (expiresInSeconds < 0 || expiresInSeconds > 86_400
                || resendAfterSeconds < 0 || resendAfterSeconds > 86_400) {
            throw new IllegalArgumentException("账户管理倒计时无效");
        }
    }

    public static AccountManagementStatePayload loaded(String email) {
        return new AccountManagementStatePayload(State.LOADED, "", safeEmail(email), "", 0, 0);
    }

    public static AccountManagementStatePayload passwordChanged(String message) {
        return new AccountManagementStatePayload(State.PASSWORD_CHANGED, message, "", "", 0, 0);
    }

    public static AccountManagementStatePayload emailCodeSent(
            String sessionId, String message, int expiresInSeconds, int resendAfterSeconds) {
        return new AccountManagementStatePayload(
                State.EMAIL_CODE_SENT, message, "", sessionId,
                expiresInSeconds, resendAfterSeconds);
    }

    public static AccountManagementStatePayload emailChanged(String email, String message) {
        return new AccountManagementStatePayload(
                State.EMAIL_CHANGED, message, safeEmail(email), "", 0, 0);
    }

    public static AccountManagementStatePayload deactivated(String message) {
        return new AccountManagementStatePayload(State.DEACTIVATED, message, "", "", 0, 0);
    }

    public static AccountManagementStatePayload error(String message, int resendAfterSeconds) {
        return new AccountManagementStatePayload(
                State.ERROR, message, "", "", 0, resendAfterSeconds);
    }

    public static AccountManagementStatePayload expired(String message) {
        return new AccountManagementStatePayload(State.EXPIRED, message, "", "", 0, 0);
    }

    private static String safeEmail(String email) {
        return email == null ? "" : email;
    }

    @SuppressWarnings("null")
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
