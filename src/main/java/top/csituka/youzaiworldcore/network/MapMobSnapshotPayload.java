package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C：完整查询分包传输，避免生物密集时仅返回最近一批；客户端收齐后原子替换。 */
public record MapMobSnapshotPayload(UUID request, String dimension, Identifier entityType,
        int part, int parts, List<Target> targets) implements CustomPacketPayload {
    public static final int BATCH_SIZE = 512, MAX_PARTS = 256;
    public record Target(UUID id, double x, double y, double z) { }
    public static final Type<MapMobSnapshotPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("youzaiworldcore", "map_mob_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapMobSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public MapMobSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
            UUID request = buf.readUUID(); String dimension = buf.readUtf(128); Identifier type = Identifier.parse(buf.readUtf(128));
            int part = buf.readVarInt(), parts = buf.readVarInt();
            if (parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts) throw new IllegalArgumentException("生物雷达分包无效");
            int count = MapStreamCodecs.count(buf, BATCH_SIZE); var targets = new ArrayList<Target>(count);
            for (int i = 0; i < count; i++) {
                var target = new Target(buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble());
                if (!Double.isFinite(target.x()) || !Double.isFinite(target.y()) || !Double.isFinite(target.z()))
                    throw new IllegalArgumentException("生物雷达坐标无效");
                targets.add(target);
            }
            return new MapMobSnapshotPayload(request, dimension, type, part, parts, List.copyOf(targets));
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, MapMobSnapshotPayload value) {
            buf.writeUUID(value.request()); buf.writeUtf(value.dimension(), 128); buf.writeUtf(value.entityType().toString(), 128);
            buf.writeVarInt(value.part()); buf.writeVarInt(value.parts()); buf.writeVarInt(value.targets().size());
            for (var target : value.targets()) { buf.writeUUID(target.id()); buf.writeDouble(target.x()); buf.writeDouble(target.y()); buf.writeDouble(target.z()); }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
