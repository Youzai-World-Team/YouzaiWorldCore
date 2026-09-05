package top.csituka.youzaiworldcore.title;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.network.TitleStatePayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** 服务端权威的在线称号缓存、权限派生和客户端同步入口。 */
@SuppressWarnings("null")
public final class TitleManager {
    private static final String MODULE = "TitleManager";
    private static final int REFRESH_INTERVAL_TICKS = 20 * 60;
    private static final Map<String, TitleDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<UUID, PlayerState> PLAYER_STATES = new LinkedHashMap<>();
    /** 每位玩家最后一次发起的请求；旧请求完成后不得覆盖较新的状态。 */
    private static final Map<UUID, Long> PLAYER_REQUEST_VERSIONS = new LinkedHashMap<>();
    /** 正在等待 Api 响应的请求；广播时暂不打断这些玩家客户端的 loading 状态。 */
    private static final Map<UUID, Long> PENDING_REQUEST_VERSIONS = new LinkedHashMap<>();
    private static final AtomicBoolean PERIODIC_REFRESH_RUNNING = new AtomicBoolean(false);
    private static int lastRefreshTick = Integer.MIN_VALUE;
    private static long nextRequestVersion;
    private static long appliedCatalogVersion;
    private static long serverGeneration;
    private static MinecraftServer activeServer;

    private record PlayerState(String username, List<String> ownedTitleIds, String equippedTitleId) {
        private PlayerState {
            ownedTitleIds = ownedTitleIds == null ? List.of() : List.copyOf(ownedTitleIds);
            equippedTitleId = equippedTitleId == null ? "" : equippedTitleId;
        }
    }

    /** 一批 Api 请求的快照上下文。所有字段只在服务端线程读取。 */
    private record RequestContext(
            long generation,
            long version,
            Map<UUID, Long> versions,
            Map<UUID, ServerPlayer> players) {
    }

    private TitleManager() {
    }

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(TitleManager::activateServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(TitleManager::clearServerState);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refreshPlayer(handler.player, false));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // 旧服务器停止后可能仍有排队的 disconnect 事件，不能清理新服务器的缓存。
            if (activeServer != server) return;
            UUID uuid = handler.player.getUUID();
            // 同一 UUID 快速重连时，旧连接的事件可能晚于新连接到达；不能误删新玩家的状态。
            ServerPlayer current = server.getPlayerList().getPlayer(uuid);
            if (current != null && current != handler.player) return;
            PLAYER_STATES.remove(uuid);
            PLAYER_REQUEST_VERSIONS.remove(uuid);
            PENDING_REQUEST_VERSIONS.remove(uuid);
            broadcastState(server, "");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            activateServer(server);
            int tick = server.getTickCount();
            if (lastRefreshTick != Integer.MIN_VALUE && tick - lastRefreshTick < REFRESH_INTERVAL_TICKS) return;
            lastRefreshTick = tick;
            refreshAll(server);
        });
        DebugLogger.info(MODULE, "称号系统已初始化，在线同步周期=%d秒", REFRESH_INTERVAL_TICKS / 20);
    }

    public static Component getEquippedComponent(ServerPlayer player) {
        return getEquippedComponent(player.getUUID());
    }

    public static Component getEquippedComponent(UUID playerUuid) {
        PlayerState state = PLAYER_STATES.get(playerUuid);
        if (state == null || state.equippedTitleId().isBlank()) return Component.empty();
        TitleDefinition definition = DEFINITIONS.get(state.equippedTitleId());
        return definition == null ? Component.empty() : definition.asComponent();
    }

    public static void refreshPlayer(ServerPlayer player, boolean reportResult) {
        MinecraftServer server = player.level().getServer();
        if (server == null || player.hasDisconnected()) return;
        activateServer(server);
        RequestContext requestContext = beginRequest(server, player);
        TitleApiClient.SyncRequest request = new TitleApiClient.SyncRequest(
                player.getScoreboardName(), permissionTitleIds(player));
        CompletableFuture.supplyAsync(() -> TitleApiClient.sync(List.of(request)))
                .whenComplete((result, error) -> server.execute(() -> {
                    if (!isCurrent(requestContext, server, player)) return;
                    if (error != null) {
                        completeRequests(requestContext);
                        DebugLogger.exception(MODULE, "refreshPlayer", error);
                        sendState(player, reportResult ? "称号服务暂时不可用，正在显示缓存" : "");
                        return;
                    }
                    applyResult(server, result, reportResult ? resultMessage(result) : "", player, requestContext);
                }));
    }

    public static void requestEquip(ServerPlayer player, String titleId) {
        MinecraftServer server = player.level().getServer();
        if (server == null || player.hasDisconnected()) return;
        activateServer(server);
        String normalized = titleId == null ? "" : titleId.trim().toLowerCase(Locale.ROOT);
        PlayerState current = PLAYER_STATES.get(player.getUUID());
        if (!normalized.isBlank() && (current == null || !current.ownedTitleIds().contains(normalized))) {
            sendState(player, "你尚未拥有此称号");
            return;
        }
        RequestContext requestContext = beginRequest(server, player);
        CompletableFuture.supplyAsync(() -> TitleApiClient.equip(player.getScoreboardName(), normalized))
                .whenComplete((result, error) -> server.execute(() -> {
                    if (!isCurrent(requestContext, server, player)) return;
                    if (error != null) {
                        completeRequests(requestContext);
                        DebugLogger.exception(MODULE, "requestEquip", error);
                        sendState(player, "称号佩戴保存失败，请稍后重试");
                        return;
                    }
                    applyResult(server, result,
                            result != null && result.success() ? "称号佩戴状态已更新" : resultMessage(result),
                            player, requestContext);
                }));
    }

    private static void refreshAll(MinecraftServer server) {
        activateServer(server);
        if (!PERIODIC_REFRESH_RUNNING.compareAndSet(false, true)) return;
        // 不要让周期同步淘汰玩家正在等待的手动刷新/佩戴请求；该请求完成后
        // 会携带同样的完整称号目录和玩家快照。否则周期请求失败时客户端会
        // 永远收不到结束 loading 的响应。
        List<ServerPlayer> players = server.getPlayerList().getPlayers().stream()
                .filter(player -> !PENDING_REQUEST_VERSIONS.containsKey(player.getUUID()))
                .toList();
        if (players.isEmpty()) {
            PERIODIC_REFRESH_RUNNING.set(false);
            return;
        }
        RequestContext requestContext = beginRequests(server, players);
        List<TitleApiClient.SyncRequest> requests = players.stream()
                .map(player -> new TitleApiClient.SyncRequest(player.getScoreboardName(), permissionTitleIds(player)))
                .toList();
        CompletableFuture.supplyAsync(() -> TitleApiClient.syncBatched(requests))
                .whenComplete((result, error) -> server.execute(() -> {
                    // 旧服务器的回调不能结束新服务器正在进行的周期请求。
                    if (!isActive(requestContext, server)) return;
                    PERIODIC_REFRESH_RUNNING.set(false);
                    completeRequests(requestContext);
                    if (error != null) {
                        DebugLogger.exception(MODULE, "refreshAll", error);
                        return;
                    }
                    applyResult(server, result, "", null, requestContext);
                }));
    }

    private static void applyResult(
            MinecraftServer server,
            TitleApiClient.Result result,
            String message,
            ServerPlayer notificationTarget,
            RequestContext requestContext) {
        if (requestContext != null && !isActive(requestContext, server)) return;
        completeRequests(requestContext);
        if (result == null || !result.success()) {
            String resultMessage = resultMessage(result);
            if (!resultMessage.isBlank()) {
                DebugLogger.warn(MODULE, "称号 Api 请求失败：%s", resultMessage);
            }
            String notification = message == null || message.isBlank() ? "" : message;
            if (notificationTarget != null) {
                // 手动刷新或佩戴失败只提示发起请求的玩家，避免把单人错误广播给全服。
                sendState(notificationTarget, notification);
            } else if (!notification.isBlank()) {
                // 周期同步没有指定请求者；只有明确的维护消息才广播。
                broadcastState(server, notification);
            }
            return;
        }
        boolean applyCatalog = requestContext == null || requestContext.version() >= appliedCatalogVersion;
        if (applyCatalog && result.definitions() != null) {
            DEFINITIONS.clear();
            DEFINITIONS.putAll(result.definitions());
            if (requestContext != null) appliedCatalogVersion = requestContext.version();
        }
        Set<UUID> updatedPlayers = new HashSet<>();
        Map<String, TitleApiClient.PlayerSnapshot> snapshots = result.players() == null
                ? Map.of() : result.players();
        for (TitleApiClient.PlayerSnapshot snapshot : snapshots.values()) {
            if (snapshot == null || snapshot.username() == null || snapshot.username().isBlank()) continue;
            UUID expectedUuid = requestContext == null
                    ? null
                    : findRequestedPlayer(requestContext, snapshot.username(), snapshot.uuid());
            if (requestContext != null && expectedUuid == null) continue;
            ServerPlayer online = expectedUuid == null
                    ? findOnlinePlayer(server, snapshot.username())
                    : requestContext.players().get(expectedUuid);
            if (requestContext != null && !isCurrent(requestContext, server, expectedUuid, online)) continue;
            // 在线玩家对象的 UUID 才是当前连接的权威键；Api 中的 UUID 可能尚未完成更新。
            UUID uuid = online != null ? online.getUUID() : snapshot.uuid();
            if (uuid == null) continue;
            PLAYER_STATES.put(uuid, new PlayerState(
                    snapshot.username(), snapshot.ownedTitleIds(), snapshot.equippedTitleId()));
            if (expectedUuid != null) updatedPlayers.add(expectedUuid);
            if (online != null) refreshTabDisplay(server, online);
        }
        if (requestContext != null) {
            // 成功响应中缺少某个仍在线玩家时，清除该玩家的旧称号状态，避免继续显示已失效授权。
            for (Map.Entry<UUID, ServerPlayer> entry : requestContext.players().entrySet()) {
                UUID uuid = entry.getKey();
                ServerPlayer player = entry.getValue();
                if (!isCurrent(requestContext, server, uuid, player) || updatedPlayers.contains(uuid)) continue;
                PLAYER_STATES.remove(uuid);
                refreshTabDisplay(server, player);
            }
        }
        broadcastState(server, message == null ? "" : message);
        DebugLogger.info(MODULE, "已同步 %d 个称号定义、%d 名在线玩家", DEFINITIONS.size(), snapshots.size());
    }

    private static String resultMessage(TitleApiClient.Result result) {
        return result == null || result.message() == null ? "" : result.message();
    }

    private static RequestContext beginRequest(MinecraftServer server, ServerPlayer player) {
        return beginRequests(server, List.of(player));
    }

    private static RequestContext beginRequests(MinecraftServer server, List<ServerPlayer> players) {
        activateServer(server);
        Map<UUID, Long> versions = new LinkedHashMap<>();
        Map<UUID, ServerPlayer> contextPlayers = new LinkedHashMap<>();
        long version = ++nextRequestVersion;
        for (ServerPlayer player : players) {
            UUID uuid = player.getUUID();
            PLAYER_REQUEST_VERSIONS.put(uuid, version);
            PENDING_REQUEST_VERSIONS.put(uuid, version);
            versions.put(uuid, version);
            contextPlayers.put(uuid, player);
        }
        return new RequestContext(serverGeneration, version, Map.copyOf(versions), Map.copyOf(contextPlayers));
    }

    private static boolean isActive(RequestContext context, MinecraftServer server) {
        return context != null && context.generation() == serverGeneration && activeServer == server;
    }

    private static boolean isCurrent(RequestContext context, MinecraftServer server, ServerPlayer player) {
        return player != null && isCurrent(context, server, player.getUUID(), player);
    }

    private static boolean isCurrent(
            RequestContext context, MinecraftServer server, UUID uuid, ServerPlayer expectedPlayer) {
        if (context == null || uuid == null || expectedPlayer == null || !isActive(context, server)) return false;
        Long expectedVersion = context.versions().get(uuid);
        return expectedVersion != null
                && expectedPlayer == context.players().get(uuid)
                && !expectedPlayer.hasDisconnected()
                && server.getPlayerList().getPlayer(uuid) == expectedPlayer
                && expectedVersion.equals(PLAYER_REQUEST_VERSIONS.get(uuid));
    }

    private static UUID findRequestedPlayer(
            RequestContext context, String username, UUID snapshotUuid) {
        if (snapshotUuid != null && context.players().containsKey(snapshotUuid)) return snapshotUuid;
        for (Map.Entry<UUID, ServerPlayer> entry : context.players().entrySet()) {
            if (entry.getValue().getScoreboardName().equalsIgnoreCase(username)) return entry.getKey();
        }
        return null;
    }

    private static ServerPlayer findOnlinePlayer(MinecraftServer server, String username) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getScoreboardName().equalsIgnoreCase(username)) return player;
        }
        return null;
    }

    private static void activateServer(MinecraftServer server) {
        if (server == null || activeServer == server) return;
        activeServer = server;
        serverGeneration++;
        clearState();
    }

    private static void clearServerState(MinecraftServer server) {
        if (activeServer != server) return;
        activeServer = null;
        serverGeneration++;
        clearState();
    }

    private static void clearState() {
        DEFINITIONS.clear();
        PLAYER_STATES.clear();
        PLAYER_REQUEST_VERSIONS.clear();
        PENDING_REQUEST_VERSIONS.clear();
        PERIODIC_REFRESH_RUNNING.set(false);
        lastRefreshTick = Integer.MIN_VALUE;
        appliedCatalogVersion = 0;
    }

    private static void refreshTabDisplay(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    private static void broadcastState(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 其他玩家的同步包不能让正在等待自己请求结果的客户端提前结束 loading。
            if (PENDING_REQUEST_VERSIONS.containsKey(player.getUUID())) continue;
            sendState(player, message);
        }
    }

    private static void completeRequests(RequestContext requestContext) {
        if (requestContext == null) return;
        for (Map.Entry<UUID, Long> entry : requestContext.versions().entrySet()) {
            if (entry.getValue().equals(PENDING_REQUEST_VERSIONS.get(entry.getKey()))) {
                PENDING_REQUEST_VERSIONS.remove(entry.getKey());
            }
        }
    }

    private static void sendState(ServerPlayer player, String message) {
        PlayerState state = PLAYER_STATES.get(player.getUUID());
        List<TitleDefinition> definitions = DEFINITIONS.values().stream()
                .sorted(Comparator.comparingInt(TitleDefinition::sortOrder).thenComparing(TitleDefinition::id))
                .toList();
        List<TitleStatePayload.EquippedPlayer> equipped = new ArrayList<>();
        for (Map.Entry<UUID, PlayerState> entry : PLAYER_STATES.entrySet()) {
            if (!entry.getValue().equippedTitleId().isBlank()) {
                equipped.add(new TitleStatePayload.EquippedPlayer(entry.getKey(), entry.getValue().equippedTitleId()));
            }
        }
        ServerPlayNetworking.send(player, new TitleStatePayload(
                definitions,
                state == null ? List.of() : state.ownedTitleIds(),
                state == null ? "" : state.equippedTitleId(),
                equipped,
                message));
    }

    private static List<String> permissionTitleIds(ServerPlayer player) {
        if (!LuckPermsHelper.isLuckPermsLoaded()) {
            return Commands.LEVEL_ADMINS.check(player.permissions())
                    ? List.of("admin_junior", "admin_middle", "admin_senior")
                    : List.of();
        }
        UUID uuid = player.getUUID();
        if (LuckPermsHelper.checkLuckPermsOnly(uuid, LuckPermsHelper.PERMISSION_ADMIN_SENIOR)) {
            return List.of("admin_senior");
        }
        if (LuckPermsHelper.checkLuckPermsOnly(uuid, LuckPermsHelper.PERMISSION_ADMIN_MIDDLE)) {
            return List.of("admin_middle");
        }
        if (LuckPermsHelper.checkLuckPermsOnly(uuid, LuckPermsHelper.PERMISSION_ADMIN_JUNIOR)) {
            return List.of("admin_junior");
        }
        return List.of();
    }
}
