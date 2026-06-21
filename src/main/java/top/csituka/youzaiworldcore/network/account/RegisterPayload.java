package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S（客户端→服务器）注册请求包。
 * <p>
 * 客户端在注册 GUI 中点击"注册"按钮后，
 * 将用户输入的密码及确认密码发送至服务端。
 * </p>
 *
 * @param password        用户设置的密码（明文）
 * @param confirmPassword 用户再次输入的确认密码（用于服务端二次校验一致性）
 */
public record RegisterPayload(String password, String confirmPassword) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "account_register");
    public static final CustomPacketPayload.Type<RegisterPayload> TYPE = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, RegisterPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.password);
                buf.writeUtf(payload.confirmPassword);
            },
            buf -> new RegisterPayload(buf.readUtf(), buf.readUtf())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
