package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：服务端 → 客户端同步实验性功能启用状态。
 */
public record FeatureSyncPayload(String featureId, boolean enabled) implements CustomPacketPayload {

    public static final Identifier FEATURE_SYNC_ID = Identifier.parse(YouzaiworldCore.MOD_ID + ":feature_sync");
    public static final CustomPacketPayload.Type<FeatureSyncPayload> ID = new CustomPacketPayload.Type<>(FEATURE_SYNC_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, FeatureSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.featureId);
                buf.writeBoolean(payload.enabled);
            },
            buf -> new FeatureSyncPayload(buf.readUtf(), buf.readBoolean())
    );

    @Override
    @NonNull
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
