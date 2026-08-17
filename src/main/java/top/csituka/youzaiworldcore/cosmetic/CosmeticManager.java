package top.csituka.youzaiworldcore.cosmetic;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.config.CosmeticModuleSettings;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.JsonFileStore;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.network.CosmeticDataPayload;
import top.csituka.youzaiworldcore.network.CosmeticInfoPayload;
import top.csituka.youzaiworldcore.network.CosmeticReadyPayload;
import top.csituka.youzaiworldcore.network.CosmeticUploadPayload;
import top.csituka.youzaiworldcore.network.CosmeticUploadResultPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 自定义皮肤与披风服务端权威管理器。
 * <p>
 * 数据存放在 {@code yzwc/server/data/cosmetic_module/<UUID>/}，负责上传校验、快照对齐、
 * 请求冷却、状态广播以及账户删除联动。
 * </p>
 */
@SuppressWarnings("null")
public final class CosmeticManager {

    private static final String MODULE = "CosmeticManager";
    private static final String SKIN_WIDE_FILE = "skin.png";
    private static final String SKIN_SLIM_FILE = "skin_slim.png";
    private static final String CLOAK_FILE = "cloak.png";
    private static final String SERVER_INSTANCE_ID_KEY = "server_instance_id";
    private static final int MAX_REQUEST_TARGETS_PER_PLAYER = 128;
    private static final byte[] EMPTY = new byte[0];
    private static final String EMPTY_SNAPSHOT_HASH = CosmeticSnapshotHasher.hash(EMPTY, EMPTY, EMPTY);

    private static final Map<UUID, Long> LAST_UPLOAD_NANOS = new HashMap<>();
    private static final Map<UUID, LinkedHashMap<UUID, Long>> LAST_REQUEST_NANOS = new HashMap<>();
    private static final Set<UUID> ONLINE_COSMETIC_OWNERS = new HashSet<>();
    private static final JsonFileStore METADATA_STORE = new JsonFileStore(
            ModPaths.serverDataFile(GlobalSettings.COSMETIC_MODULE));

    private static UUID serverInstanceId;

    static {
        METADATA_STORE.setDefaultsWriter(CosmeticManager::writeMetadataDefaults);
    }

    private CosmeticManager() {
    }

    /** 初始化服务端数据目录与内存冷却表。 */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");
        ModPaths.ensureDir(ModPaths.serverData(GlobalSettings.COSMETIC_MODULE));
        METADATA_STORE.loadOrCreateDefaults();
        ConfigSection metadata = METADATA_STORE.section(GlobalSettings.COSMETIC_MODULE);
        if (!metadata.has(SERVER_INSTANCE_ID_KEY)) {
            serverInstanceId = UUID.randomUUID();
            metadata.set(SERVER_INSTANCE_ID_KEY, serverInstanceId.toString());
            METADATA_STORE.save();
        } else {
            String rawInstanceId = metadata.getString(SERVER_INSTANCE_ID_KEY, "");
            try {
                serverInstanceId = UUID.fromString(rawInstanceId);
            } catch (IllegalArgumentException e) {
                metadata.fail(SERVER_INSTANCE_ID_KEY, "必须是合法 UUID，实际为：" + rawInstanceId);
            }
        }
        LAST_UPLOAD_NANOS.clear();
        LAST_REQUEST_NANOS.clear();
        ONLINE_COSMETIC_OWNERS.clear();
        DebugLogger.exiting(MODULE, "initialize", "serverInstanceId=" + serverInstanceId);
    }

    /** 服务器停止时释放仅存于内存的冷却记录。 */
    public static void shutdown() {
        LAST_UPLOAD_NANOS.clear();
        LAST_REQUEST_NANOS.clear();
        ONLINE_COSMETIC_OWNERS.clear();
    }

    /** 配置重载后清空旧冷却并重新向在线玩家同步链路状态。 */
    public static void reload(MinecraftServer server) {
        LAST_UPLOAD_NANOS.clear();
        LAST_REQUEST_NANOS.clear();
        ONLINE_COSMETIC_OWNERS.clear();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, CosmeticReadyPayload.disabled());
        }
        if (!isServerActive(server)) {
            DebugLogger.info(MODULE, "外观模块配置已重载，模块当前关闭");
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAuthenticated(player)) {
                onAuthenticated(player);
            }
        }
        DebugLogger.info(MODULE, "外观模块配置已重载并同步至在线玩家");
    }

    /**
     * 玩家完成账户认证后下发 ready，并同步当前在线玩家的外观状态。
     */
    public static void onAuthenticated(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (!isServerActive(server)) {
            ServerPlayNetworking.send(player, CosmeticReadyPayload.disabled());
            return;
        }

        CosmeticSnapshot ownSnapshot = readSnapshot(player.getUUID());
        trackOnlineSnapshot(player.getUUID(), ownSnapshot);
        ServerPlayNetworking.send(player, new CosmeticReadyPayload(
                true,
                serverInstanceId,
                ownSnapshot.snapshotHash(),
                CosmeticModuleSettings.getRequestCooldownSeconds()));

        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (!isAuthenticated(online)) {
                continue;
            }
            CosmeticSnapshot snapshot = readSnapshot(online.getUUID());
            trackOnlineSnapshot(online.getUUID(), snapshot);
            CosmeticState state = snapshot.state();
            if (snapshot.hasAny()) {
                ServerPlayNetworking.send(player,
                        new CosmeticInfoPayload(
                                online.getUUID(), state.hasSkin(), state.hasCloak(), snapshot.snapshotHash()));
            }
        }

        if (ownSnapshot.hasAny()) {
            broadcastInfo(server, player.getUUID(), ownSnapshot, player.getUUID());
        }
        DebugLogger.info(MODULE, "已向玩家 %s 下发自定义外观就绪状态", player.getScoreboardName());
    }

    /** 玩家离线时通知其他客户端释放其动态纹理，但保留服务端文件。 */
    public static void onPlayerDisconnect(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        UUID playerUuid = player.getUUID();
        LAST_UPLOAD_NANOS.remove(playerUuid);
        clearRequestCooldowns(playerUuid);
        ONLINE_COSMETIC_OWNERS.remove(playerUuid);
        if (server != null && isServerActive(server)) {
            broadcastInfo(server, playerUuid, emptySnapshot(), playerUuid);
        }
    }

    /** 玩家主动登出或被要求重新认证时，关闭本人链路并通知其他客户端释放纹理。 */
    public static void onDeauthenticated(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerPlayNetworking.send(player, CosmeticReadyPayload.disabled());
        LAST_UPLOAD_NANOS.remove(player.getUUID());
        clearRequestCooldowns(player.getUUID());
        ONLINE_COSMETIC_OWNERS.remove(player.getUUID());
        if (isServerActive(server)) {
            broadcastInfo(server, player.getUUID(), emptySnapshot(), player.getUUID());
        }
    }

    /** 校验并应用客户端上传的完整外观快照。 */
    public static void applySnapshot(ServerPlayer player, CosmeticUploadPayload payload) {
        DebugLogger.entering(MODULE, "applySnapshot", "player=" + player.getScoreboardName());
        MinecraftServer server = player.level().getServer();
        if (server == null || !isServerActive(server)) {
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            DebugLogger.exiting(MODULE, "applySnapshot", "server inactive");
            return;
        }
        if (CosmeticModuleSettings.isRequireAuthenticated() && !isAuthenticated(player)) {
            DebugLogger.warn(MODULE, "拒绝未认证玩家 %s 的外观上传", player.getScoreboardName());
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            return;
        }
        if (!payload.offlineSession()) {
            DebugLogger.warn(MODULE, "拒绝玩家 %s 的外观上传：客户端未声明离线会话", player.getScoreboardName());
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            return;
        }
        if (payload.skinWide().length > 0 && payload.skinSlim().length > 0) {
            DebugLogger.warn(MODULE, "拒绝玩家 %s 的外观上传：skin.png 与 skin_slim.png 不得同时启用",
                    player.getScoreboardName());
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            return;
        }

        SlotData[] slots = {
                new SlotData(SKIN_WIDE_FILE, payload.skinWide(), false),
                new SlotData(SKIN_SLIM_FILE, payload.skinSlim(), false),
                new SlotData(CLOAK_FILE, payload.cloak(), true)
        };
        for (SlotData slot : slots) {
            if (slot.data().length == 0) {
                continue;
            }
            CosmeticPngValidator.Validation validation = slot.cloak()
                    ? CosmeticPngValidator.validateCloak(slot.data(), CosmeticModuleSettings.getMaxFileBytes())
                    : CosmeticPngValidator.validateSkin(slot.data(), CosmeticModuleSettings.getMaxFileBytes());
            if (!validation.valid()) {
                DebugLogger.warn(MODULE, "拒绝玩家 %s 的 %s：%s",
                        player.getScoreboardName(), slot.fileName(), validation.reason());
                sendUploadResult(player, payload.snapshotHash(), false, 0);
                return;
            }
        }

        String actualSnapshotHash = CosmeticSnapshotHasher.hash(
                payload.skinWide(), payload.skinSlim(), payload.cloak());
        if (!payload.snapshotHash().equals(actualSnapshotHash)) {
            DebugLogger.warn(MODULE, "拒绝玩家 %s 的外观上传：快照哈希不匹配", player.getScoreboardName());
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            return;
        }

        CosmeticSnapshot beforeSnapshot = readSnapshot(player.getUUID());
        if (beforeSnapshot.snapshotHash().equals(payload.snapshotHash())) {
            trackOnlineSnapshot(player.getUUID(), beforeSnapshot);
            sendUploadResult(player, payload.snapshotHash(), true, 0);
            DebugLogger.exiting(MODULE, "applySnapshot", "already aligned");
            return;
        }

        long now = System.nanoTime();
        long cooldownNanos = secondsToNanos(CosmeticModuleSettings.getUploadCooldownSeconds());
        Long lastUpload = LAST_UPLOAD_NANOS.get(player.getUUID());
        if (lastUpload != null && now - lastUpload < cooldownNanos) {
            DebugLogger.warn(MODULE, "玩家 %s 的外观上传触发冷却", player.getScoreboardName());
            long remainingNanos = cooldownNanos - (now - lastUpload);
            int retryAfter = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(1L, (remainingNanos + 999_999_999L) / 1_000_000_000L));
            sendUploadResult(player, payload.snapshotHash(), false, retryAfter);
            return;
        }

        Path playerDir = playerDataDir(player.getUUID());
        boolean changed = false;
        try {
            for (SlotData slot : slots) {
                changed |= alignSlot(playerDir, slot.fileName(), slot.data());
            }
            removeDirectoryIfEmpty(playerDir);
            LAST_UPLOAD_NANOS.put(player.getUUID(), now);
        } catch (IOException e) {
            DebugLogger.exception(MODULE, "applySnapshot", e);
            CosmeticSnapshot actualSnapshot = readSnapshot(player.getUUID());
            trackOnlineSnapshot(player.getUUID(), actualSnapshot);
            if (!actualSnapshot.snapshotHash().equals(beforeSnapshot.snapshotHash())) {
                clearTargetRequestCooldowns(player.getUUID());
                broadcastInfo(server, player.getUUID(), actualSnapshot, null);
            }
            sendUploadResult(player, payload.snapshotHash(), false,
                    Math.max(1, CosmeticModuleSettings.getUploadCooldownSeconds()));
            return;
        }

        CosmeticSnapshot storedSnapshot = readSnapshot(player.getUUID());
        trackOnlineSnapshot(player.getUUID(), storedSnapshot);
        if (!storedSnapshot.snapshotHash().equals(payload.snapshotHash())) {
            if (!storedSnapshot.snapshotHash().equals(beforeSnapshot.snapshotHash())) {
                clearTargetRequestCooldowns(player.getUUID());
                broadcastInfo(server, player.getUUID(), storedSnapshot, null);
            }
            DebugLogger.warn(MODULE, "玩家 %s 的外观快照写入后校验不一致", player.getScoreboardName());
            sendUploadResult(player, payload.snapshotHash(), false,
                    Math.max(1, CosmeticModuleSettings.getUploadCooldownSeconds()));
            return;
        }

        if (changed) {
            clearTargetRequestCooldowns(player.getUUID());
            broadcastInfo(server, player.getUUID(), storedSnapshot, null);
            DebugLogger.info(MODULE, "玩家 %s 的自定义外观快照已更新", player.getScoreboardName());
        }
        sendUploadResult(player, payload.snapshotHash(), true, 0);
        DebugLogger.exiting(MODULE, "applySnapshot", changed ? "changed" : "unchanged");
    }

    /** 按请求冷却读取目标玩家文件并回传。 */
    public static void handleRequest(ServerPlayer requester, UUID targetUuid) {
        MinecraftServer server = requester.level().getServer();
        if (server == null || !isServerActive(server)) {
            return;
        }
        if (CosmeticModuleSettings.isRequireAuthenticated() && !isAuthenticated(requester)) {
            return;
        }

        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        if (target == null || !isAuthenticated(target)) {
            return;
        }
        if (!ONLINE_COSMETIC_OWNERS.contains(targetUuid)) {
            return;
        }

        long now = System.nanoTime();
        long cooldownNanos = secondsToNanos(CosmeticModuleSettings.getRequestCooldownSeconds());
        pruneRequestCooldowns(now, cooldownNanos);
        LinkedHashMap<UUID, Long> requesterRequests = LAST_REQUEST_NANOS.computeIfAbsent(
                requester.getUUID(), ignored -> new LinkedHashMap<>(16, 0.75F, true));
        Long lastRequest = requesterRequests.get(targetUuid);
        if (lastRequest != null && now - lastRequest < cooldownNanos) {
            return;
        }

        CosmeticSnapshot snapshot = readSnapshot(targetUuid);
        if (!snapshot.hasAny()) {
            ONLINE_COSMETIC_OWNERS.remove(targetUuid);
            if (requesterRequests.isEmpty()) {
                LAST_REQUEST_NANOS.remove(requester.getUUID());
            }
            return;
        }
        requesterRequests.put(targetUuid, now);
        while (requesterRequests.size() > MAX_REQUEST_TARGETS_PER_PLAYER) {
            requesterRequests.remove(requesterRequests.keySet().iterator().next());
        }
        ServerPlayNetworking.send(requester,
                new CosmeticDataPayload(targetUuid, snapshot.snapshotHash(),
                        snapshot.skinWide(), snapshot.skinSlim(), snapshot.cloak()));
    }

    /** 删除指定账户的全部自定义外观数据并广播清除。 */
    public static void deletePlayerData(MinecraftServer server, String uuidText) {
        if (uuidText == null || uuidText.isBlank()) {
            DebugLogger.warn(MODULE, "账户缺少 UUID，无法删除自定义外观数据");
            return;
        }
        try {
            deletePlayerData(server, UUID.fromString(uuidText));
        } catch (IllegalArgumentException e) {
            DebugLogger.warn(MODULE, "账户 UUID 非法，拒绝删除自定义外观数据：%s", uuidText);
        }
    }

    /** 删除指定 UUID 的全部自定义外观数据并广播清除。 */
    public static void deletePlayerData(MinecraftServer server, UUID playerUuid) {
        Path moduleRoot = ModPaths.serverData(GlobalSettings.COSMETIC_MODULE)
                .toAbsolutePath().normalize();
        Path target = playerDataDir(playerUuid).toAbsolutePath().normalize();
        if (!target.startsWith(moduleRoot) || target.equals(moduleRoot)) {
            DebugLogger.error(MODULE, "拒绝删除未通过路径校验的外观目录：%s", target);
            return;
        }

        try {
            if (Files.exists(target)) {
                try (Stream<Path> paths = Files.walk(target)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
            }
            LAST_UPLOAD_NANOS.remove(playerUuid);
            clearRequestCooldowns(playerUuid);
            ONLINE_COSMETIC_OWNERS.remove(playerUuid);
            if (server != null) {
                broadcastInfo(server, playerUuid, emptySnapshot(), null);
            }
            DebugLogger.info(MODULE, "已删除玩家 %s 的自定义外观数据", playerUuid);
        } catch (IOException e) {
            DebugLogger.exception(MODULE, "deletePlayerData", e);
        }
    }

    private static boolean isServerActive(MinecraftServer server) {
        return CosmeticModuleSettings.isEnabled()
                && (server.isSingleplayer() || !server.usesAuthentication());
    }

    private static boolean isAuthenticated(ServerPlayer player) {
        return AuthPlayerHelper.isAuthenticated(player) || AuthPlayerHelper.canSkipAuth(player);
    }

    private static Path playerDataDir(UUID playerUuid) {
        return ModPaths.serverPlayerData(GlobalSettings.COSMETIC_MODULE, playerUuid);
    }

    private static boolean alignSlot(Path playerDir, String fileName, byte[] data) throws IOException {
        Path target = playerDir.resolve(fileName);
        if (data.length == 0) {
            return Files.deleteIfExists(target);
        }
        if (Files.isRegularFile(target)
                && Files.size(target) == data.length
                && Arrays.equals(Files.readAllBytes(target), data)) {
            return false;
        }

        Files.createDirectories(playerDir);
        Path temp = playerDir.resolve(fileName + ".tmp");
        try {
            Files.write(temp, data,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    private static void removeDirectoryIfEmpty(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> children = Files.list(directory)) {
            if (children.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private static CosmeticSnapshot readSnapshot(UUID playerUuid) {
        byte[] skinWide = readSlot(playerUuid, SKIN_WIDE_FILE, false);
        byte[] skinSlim = skinWide.length > 0 ? EMPTY : readSlot(playerUuid, SKIN_SLIM_FILE, false);
        byte[] cloak = readSlot(playerUuid, CLOAK_FILE, true);
        return new CosmeticSnapshot(
                skinWide,
                skinSlim,
                cloak,
                CosmeticSnapshotHasher.hash(skinWide, skinSlim, cloak));
    }

    private static byte[] readSlot(UUID playerUuid, String fileName, boolean cloak) {
        Path file = playerDataDir(playerUuid).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return EMPTY;
        }
        try {
            long size = Files.size(file);
            if (size <= 0 || size > CosmeticModuleSettings.getMaxFileBytes()) {
                DebugLogger.warn(MODULE, "跳过大小非法的服务端外观文件：%s", file);
                return EMPTY;
            }
            byte[] data = Files.readAllBytes(file);
            CosmeticPngValidator.Validation validation = cloak
                    ? CosmeticPngValidator.validateCloak(data, CosmeticModuleSettings.getMaxFileBytes())
                    : CosmeticPngValidator.validateSkin(data, CosmeticModuleSettings.getMaxFileBytes());
            if (!validation.valid()) {
                DebugLogger.warn(MODULE, "跳过内容非法的服务端外观文件 %s：%s", file, validation.reason());
                return EMPTY;
            }
            return data;
        } catch (IOException e) {
            DebugLogger.exception(MODULE, "readSlot", e);
            return EMPTY;
        }
    }

    private static void broadcastInfo(
            MinecraftServer server, UUID ownerUuid, CosmeticSnapshot snapshot, UUID excludedUuid) {
        CosmeticState state = snapshot.state();
        CosmeticInfoPayload payload = new CosmeticInfoPayload(
                ownerUuid, state.hasSkin(), state.hasCloak(), snapshot.snapshotHash());
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (excludedUuid != null && online.getUUID().equals(excludedUuid)) {
                continue;
            }
            if (isAuthenticated(online)) {
                ServerPlayNetworking.send(online, payload);
            }
        }
    }

    private static long secondsToNanos(int seconds) {
        return seconds <= 0 ? 0L : seconds * 1_000_000_000L;
    }

    private static void writeMetadataDefaults() {
        UUID generated = UUID.randomUUID();
        METADATA_STORE.section(GlobalSettings.COSMETIC_MODULE)
                .set(SERVER_INSTANCE_ID_KEY, generated.toString());
    }

    private static void sendUploadResult(
            ServerPlayer player, String snapshotHash, boolean accepted, int retryAfterSeconds) {
        ServerPlayNetworking.send(player, new CosmeticUploadResultPayload(
                snapshotHash, accepted, Math.max(0, retryAfterSeconds)));
    }

    private static CosmeticSnapshot emptySnapshot() {
        return new CosmeticSnapshot(EMPTY, EMPTY, EMPTY, EMPTY_SNAPSHOT_HASH);
    }

    private static void clearRequestCooldowns(UUID playerUuid) {
        LAST_REQUEST_NANOS.remove(playerUuid);
        clearTargetRequestCooldowns(playerUuid);
    }

    private static void trackOnlineSnapshot(UUID playerUuid, CosmeticSnapshot snapshot) {
        if (snapshot.hasAny()) {
            ONLINE_COSMETIC_OWNERS.add(playerUuid);
        } else {
            ONLINE_COSMETIC_OWNERS.remove(playerUuid);
        }
    }

    private static void clearTargetRequestCooldowns(UUID targetUuid) {
        LAST_REQUEST_NANOS.values().forEach(requests -> requests.remove(targetUuid));
        LAST_REQUEST_NANOS.values().removeIf(Map::isEmpty);
    }

    private static void pruneRequestCooldowns(long now, long cooldownNanos) {
        if (cooldownNanos <= 0L) {
            LAST_REQUEST_NANOS.clear();
            return;
        }
        LAST_REQUEST_NANOS.values()
                .forEach(requests -> requests.entrySet().removeIf(entry -> now - entry.getValue() >= cooldownNanos));
        LAST_REQUEST_NANOS.values().removeIf(Map::isEmpty);
    }

    private record SlotData(String fileName, byte[] data, boolean cloak) {
    }

    private record CosmeticState(boolean hasSkin, boolean hasCloak) {
    }

    private record CosmeticSnapshot(
            byte[] skinWide,
            byte[] skinSlim,
            byte[] cloak,
            String snapshotHash) {

        private boolean hasAny() {
            return skinWide.length > 0 || skinSlim.length > 0 || cloak.length > 0;
        }

        private CosmeticState state() {
            return new CosmeticState(skinWide.length > 0 || skinSlim.length > 0,
                    cloak.length > 0);
        }
    }
}
