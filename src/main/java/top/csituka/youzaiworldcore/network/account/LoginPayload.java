package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S: 客户端发送登录请求
 */
public record LoginPayload(String password) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "account_login");
    public static final CustomPacketPayload.Type<LoginPayload> TYPE = new CustomPacketPayload.Type<>(IDENTIFIER);

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
