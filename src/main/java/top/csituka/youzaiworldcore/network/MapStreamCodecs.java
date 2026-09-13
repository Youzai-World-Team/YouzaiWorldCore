package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import top.csituka.youzaiworldcore.map.MapLayer;
import top.csituka.youzaiworldcore.map.MapTile;
import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.map.MapWaypoint;

import java.util.ArrayList;

/** 地图协议使用定长像素数组和有上限的集合，不接受客户端提交地形。 */
public final class MapStreamCodecs {
    private MapStreamCodecs() { }

    /** 在分配集合前检查网络计数。 */
    public static int count(RegistryFriendlyByteBuf buf, int maximum) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("地图数据数量超限");
        return count;
    }

    /** 读取一个不可再修改的地形快照。 */
    public static MapTile readTile(RegistryFriendlyByteBuf buf) {
        MapTileKey key = new MapTileKey(buf.readUtf(128), buf.readEnum(MapLayer.class),
                buf.readInt(), buf.readInt(), buf.readInt());
        long revision = buf.readLong();
        int size = count(buf, 256);
        var biomes = new ArrayList<String>(size);
        int[] biomeColors = new int[size];
        for (int i = 0; i < size; i++) {
            biomes.add(buf.readUtf(128));
            biomeColors[i] = buf.readInt();
        }
        int[] colors = new int[256];
        short[] heights = new short[256];
        byte[] lights = new byte[256], indices = new byte[256];
        for (int i = 0; i < 256; i++) colors[i] = buf.readInt();
        for (int i = 0; i < 256; i++) heights[i] = buf.readShort();
        buf.readBytes(lights);
        buf.readBytes(indices);
        return new MapTile(key, revision, colors, heights, lights, indices, biomes, biomeColors);
    }

    /** 写入一个瓦片；最坏情况下也远小于单个自定义包的大小上限。 */
    public static void writeTile(RegistryFriendlyByteBuf buf, MapTile tile) {
        var key = tile.key();
        buf.writeUtf(key.dimension(), 128);
        buf.writeEnum(key.layer());
        buf.writeInt(key.height());
        buf.writeInt(key.chunkX());
        buf.writeInt(key.chunkZ());
        buf.writeLong(tile.revision());
        buf.writeVarInt(tile.biomes().size());
        for (int i = 0; i < tile.biomes().size(); i++) {
            buf.writeUtf(tile.biomes().get(i), 128);
            buf.writeInt(tile.biomeColors()[i]);
        }
        for (int value : tile.colors()) buf.writeInt(value);
        for (short value : tile.heights()) buf.writeShort(value);
        buf.writeBytes(tile.lights());
        buf.writeBytes(tile.biomeIndices());
    }

    /** 读取有长度与坐标限制的路径点。 */
    public static MapWaypoint readWaypoint(RegistryFriendlyByteBuf buf) {
        return new MapWaypoint(buf.readUUID(), buf.readUUID(), buf.readUtf(64), buf.readUtf(32),
                buf.readUtf(128), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readBoolean(), buf.readBoolean(), buf.readEnum(MapWaypoint.Kind.class), buf.readLong());
    }

    /** 写入路径点。 */
    public static void writeWaypoint(RegistryFriendlyByteBuf buf, MapWaypoint point) {
        buf.writeUUID(point.id());
        buf.writeUUID(point.owner());
        buf.writeUtf(point.name(), 64);
        buf.writeUtf(point.group(), 32);
        buf.writeUtf(point.dimension(), 128);
        buf.writeInt(point.x());
        buf.writeInt(point.y());
        buf.writeInt(point.z());
        buf.writeInt(point.color());
        buf.writeBoolean(point.enabled());
        buf.writeBoolean(point.shared());
        buf.writeEnum(point.kind());
        buf.writeLong(point.createdAt());
    }
}
