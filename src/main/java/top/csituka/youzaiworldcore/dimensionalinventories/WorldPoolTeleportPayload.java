package top.csituka.youzaiworldcore.dimensionalinventories;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 客户端 -> 服务端：请求传送到指定维度池。
 * <p>
 * 由"切换世界"屏幕按钮触发，客户端发送目标池 ID，
 * 服务端接收后执行 DimensionPoolManager.teleportToPool()。
 */
@SuppressWarnings("null")
public record WorldPoolTeleportPayload(String poolId) implements CustomPacketPayload {

    public static final Identifier PACKET_ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "world_pool_teleport");

    public static final CustomPacketPayload.Type<WorldPoolTeleportPayload> ID =
            new CustomPacketPayload.Type<>(PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, WorldPoolTeleportPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        DebugLogger.entering("WorldPoolPayload", "encode", "poolId=" + payload.poolId);
                        buf.writeUtf(payload.poolId);
                        DebugLogger.exiting("WorldPoolPayload", "encode");
                    },
                    buf -> {
                        DebugLogger.entering("WorldPoolPayload", "decode");
                        String poolId = buf.readUtf();
                        DebugLogger.exiting("WorldPoolPayload", "decode", "poolId=" + poolId);
                        return new WorldPoolTeleportPayload(poolId);
                    }
            );

    @Override
    @NotNull
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
