package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C：稳定的存档身份与当前玩家实际拥有的地图能力；不发送世界种子。 */
public record MapSessionPayload(UUID worldId, int flags, List<Dimension> dimensions) implements CustomPacketPayload {
    /** 服务端提供的维度标识与合法建造高度。 */
    public record Dimension(String id, int minY, int maxY) {
        public Dimension {
            if (id == null || Identifier.tryParse(id) == null || minY < -4096 || maxY > 4095 || minY > maxY) throw new IllegalArgumentException("地图维度无效");
        }
    }
    public static final int ENABLED = 1, TERRAIN = 2, RADAR = 4, WAYPOINTS = 8,
            LOAD_STATE = 16, TELEPORT = 32, MANAGE = 64, PUBLISH = 128;
    public static final Type<MapSessionPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapSessionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapSessionPayload decode(RegistryFriendlyByteBuf buf) {
            UUID world = buf.readUUID();
            int flags = buf.readInt();
            int count = MapStreamCodecs.count(buf, 128);
            var dimensions = new ArrayList<Dimension>(count);
            for (int i = 0; i < count; i++) dimensions.add(new Dimension(buf.readUtf(128), buf.readInt(), buf.readInt()));
            return new MapSessionPayload(world, flags, List.copyOf(dimensions));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapSessionPayload value) {
            buf.writeUUID(value.worldId());
            buf.writeInt(value.flags());
            buf.writeVarInt(value.dimensions().size());
            for (Dimension dimension : value.dimensions()) { buf.writeUtf(dimension.id(), 128); buf.writeInt(dimension.minY()); buf.writeInt(dimension.maxY()); }
        }
    };
    /** @return 服务端是否向该玩家开放指定能力 */
    public boolean allows(int flag) { return (flags & ENABLED) != 0 && (flags & flag) != 0; }
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
