package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C（服务器→客户端）注册结果响应包。
 * <p>
 * 服务端处理完注册请求后，将结果以数字编码 + 文本消息的形式返回给客户端。
 * 客户端根据 {@code resultCode} 在界面上显示对应的提示文本。
 * </p>
 *
 * <p>resultCode 定义：</p>
 * <ul>
 *   <li>0 — 注册成功</li>
 *   <li>1 — 该玩家已经注册过了</li>
 *   <li>2 — 该玩家代号已被占用</li>
 *   <li>3 — 两次输入的密码不匹配 或 密码为空</li>
 * </ul>
 *
 * @param resultCode 结果代码（0~3）
 * @param message    显示给玩家的文本消息
 */
public record RegisterResultPayload(int resultCode, String message) implements CustomPacketPayload {

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
