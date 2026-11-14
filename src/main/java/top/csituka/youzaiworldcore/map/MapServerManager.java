package top.csituka.youzaiworldcore.map;

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
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.config.ModPaths;
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
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 地图服务端权威入口。本人锚点、共享玩家、地形与加载等级均在认证之后提供；
 * 配置使用 map_module，地形随世界保存在 data/yzwc/data/map_module。
 */
@SuppressWarnings("null")
public final class MapServerManager {
    private static final Map<UUID, Subscription> CLIENTS = new LinkedHashMap<>();
    private static final Map<UUID, Boolean> VISIBILITY = new HashMap<>();
    private static MinecraftServer server;
    private static MapTerrainStore terrain;
    private static MapDatabase metadata;
    private static MapDiskCapture capture;
    private static UUID worldId;
    private static MapApiUploader uploader;

    private MapServerManager() {
    }

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
        metadata = new MapDatabase(ModPaths.worldMapDatabase(instance));
        var identity = metadata.settings("identity");
        worldId = UUID.fromString(identity.getOrDefault("world_id", UUID.randomUUID().toString()));
        metadata.settings("identity", Map.of("world_id", worldId.toString()));
        terrain = new MapTerrainStore(metadata);
        capture = new MapDiskCapture(instance, terrain);
        uploader = new MapApiUploader(instance, terrain, metadata, worldId);
        DebugLogger.info("MapServerManager", "地图存档已打开：%s", worldId);
    }

    private static void stop(MinecraftServer instance) {
        if (server != instance)
            return;
        if (uploader != null)
            uploader.stop();
        uploader = null;
        if (capture != null)
            capture.close();
        capture = null;
        if (terrain != null)
            terrain.close();
        MapSocialManager.clear();
        MapMobRadarServer.clear();
        CLIENTS.clear();
        VISIBILITY.clear();
        if (metadata != null)
            metadata.close();
        terrain = null;
        metadata = null;
        server = null;
        worldId = null;
        DebugLogger.info("MapServerManager", "地图共享服务已关闭");
    }

    /** 客户端接收器回到服务器线程后调用；非法视口不会触发磁盘读取或区块加载。 */
    public static void request(ServerPlayer player, MapViewRequestPayload view) {
        if (!eligible(player) || terrain == null || !ServerPlayNetworking.canSend(player, MapSessionPayload.ID))
            return;
        var state = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new Subscription());
        int tick = server.getTickCount();
        if (tick - state.lastRequest < 5)
            return;
        state.lastRequest = tick;
        sendSession(player, state);
        long width = (long) view.maxX() - view.minX() + 1, height = (long) view.maxZ() - view.minZ() + 1;
        if (view.layer() == MapLayer.AUTO || width < 1 || height < 1 || width > 256 || height > 256
                || Math.abs((long) view.minX()) > MapTileKey.CHUNK_LIMIT
                || Math.abs((long) view.maxX()) > MapTileKey.CHUNK_LIMIT
                || Math.abs((long) view.minZ()) > MapTileKey.CHUNK_LIMIT
                || Math.abs((long) view.maxZ()) > MapTileKey.CHUNK_LIMIT)
            return;
        Identifier identifier = Identifier.tryParse(view.dimension());
        ServerLevel level = identifier == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, identifier));
        if (level == null
                || (view.layer().hasHeight() && (view.height() < level.getMinY() || view.height() > level.getMaxY())))
            return;
        if (!view.layer().hasHeight())
            view = new MapViewRequestPayload(view.dimension(), view.layer(), 0,
                    view.minX(), view.minZ(), view.maxX(), view.maxZ(), view.generation());
        if (!view.equals(state.view)) {
            boolean changedLayer = state.view == null || !view.dimension().equals(state.view.dimension())
                    || view.layer() != state.view.layer()
                    || view.height() != state.view.height() || view.generation() != state.view.generation();
            state.view = view;
            state.cursor = (int) (width * (height / 2) + width / 2);
            if (changedLayer)
                state.sent.clear();
        }
    }

    private static void tick(MinecraftServer instance) {
        if (instance != server || terrain == null)
            return;
        terrain.tick();
        int tick = instance.getTickCount();
        CLIENTS.keySet().removeIf(id -> instance.getPlayerList().getPlayer(id) == null);
        VISIBILITY.keySet().removeIf(id -> instance.getPlayerList().getPlayer(id) == null);
        var subscribers = new ArrayList<>(CLIENTS.entrySet());
        for (int index = 0; index < subscribers.size(); index++) {
            var entry = subscribers.get((index + tick) % subscribers.size());
            ServerPlayer player = instance.getPlayerList().getPlayer(entry.getKey());
            if (!eligible(player))
                continue;
            var state = entry.getValue();
            state.credit = Math.min(Math.max(MapServerSettings.bandwidth, 106496),
                    state.credit + MapServerSettings.bandwidth / 20.0);
            if (tick % 20 == 0) {
                sendSession(player, state);
                if (!state.anchors.equals(TeleportAnchorManager.get(server).getPointsForPlayer(player)))
                    sendPoints(player, state);
                if (state.view != null && tick - state.lastRequest <= 100)
                    sendLive(player, state);
            }
            if (MapServerSettings.enabled && MapServerSettings.shareTerrain && state.view != null
                    && tick - state.lastRequest <= 100) {
                stream(player, state);
            }
        }
    }

    /** 保存事件只安排读取 Minecraft 区块存储，不接收玩家客户端地形。 */
    public static void chunkSaved(ServerLevel level, net.minecraft.world.level.ChunkPos pos) {
        var active = capture;
        if (active != null && level.getServer() == server)
            active.dirty(level.dimension().identifier().toString(), pos.x(), pos.z());
    }

    /** 管理员命令触发异步上传，实际权限由命令入口核验。 */
    public static boolean uploadToApi() {
        return uploader != null && uploader.upload();
    }

    private static void stream(ServerPlayer player, Subscription state) {
        if (!ServerPlayNetworking.canSend(player, MapTilePayload.ID))
            return;
        var view = state.view;
        int width = view.maxX() - view.minX() + 1, height = view.maxZ() - view.minZ() + 1;
        for (int scanned = 0; scanned < 48; scanned++) {
            int cursor = Math.floorMod(state.cursor++, width * height);
            var key = new MapTileKey(view.dimension(), view.layer(), view.height(), view.minX() + cursor % width,
                    view.minZ() + cursor / width);
            MapTile tile = terrain.get(key);
            if (tile == null) {
                if (capture != null)
                    capture.request(key);
                continue;
            }
            if (state.sent.getOrDefault(key, Long.MIN_VALUE) == tile.revision())
                continue;
            int bytes = 2200 + tile.biomes().stream().mapToInt(value -> value.length() * 3 + 8).sum();
            if (state.credit < bytes)
                return;
            ServerPlayNetworking.send(player, new MapTilePayload(worldId, tile));
            state.credit -= bytes;
            state.bytes += bytes;
            state.tiles++;
            state.sent.put(key, tile.revision());
            while (state.sent.size() > 8192)
                state.sent.remove(state.sent.keySet().iterator().next());
        }
    }

    private static int flags(ServerPlayer player) {
        if (!MapServerSettings.enabled)
            return 0;
        int flags = MapSessionPayload.ENABLED;
        if (MapServerSettings.shareTerrain)
            flags |= MapSessionPayload.TERRAIN;
        if (MapServerSettings.sharePlayers)
            flags |= MapSessionPayload.RADAR;
        flags |= MapSessionPayload.ANCHORS;
        if (MapServerSettings.shareLoadState)
            flags |= MapSessionPayload.LOAD_STATE;
        if (canTeleport(player))
            flags |= MapSessionPayload.TELEPORT;
        return flags;
    }

    private static void sendSession(ServerPlayer player, Subscription state) {
        if (!ServerPlayNetworking.canSend(player, MapSessionPayload.ID))
            return;
        int flags = flags(player);
        if (state.flags == flags)
            return;
        state.flags = flags;
        var dimensions = server.levelKeys().stream()
                .sorted(java.util.Comparator.comparing(value -> value.identifier().toString()))
                .limit(128).map(key -> {
                    var level = server.getLevel(key);
                    var pool = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                            .getPoolByDimension(key.identifier().toString()).orElse(null);
                    return new MapSessionPayload.Dimension(key.identifier().toString(), level.getMinY(),
                            level.getMaxY(),
                            pool == null ? "" : pool.id(), pool == null ? "" : pool.displayName());
                }).toList();
        ServerPlayNetworking.send(player, new MapSessionPayload(worldId, flags, dimensions));
        sendPoints(player, state);
    }

    private static void sendPoints(ServerPlayer player, Subscription state) {
        if (!ServerPlayNetworking.canSend(player, MapWaypointsPayload.ID))
            return;
        // 只发送接收者自己的激活记录；不读取远处区块，不创建区块加载票。
        state.anchors = List.copyOf(TeleportAnchorManager.get(server).getPointsForPlayer(player));
        var anchors = new ArrayList<MapWaypoint>();
        if (MapServerSettings.enabled)
            for (var anchor : state.anchors) {
                if (anchors.size() >= 512)
                    break;
                String dimension = anchor.dimension().identifier().toString();
                var pos = anchor.pos();
                if (dimension.length() > 128 || Math.abs((long) pos.getX()) > MapTileKey.WORLD_LIMIT
                        || Math.abs((long) pos.getY()) > MapTileKey.WORLD_LIMIT
                        || Math.abs((long) pos.getZ()) > MapTileKey.WORLD_LIMIT)
                    continue;
                String identity = "map-anchor:" + player.getUUID() + ":" + dimension + ":" + pos.asLong();
                String name = anchor.name().isBlank() ? "传送锚点" : anchor.name();
                anchors.add(new MapWaypoint(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)),
                        player.getUUID(),
                        name.substring(0, Math.min(64, name.length())), MapWaypointGroup.UNGROUPED.id(), dimension,
                        pos.getX(), pos.getY(), pos.getZ(),
                        0xFF87CDA3, true, true, MapWaypoint.Kind.SERVER, 0));
            }
        ServerPlayNetworking.send(player, new MapWaypointsPayload(worldId, List.of(), List.copyOf(anchors)));
        DebugLogger.debug("MapServerManager", "同步本人地图锚点：%d", anchors.size());
    }

    private static void sendLive(ServerPlayer receiver, Subscription state) {
        if (!ServerPlayNetworking.canSend(receiver, MapLivePayload.ID))
            return;
        List<MapLivePayload.Player> players = new ArrayList<>();
        if (MapServerSettings.enabled && MapServerSettings.sharePlayers) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (players.size() >= 256)
                    break;
                if (!eligible(player) || !player.isAlive() || player.isSpectator() || player.isInvisible()
                        || InvisibilityManager.isInvisible(player.getUUID()) || !positionShared(player)
                        || !MapSocialManager.visibleTo(player.getUUID(), receiver.getUUID()))
                    continue;
                players.add(new MapLivePayload.Player(player.getUUID(), player.getName().getString(),
                        player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ(),
                        player.getYRot()));
            }
        }
        List<MapLivePayload.LoadedChunk> chunks = new ArrayList<>();
        var view = state.view;
        if (MapServerSettings.enabled && MapServerSettings.shareLoadState) {
            ServerLevel level = server
                    .getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(view.dimension())));
            if (level != null) {
                int centerX = (view.minX() + view.maxX()) / 2, centerZ = (view.minZ() + view.maxZ()) / 2;
                var access = (ChunkMapAccessor) level.getChunkSource().chunkMap;
                for (int z = Math.max(view.minZ(), centerZ - 15); z <= Math.min(view.maxZ(), centerZ + 16); z++) {
                    for (int x = Math.max(view.minX(), centerX - 15); x <= Math.min(view.maxX(), centerX + 16); x++) {
                        var holder = access.youzaiworldcore$getVisibleChunk((x & 0xFFFFFFFFL) | ((long) z << 32));
                        if (holder != null)
                            chunks.add(new MapLivePayload.LoadedChunk(x, z, holder.getTicketLevel()));
                    }
                }
            }
        }
        ServerPlayNetworking.send(receiver,
                new MapLivePayload(worldId, view.dimension(), List.copyOf(players), List.copyOf(chunks)));
    }

    /** 生物查询沿用地图认证及总开关，与玩家位置共享开关独立。 */
    public static boolean mobRadarEligible(ServerPlayer player) {
        return eligible(player) && MapServerSettings.enabled;
    }

    /** 共享管理沿用地图认证与服务端功能开关。 */
    public static boolean socialEligible(ServerPlayer player) {
        return eligible(player) && MapServerSettings.enabled && MapServerSettings.sharePlayers;
    }

    /** 读取服务端保存的位置共享总开关，客户端显示开关不影响此值。 */
    public static boolean positionShared(ServerPlayer player) {
        return VISIBILITY.computeIfAbsent(player.getUUID(),
                id -> new MapSqlSettings(MapServerSettings.database(), "player:" + id)
                        .getBoolean("share_position", true));
    }

    /** 网络操作的确认入口；拒绝、限速和成功均返回对应请求的结果。 */
    public static void receiveAction(ServerPlayer player, MapActionPayload request) {
        if (!eligible(player) || worldId == null)
            return;
        var state = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new Subscription());
        state.lastResult = "denied";
        boolean success = action(player, request);
        if (ServerPlayNetworking.canSend(player, MapActionResultPayload.ID))
            ServerPlayNetworking.send(player,
                    new MapActionResultPayload(worldId, request.point().id(), request.action(), success,
                            state.lastResult));
    }

    /** 处理经认证的位置共享与管理员传送；旧公共点操作一律拒绝。 */
    public static boolean action(ServerPlayer player, MapActionPayload request) {
        if (!eligible(player) || !MapServerSettings.enabled)
            return false;
        var subscription = CLIENTS.computeIfAbsent(player.getUUID(), ignored -> new Subscription());
        int tick = server.getTickCount();
        if (tick - subscription.lastAction < 4)
            return false;
        subscription.lastAction = tick;
        var input = request.point();
        if (request.action() == MapActionPayload.Action.HIDE_POSITION
                || request.action() == MapActionPayload.Action.SHOW_POSITION) {
            boolean value = request.action() == MapActionPayload.Action.SHOW_POSITION;
            MapServerSettings.database().settings("player:" + player.getUUID(),
                    Map.of("share_position", Boolean.toString(value)));
            VISIBILITY.put(player.getUUID(), value);
            MapSocialManager.refresh(player);
            DebugLogger.info("MapServerManager", "玩家位置共享：%s = %s", player.getUUID(), value);
            return feedback(player, true, value ? "position_shown" : "position_hidden");
        }
        if (request.action() == MapActionPayload.Action.TELEPORT) {
            if (!canTeleport(player))
                return feedback(player, false, "denied");
            ServerLevel target = server
                    .getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(input.dimension())));
            if (target == null || input.y() < target.getMinY() || input.y() > target.getMaxY()
                    || !target.getWorldBorder()
                            .isWithinBounds(new net.minecraft.core.BlockPos(input.x(), input.y(), input.z()))) {
                return feedback(player, false, "invalid_position");
            }
            if (!player.teleportTo(target, input.x() + 0.5, input.y(), input.z() + 0.5, Set.of(), player.getYRot(),
                    player.getXRot(), true))
                return feedback(player, false, "invalid_position");
            DebugLogger.info("MapServerManager", "管理员地图传送：%s → %s %d %d %d", player.getName().getString(),
                    input.dimension(), input.x(), input.y(), input.z());
            return feedback(player, true, "teleported");
        }
        return feedback(player, false, "denied");
    }

    private static boolean feedback(ServerPlayer player, boolean result, String key) {
        var state = CLIENTS.get(player.getUUID());
        if (state != null)
            state.lastResult = key;
        if (!player.hasDisconnected())
            player.sendSystemMessage(Component.translatable("map.youzaiworldcore." + key));
        return result;
    }

    public static boolean canTeleport(ServerPlayer player) {
        return MapServerSettings.allowTeleport && LuckPermsHelper.checkPermission(
                player.createCommandSourceStack(), LuckPermsHelper.PERMISSION_MAP_TELEPORT, Commands.LEVEL_ADMINS);
    }

    private static boolean eligible(ServerPlayer player) {
        return player != null && server != null && player.level().getServer() == server
                && !player.hasDisconnected() && !AuthPlayerHelper.shouldBlockActions(player);
    }

    /** @return 当前连接的地形同步统计 */
    public static Component performance(UUID player) {
        var state = CLIENTS.get(player);
        return Component.translatable("map.youzaiworldcore.performance", state == null ? 0 : state.tiles,
                state == null ? 0 : state.bytes / 1024);
    }

    /** 重载配置后使地图能力失效，不删除已探索地形。 */
    public static void reload() {
        MapServerSettings.load();
        VISIBILITY.clear();
        MapSocialManager.clear();
        if (capture != null)
            capture.configure();
        CLIENTS.values().forEach(state -> state.flags = -1);
    }

    private static final class Subscription {
        private MapViewRequestPayload view;
        private int cursor, flags = -1, lastRequest = -1000, lastAction = -1000;
        private List<TeleportAnchorData> anchors = List.of();
        private long bytes, tiles;
        private double credit = MapServerSettings.bandwidth;
        private String lastResult = "denied";
        private final Map<MapTileKey, Long> sent = new LinkedHashMap<>();
    }

}
