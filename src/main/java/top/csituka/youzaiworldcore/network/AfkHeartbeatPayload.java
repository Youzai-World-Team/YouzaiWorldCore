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
 * 同一输入序号的重复心跳只保活；命令发送前先发心跳，建立切换命令的输入边界。
 *
 * @param inputSequence 当前会话的真实输入序号，仅有新操作时递增
 * @param idleTicks 客户端自最后一次输入以来经过的 tick 数（>= 0）
 */
public record AfkHeartbeatPayload(int idleTicks, long inputSequence) implements CustomPacketPayload {

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "afk_heartbeat_v2");

    @SuppressWarnings("null")
    public static final Type<AfkHeartbeatPayload> ID = new Type<>(IDENTIFIER);

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, AfkHeartbeatPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.idleTicks());
                        buf.writeVarLong(p.inputSequence());
                    },
                    buf -> new AfkHeartbeatPayload(buf.readVarInt(), buf.readVarLong())
            );

    @Override
    @SuppressWarnings("null")
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
