package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.util.UUID;

/**
 * S2C 数据包：服务端 → 客户端同步实验性功能启用状态。
 * <p>
 * 当 targetPlayer 为 null 时表示全局同步（scope = all），
 * 非 null 时表示仅对指定玩家同步（scope = self / only）。
 * </p>
 */
public record FeatureSyncPayload(String featureId, boolean enabled, @Nullable UUID targetPlayer) implements CustomPacketPayload {

    public static final Identifier FEATURE_SYNC_ID = Identifier.parse(YouzaiworldCore.MOD_ID + ":feature_sync");
    public static final CustomPacketPayload.Type<FeatureSyncPayload> ID = new CustomPacketPayload.Type<>(FEATURE_SYNC_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, FeatureSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.featureId);
                buf.writeBoolean(payload.enabled);
                buf.writeBoolean(payload.targetPlayer != null);
                if (payload.targetPlayer != null) {
                    buf.writeUUID(payload.targetPlayer);
                }
            },
            buf -> {
                String featureId = buf.readUtf();
                boolean enabled = buf.readBoolean();
                UUID targetPlayer = buf.readBoolean() ? buf.readUUID() : null;
                return new FeatureSyncPayload(featureId, enabled, targetPlayer);
            }
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
