package top.csituka.youzaiworldcore.client.map;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.map.MapSettingsScreen;
import top.csituka.youzaiworldcore.client.screen.map.MapWaypointEditScreen;
import top.csituka.youzaiworldcore.client.screen.map.MapWaypointListScreen;
import top.csituka.youzaiworldcore.client.screen.map.YzWorldMapScreen;
import top.csituka.youzaiworldcore.map.MapLayer;
import top.csituka.youzaiworldcore.map.MapSampler;
import top.csituka.youzaiworldcore.map.MapScanPattern;
import top.csituka.youzaiworldcore.map.MapTile;
import top.csituka.youzaiworldcore.map.MapTileKey;
import top.csituka.youzaiworldcore.map.MapVertex;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.network.MapActionPayload;
import top.csituka.youzaiworldcore.network.MapActionResultPayload;
import top.csituka.youzaiworldcore.network.MapLivePayload;
import top.csituka.youzaiworldcore.network.MapSessionPayload;
import top.csituka.youzaiworldcore.network.MapTilePayload;
import top.csituka.youzaiworldcore.network.MapViewRequestPayload;
import top.csituka.youzaiworldcore.network.MapWaypointsPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 客户端地图装配、采样与连接状态。客户端只接收共享地形，不上传地形或直连 Api。 */
@SuppressWarnings("null")
public final class MapClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("youzaiworldcore", "map"));
    public static final KeyMapping OPEN = key("open", GLFW.GLFW_KEY_M);
    private static final KeyMapping TOGGLE = key("toggle", GLFW.GLFW_KEY_H), ZOOM_IN = key("zoom_in", GLFW.GLFW_KEY_RIGHT_BRACKET),
            ZOOM_OUT = key("zoom_out", GLFW.GLFW_KEY_LEFT_BRACKET), LAYER = key("layer", GLFW.GLFW_KEY_Y),
            POINTS_KEY = key("waypoints", GLFW.GLFW_KEY_U), ADD = key("add", GLFW.GLFW_KEY_N),
            TOGGLE_POINTS = key("toggle_points", GLFW.GLFW_KEY_J), SETTINGS = key("settings", GLFW.GLFW_KEY_COMMA),
            REFRESH = key("refresh", GLFW.GLFW_KEY_F9);
    private static final KeyMapping[] KEYS = {OPEN, TOGGLE, ZOOM_IN, ZOOM_OUT, LAYER, POINTS_KEY, ADD, TOGGLE_POINTS, SETTINGS, REFRESH};
    private static final MapTileCache CACHE = new MapTileCache();
    private static final LinkedHashMap<MapTileKey, Integer> SAMPLED = new LinkedHashMap<>();
    private static final ArrayDeque<MapVertex> TRAIL = new ArrayDeque<>();
    private static final Map<String, Seen> TRACKED_HISTORY = new HashMap<>();
    private static ClientLevel level;
    private static MapSampler.Job sampling;
    private static MapSessionPayload session;
    private static List<MapWaypoint> shared = List.of();
    private static List<MapLivePayload.Player> remotePlayers = List.of();
    private static Map<Long, Integer> loadLevels = Map.of();
    private static String loadDimension = "";
    private static List<Radar> radar = List.of();
    private static long liveAt;
    private static int ticks, scanCursor, scanX = Integer.MIN_VALUE, scanZ = Integer.MIN_VALUE, refresh;
    private static int shortcutsAfter;
    private static boolean alive;
    private static UUID tracked;
    private static UUID navigation;
    private static net.minecraft.client.gui.screens.Screen nextScreen;
    private static long receivedTiles;
    private record Pending(MapActionPayload.Action action, long deadline, java.util.function.BiConsumer<Boolean, String> callback) { }
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private record Seen(Radar marker, long time) { }
    public record Radar(UUID id, String name, String dimension, double x, double y, double z,
                        float yaw, int color, boolean player, Entity entity) { }
    private MapClient() { }

    /** 注册按键、Tick 和连接生命周期，接收器仍由 ClientNetworking 集中注册。 */
    public static void initialize() {
        MapSettings.load();
        ClientTickEvents.END_CLIENT_TICK.register(MapClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> reset());
        MapClientCommands.register();
        DebugLogger.info("MapClient", "悠哉地图已初始化：M 打开地图，N 新建路径点，U 查看路径点");
    }

    private static KeyMapping key(String key, int code) { return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.youzaiworldcore.map." + key, code, CATEGORY)); }
    public static MapTileCache cache() { return CACHE; }
    public static MapSessionPayload session() { return session; }
    public static long receivedTiles() { return receivedTiles; }
    public static List<Radar> radar() { return radar; }
    public static List<MapVertex> trail() { return List.copyOf(TRAIL); }
    public static UUID trackedId() { return tracked; }
    public static void track(UUID id) { tracked = id; TRACKED_HISTORY.clear(); }
    /** 选中路径点用于世界导航；传入 null 可取消。 */
    public static void navigate(UUID id) { navigation = id; }
    public static UUID navigation() { return navigation; }
    /** 延后一帧打开命令请求的页面，避开聊天界面的关闭流程。 */
    public static void openNext(net.minecraft.client.gui.screens.Screen screen) { nextScreen = screen; }
    /** 页面通过地图键关闭后清空短时间内的按键计数，避免同一次按键再次打开地图。 */
    public static void suppressShortcuts() { shortcutsAfter = ticks + 2; }

    /** 合并私人点与服务端公共点，公共点不能被本地配置覆盖。 */
    public static List<MapWaypoint> waypoints() {
        var points = new LinkedHashMap<UUID, MapWaypoint>();
        MapPersonalData.points().forEach(point -> points.put(point.id(), point)); shared.forEach(point -> points.put(point.id(), point));
        return List.copyOf(points.values());
    }

    public static List<String> dimensions() {
        var dimensions = new java.util.LinkedHashSet<String>();
        dimensions.add(dimension());
        if (session != null) session.dimensions().forEach(value -> dimensions.add(value.id()));
        for (var point : MapPersonalData.points()) dimensions.add(point.dimension());
        return List.copyOf(dimensions);
    }

    public static String dimension() { return level == null ? "minecraft:overworld" : level.dimension().identifier().toString(); }

    /** 自动模式在下界或玩家深入地表时切换到当前洞穴。 */
    public static MapLayer layer(String dimension) {
        MapLayer selected = MapSettings.layer();
        if (selected != MapLayer.AUTO) return selected;
        var player = Minecraft.getInstance().player;
        if (level == null || player == null || !dimension().equals(dimension)) return MapLayer.SURFACE;
        if (level.dimensionType().hasCeiling()) return MapLayer.CAVE;
        var chunk = level.getChunkSource().getChunk(Math.floorDiv(player.getBlockX(), 16), Math.floorDiv(player.getBlockZ(), 16), ChunkStatus.FULL, false);
        return chunk != null && player.getY() + 6 < chunk.getHeight(Heightmap.Types.WORLD_SURFACE, player.getBlockX(), player.getBlockZ())
                ? MapLayer.CAVE : MapLayer.SURFACE;
    }

    /** 洞穴按八格高度分段，固定高度使用玩家配置。 */
    public static int height(MapLayer layer) {
        return height(dimension(), layer);
    }

    /** 跨维度浏览使用目标维度的高度边界。 */
    public static int height(String dimension, MapLayer layer) {
        if (!layer.hasHeight()) return 0;
        var player = Minecraft.getInstance().player;
        int height = layer == MapLayer.CAVE && player != null && dimension().equals(dimension)
                ? Math.floorDiv(player.getBlockY(), 8) * 8 + 7 : MapSettings.fixedHeight();
        if (session != null) for (var value : session.dimensions()) if (value.id().equals(dimension)) return Math.clamp(height, value.minY(), value.maxY());
        return level == null || !dimension().equals(dimension) ? height : Math.clamp(height, level.getMinY(), level.getMaxY());
    }

    /** 当前世界的光照和主题底色快照。 */
    public static MapRaster.Style rasterStyle(String dimension) {
        var client = Minecraft.getInstance();
        return new MapRaster.Style(MapSettings.overlay(), MapSettings.enabled(MapSettings.Toggle.LIGHTING),
                level == null ? 0 : level.getSkyDarken(), client.options.gamma().get(),
                YzuiTheme.palette().surfaceLow(), YzuiTheme.palette().background(), loadDimension.equals(dimension) ? liveLoadLevels() : Map.of());
    }

    public static Map<Long, Integer> liveLoadLevels() {
        return System.currentTimeMillis() - liveAt > 3000 ? Map.of() : loadLevels;
    }
    public static String loadDimension() { return loadDimension; }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null) { if (level != null) reset(); return; }
        ticks++;
        if (nextScreen != null) { var screen = nextScreen; nextScreen = null; client.gui.setScreen(screen); }
        var expired = PENDING.entrySet().stream().filter(entry -> System.currentTimeMillis() > entry.getValue().deadline()).toList();
        for (var entry : expired) { PENDING.remove(entry.getKey()); entry.getValue().callback().accept(false, "request_timeout"); }
        if (client.level != level) {
            level = client.level; sampling = null; scanCursor = 0; scanX = Integer.MIN_VALUE; TRAIL.clear();
            alive = client.player.isAlive();
            if (MapPersonalData.worldId().isEmpty()) {
                String identity = client.getSingleplayerServer() != null
                        ? "local:" + client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                        : "server:" + (client.getCurrentServer() == null ? "unknown" : client.getCurrentServer().ip);
                MapPersonalData.selectWorld(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString(),
                        client.getCurrentServer() == null ? "单人世界" : client.getCurrentServer().name);
            }
        }
        if (dimension().equals("youzaiworldcore:login_hall")) return;
        boolean living = client.player.isAlive();
        if (alive && !living) MapPersonalData.death(client.player.getUUID(), dimension(), client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ());
        alive = living;
        if (living) {
            sample(client);
            if (ticks % 5 == 0) {
                var point = new MapVertex(Math.clamp(client.player.getX(), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT),
                        Math.clamp(client.player.getZ(), -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT));
                if (TRAIL.isEmpty() || Math.hypot(point.x() - TRAIL.getLast().x(), point.z() - TRAIL.getLast().z()) >= 1) TRAIL.add(point);
                while (TRAIL.size() > 256) TRAIL.removeFirst();
            }
        }
        if (ticks % 5 == 0) updateRadar(client);
        if (ticks % 20 == 0 && MapSettings.enabled(MapSettings.Toggle.SERVER_SYNC) && ClientPlayNetworking.canSend(MapViewRequestPayload.ID)) {
            MapViewRequestPayload request;
            if (client.gui.screen() instanceof YzWorldMapScreen map) request = map.subscription();
            else {
                // 小地图视口按设计单位绘制，世界跨度取决于设计边长而非屏幕显示尺寸。
                double radius = MapRenderer.designSize() / MapSettings.zoom() * 0.75;
                request = viewRequest(dimension(), layer(dimension()), height(layer(dimension())),
                        client.player.getX() - radius, client.player.getZ() - radius,
                        client.player.getX() + radius, client.player.getZ() + radius);
            }
            ClientPlayNetworking.send(request);
        }
        if (client.gui.screen() == null && living && ticks > shortcutsAfter) keys(client);
        else for (var key : KEYS) while (key.consumeClick()) { }
    }

    private static void sample(Minecraft client) {
        MapLayer layer = layer(dimension()); int height = height(layer);
        int cx = Math.floorDiv(client.player.getBlockX(), 16), cz = Math.floorDiv(client.player.getBlockZ(), 16);
        if (cx != scanX || cz != scanZ) { scanX = cx; scanZ = cz; scanCursor = 0; }
        if (sampling != null && (sampling.key().layer() != layer || sampling.key().height() != height
                || level.getChunkSource().getChunk(sampling.key().chunkX(), sampling.key().chunkZ(), ChunkStatus.FULL, false) != sampling.chunk())) sampling = null;
        long deadline = System.nanoTime() + 1_500_000L;
        if (sampling == null) {
            var offsets = MapScanPattern.offsets(16);
            for (int attempt = 0; attempt < 32 && System.nanoTime() < deadline; attempt++) {
                var offset = offsets.get(Math.floorMod(scanCursor++, offsets.size()));
                int x = cx + offset.x(), z = cz + offset.z();
                if (Math.abs((long) x) > MapTileKey.CHUNK_LIMIT || Math.abs((long) z) > MapTileKey.CHUNK_LIMIT) continue;
                var key = new MapTileKey(dimension(), layer, height, x, z);
                if (ticks - SAMPLED.getOrDefault(key, -1000) < 100) continue;
                var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) { sampling = MapSampler.start(level, chunk, key); break; }
            }
        }
        if (sampling != null) {
            sampling.step(MapSettings.columnsPerTick(), deadline);
            if (sampling.complete()) {
                CACHE.put(sampling.finish()); SAMPLED.put(sampling.key(), ticks); sampling = null;
                while (SAMPLED.size() > MapSettings.cacheTiles()) SAMPLED.remove(SAMPLED.keySet().iterator().next());
            }
        }
    }

    private static void keys(Minecraft client) {
        if (TOGGLE.consumeClick()) MapSettings.toggle(MapSettings.Toggle.MINIMAP);
        if (TOGGLE_POINTS.consumeClick()) MapSettings.toggle(MapSettings.Toggle.WAYPOINTS);
        if (ZOOM_IN.consumeClick()) MapSettings.setZoom(MapSettings.zoom() * 1.25);
        if (ZOOM_OUT.consumeClick()) MapSettings.setZoom(MapSettings.zoom() / 1.25);
        if (LAYER.consumeClick()) cycleLayer();
        if (REFRESH.consumeClick()) refresh();
        if (OPEN.consumeClick()) client.gui.setScreen(new YzWorldMapScreen(null));
        else if (POINTS_KEY.consumeClick()) client.gui.setScreen(new MapWaypointListScreen(null));
        else if (ADD.consumeClick()) client.gui.setScreen(new MapWaypointEditScreen(null, newPoint(dimension(), client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ()), false));
        else if (SETTINGS.consumeClick()) client.gui.setScreen(new MapSettingsScreen(null));
    }

    public static void cycleLayer() {
        MapSettings.setLayer(MapLayer.values()[(MapSettings.layer().ordinal() + 1) % MapLayer.values().length]);
        sampling = null; scanCursor = 0;
    }

    /** 刷新只重建本地快照与订阅代号，不删除服务器已经探索的地图。 */
    public static void refresh() { CACHE.clear(); SAMPLED.clear(); sampling = null; scanCursor = 0; refresh++; }

    public static MapWaypoint newPoint(String dimension, int x, int y, int z) {
        var player = Minecraft.getInstance().player;
        x = Math.clamp(x, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT);
        y = Math.clamp(y, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT);
        z = Math.clamp(z, -MapTileKey.WORLD_LIMIT, MapTileKey.WORLD_LIMIT);
        return new MapWaypoint(UUID.randomUUID(), player == null ? new UUID(0, 0) : player.getUUID(),
                MapTexts.text("new_point").getString(), "", dimension, x, y, z, YzuiTheme.primary(), true, false, MapWaypoint.Kind.NORMAL, System.currentTimeMillis());
    }

    /** 从世界矩形生成有界订阅，远距离浏览不会要求服务器生成区块。 */
    public static MapViewRequestPayload viewRequest(String dimension, MapLayer layer, int height, double x1, double z1, double x2, double z2) {
        int minX = blockChunk(Math.min(x1, x2)), minZ = blockChunk(Math.min(z1, z2));
        int maxX = blockChunk(Math.max(x1, x2)), maxZ = blockChunk(Math.max(z1, z2));
        if (maxX - minX >= 256) { int center = (minX + maxX) / 2; minX = center - 127; maxX = center + 128; }
        if (maxZ - minZ >= 256) { int center = (minZ + maxZ) / 2; minZ = center - 127; maxZ = center + 128; }
        return new MapViewRequestPayload(dimension, layer, height, minX, minZ, maxX, maxZ, refresh);
    }

    private static int blockChunk(double coordinate) {
        return Math.clamp(Math.floorDiv((int) Math.floor(coordinate), 16), -MapTileKey.CHUNK_LIMIT, MapTileKey.CHUNK_LIMIT);
    }

    private static void updateRadar(Minecraft client) {
        var markers = new LinkedHashMap<UUID, Radar>();
        for (Entity entity : level.entitiesForRendering()) {
            if (markers.size() >= 256) break;
            if (entity == client.player || !entity.isAlive() || entity.isInvisibleTo(client.player)) continue;
            boolean player = entity instanceof Player;
            if (player && (session != null || !MapSettings.enabled(MapSettings.Toggle.RADAR_PLAYERS))) continue;
            var category = entity.getType().getCategory();
            var toggle = player ? MapSettings.Toggle.RADAR_PLAYERS : category == MobCategory.MONSTER ? MapSettings.Toggle.RADAR_HOSTILE
                    : category == MobCategory.MISC ? MapSettings.Toggle.RADAR_OTHER : MapSettings.Toggle.RADAR_FRIENDLY;
            if (!MapSettings.enabled(toggle)) continue;
            int color = player ? 0xFF79BDEB : category == MobCategory.MONSTER ? 0xFFF08B87 : category == MobCategory.MISC ? 0xFFE3C17A : 0xFF86CF9E;
            markers.put(entity.getUUID(), new Radar(entity.getUUID(), entity.getName().getString(), dimension(), entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), color, player, entity));
        }
        if (MapSettings.enabled(MapSettings.Toggle.RADAR_PLAYERS) && System.currentTimeMillis() - liveAt <= 3000) {
            for (var player : remotePlayers) {
                if (player.id().equals(client.player.getUUID())) continue;
                markers.put(player.id(), new Radar(player.id(), player.name(), player.dimension(), player.x(), player.y(), player.z(), player.yaw(), 0xFF79BDEB, true, null));
            }
        }
        radar = List.copyOf(markers.values());
        if (tracked != null) for (var marker : radar) {
            if (marker.id().equals(tracked)) TRACKED_HISTORY.put(marker.dimension(), new Seen(marker, System.currentTimeMillis()));
        }
        TRACKED_HISTORY.values().removeIf(value -> System.currentTimeMillis() - value.time > 10000);
    }

    public static Radar tracked(String dimension) {
        Seen seen = TRACKED_HISTORY.get(dimension);
        return seen == null || System.currentTimeMillis() - seen.time > 10000 ? null : seen.marker;
    }

    /** 接收器执行前绑定当前连接，断线后的旧任务不能污染新服务器的数据。 */
    public static void receive(Minecraft client, Runnable action) {
        var connection = client.getConnection();
        client.execute(() -> { if (connection != null && client.getConnection() == connection && client.level != null) action.run(); });
    }

    public static void session(MapSessionPayload value) {
        if (session == null || !session.worldId().equals(value.worldId())) {
            CACHE.clear(); SAMPLED.clear(); shared = List.of(); remotePlayers = List.of(); loadLevels = Map.of();
            sampling = null; scanCursor = 0;
            MapPersonalData.selectWorld(value.worldId().toString(), "服务器地图");
        }
        session = value;
        if (!value.allows(MapSessionPayload.RADAR)) { remotePlayers = List.of(); radar = List.of(); TRACKED_HISTORY.clear(); }
        if (!value.allows(MapSessionPayload.WAYPOINTS)) shared = List.of();
        if (!value.allows(MapSessionPayload.LOAD_STATE)) loadLevels = Map.of();
        DebugLogger.info("MapClient", "地图服务已连接：%s，能力=%d", value.worldId(), value.flags());
    }

    public static void tile(MapTilePayload value) {
        if (session == null || !session.worldId().equals(value.worldId()) || !session.allows(MapSessionPayload.TERRAIN)) return;
        CACHE.put(value.tile()); receivedTiles++;
    }

    public static void waypoints(MapWaypointsPayload value) {
        if (session != null && session.worldId().equals(value.worldId()) && session.allows(MapSessionPayload.WAYPOINTS)) shared = value.points();
    }

    public static void live(MapLivePayload value) {
        if (session == null || !session.worldId().equals(value.worldId())) return;
        remotePlayers = session.allows(MapSessionPayload.RADAR) ? value.players() : List.of();
        var levels = new HashMap<Long, Integer>();
        if (session.allows(MapSessionPayload.LOAD_STATE)) for (var chunk : value.chunks()) levels.put(MapTileCache.position(chunk.x(), chunk.z()), chunk.ticketLevel());
        loadLevels = Map.copyOf(levels); loadDimension = value.dimension(); liveAt = System.currentTimeMillis();
    }

    /** 发出需要服务器核验的操作，离线或能力不足时保留用户编辑内容。 */
    public static boolean send(MapActionPayload.Action action, MapWaypoint point) {
        if (session == null || !ClientPlayNetworking.canSend(MapActionPayload.ID)) { message("server_required"); return false; }
        ClientPlayNetworking.send(new MapActionPayload(action, point)); return true;
    }

    /** 提交需要 UI 确认的请求；超时保留编辑内容，断线自动清理回调。 */
    public static boolean send(MapActionPayload.Action action, MapWaypoint point, java.util.function.BiConsumer<Boolean, String> callback) {
        if (PENDING.size() >= 64 || PENDING.containsKey(point.id())) return false;
        if (!send(action, point)) return false;
        PENDING.put(point.id(), new Pending(action, System.currentTimeMillis() + 10000, callback)); return true;
    }

    /** 服务端确认当前世界的操作结果。 */
    public static void actionResult(MapActionResultPayload value) {
        if (session == null || !session.worldId().equals(value.worldId())) return;
        var pending = PENDING.get(value.id());
        if (pending != null && pending.action() == value.action()) { PENDING.remove(value.id()); pending.callback().accept(value.success(), value.message()); }
    }

    public static void message(String key, Object... args) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(MapTexts.text(key, args));
    }

    private static void reset() {
        MapRenderer.reset(); CACHE.clear(); SAMPLED.clear(); TRAIL.clear(); TRACKED_HISTORY.clear();
        sampling = null; session = null; level = null; shared = List.of(); remotePlayers = List.of(); radar = List.of();
        loadLevels = Map.of(); loadDimension = ""; liveAt = 0; ticks = 0; receivedTiles = 0; tracked = null; navigation = null;
        MapPersonalData.disconnect();
        PENDING.clear(); nextScreen = null; shortcutsAfter = 0;
    }
}
