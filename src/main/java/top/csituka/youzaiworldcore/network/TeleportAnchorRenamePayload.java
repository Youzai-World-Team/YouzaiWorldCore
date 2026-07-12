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
 * C2S 数据包：客户端请求重命名指定坐标的传送锚点。
 * <p>
 * 使用坐标+维度作为标识而非列表索引，避免索引错位。
 *
 * @param pos       目标传送锚点的世界坐标
 * @param dimension 目标传送锚点所在的维度
 * @param newName   新的显示名称
 */
@SuppressWarnings("null")
public record TeleportAnchorRenamePayload(BlockPos pos, ResourceKey<Level> dimension, String newName) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_rename");

    public static final Type<TeleportAnchorRenamePayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorRenamePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeBlockPos(payload.pos);
                        buf.writeUtf(payload.dimension().identifier().toString());
                        buf.writeUtf(payload.newName);
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        String dimStr = buf.readUtf();
                        ResourceKey<Level> dimension = ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                Identifier.parse(dimStr));
                        String newName = buf.readUtf();
                        return new TeleportAnchorRenamePayload(pos, dimension, newName);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
