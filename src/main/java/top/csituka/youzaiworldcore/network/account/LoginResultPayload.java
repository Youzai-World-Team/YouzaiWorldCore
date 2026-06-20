package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C: 服务器返回登录结果给客户端
 */
public record LoginResultPayload(int resultCode, String message) implements CustomPacketPayload {

    // resultCode: 0=成功, 1=密码错误, 2=踢出(3次), 3=阻止(5次), 4=未注册

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "account_login_result");
    public static final CustomPacketPayload.Type<LoginResultPayload> TYPE = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, LoginResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.resultCode);
                buf.writeUtf(payload.message);
            },
            buf -> new LoginResultPayload(buf.readVarInt(), buf.readUtf())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
