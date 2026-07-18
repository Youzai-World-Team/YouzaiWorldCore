package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求切换 / 查询自身的双开门功能开关。
 * <p>
 * 由于 {@code /yzwc} 根命令在客户端被注册为客户端命令（用于 {@code /yzwc settings}），
 * 客户端在解析 {@code /yzwc function double_doors} 时会因找不到子节点而失败。
 * 因此该指令在客户端仅做解析与转发，真正的状态写入由服务端通过此数据包完成
 * （服务端持有 {@code DoubleDoorsState} 的权威内存状态）。
 * </p>
 *
 * @param enabled 目标开关；{@code null} 表示仅查询自身当前状态
 */
@SuppressWarnings("null")
public record DoubleDoorsTogglePayload(@Nullable Boolean enabled) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "double_doors_toggle");

    public static final Type<DoubleDoorsTogglePayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, DoubleDoorsTogglePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.enabled != null);
                        if (p.enabled != null) {
                            buf.writeBoolean(p.enabled);
                        }
                    },
                    buf -> {
                        boolean has = buf.readBoolean();
                        Boolean enabled = has ? buf.readBoolean() : null;
                        return new DoubleDoorsTogglePayload(enabled);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
