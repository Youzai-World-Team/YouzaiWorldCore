package top.csituka.youzaiworldcore.title;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final AtomicBoolean PERIODIC_REFRESH_RUNNING = new AtomicBoolean(false);
    private static int lastRefreshTick = Integer.MIN_VALUE;

    private record PlayerState(String username, List<String> ownedTitleIds, String equippedTitleId) {
        private PlayerState {
            ownedTitleIds = ownedTitleIds == null ? List.of() : List.copyOf(ownedTitleIds);
            equippedTitleId = equippedTitleId == null ? "" : equippedTitleId;
        }
    }

    private TitleManager() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> refreshPlayer(handler.player, false));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PLAYER_STATES.remove(handler.player.getUUID());
            broadcastState(server, "");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
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
        TitleApiClient.SyncRequest request = new TitleApiClient.SyncRequest(
                player.getScoreboardName(), permissionTitleIds(player));
        CompletableFuture.supplyAsync(() -> TitleApiClient.sync(List.of(request)))
                .whenComplete((result, error) -> server.execute(() -> {
                    if (player.hasDisconnected()) return;
                    if (error != null) {
                        DebugLogger.exception(MODULE, "refreshPlayer", error);
                        sendState(player, reportResult ? "称号服务暂时不可用，正在显示缓存" : "");
                        return;
                    }
                    applyResult(server, result, reportResult ? result.message() : "");
                }));
    }

    public static void requestEquip(ServerPlayer player, String titleId) {
        MinecraftServer server = player.level().getServer();
        if (server == null || player.hasDisconnected()) return;
        String normalized = titleId == null ? "" : titleId.trim().toLowerCase(Locale.ROOT);
        PlayerState current = PLAYER_STATES.get(player.getUUID());
        if (!normalized.isBlank() && (current == null || !current.ownedTitleIds().contains(normalized))) {
            sendState(player, "你尚未拥有此称号");
            return;
        }
        CompletableFuture.supplyAsync(() -> TitleApiClient.equip(player.getScoreboardName(), normalized))
                .whenComplete((result, error) -> server.execute(() -> {
                    if (player.hasDisconnected()) return;
                    if (error != null) {
                        DebugLogger.exception(MODULE, "requestEquip", error);
                        sendState(player, "称号佩戴保存失败，请稍后重试");
                        return;
                    }
                    applyResult(server, result, result.success() ? "称号佩戴状态已更新" : result.message());
                }));
    }

    private static void refreshAll(MinecraftServer server) {
        if (!PERIODIC_REFRESH_RUNNING.compareAndSet(false, true)) return;
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        if (players.isEmpty()) {
            PERIODIC_REFRESH_RUNNING.set(false);
            return;
        }
        List<TitleApiClient.SyncRequest> requests = players.stream()
                .map(player -> new TitleApiClient.SyncRequest(player.getScoreboardName(), permissionTitleIds(player)))
                .toList();
        CompletableFuture.supplyAsync(() -> TitleApiClient.sync(requests))
                .whenComplete((result, error) -> server.execute(() -> {
                    PERIODIC_REFRESH_RUNNING.set(false);
                    if (error != null) {
                        DebugLogger.exception(MODULE, "refreshAll", error);
                        return;
                    }
                    applyResult(server, result, "");
                }));
    }

    private static void applyResult(MinecraftServer server, TitleApiClient.Result result, String message) {
        if (result == null || !result.success()) {
            if (result != null && !result.message().isBlank()) {
                DebugLogger.warn(MODULE, "称号 Api 请求失败：%s", result.message());
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendState(player, message == null || message.isBlank() ? "" : message);
            }
            return;
        }
        DEFINITIONS.clear();
        DEFINITIONS.putAll(result.definitions());
        for (TitleApiClient.PlayerSnapshot snapshot : result.players().values()) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(snapshot.username());
            UUID uuid = snapshot.uuid() != null ? snapshot.uuid() : online == null ? null : online.getUUID();
            if (uuid == null) continue;
            PLAYER_STATES.put(uuid, new PlayerState(
                    snapshot.username(), snapshot.ownedTitleIds(), snapshot.equippedTitleId()));
            if (online != null) refreshTabDisplay(server, online);
        }
        broadcastState(server, message == null ? "" : message);
        DebugLogger.info(MODULE, "已同步 %d 个称号定义、%d 名在线玩家", DEFINITIONS.size(), result.players().size());
    }

    private static void refreshTabDisplay(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
    }

    private static void broadcastState(MinecraftServer server, String message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sendState(player, message);
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
