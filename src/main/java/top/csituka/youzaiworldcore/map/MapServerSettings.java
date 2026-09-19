package top.csituka.youzaiworldcore.map;

import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 服务端 map_module 配置；只控制本服共享能力，客户端视觉设置保持独立。 */
public final class MapServerSettings {
    public static final boolean DEFAULT_ENABLED = true, DEFAULT_SHARE_TERRAIN = true,
            DEFAULT_SHARE_PLAYERS = true, DEFAULT_SHARE_WAYPOINTS = true, DEFAULT_SHARE_STRUCTURES = true,
            DEFAULT_SHARE_LOAD_STATE = false, DEFAULT_ALLOW_TELEPORT = false, DEFAULT_ALLOW_PUBLISH = true,
            DEFAULT_API_UPLOAD = true, DEFAULT_CAPTURE_UNDERGROUND = true;
    public static final int DEFAULT_MAX_SHARED = 512, DEFAULT_MAX_PER_PLAYER = 32,
            DEFAULT_COLUMNS = 256, DEFAULT_BUDGET_MICROS = 2000, DEFAULT_BANDWIDTH = 131072,
            DEFAULT_CACHE_TILES = 8192, DEFAULT_CAPTURE_RADIUS = 8;
    public static boolean enabled = DEFAULT_ENABLED, shareTerrain = DEFAULT_SHARE_TERRAIN,
            sharePlayers = DEFAULT_SHARE_PLAYERS, shareWaypoints = DEFAULT_SHARE_WAYPOINTS,
            shareStructures = DEFAULT_SHARE_STRUCTURES, shareLoadState = DEFAULT_SHARE_LOAD_STATE,
            allowTeleport = DEFAULT_ALLOW_TELEPORT, allowPublish = DEFAULT_ALLOW_PUBLISH,
            apiUpload = DEFAULT_API_UPLOAD, captureUnderground = DEFAULT_CAPTURE_UNDERGROUND;
    public static int maxShared = DEFAULT_MAX_SHARED, maxPerPlayer = DEFAULT_MAX_PER_PLAYER,
            columns = DEFAULT_COLUMNS, budgetMicros = DEFAULT_BUDGET_MICROS, bandwidth = DEFAULT_BANDWIDTH,
            cacheTiles = DEFAULT_CACHE_TILES, captureRadius = DEFAULT_CAPTURE_RADIUS;
    private MapServerSettings() { }

    /** 加载服务端共享设置；格式错误由统一配置层处理。 */
    public static void load() {
        var section = GlobalSettings.section(GlobalSettings.MAP_MODULE);
        enabled = section.getBoolean("enabled", DEFAULT_ENABLED);
        shareTerrain = section.getBoolean("share_terrain", DEFAULT_SHARE_TERRAIN);
        sharePlayers = section.getBoolean("share_players", DEFAULT_SHARE_PLAYERS);
        shareWaypoints = section.getBoolean("share_waypoints", DEFAULT_SHARE_WAYPOINTS);
        shareStructures = section.getBoolean("share_structures", DEFAULT_SHARE_STRUCTURES);
        shareLoadState = section.getBoolean("share_load_state", DEFAULT_SHARE_LOAD_STATE);
        allowTeleport = section.getBoolean("allow_teleport", DEFAULT_ALLOW_TELEPORT);
        allowPublish = section.getBoolean("allow_publish", DEFAULT_ALLOW_PUBLISH);
        apiUpload = section.getBoolean("api_upload", DEFAULT_API_UPLOAD);
        captureUnderground = section.getBoolean("capture_underground", DEFAULT_CAPTURE_UNDERGROUND);
        maxShared = section.getInt("max_shared_waypoints", DEFAULT_MAX_SHARED, 1, 512);
        maxPerPlayer = section.getInt("max_waypoints_per_player", DEFAULT_MAX_PER_PLAYER, 1, 128);
        columns = section.getInt("columns_per_tick", DEFAULT_COLUMNS, 16, 2048);
        budgetMicros = section.getInt("sampling_budget_micros", DEFAULT_BUDGET_MICROS, 250, 10000);
        bandwidth = section.getInt("bytes_per_second_per_player", DEFAULT_BANDWIDTH, 8192, 1048576);
        cacheTiles = section.getInt("cache_tiles", DEFAULT_CACHE_TILES, 256, 32768);
        captureRadius = section.getInt("capture_radius", DEFAULT_CAPTURE_RADIUS, 2, 16);
        save();
        DebugLogger.info("MapServerSettings", "地图共享配置已加载，地形=%s，公共点=%s，玩家=%s", shareTerrain, shareWaypoints, sharePlayers);
    }

    /** 保存全部服务端地图配置。 */
    public static void save() {
        var section = GlobalSettings.section(GlobalSettings.MAP_MODULE);
        section.set("enabled", enabled);
        section.set("share_terrain", shareTerrain);
        section.set("share_players", sharePlayers);
        section.set("share_waypoints", shareWaypoints);
        section.set("share_structures", shareStructures);
        section.set("share_load_state", shareLoadState);
        section.set("allow_teleport", allowTeleport);
        section.set("allow_publish", allowPublish);
        section.set("api_upload", apiUpload);
        section.set("capture_underground", captureUnderground);
        section.set("max_shared_waypoints", maxShared);
        section.set("max_waypoints_per_player", maxPerPlayer);
        section.set("columns_per_tick", columns);
        section.set("sampling_budget_micros", budgetMicros);
        section.set("bytes_per_second_per_player", bandwidth);
        section.set("cache_tiles", cacheTiles);
        section.set("capture_radius", captureRadius);
        GlobalSettings.save();
    }

    /** 先恢复 DEFAULT 常量再写入，供初次启动和坏配置恢复调用。 */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED; shareTerrain = DEFAULT_SHARE_TERRAIN; sharePlayers = DEFAULT_SHARE_PLAYERS;
        shareWaypoints = DEFAULT_SHARE_WAYPOINTS; shareStructures = DEFAULT_SHARE_STRUCTURES;
        shareLoadState = DEFAULT_SHARE_LOAD_STATE; allowTeleport = DEFAULT_ALLOW_TELEPORT; allowPublish = DEFAULT_ALLOW_PUBLISH;
        apiUpload = DEFAULT_API_UPLOAD; captureUnderground = DEFAULT_CAPTURE_UNDERGROUND;
        maxShared = DEFAULT_MAX_SHARED; maxPerPlayer = DEFAULT_MAX_PER_PLAYER; columns = DEFAULT_COLUMNS;
        budgetMicros = DEFAULT_BUDGET_MICROS; bandwidth = DEFAULT_BANDWIDTH;
        cacheTiles = DEFAULT_CACHE_TILES; captureRadius = DEFAULT_CAPTURE_RADIUS;
        save();
    }
}
