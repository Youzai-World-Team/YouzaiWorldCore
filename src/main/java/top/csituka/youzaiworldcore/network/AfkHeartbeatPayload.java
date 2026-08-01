package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * C2S 数据包：AFK 客户端心跳。
 * <p>
 * 客户端在 mixin 键盘 / 鼠标输入后记录「最后输入时间」，每 1 秒（20 tick）
 * 发送一次本包。字段为<b>距最后输入的 tick 差值</b>而非时间戳，避免客户端与
 * 服务端时钟不同步；服务端换算：
 * {@code clientLastActivityTick = serverTick - idleTicks}。
 * </p>
 *
 * @param idleTicks 客户端自最后一次输入以来经过的 tick 数（>= 0）
 */
public record AfkHeartbeatPayload(int idleTicks) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "afk_heartbeat");

    @SuppressWarnings("null")
    public static final Type<AfkHeartbeatPayload> ID = new Type<>(IDENTIFIER);

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, AfkHeartbeatPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeVarInt(p.idleTicks()),
                    buf -> new AfkHeartbeatPayload(buf.readVarInt())
            );

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
