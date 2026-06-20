package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C: 服务器通知客户端注册结果
 */
public record RegisterResultPayload(int resultCode, String message) implements CustomPacketPayload {

    // resultCode: 0=成功, 1=已注册, 2=名称已被占用, 3=密码不匹配

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "account_register_result");
    public static final CustomPacketPayload.Type<RegisterResultPayload> TYPE = new CustomPacketPayload.Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, RegisterResultPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.resultCode);
                buf.writeUtf(payload.message);
            },
            buf -> new RegisterResultPayload(buf.readVarInt(), buf.readUtf())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
