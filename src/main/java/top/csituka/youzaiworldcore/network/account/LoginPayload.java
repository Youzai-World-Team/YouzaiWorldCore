package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S（客户端→服务器）登录请求包。
 * <p>
 * 客户端在登录 GUI 中点击"登入"按钮后，
 * 将用户输入的密码发送至服务端进行校验。
 * </p>
 *
 * @param password 用户输入的明文密码
 */
public record LoginPayload(String password) implements CustomPacketPayload {

    /** 该包类型的唯一标识符，由 MOD_ID 和路径名组成 */
    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "account_login");

    /** 注册到 Fabric 网络系统的包类型 */
    public static final CustomPacketPayload.Type<LoginPayload> TYPE = new CustomPacketPayload.Type<>(IDENTIFIER);

    /** 编解码器：将 password 字段以 UTF-8 字符串在网络上传输 */
    public static final StreamCodec<RegistryFriendlyByteBuf, LoginPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.password),
            buf -> new LoginPayload(buf.readUtf())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
