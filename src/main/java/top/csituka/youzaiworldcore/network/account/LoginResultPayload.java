package top.csituka.youzaiworldcore.network.account;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C（服务器→客户端）登录结果响应包。
 * <p>
 * 服务端处理完登录请求后，将结果以数字编码 + 文本消息的形式返回给客户端。
 * 客户端根据 {@code resultCode} 在界面上显示对应的提示文本。
 * </p>
 *
 * <p>resultCode 定义：</p>
 * <ul>
 *   <li>0 — 成功</li>
 *   <li>1 — 密码错误</li>
 *   <li>2 — 踢出（连续 3 次失败）</li>
 *   <li>3 — 阻止（连续 5 次失败）</li>
 *   <li>4 — 未注册</li>
 * </ul>
 *
 * @param resultCode 结果代码（0~4）
 * @param message    显示给玩家的文本消息
 */
public record LoginResultPayload(int resultCode, String message) implements CustomPacketPayload {

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
