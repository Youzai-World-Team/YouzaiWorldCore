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
 * C2S 数据包：玩家在命名界面确认后，发送锚点位置、维度和自定义名称给服务端完成激活。
 */
@SuppressWarnings("null")
public record TeleportAnchorActivatePayload(BlockPos pos, ResourceKey<Level> dimension, String name) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_activate");

    public static final Type<TeleportAnchorActivatePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorActivatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeUtf(payload.dimension().identifier().toString());
                        buf.writeUtf(payload.name());
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        String dimStr = buf.readUtf();
                        ResourceKey<Level> dimension = ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                Identifier.parse(dimStr));
                        String name = buf.readUtf();
                        return new TeleportAnchorActivatePayload(pos, dimension, name);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
