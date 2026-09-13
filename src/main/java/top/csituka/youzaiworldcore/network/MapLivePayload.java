package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C：短时有效的玩家位置与区块加载等级；客户端在心跳超时后撤下这些标记。 */
public record MapLivePayload(UUID worldId, String dimension, List<Player> players,
                              List<LoadedChunk> chunks) implements CustomPacketPayload {
    public record Player(UUID id, String name, String dimension, double x, double y, double z, float yaw) { }
    public record LoadedChunk(int x, int z, int ticketLevel) { }
    public static final Type<MapLivePayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_live"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapLivePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapLivePayload decode(RegistryFriendlyByteBuf buf) {
            UUID world = buf.readUUID();
            String dimension = buf.readUtf(128);
            int count = MapStreamCodecs.count(buf, 256);
            var players = new ArrayList<Player>(count);
            for (int i = 0; i < count; i++) {
                var player = new Player(buf.readUUID(), buf.readUtf(64), buf.readUtf(128),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat());
                if (!Double.isFinite(player.x()) || !Double.isFinite(player.y()) || !Double.isFinite(player.z())
                        || !Float.isFinite(player.yaw())) throw new IllegalArgumentException("玩家地图坐标无效");
                players.add(player);
            }
            count = MapStreamCodecs.count(buf, 1024);
            var chunks = new ArrayList<LoadedChunk>(count);
            for (int i = 0; i < count; i++) chunks.add(new LoadedChunk(buf.readInt(), buf.readInt(), buf.readInt()));
            return new MapLivePayload(world, dimension, List.copyOf(players), List.copyOf(chunks));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapLivePayload value) {
            buf.writeUUID(value.worldId());
            buf.writeUtf(value.dimension(), 128);
            buf.writeVarInt(value.players().size());
            for (Player player : value.players()) {
                buf.writeUUID(player.id());
                buf.writeUtf(player.name(), 64);
                buf.writeUtf(player.dimension(), 128);
                buf.writeDouble(player.x());
                buf.writeDouble(player.y());
                buf.writeDouble(player.z());
                buf.writeFloat(player.yaw());
            }
            buf.writeVarInt(value.chunks().size());
            for (LoadedChunk chunk : value.chunks()) {
                buf.writeInt(chunk.x());
                buf.writeInt(chunk.z());
                buf.writeInt(chunk.ticketLevel());
            }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
