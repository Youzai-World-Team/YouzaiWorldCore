package top.csituka.youzaiworldcore.client.config;

import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.map.MapDatabase;
import top.csituka.youzaiworldcore.map.MapSqlSettings;
import top.csituka.youzaiworldcore.map.MapLayer;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.EnumMap;
import java.util.Locale;

/** 地图的本机显示设置，存入 yzwc/client/map_module/map.db。 */
public final class MapSettings {
    public enum Shape {
        ROUNDED, CIRCLE, SQUARE
    }

    public enum LabelPosition {
        BELOW, ABOVE, LEFT, RIGHT
    }

    public enum Overlay {
        TERRAIN, BIOME, LOAD_STATE
    }

    public enum Toggle {
        MINIMAP(true), ROTATE(false), COORDINATES(true), BIOME_LABEL(true), LAYER_LABEL(true),
        WAYPOINTS(true), WAYPOINT_HUD(true), PORTAL_PROJECTION(true), DRAWINGS(true),
        RADAR_PLAYERS(true), RADAR_HOSTILE(true), RADAR_FRIENDLY(true), RADAR_OTHER(false),
        MARKER_ICONS(true), MARKER_LABELS(true), ANCHORS(true), RADAR_TAMED(true), QUICK_LOCATE(true),
        REMEMBER_VIEW(false),
        RADAR_ICONS(true), GRID(false), LIGHTING(true), SERVER_SYNC(true), AVOID_HUD(true), EXACT_LOAD_LEVEL(false);

        private final boolean defaultValue;

        Toggle(boolean value) {
            defaultValue = value;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final int DEFAULT_SIZE = 144, DEFAULT_FIXED_HEIGHT = 64, DEFAULT_DEATH_LIMIT = 5;
    public static final int DEFAULT_COLUMNS_PER_TICK = 256,
            DEFAULT_WAYPOINT_DISTANCE = 10000;
    public static final double DEFAULT_ZOOM = 1.0;
    public static final LabelPosition DEFAULT_LABEL_POSITION = LabelPosition.BELOW;
    public static final @NonNull Shape DEFAULT_SHAPE = Shape.ROUNDED;
    public static final @NonNull MapLayer DEFAULT_LAYER = MapLayer.AUTO;
    public static final @NonNull Overlay DEFAULT_OVERLAY = Overlay.TERRAIN;
    private static final EnumMap<@NonNull Toggle, Boolean> TOGGLES = new EnumMap<>(Toggle.class);
    private static int size = DEFAULT_SIZE, fixedHeight = DEFAULT_FIXED_HEIGHT, deathLimit = DEFAULT_DEATH_LIMIT;
    private static int columnsPerTick = DEFAULT_COLUMNS_PER_TICK;
    private static MapDatabase database;
    public static MapDatabase database() {
        if (database == null) database = new MapDatabase(ModPaths.clientMapDatabase());
        return database;
    }
    private static int waypointDistance = DEFAULT_WAYPOINT_DISTANCE;
    private static double zoom = DEFAULT_ZOOM;
    private static LabelPosition labelPosition = DEFAULT_LABEL_POSITION;
    private static Shape shape = DEFAULT_SHAPE;
    private static MapLayer layer = DEFAULT_LAYER;
    private static Overlay overlay = DEFAULT_OVERLAY;
    static {
        resetFields();
    }

    private MapSettings() {
    }

    public static boolean enabled(Toggle option) {
        return TOGGLES.get(option);
    }

    public static int size() {
        return size;
    }

    public static int fixedHeight() {
        return fixedHeight;
    }

    public static int deathLimit() {
        return deathLimit;
    }

    public static int columnsPerTick() {
        return columnsPerTick;
    }

    public static int waypointDistance() {
        return waypointDistance;
    }

    public static double zoom() {
        return zoom;
    }

    public static LabelPosition labelPosition() {
        return labelPosition;
    }

    public static Shape shape() {
        return shape;
    }

    public static MapLayer layer() {
        return layer;
    }

    public static Overlay overlay() {
        return overlay;
    }

    /** 切换显示开关并保存。 */
    @SuppressWarnings("null")
    public static void toggle(Toggle option) {
        TOGGLES.put(option, !enabled(option));
        save();
    }

    public static void setSize(int value) {
        size = Math.clamp(value, 80, 240);
        save();
    }

    public static void setFixedHeight(int value) {
        fixedHeight = Math.clamp(value, -4096, 4095);
        save();
    }

    public static void setDeathLimit(int value) {
        deathLimit = Math.clamp(value, 0, 50);
        save();
    }

    public static void setWaypointDistance(int value) {
        waypointDistance = Math.clamp(value, 64, 100000);
        save();
    }

    public static void setZoom(double value) {
        zoom = Double.isFinite(value) ? Math.clamp(value, 0.25, 8) : DEFAULT_ZOOM;
        save();
    }

    public static void setLabelPosition(LabelPosition value) {
        labelPosition = value;
        save();
    }

    public static void setShape(Shape value) {
        shape = value;
        save();
    }

    public static void setLayer(MapLayer value) {
        layer = value;
        save();
    }

    public static void setOverlay(Overlay value) {
        overlay = value;
        save();
    }

    /** 使用强类型 getter 加载，禁止吞掉配置格式错误。 */
    @SuppressWarnings("null")
    public static void load() {
        MapSqlSettings section = new MapSqlSettings(database(), "display");
        for (Toggle toggle : Toggle.values())
            TOGGLES.put(toggle, section.getBoolean(toggle.key(), toggle.defaultValue));
        size = section.getInt("size", DEFAULT_SIZE, 80, 240);
        fixedHeight = section.getInt("fixed_height", DEFAULT_FIXED_HEIGHT, -4096, 4095);
        deathLimit = section.getInt("death_limit", DEFAULT_DEATH_LIMIT, 0, 50);
        columnsPerTick = section.getInt("columns_per_tick", DEFAULT_COLUMNS_PER_TICK, 16, 1024);
        waypointDistance = section.getInt("waypoint_distance", DEFAULT_WAYPOINT_DISTANCE, 64, 100000);
        zoom = section.getDouble("zoom", DEFAULT_ZOOM, 0.25, 8);
        labelPosition = section.getEnum("label_position", DEFAULT_LABEL_POSITION, LabelPosition.class);
        shape = section.getEnum("shape", DEFAULT_SHAPE, Shape.class);
        layer = section.getEnum("layer", DEFAULT_LAYER, MapLayer.class);
        overlay = section.getEnum("overlay", DEFAULT_OVERLAY, Overlay.class);
        save();
        DebugLogger.info("MapSettings", "地图配置已加载，形状=%s，图层=%s", shape, layer);
    }

    /** 保存显示设置；私人数据保存在独立的 SQLite 记录中。 */
    public static void save() {
        MapSqlSettings section = new MapSqlSettings(database(), "display");
        for (Toggle option : Toggle.values())
            section.set(option.key(), enabled(option));
        section.set("size", size);
        section.set("fixed_height", fixedHeight);
        section.set("death_limit", deathLimit);
        section.set("columns_per_tick", columnsPerTick);
        section.set("waypoint_distance", waypointDistance);
        section.set("zoom", zoom);
        section.set("label_position", labelPosition);
        section.set("shape", shape);
        section.set("layer", layer);
        section.set("overlay", overlay);
        section.save();
        DebugLogger.debug("MapSettings", "地图显示设置已保存");
    }

    /** 恢复显示默认值，不删除玩家的路径点与绘图。 */
    public static void writeDefaults() {
        resetFields();
        save();
    }

    private static void resetFields() {
        for (Toggle toggle : Toggle.values())
            TOGGLES.put(toggle, toggle.defaultValue);
        size = DEFAULT_SIZE;
        fixedHeight = DEFAULT_FIXED_HEIGHT;
        deathLimit = DEFAULT_DEATH_LIMIT;
        columnsPerTick = DEFAULT_COLUMNS_PER_TICK;
        waypointDistance = DEFAULT_WAYPOINT_DISTANCE;
        zoom = DEFAULT_ZOOM;
        labelPosition = DEFAULT_LABEL_POSITION;
        shape = DEFAULT_SHAPE;
        layer = DEFAULT_LAYER;
        overlay = DEFAULT_OVERLAY;
    }
}
