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
 * C2S 数据包：客户端请求传送到指定坐标的传送锚点。
 * <p>
 * 使用坐标+维度作为标识而非列表索引，避免客户端过滤后的列表
 * 与服务端未过滤列表之间的索引空间不一致问题。
 *
 * @param pos       目标传送锚点的世界坐标
 * @param dimension 目标传送锚点所在的维度
 */
@SuppressWarnings("null")
public record TeleportAnchorTeleportPayload(BlockPos pos, ResourceKey<Level> dimension) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_teleport");

    public static final Type<TeleportAnchorTeleportPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorTeleportPayload> STREAM_CODEC =
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
                        return new TeleportAnchorTeleportPayload(pos, dimension);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
