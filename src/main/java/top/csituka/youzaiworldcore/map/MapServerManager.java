package top.csituka.youzaiworldcore.map;

import com.google.gson.JsonArray;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.JsonFileStore;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.config.UserSettings;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.mixin.map.ChunkMapAccessor;
import top.csituka.youzaiworldcore.network.MapActionPayload;
import top.csituka.youzaiworldcore.network.MapActionResultPayload;
import top.csituka.youzaiworldcore.network.MapLivePayload;
import top.csituka.youzaiworldcore.network.MapSessionPayload;
import top.csituka.youzaiworldcore.network.MapTilePayload;
import top.csituka.youzaiworldcore.network.MapViewRequestPayload;
import top.csituka.youzaiworldcore.network.MapWaypointsPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 地图服务端权威入口。公共点、共享玩家、地形与加载等级均在认证之后提供；
 * 配置使用 map_module，地形与公共点随世界保存在 data/yzwc/data/map_module。
 */
@SuppressWarnings("null")
public final class MapServerManager {
    private static final UUID SERVER_OWNER = new UUID(0, 0);
    private static final Map<UUID, Subscription> CLIENTS = new LinkedHashMap<>();
    private static final Map<UUID, Cursor> CAPTURES = new HashMap<>();
    private static final Map<UUID, Boolean> VISIBILITY = new HashMap<>();
    private static final LinkedHashMap<MapTileKey, Integer> SAMPLED = new LinkedHashMap<>();
    private static final List<MapWaypoint> POINTS = new ArrayList<>();
    private static MinecraftServer server;
    private static MapTerrainStore terrain;
    private static JsonFileStore metadata;
    private static UUID worldId;
    private static MapSampler.Job sampling;
    private static int playerCursor;
    private static long pointsRevision;
    private static boolean metadataDirty;
    private MapServerManager() { }

    /** 在服务端初始化阶段注册生命周期，不启动额外端口或网页服务。 */
    public static void initialize() {
        MapServerSettings.load();
        ServerLifecycleEvents.SERVER_STARTED.register(MapServerManager::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(MapServerManager::stop);
        ServerTickEvents.END_SERVER_TICK.register(MapServerManager::tick);
        DebugLogger.info("MapServerManager", "地图共享服务已注册");
    }

    private static void start(MinecraftServer instance) {
        server = instance;
        metadata = new JsonFileStore(ModPaths.worldDataFile(instance, GlobalSettings.MAP_MODULE));
        metadata.setDefaultsWriter(() -> {
            var section = metadata.section(GlobalSettings.MAP_MODULE);
            section.set("world_id", UUID.randomUUID().toString());
            section.set("waypoints", new JsonArray());
        });
        metadata.loadOrCreateDefaults();
        var section = metadata.section(GlobalSettings.MAP_MODULE);
        worldId = MapDataCodec.uuid(section, "world_id");
        var entries = section.getObjectList("waypoints");
        POINTS.clear();
        if (entries != null) {
            if (entries.size() > 512) section.fail("waypoints", "公共点数量不能超过 512");
            Set<UUID> ids = new java.util.HashSet<>();
            for (var entry : entries) {
                var point = MapDataCodec.readWaypoint(entry);
                if (!point.shared() || !ids.add(point.id())) entry.fail("id", "公共路径点身份无效或重复");
                POINTS.add(point);
            }
        }
        terrain = new MapTerrainStore(instance);
        pointsRevision++;
        DebugLogger.info("MapServerManager", "地图存档已打开：%s，公共点=%d", worldId, POINTS.size());
    }

    private static void stop(MinecraftServer instance) {
        if (server != instance) return;
        if (metadataDirty) savePoints();
        if (terrain != null) terrain.close();
        CLIENTS.clear(); CAPTURES.clear(); VISIBILITY.clear(); SAMPLED.clear(); POINTS.clear();
        sampling = null; terrain = null; metadata = null; server = null; worldId = null; playerCursor = 0;
        DebugLogger.info("MapServerManager", "地图共享服务已关闭");
    }

    /** 客户端接收器回到服务器线程后调用；非法视口不会触发磁盘读取或区块加载。 */
    public static void request(ServerPlayer player, MapViewRequestPayload view) {
        if (!eligible(player) || terrain == null || !ServerPlayNetworking.canSend(player, MapSessionPayload.ID)) return;
        var state = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new Subscription());
        int tick = server.getTickCount();
        if (tick - state.lastRequest < 5) return;
        state.lastRequest = tick;
        sendSession(player, state);
        long width = (long) view.maxX() - view.minX() + 1, height = (long) view.maxZ() - view.minZ() + 1;
        if (view.layer() == MapLayer.AUTO || width < 1 || height < 1 || width > 256 || height > 256
                || Math.abs((long) view.minX()) > MapTileKey.CHUNK_LIMIT || Math.abs((long) view.maxX()) > MapTileKey.CHUNK_LIMIT
                || Math.abs((long) view.minZ()) > MapTileKey.CHUNK_LIMIT || Math.abs((long) view.maxZ()) > MapTileKey.CHUNK_LIMIT) return;
        Identifier identifier = Identifier.tryParse(view.dimension());
        ServerLevel level = identifier == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, identifier));
        if (level == null || (view.layer().hasHeight() && (view.height() < level.getMinY() || view.height() > level.getMaxY()))) return;
        if (!view.layer().hasHeight()) view = new MapViewRequestPayload(view.dimension(), view.layer(), 0,
                view.minX(), view.minZ(), view.maxX(), view.maxZ(), view.generation());
        if (!view.equals(state.view)) {
            boolean changedLayer = state.view == null || !view.dimension().equals(state.view.dimension()) || view.layer() != state.view.layer()
                    || view.height() != state.view.height() || view.generation() != state.view.generation();
            state.view = view;
            state.cursor = (int) (width * (height / 2) + width / 2);
            if (changedLayer) state.sent.clear();
        }
    }

    private static void tick(MinecraftServer instance) {
        if (instance != server || terrain == null) return;
        terrain.tick();
        int tick = instance.getTickCount();
        CLIENTS.keySet().removeIf(id -> instance.getPlayerList().getPlayer(id) == null);
        CAPTURES.keySet().removeIf(id -> instance.getPlayerList().getPlayer(id) == null);
        VISIBILITY.keySet().removeIf(id -> instance.getPlayerList().getPlayer(id) == null);
        if (MapServerSettings.enabled && MapServerSettings.shareTerrain && terrain.acceptsSamples()) capture();
        var subscribers = new ArrayList<>(CLIENTS.entrySet());
        for (int index = 0; index < subscribers.size(); index++) {
            var entry = subscribers.get((index + tick) % subscribers.size());
            ServerPlayer player = instance.getPlayerList().getPlayer(entry.getKey());
            if (!eligible(player)) continue;
            var state = entry.getValue();
            state.credit = Math.min(Math.max(MapServerSettings.bandwidth, 106496), state.credit + MapServerSettings.bandwidth / 20.0);
            if (tick % 20 == 0) {
                sendSession(player, state);
                if (state.pointsRevision != pointsRevision) sendPoints(player, state);
                if (state.view != null && tick - state.lastRequest <= 100) sendLive(player, state);
            }
            if (MapServerSettings.enabled && MapServerSettings.shareTerrain && state.view != null && tick - state.lastRequest <= 100) {
                stream(player, state);
            }
        }
        if (metadataDirty && tick % 200 == 0) savePoints();
    }

    private static void capture() {
        long deadline = System.nanoTime() + MapServerSettings.budgetMicros * 1000L;
        int remaining = MapServerSettings.columns;
        if (sampling != null && ((ServerLevel) sampling.level()).getChunkSource()
                .getChunkNow(sampling.key().chunkX(), sampling.key().chunkZ()) != sampling.chunk()) sampling = null;
        while (remaining > 0 && System.nanoTime() < deadline && terrain.acceptsSamples()) {
            if (sampling == null) sampling = nextSample();
            if (sampling == null) return;
            int done = sampling.step(remaining, deadline);
            remaining -= done;
            if (!sampling.complete()) return;
            terrain.put(sampling.finish());
            SAMPLED.put(sampling.key(), server.getTickCount());
            if (MapServerSettings.shareStructures && sampling.key().layer() == MapLayer.SURFACE) discoverStructures(sampling);
            while (SAMPLED.size() > MapServerSettings.cacheTiles) SAMPLED.remove(SAMPLED.keySet().iterator().next());
            sampling = null;
        }
    }

    private static MapSampler.Job nextSample() {
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return null;
        var offsets = MapScanPattern.offsets(MapServerSettings.captureRadius);
        for (int attempt = 0; attempt < 32; attempt++) {
            ServerPlayer player = players.get(Math.floorMod(playerCursor++, players.size()));
            if (!eligible(player) || !player.isAlive()) continue;
            var cursor = CAPTURES.computeIfAbsent(player.getUUID(), ignored -> new Cursor());
            int px = Math.floorDiv(player.getBlockX(), 16), pz = Math.floorDiv(player.getBlockZ(), 16);
            if (px != cursor.x || pz != cursor.z) { cursor.x = px; cursor.z = pz; cursor.index = 0; }
            int sequence = cursor.index++;
            var offset = offsets.get(Math.floorMod(sequence / 2, offsets.size()));
            int x = px + offset.x(), z = pz + offset.z();
            if (Math.abs((long) x) > MapTileKey.CHUNK_LIMIT || Math.abs((long) z) > MapTileKey.CHUNK_LIMIT) continue;
            var chunk = player.level().getChunkSource().getChunkNow(x, z);
            if (chunk == null) continue;
            MapLayer layer = MapLayer.SURFACE;
            int height = 0;
            var subscription = CLIENTS.get(player.getUUID());
            if ((sequence & 1) != 0 && subscription != null && subscription.view != null
                    && subscription.view.dimension().equals(player.level().dimension().identifier().toString())) {
                layer = subscription.view.layer(); height = subscription.view.height();
            }
            var key = new MapTileKey(player.level().dimension().identifier().toString(), layer, height, x, z);
            if (server.getTickCount() - SAMPLED.getOrDefault(key, -1000) < 100) continue;
            return MapSampler.start(player.level(), chunk, key);
        }
        return null;
    }

    private static void stream(ServerPlayer player, Subscription state) {
        if (!ServerPlayNetworking.canSend(player, MapTilePayload.ID)) return;
        var view = state.view;
        int width = view.maxX() - view.minX() + 1, height = view.maxZ() - view.minZ() + 1;
        for (int scanned = 0; scanned < 48; scanned++) {
            int cursor = Math.floorMod(state.cursor++, width * height);
            var key = new MapTileKey(view.dimension(), view.layer(), view.height(), view.minX() + cursor % width, view.minZ() + cursor / width);
            MapTile tile = terrain.get(key);
            if (tile == null || state.sent.getOrDefault(key, Long.MIN_VALUE) == tile.revision()) continue;
            int bytes = 2200 + tile.biomes().stream().mapToInt(value -> value.length() * 3 + 8).sum();
            if (state.credit < bytes) return;
            ServerPlayNetworking.send(player, new MapTilePayload(worldId, tile));
            state.credit -= bytes; state.bytes += bytes; state.tiles++;
            state.sent.put(key, tile.revision());
            while (state.sent.size() > 8192) state.sent.remove(state.sent.keySet().iterator().next());
        }
    }

    private static int flags(ServerPlayer player) {
        if (!MapServerSettings.enabled) return 0;
        int flags = MapSessionPayload.ENABLED;
        if (MapServerSettings.shareTerrain) flags |= MapSessionPayload.TERRAIN;
        if (MapServerSettings.sharePlayers) flags |= MapSessionPayload.RADAR;
        if (MapServerSettings.shareWaypoints) flags |= MapSessionPayload.WAYPOINTS;
        if (MapServerSettings.shareLoadState) flags |= MapSessionPayload.LOAD_STATE;
        if (canTeleport(player)) flags |= MapSessionPayload.TELEPORT;
        if (canManage(player)) flags |= MapSessionPayload.MANAGE;
        if (canPublish(player)) flags |= MapSessionPayload.PUBLISH;
        return flags;
    }

    private static void sendSession(ServerPlayer player, Subscription state) {
        if (!ServerPlayNetworking.canSend(player, MapSessionPayload.ID)) return;
        int flags = flags(player);
        if (state.flags == flags) return;
        state.flags = flags; state.pointsRevision = -1;
        var dimensions = server.levelKeys().stream().sorted(java.util.Comparator.comparing(value -> value.identifier().toString()))
                .limit(128).map(key -> {
                    var level = server.getLevel(key);
                    return new MapSessionPayload.Dimension(key.identifier().toString(), level.getMinY(), level.getMaxY());
                }).toList();
        ServerPlayNetworking.send(player, new MapSessionPayload(worldId, flags, dimensions));
        sendPoints(player, state);
    }

    private static void sendPoints(ServerPlayer player, Subscription state) {
        if (!ServerPlayNetworking.canSend(player, MapWaypointsPayload.ID)) return;
        var points = MapServerSettings.enabled && MapServerSettings.shareWaypoints
                ? POINTS.stream().filter(value -> value.kind() != MapWaypoint.Kind.STRUCTURE || MapServerSettings.shareStructures).toList()
                : List.<MapWaypoint>of();
        ServerPlayNetworking.send(player, new MapWaypointsPayload(worldId, points));
        state.pointsRevision = pointsRevision;
    }

    private static void sendLive(ServerPlayer receiver, Subscription state) {
        if (!ServerPlayNetworking.canSend(receiver, MapLivePayload.ID)) return;
        List<MapLivePayload.Player> players = new ArrayList<>();
        if (MapServerSettings.enabled && MapServerSettings.sharePlayers) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (players.size() >= 256) break;
                if (!eligible(player) || !player.isAlive() || player.isSpectator() || player.isInvisible()
                        || InvisibilityManager.isInvisible(player.getUUID()) || !visible(player)) continue;
                players.add(new MapLivePayload.Player(player.getUUID(), player.getName().getString(),
                        player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot()));
            }
        }
        List<MapLivePayload.LoadedChunk> chunks = new ArrayList<>();
        var view = state.view;
        if (MapServerSettings.enabled && MapServerSettings.shareLoadState) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(view.dimension())));
            if (level != null) {
                int centerX = (view.minX() + view.maxX()) / 2, centerZ = (view.minZ() + view.maxZ()) / 2;
                var access = (ChunkMapAccessor) level.getChunkSource().chunkMap;
                for (int z = Math.max(view.minZ(), centerZ - 15); z <= Math.min(view.maxZ(), centerZ + 16); z++) {
                    for (int x = Math.max(view.minX(), centerX - 15); x <= Math.min(view.maxX(), centerX + 16); x++) {
                        var holder = access.youzaiworldcore$getVisibleChunk((x & 0xFFFFFFFFL) | ((long) z << 32));
                        if (holder != null) chunks.add(new MapLivePayload.LoadedChunk(x, z, holder.getTicketLevel()));
                    }
                }
            }
        }
        ServerPlayNetworking.send(receiver, new MapLivePayload(worldId, view.dimension(), List.copyOf(players), List.copyOf(chunks)));
    }

    private static boolean visible(ServerPlayer player) {
        return VISIBILITY.computeIfAbsent(player.getUUID(), id -> UserSettings.section(id, GlobalSettings.MAP_MODULE)
                .getBoolean("share_position", true));
    }

    /** 网络操作的确认入口；拒绝、限速和成功均返回对应请求的结果。 */
    public static void receiveAction(ServerPlayer player, MapActionPayload request) {
        if (!eligible(player) || worldId == null) return;
        var state = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new Subscription());
        state.lastResult = "denied";
        boolean success = action(player, request);
        if (ServerPlayNetworking.canSend(player, MapActionResultPayload.ID)) ServerPlayNetworking.send(player,
                new MapActionResultPayload(worldId, request.point().id(), request.action(), success, state.lastResult));
    }

    /** 处理经认证的公共点操作与管理员传送；所有权始终取服务端已有记录。 */
    public static boolean action(ServerPlayer player, MapActionPayload request) {
        if (!eligible(player) || !MapServerSettings.enabled) return false;
        var subscription = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new Subscription());
        int tick = server.getTickCount();
        if (tick - subscription.lastAction < 4) return false;
        subscription.lastAction = tick;
        var input = request.point();
        if (request.action() == MapActionPayload.Action.HIDE_POSITION || request.action() == MapActionPayload.Action.SHOW_POSITION) {
            boolean value = request.action() == MapActionPayload.Action.SHOW_POSITION;
            VISIBILITY.put(player.getUUID(), value);
            UserSettings.section(player.getUUID(), GlobalSettings.MAP_MODULE).set("share_position", value);
            UserSettings.save(player.getUUID());
            return feedback(player, true, value ? "position_shown" : "position_hidden");
        }
        if (request.action() == MapActionPayload.Action.TELEPORT) {
            if (!canTeleport(player)) return feedback(player, false, "denied");
            ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(input.dimension())));
            if (target == null || input.y() < target.getMinY() || input.y() > target.getMaxY()
                    || !target.getWorldBorder().isWithinBounds(new net.minecraft.core.BlockPos(input.x(), input.y(), input.z()))) {
                return feedback(player, false, "invalid_position");
            }
            if (!player.teleportTo(target, input.x() + 0.5, input.y(), input.z() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true)) return feedback(player, false, "invalid_position");
            DebugLogger.info("MapServerManager", "管理员地图传送：%s → %s %d %d %d", player.getName().getString(), input.dimension(), input.x(), input.y(), input.z());
            return feedback(player, true, "teleported");
        }
        if (!MapServerSettings.shareWaypoints || !canPublish(player)) return feedback(player, false, "denied");
        if (request.action() == MapActionPayload.Action.ADD || request.action() == MapActionPayload.Action.UPDATE) {
            ServerLevel target = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(input.dimension())));
            if (target == null || input.y() < target.getMinY() || input.y() > target.getMaxY()
                    || !target.getWorldBorder().isWithinBounds(new net.minecraft.core.BlockPos(input.x(), input.y(), input.z()))) return feedback(player, false, "invalid_position");
        }
        var existing = POINTS.stream().filter(value -> value.id().equals(input.id())).findFirst().orElse(null);
        if (request.action() == MapActionPayload.Action.ADD) {
            if (existing != null || POINTS.size() >= MapServerSettings.maxShared
                    || (!canManage(player) && POINTS.stream().filter(value -> value.owner().equals(player.getUUID())).count() >= MapServerSettings.maxPerPlayer)) {
                return feedback(player, false, "limit");
            }
            POINTS.add(new MapWaypoint(input.id(), player.getUUID(), input.name(), input.group(), input.dimension(),
                    input.x(), input.y(), input.z(), input.color(), true, true, MapWaypoint.Kind.NORMAL, System.currentTimeMillis()));
        } else {
            if (existing == null) return feedback(player, false, "missing");
            boolean manage = canManage(player);
            if (!manage && (!existing.owner().equals(player.getUUID()) || existing.kind() != MapWaypoint.Kind.NORMAL)) {
                return feedback(player, false, "denied");
            }
            if ((request.action() == MapActionPayload.Action.LOCK || request.action() == MapActionPayload.Action.UNLOCK) && !manage) {
                return feedback(player, false, "denied");
            }
            if (request.action() == MapActionPayload.Action.DELETE) POINTS.remove(existing);
            else {
                MapWaypoint replacement = switch (request.action()) {
                    case LOCK, UNLOCK -> new MapWaypoint(existing.id(), existing.owner(), existing.name(), existing.group(), existing.dimension(),
                            existing.x(), existing.y(), existing.z(), existing.color(), true, true,
                            request.action() == MapActionPayload.Action.LOCK ? MapWaypoint.Kind.SERVER : MapWaypoint.Kind.NORMAL, existing.createdAt());
                    case UPDATE -> new MapWaypoint(existing.id(), existing.owner(), input.name(), input.group(), input.dimension(),
                            input.x(), input.y(), input.z(), input.color(), true, true, existing.kind(), existing.createdAt());
                    default -> null;
                };
                if (replacement == null) return feedback(player, false, "denied");
                POINTS.set(POINTS.indexOf(existing), replacement);
            }
        }
        pointsRevision++; savePoints();
        for (var entry : CLIENTS.entrySet()) {
            var online = server.getPlayerList().getPlayer(entry.getKey());
            if (eligible(online) && ServerPlayNetworking.canSend(online, MapWaypointsPayload.ID)) sendPoints(online, entry.getValue());
        }
        DebugLogger.info("MapServerManager", "公共点操作：%s %s %s", player.getName().getString(), request.action(), input.id());
        return feedback(player, true, "saved");
    }

    private static boolean feedback(ServerPlayer player, boolean result, String key) {
        var state = CLIENTS.get(player.getUUID());
        if (state != null) state.lastResult = key;
        if (!player.hasDisconnected()) player.sendSystemMessage(Component.translatable("map.youzaiworldcore." + key));
        return result;
    }

    public static boolean canManage(ServerPlayer player) {
        return LuckPermsHelper.checkPermission(player.createCommandSourceStack(), LuckPermsHelper.PERMISSION_MAP_MANAGE, Commands.LEVEL_ADMINS);
    }

    public static boolean canPublish(ServerPlayer player) {
        return canManage(player) || (MapServerSettings.allowPublish && LuckPermsHelper.checkPermission(
                player.createCommandSourceStack(), LuckPermsHelper.PERMISSION_MAP_SHARED, Commands.LEVEL_ALL));
    }

    public static boolean canTeleport(ServerPlayer player) {
        return MapServerSettings.allowTeleport && LuckPermsHelper.checkPermission(
                player.createCommandSourceStack(), LuckPermsHelper.PERMISSION_MAP_TELEPORT, Commands.LEVEL_ADMINS);
    }

    private static boolean eligible(ServerPlayer player) {
        return player != null && server != null && player.level().getServer() == server
                && !player.hasDisconnected() && !AuthPlayerHelper.shouldBlockActions(player);
    }

    private static void savePoints() {
        var values = new JsonArray();
        POINTS.forEach(value -> values.add(MapDataCodec.writeWaypoint(value)));
        metadata.section(GlobalSettings.MAP_MODULE).set("waypoints", values);
        metadata.save(); metadataDirty = false;
    }

    private static void discoverStructures(MapSampler.Job job) {
        int structureLimit = Math.min(256, MapServerSettings.maxShared / 2);
        long structureCount = POINTS.stream().filter(point -> point.kind() == MapWaypoint.Kind.STRUCTURE).count();
        if (POINTS.size() >= MapServerSettings.maxShared || structureCount >= structureLimit) return;
        for (var entry : job.chunk().getAllStarts().entrySet()) {
            if (!entry.getValue().isValid()) continue;
            Identifier type = job.level().registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
            if (type == null) continue;
            var position = entry.getValue().getBoundingBox().getCenter();
            if (Math.abs((long) position.getX()) > MapTileKey.WORLD_LIMIT || Math.abs((long) position.getY()) > MapTileKey.WORLD_LIMIT
                    || Math.abs((long) position.getZ()) > MapTileKey.WORLD_LIMIT) continue;
            String identity = job.key().dimension() + "/" + type + "/" + job.key().chunkX() + "/" + job.key().chunkZ();
            UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
            if (POINTS.stream().anyMatch(point -> point.id().equals(id))) continue;
            String name = type.toString();
            if (name.length() > 64) name = name.substring(0, 64);
            POINTS.add(new MapWaypoint(id, SERVER_OWNER, name, "已探索结构", job.key().dimension(), position.getX(), position.getY(), position.getZ(),
                    0xFFE8BB68, true, true, MapWaypoint.Kind.STRUCTURE, System.currentTimeMillis()));
            pointsRevision++; metadataDirty = true;
            structureCount++;
            if (POINTS.size() >= MapServerSettings.maxShared || structureCount >= structureLimit) break;
        }
    }

    /** 命令行读取的公共点快照。 */
    public static List<MapWaypoint> points() { return List.copyOf(POINTS); }

    /** @return 当前连接的地形同步统计 */
    public static Component performance(UUID player) {
        var state = CLIENTS.get(player);
        return Component.translatable("map.youzaiworldcore.performance", state == null ? 0 : state.tiles, state == null ? 0 : state.bytes / 1024);
    }

    /** 重载配置后使能力与公共点推送失效，不删除已探索地形。 */
    public static void reload() {
        MapServerSettings.load(); pointsRevision++; sampling = null;
        CLIENTS.values().forEach(state -> { state.flags = -1; state.pointsRevision = -1; });
    }

    private static final class Subscription {
        private MapViewRequestPayload view;
        private int cursor, flags = -1, lastRequest = -1000, lastAction = -1000;
        private long pointsRevision = -1, bytes, tiles;
        private double credit = MapServerSettings.bandwidth;
        private String lastResult = "denied";
        private final Map<MapTileKey, Long> sent = new LinkedHashMap<>();
    }

    private static final class Cursor { private int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE, index; }
}
