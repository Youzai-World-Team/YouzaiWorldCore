package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：客户端请求切换自身的隐身功能开关。
 * <p>
 * 与双开门命令同理，{@code /yzwc} 根命令已在客户端被注册（用于 {@code /yzwc settings}），
 * 客户端在解析 {@code /yzwc function invisibility} 时会因找不到子节点而失败。
 * 因此该指令在客户端仅做解析与转发，真正的权限 / 创造模式校验
 * 与状态变更由服务端通过此数据包完成
 * （服务端持有 {@code InvisibilityManager} 的权威逻辑）。
 * </p>
 *
 * @param enabled 目标开关：{@code true} 开启隐身，{@code false} 关闭隐身
 */
@SuppressWarnings("null")
public record InvisibilityPayload(boolean enabled) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "invisibility_toggle");

    public static final Type<InvisibilityPayload> ID = new Type<>(IDENTIFIER);

    public static final StreamCodec<RegistryFriendlyByteBuf, InvisibilityPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.enabled()),
                    buf -> new InvisibilityPayload(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
