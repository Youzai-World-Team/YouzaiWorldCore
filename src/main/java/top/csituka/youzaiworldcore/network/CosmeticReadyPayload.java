package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * S2C：通知客户端认证已完成，可以开始同步自定义皮肤与披风。
 *
 * @param ready 当前服务器是否允许启用自定义外观链路
 * @param serverInstanceId 服务端外观数据实例 ID，用于隔离客户端上传状态
 * @param ownSnapshotHash 该玩家当前存储在服务端的外观快照哈希
 * @param requestCooldownSeconds 客户端拉取失败后的最小重试间隔
 */
@SuppressWarnings("null")
public record CosmeticReadyPayload(
        boolean ready,
        UUID serverInstanceId,
        String ownSnapshotHash,
        int requestCooldownSeconds) implements CustomPacketPayload {

    private static final UUID EMPTY_INSTANCE_ID = new UUID(0L, 0L);

    public CosmeticReadyPayload {
        serverInstanceId = serverInstanceId == null ? EMPTY_INSTANCE_ID : serverInstanceId;
        ownSnapshotHash = ownSnapshotHash == null ? "" : ownSnapshotHash;
        requestCooldownSeconds = Math.max(0, requestCooldownSeconds);
    }

    /** 创建关闭客户端外观链路的通知。 */
    public static CosmeticReadyPayload disabled() {
        return new CosmeticReadyPayload(false, EMPTY_INSTANCE_ID, "", 0);
    }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "cosmetic_ready");
    public static final Type<CosmeticReadyPayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticReadyPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBoolean(payload.ready());
                        buf.writeUUID(payload.serverInstanceId());
                        buf.writeUtf(payload.ownSnapshotHash(), 64);
                        buf.writeVarInt(payload.requestCooldownSeconds());
                    },
                    buf -> new CosmeticReadyPayload(
                            buf.readBoolean(), buf.readUUID(), buf.readUtf(64), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
