package top.csituka.youzaiworldcore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * S2C 数据包：服务端通知客户端打开传送锚点命名界面。
 */
@SuppressWarnings("null")
public record TeleportAnchorOpenNamePayload(BlockPos pos, ResourceKey<Level> dimension) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_open_name");

    public static final Type<TeleportAnchorOpenNamePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorOpenNamePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeUtf(payload.dimension().identifier().toString());
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        String dimStr = buf.readUtf();
                        ResourceKey<Level> dimension = ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                Identifier.parse(dimStr));
                        return new TeleportAnchorOpenNamePayload(pos, dimension);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
