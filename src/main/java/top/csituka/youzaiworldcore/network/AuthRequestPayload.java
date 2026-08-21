package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** 客户端认证请求；密码不进入聊天命令字符串。 */
public record AuthRequestPayload(Action action, String password, String confirmation)
        implements CustomPacketPayload {

    public enum Action { LOGIN, REGISTER }

    public static final Type<AuthRequestPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "auth_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action);
                buf.writeUtf(payload.password, 128);
                buf.writeUtf(payload.confirmation, 128);
            },
            buf -> new AuthRequestPayload(buf.readEnum(Action.class), buf.readUtf(128), buf.readUtf(128))
    );

    public AuthRequestPayload {
        if (action == null) throw new IllegalArgumentException("认证动作不能为空");
        if (password == null || password.length() > 128 || confirmation == null || confirmation.length() > 128) {
            throw new IllegalArgumentException("认证字段长度无效");
        }
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
