package top.csituka.youzaiworldcore.map;

import java.io.*;
import java.util.ArrayList;
import java.util.UUID;

/** SQLite BLOB 的内部二进制格式，不提供用户数据导入导出。 */
public final class MapBinaryCodec {
    @FunctionalInterface public interface Writer { void write(DataOutputStream out) throws IOException; }
    @FunctionalInterface public interface Reader<T> { T read(DataInputStream in) throws IOException; }
    private MapBinaryCodec() { }
    public static byte[] encode(Writer writer) {
        try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) {
            out.writeInt(1); writer.write(out); out.flush(); return bytes.toByteArray();
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    public static <T> T decode(byte[] bytes, Reader<T> reader) {
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) throw new IOException("地图数据库记录版本无效");
            T result = reader.read(in);
            if (in.read() != -1) throw new IOException("地图数据库记录包含多余字节");
            return result;
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
    public static byte[] waypoint(MapWaypoint p) {
        return encode(out -> {
            out.writeUTF(p.id().toString()); out.writeUTF(p.owner().toString()); out.writeUTF(p.name()); out.writeUTF(p.group());
            out.writeUTF(p.dimension()); out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z()); out.writeInt(p.color());
            out.writeBoolean(p.enabled()); out.writeBoolean(p.shared()); out.writeUTF(p.kind().name()); out.writeLong(p.createdAt());
        });
    }
    public static MapWaypoint waypoint(byte[] bytes) {
        return decode(bytes, in -> new MapWaypoint(UUID.fromString(in.readUTF()), UUID.fromString(in.readUTF()), in.readUTF(), in.readUTF(),
                in.readUTF(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readBoolean(), in.readBoolean(),
                MapWaypoint.Kind.valueOf(in.readUTF()), in.readLong()));
    }
    /** 分组名称与图标独立保存，编辑不改变任何路径点身份。 */
    public static byte[] group(MapWaypointGroup group) {
        return encode(out -> { out.writeUTF(group.name()); out.writeUTF(group.icon().id()); });
    }
    public static MapWaypointGroup group(String id, byte[] bytes) {
        return decode(bytes, in -> new MapWaypointGroup(id, in.readUTF(), MapWaypointIcon.byId(in.readUTF())));
    }
    public static byte[] tile(MapTile t) {
        return encode(out -> {
            out.writeInt(t.biomes().size());
            for (int i = 0; i < t.biomes().size(); i++) { out.writeUTF(t.biomes().get(i)); out.writeInt(t.biomeColors()[i]); }
            for (int color : t.colors()) out.writeInt(color);
            for (short height : t.heights()) out.writeShort(height);
            out.write(t.lights()); out.write(t.biomeIndices());
        });
    }
    public static MapTile tile(MapTileKey key, long revision, byte[] bytes) {
        return decode(bytes, in -> {
            int size = in.readInt(); if (size < 1 || size > 256) throw new IOException("地图群系数量无效");
            var biomes = new ArrayList<String>(); int[] biomeColors = new int[size];
            for (int i = 0; i < size; i++) { biomes.add(in.readUTF()); biomeColors[i] = in.readInt(); }
            int[] colors = new int[256]; short[] heights = new short[256]; byte[] lights = new byte[256], indices = new byte[256];
            for (int i = 0; i < 256; i++) colors[i] = in.readInt();
            for (int i = 0; i < 256; i++) heights[i] = in.readShort();
            in.readFully(lights); in.readFully(indices);
            return new MapTile(key, revision, colors, heights, lights, indices, biomes, biomeColors);
        });
    }
}
