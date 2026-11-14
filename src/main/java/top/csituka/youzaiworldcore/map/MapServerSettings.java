package top.csituka.youzaiworldcore.map;

import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.LinkedHashMap;

/** 服务端 map_module 配置；只控制本服共享能力，客户端视觉设置保持独立。 */
public final class MapServerSettings {
    public static final boolean DEFAULT_ENABLED = true, DEFAULT_SHARE_TERRAIN = true,
            DEFAULT_SHARE_PLAYERS = true,
            DEFAULT_SHARE_LOAD_STATE = false, DEFAULT_ALLOW_TELEPORT = false,
            DEFAULT_API_UPLOAD = true, DEFAULT_CAPTURE_UNDERGROUND = true;
    public static final int DEFAULT_BANDWIDTH = 131072,
            DEFAULT_CACHE_TILES = 8192;
    public static boolean enabled = DEFAULT_ENABLED, shareTerrain = DEFAULT_SHARE_TERRAIN,
            sharePlayers = DEFAULT_SHARE_PLAYERS, shareLoadState = DEFAULT_SHARE_LOAD_STATE,
            allowTeleport = DEFAULT_ALLOW_TELEPORT,
            apiUpload = DEFAULT_API_UPLOAD, captureUnderground = DEFAULT_CAPTURE_UNDERGROUND;
    public static int bandwidth = DEFAULT_BANDWIDTH,
            cacheTiles = DEFAULT_CACHE_TILES;
    public static volatile int scanIntervalSeconds = 60, chunksPerPass = 64, uploadIntervalMinutes = 2880;
    private static MapDatabase database;
    public static MapDatabase database() {
        if (database == null) database = new MapDatabase(ModPaths.serverMapDatabase());
        return database;
    }
    private MapServerSettings() { }

    /** 加载服务端共享设置；格式错误保留原数据库并终止加载。 */
    public static void load() {
        var section = new MapSqlSettings(database(), "server");
        enabled = section.getBoolean("enabled", DEFAULT_ENABLED);
        shareTerrain = section.getBoolean("share_terrain", DEFAULT_SHARE_TERRAIN);
        sharePlayers = section.getBoolean("share_players", DEFAULT_SHARE_PLAYERS);
        shareLoadState = section.getBoolean("share_load_state", DEFAULT_SHARE_LOAD_STATE);
        allowTeleport = section.getBoolean("allow_teleport", DEFAULT_ALLOW_TELEPORT);
        apiUpload = section.getBoolean("api_upload", DEFAULT_API_UPLOAD);
        captureUnderground = section.getBoolean("capture_underground", DEFAULT_CAPTURE_UNDERGROUND);
        bandwidth = section.getInt("bytes_per_second_per_player", DEFAULT_BANDWIDTH, 8192, 1048576);
        cacheTiles = section.getInt("cache_tiles", DEFAULT_CACHE_TILES, 256, 32768);
        scanIntervalSeconds = section.getInt("scan_interval_seconds", 60, 5, 3600);
        chunksPerPass = section.getInt("chunks_per_pass", 64, 1, 1024);
        uploadIntervalMinutes = section.getInt("upload_interval_minutes", 2880, 1, 43200);
        save();
        DebugLogger.info("MapServerSettings", "地图共享配置已加载，地形=%s，玩家=%s", shareTerrain, sharePlayers);
    }

    /** 只写当前有效设置，旧设置行保留但不再读取或写入。 */
    public static void save() {
        var values = new LinkedHashMap<String, String>();
        values.put("enabled", Boolean.toString(enabled));
        values.put("share_terrain", Boolean.toString(shareTerrain));
        values.put("share_players", Boolean.toString(sharePlayers));
        values.put("share_load_state", Boolean.toString(shareLoadState));
        values.put("allow_teleport", Boolean.toString(allowTeleport));
        values.put("api_upload", Boolean.toString(apiUpload));
        values.put("capture_underground", Boolean.toString(captureUnderground));
        values.put("bytes_per_second_per_player", Integer.toString(bandwidth));
        values.put("cache_tiles", Integer.toString(cacheTiles));
        values.put("scan_interval_seconds", Integer.toString(scanIntervalSeconds));
        values.put("chunks_per_pass", Integer.toString(chunksPerPass));
        values.put("upload_interval_minutes", Integer.toString(uploadIntervalMinutes));
        database().settings("server", values);
    }

    /** 先恢复 DEFAULT 常量再写入，不删除地形和私人数据。 */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED; shareTerrain = DEFAULT_SHARE_TERRAIN; sharePlayers = DEFAULT_SHARE_PLAYERS;
        shareLoadState = DEFAULT_SHARE_LOAD_STATE; allowTeleport = DEFAULT_ALLOW_TELEPORT;
        apiUpload = DEFAULT_API_UPLOAD; captureUnderground = DEFAULT_CAPTURE_UNDERGROUND;
        bandwidth = DEFAULT_BANDWIDTH;
        cacheTiles = DEFAULT_CACHE_TILES;
        scanIntervalSeconds = 60; chunksPerPass = 64; uploadIntervalMinutes = 2880;
        save();
    }
}
