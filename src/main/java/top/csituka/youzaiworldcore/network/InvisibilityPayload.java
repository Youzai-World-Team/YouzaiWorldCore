package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求切换 / 查询隐身功能开关。
 *
 * @param enabled 目标开关：{@code true} 开启，{@code false} 关闭，{@code null} 查询
 */
@SuppressWarnings("null")
public record InvisibilityPayload(Boolean enabled) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "invisibility_toggle");

    public static final Type<InvisibilityPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, InvisibilityPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.enabled() != null);
                        if (p.enabled() != null) buf.writeBoolean(p.enabled());
                    },
                    buf -> {
                        if (buf.readBoolean()) return new InvisibilityPayload(buf.readBoolean());
                        return new InvisibilityPayload(null);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
