package top.csituka.youzaiworldcore.client.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import top.csituka.youzaiworldcore.map.MapWaypoint;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** 独立编写的路径点交换格式与常见文本格式解析器；导入一律创建私人点。 */
public final class MapTransfer {
    private static final Pattern COORDINATE = Pattern.compile("^\\[YZMAP (-?\\d+) (-?\\d+) (-?\\d+) ([a-z0-9_.-]+:[a-z0-9_./-]+)] (.{1,64})$");
    private static final int[] COLORS = {0x191919, 0x3566AF, 0x549844, 0x46989C, 0xBB4848, 0x9150AB, 0xD4A34F, 0xB6B6B6,
            0x555555, 0x78A7EE, 0x91D479, 0x78DDDD, 0xEF8E8B, 0xCE98DD, 0xEFE391, 0xF5F5F5};
    private MapTransfer() { }

    /** 可直接粘贴导入的聊天坐标。 */
    public static String coordinate(MapWaypoint point) {
        return "[YZMAP " + point.x() + " " + point.y() + " " + point.z() + " " + point.dimension() + "] " + point.name().replace('\n', ' ').replace('\r', ' ');
    }

    /** 打开带坐标的聊天草稿，由玩家确认发送。 */
    public static void share(MapWaypoint point) {
        Minecraft.getInstance().gui.openChatAndAddText(ChatComponent.ChatMethod.MESSAGE, coordinate(point));
    }

    /** 导出不带账户身份或权限信息的 JSON 文本。 */
    public static String encode(List<MapWaypoint> points) {
        JsonObject root = new JsonObject(); root.addProperty("format", "yzwc_map"); root.addProperty("version", 1);
        JsonArray entries = new JsonArray();
        for (var point : points) {
            var entry = new JsonObject();
            entry.addProperty("name", point.name()); entry.addProperty("group", point.group()); entry.addProperty("dimension", point.dimension());
            entry.addProperty("x", point.x()); entry.addProperty("y", point.y()); entry.addProperty("z", point.z());
            entry.addProperty("color", point.color() & 0xFFFFFF); entry.addProperty("enabled", point.enabled()); entries.add(entry);
        }
        root.add("waypoints", entries); return root.toString();
    }

    /** 先完整校验再返回预览，单次最多 512 个点/256KiB 文本，错误不会改动已有数据。 */
    public static List<MapWaypoint> decode(String text, String dimension, UUID owner) {
        if (text == null || text.isBlank() || text.length() > 262144
                || text.getBytes(StandardCharsets.UTF_8).length > 262144) throw new IllegalArgumentException("import_invalid");
        var points = new ArrayList<MapWaypoint>();
        try {
            text = text.strip();
            if (text.startsWith("{")) {
                JsonObject root = JsonParser.parseString(text).getAsJsonObject();
                if (!"yzwc_map".equals(string(root, "format", "")) || integer(root, "version") != 1) throw new IllegalArgumentException("import_version");
                var values = root.getAsJsonArray("waypoints");
                if (values == null || values.size() > 512) throw new IllegalArgumentException("limit");
                for (var raw : values) {
                    var item = raw.getAsJsonObject();
                    boolean enabled = true;
                    if (item.has("enabled")) {
                        if (!item.get("enabled").isJsonPrimitive() || !item.get("enabled").getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("import_invalid");
                        enabled = item.get("enabled").getAsBoolean();
                    }
                    points.add(point(owner, string(item, "name", ""), string(item, "group", ""), string(item, "dimension", dimension),
                            integer(item, "x"), integer(item, "y"), integer(item, "z"), integer(item, "color"), enabled));
                }
            } else {
                for (String raw : text.split("\\R")) {
                    String line = raw.strip();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    var coordinate = COORDINATE.matcher(line);
                    if (coordinate.matches()) {
                        points.add(point(owner, coordinate.group(5), "", coordinate.group(4), Integer.parseInt(coordinate.group(1)),
                                Integer.parseInt(coordinate.group(2)), Integer.parseInt(coordinate.group(3)), 0x7AB7C8, true));
                    } else if (line.startsWith("waypoint:")) {
                        String[] fields = line.split(":", -1);
                        if (fields.length < 8 || !(fields[7].equals("true") || fields[7].equals("false"))) throw new IllegalArgumentException("import_invalid");
                        int color = Integer.parseInt(fields[6]);
                        points.add(point(owner, fields[1], "Xaero", dimension, Integer.parseInt(fields[3]), Integer.parseInt(fields[4]),
                                Integer.parseInt(fields[5]), COLORS[Math.floorMod(color, COLORS.length)], !Boolean.parseBoolean(fields[7])));
                    } else if (line.startsWith("name:")) {
                        var fields = new java.util.HashMap<String, String>();
                        for (String part : line.split(",")) { int split = part.indexOf(':'); if (split > 0) fields.put(part.substring(0, split), part.substring(split + 1)); }
                        int red = channel(fields.getOrDefault("red", "0.5")), green = channel(fields.getOrDefault("green", "0.7")), blue = channel(fields.getOrDefault("blue", "0.8"));
                        points.add(point(owner, fields.get("name"), "VoxelMap", dimension, Integer.parseInt(fields.get("x")), Integer.parseInt(fields.get("y")),
                                Integer.parseInt(fields.get("z")), red << 16 | green << 8 | blue, !"false".equals(fields.get("enabled"))));
                    } else throw new IllegalArgumentException("import_invalid");
                    if (points.size() > 512) throw new IllegalArgumentException("limit");
                }
            }
        } catch (RuntimeException error) {
            String key = error.getMessage();
            throw new IllegalArgumentException("import_version".equals(key) || "limit".equals(key) ? key : "import_invalid");
        }
        if (points.isEmpty()) throw new IllegalArgumentException("import_invalid");
        return List.copyOf(points);
    }

    private static int integer(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("import_invalid");
        return value.getAsBigDecimal().intValueExact();
    }
    private static String string(JsonObject object, String key, String fallback) {
        var value = object.get(key); if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("import_invalid");
        return value.getAsString();
    }
    private static int channel(String value) {
        double number = Double.parseDouble(value);
        if (!Double.isFinite(number) || number < 0 || number > 1) throw new IllegalArgumentException("import_invalid");
        return (int) Math.round(number * 255);
    }
    private static MapWaypoint point(UUID owner, String name, String group, String dimension, int x, int y, int z, int color, boolean enabled) {
        return new MapWaypoint(UUID.randomUUID(), owner, name, group, dimension, x, y, z, color, enabled, false, MapWaypoint.Kind.NORMAL, System.currentTimeMillis());
    }
}
