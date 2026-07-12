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
 * C2S 数据包：客户端请求删除指定坐标的传送锚点（仅从当前玩家列表删除）。
 * <p>
 * 使用坐标+维度作为标识而非列表索引，避免索引错位。
 *
 * @param pos       目标传送锚点的世界坐标
 * @param dimension 目标传送锚点所在的维度
 */
@SuppressWarnings("null")
public record TeleportAnchorDeletePayload(BlockPos pos, ResourceKey<Level> dimension) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_delete");

    public static final Type<TeleportAnchorDeletePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorDeletePayload> STREAM_CODEC =
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
                        return new TeleportAnchorDeletePayload(pos, dimension);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
