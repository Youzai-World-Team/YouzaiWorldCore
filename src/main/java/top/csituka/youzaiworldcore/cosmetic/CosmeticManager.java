package top.csituka.youzaiworldcore.cosmetic;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.account.OnlineUuidLoginManager;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.api.ApiServiceClient;
import top.csituka.youzaiworldcore.config.CosmeticModuleSettings;
import top.csituka.youzaiworldcore.network.CosmeticDataPayload;
import top.csituka.youzaiworldcore.network.CosmeticInfoPayload;
import top.csituka.youzaiworldcore.network.CosmeticReadyPayload;
import top.csituka.youzaiworldcore.network.CosmeticUploadPayload;
import top.csituka.youzaiworldcore.network.CosmeticUploadResultPayload;
import top.csituka.youzaiworldcore.network.MojangAuthChallengePayload;
import top.csituka.youzaiworldcore.network.MojangProfileRequestPayload;
import top.csituka.youzaiworldcore.network.MojangSkinPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 自定义皮肤与披风服务端权威管理器。
 * <p>
 * 文件由 Api 服务端持久化，本类负责上传校验、快照同步、请求冷却和状态广播。
 * </p>
 */
@SuppressWarnings("null")
public final class CosmeticManager {

    private static final String MODULE = "CosmeticManager";
    private static final String SKIN_WIDE_FILE = "skin.png";
    private static final String SKIN_SLIM_FILE = "skin_slim.png";
    private static final String CLOAK_FILE = "cloak.png";
    private static final int MAX_REQUEST_TARGETS_PER_PLAYER = 128;
    private static final int MOJANG_VERIFICATION_TIMEOUT_SECONDS = 10;
    private static final byte[] EMPTY = new byte[0];
    private static final String EMPTY_SNAPSHOT_HASH = CosmeticSnapshotHasher.hash(EMPTY, EMPTY, EMPTY);

    private static final Map<UUID, Long> LAST_UPLOAD_NANOS = new HashMap<>();
    private static final Map<UUID, LinkedHashMap<UUID, Long>> LAST_REQUEST_NANOS = new HashMap<>();
    private static final Map<UUID, CosmeticSnapshot> ONLINE_COSMETICS = new HashMap<>();
    private static final Map<UUID, String> LOADING_SNAPSHOTS = new HashMap<>();
    private static final Map<UUID, CompletableFuture<Boolean>> UPLOADING_SNAPSHOTS = new HashMap<>();
    private static final Map<UUID, MojangChallenge> PENDING_MOJANG_CHALLENGES = new HashMap<>();
    private static final Map<UUID, String> VERIFYING_MOJANG_PROFILES = new HashMap<>();
    private static final Map<UUID, MojangProfile> VERIFIED_MOJANG_PROFILES = new HashMap<>();
    private static final Map<UUID, String> ACTIVE_APPEARANCE_SESSIONS = new HashMap<>();

    private CosmeticManager() {
    }

    /** 初始化会话状态。 */
    public static void initialize() {
        shutdown();
    }

    /** 服务器停止时清理会话快照、验证状态和冷却。 */
    public static void shutdown() {
        LAST_UPLOAD_NANOS.clear();
        LAST_REQUEST_NANOS.clear();
        ONLINE_COSMETICS.clear();
        LOADING_SNAPSHOTS.clear();
        UPLOADING_SNAPSHOTS.clear();
        PENDING_MOJANG_CHALLENGES.clear();
        VERIFYING_MOJANG_PROFILES.clear();
        VERIFIED_MOJANG_PROFILES.clear();
        ACTIVE_APPEARANCE_SESSIONS.clear();
    }

    /** 配置重载后清空旧冷却并重新向在线玩家同步链路状态。 */
    public static void reload(MinecraftServer server) {
        LAST_UPLOAD_NANOS.clear();
        LAST_REQUEST_NANOS.clear();
        ONLINE_COSMETICS.clear();
        LOADING_SNAPSHOTS.clear();
        PENDING_MOJANG_CHALLENGES.clear();
        VERIFYING_MOJANG_PROFILES.clear();
        VERIFIED_MOJANG_PROFILES.clear();
        ACTIVE_APPEARANCE_SESSIONS.clear();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, CosmeticReadyPayload.disabled());
        }
        if (!isServerActive(server)) {
            DebugLogger.SERVER.debug(MODULE, "外观模块配置已重载，模块当前关闭");
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isAuthenticated(player)) {
                onAuthenticated(player);
            }
        }
        DebugLogger.SERVER.debug(MODULE, "外观模块配置已重载并同步至在线玩家");
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

        ACTIVE_APPEARANCE_SESSIONS.remove(player.getUUID());
        LOADING_SNAPSHOTS.remove(player.getUUID());
        VERIFIED_MOJANG_PROFILES.remove(player.getUUID());
        PENDING_MOJANG_CHALLENGES.remove(player.getUUID());
        VERIFYING_MOJANG_PROFILES.remove(player.getUUID());
        ONLINE_COSMETICS.remove(player.getUUID());
        ServerPlayNetworking.send(player, CosmeticReadyPayload.disabled());
        if (OnlineUuidLoginManager.isTrustedOnlineIdentity(server, player.getUUID())) {
            // 登录阶段已经证明正版身份，直接复用该连接的完整档案，避免重复挑战失败后回落离线外观。
            GameProfile profile = player.getGameProfile();
            Property textures = findTexturesProperty(profile);
            ACTIVE_APPEARANCE_SESSIONS.put(player.getUUID(), UUID.randomUUID().toString());
            handleMojangVerificationSucceeded(player, new MojangProfile(
                    profile.id(), profile.name(),
                    textures == null ? "" : textures.value(),
                    textures == null ? "" : textures.signature()));
            DebugLogger.SERVER.debug(MODULE, "已复用玩家 {} 登录阶段核验的正版外观档案", player.getScoreboardName());
            return;
        }
        requestMojangVerification(player);
        DebugLogger.SERVER.debug(MODULE, "已向玩家 {} 发起 Mojang 正版外观核验", player.getScoreboardName());
    }

    /** 玩家离线时通知其他客户端释放其动态纹理，但保留服务端文件。 */
    public static void onPlayerDisconnect(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        UUID playerUuid = player.getUUID();
        LAST_UPLOAD_NANOS.remove(playerUuid);
        clearRequestCooldowns(playerUuid);
        ONLINE_COSMETICS.remove(playerUuid);
        PENDING_MOJANG_CHALLENGES.remove(playerUuid);
        VERIFYING_MOJANG_PROFILES.remove(playerUuid);
        VERIFIED_MOJANG_PROFILES.remove(playerUuid);
        ACTIVE_APPEARANCE_SESSIONS.remove(playerUuid);
        LOADING_SNAPSHOTS.remove(playerUuid);
        if (server != null && isServerActive(server)) {
            broadcastInfo(server, playerUuid, emptySnapshot(), playerUuid);
            broadcastMojang(server, MojangSkinPayload.disabled(playerUuid));
        }
    }

    /** 玩家主动登出或被要求重新认证时，关闭本人链路并通知其他客户端释放纹理。 */
    public static void onDeauthenticated(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerPlayNetworking.send(player, CosmeticReadyPayload.disabled());
        LAST_UPLOAD_NANOS.remove(player.getUUID());
        clearRequestCooldowns(player.getUUID());
        ONLINE_COSMETICS.remove(player.getUUID());
        PENDING_MOJANG_CHALLENGES.remove(player.getUUID());
        VERIFYING_MOJANG_PROFILES.remove(player.getUUID());
        VERIFIED_MOJANG_PROFILES.remove(player.getUUID());
        ACTIVE_APPEARANCE_SESSIONS.remove(player.getUUID());
        LOADING_SNAPSHOTS.remove(player.getUUID());
        if (isServerActive(server)) {
            broadcastInfo(server, player.getUUID(), emptySnapshot(), player.getUUID());
            broadcastMojang(server, MojangSkinPayload.disabled(player.getUUID()));
        }
    }

    /** 处理客户端完成 Mojang joinServer 后提交的档案标识。 */
    public static void handleMojangProfileRequest(ServerPlayer player, MojangProfileRequestPayload payload) {
        MinecraftServer server = player.level().getServer();
        if (server == null || !isServerActive(server) || !isAuthenticated(player)) {
            return;
        }
        MojangChallenge pending = PENDING_MOJANG_CHALLENGES.get(player.getUUID());
        if (pending == null
                || pending.expiresAtNanos() < System.nanoTime()
                || !pending.challenge().equals(payload.challenge())) {
            DebugLogger.SERVER.debug(MODULE, "忽略玩家 {} 的过期 Mojang 外观核验响应", player.getScoreboardName());
            return;
        }
        PENDING_MOJANG_CHALLENGES.remove(player.getUUID(), pending);
        if (payload.profileId().equals(new UUID(0L, 0L)) || payload.profileName().isBlank()) {
            handleMojangVerificationFailed(player, "客户端档案标识为空");
            return;
        }
        if (!payload.profileName().equalsIgnoreCase(player.getScoreboardName())) {
            handleMojangVerificationFailed(player, "Mojang 档案名与当前玩家名不一致");
            return;
        }

        UUID playerUuid = player.getUUID();
        String challenge = pending.challenge();
        VERIFYING_MOJANG_PROFILES.put(playerUuid, challenge);
        CompletableFuture
                .supplyAsync(() -> verifyMojangProfile(server, challenge, payload))
                .orTimeout(MOJANG_VERIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((profile, error) -> server.execute(() -> {
                    if (!VERIFYING_MOJANG_PROFILES.remove(playerUuid, challenge)) {
                        DebugLogger.SERVER.debug(MODULE, "忽略玩家 {} 的旧 Mojang 外观核验结果", player.getScoreboardName());
                        return;
                    }
                    if (error != null || profile == null) {
                        handleMojangVerificationFailed(player,
                                error == null ? "Mojang 未返回匹配档案" : error.getClass().getSimpleName());
                    } else {
                        handleMojangVerificationSucceeded(player, profile);
                    }
                }));
    }

    /** 校验并应用客户端上传的完整外观快照。 */
    public static void applySnapshot(ServerPlayer player, CosmeticUploadPayload payload) {
        DebugLogger.SERVER.entering(MODULE, "applySnapshot", "player=" + player.getScoreboardName());
        MinecraftServer server = player.level().getServer();
        if (server == null || !isServerActive(server)) {
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            DebugLogger.SERVER.exiting(MODULE, "applySnapshot", "server inactive");
            return;
        }
        UUID playerUuid = player.getUUID();
        String session = ACTIVE_APPEARANCE_SESSIONS.get(playerUuid);
        if (!isCurrentCustomSession(player, session)) {
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            return;
        }
        if (!payload.offlineSession()) {
            DebugLogger.SERVER.warn(MODULE, "拒绝玩家 {} 的外观上传：客户端未声明离线会话", player.getScoreboardName());
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            return;
        }
        if (payload.skinWide().length > 0 && payload.skinSlim().length > 0) {
            DebugLogger.SERVER.warn(MODULE, "拒绝玩家 {} 的外观上传：skin.png 与 skin_slim.png 不得同时启用", player.getScoreboardName());
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
                DebugLogger.SERVER.warn(MODULE, "拒绝玩家 {} 的 {}：{}", player.getScoreboardName(), slot.fileName(), validation.reason());
                sendUploadResult(player, payload.snapshotHash(), false, 0);
                return;
            }
        }

        String actualSnapshotHash = CosmeticSnapshotHasher.hash(
                payload.skinWide(), payload.skinSlim(), payload.cloak());
        if (!payload.snapshotHash().equals(actualSnapshotHash)) {
            DebugLogger.SERVER.warn(MODULE, "拒绝玩家 {} 的外观上传：快照哈希不匹配", player.getScoreboardName());
            sendUploadResult(player, payload.snapshotHash(), false, 0);
            return;
        }

        CosmeticSnapshot snapshot = new CosmeticSnapshot(
                payload.skinWide(), payload.skinSlim(), payload.cloak(), actualSnapshotHash);
        CosmeticSnapshot current = ONLINE_COSMETICS.get(playerUuid);
        if (UPLOADING_SNAPSHOTS.containsKey(playerUuid) || current == null
                || LOADING_SNAPSHOTS.containsKey(playerUuid)) {
            sendUploadResult(player, payload.snapshotHash(), false, 1);
            return;
        }
        if (current.snapshotHash().equals(actualSnapshotHash)) {
            sendUploadResult(player, actualSnapshotHash, true, 0);
            return;
        }

        long now = System.nanoTime();
        long cooldownNanos = secondsToNanos(CosmeticModuleSettings.getUploadCooldownSeconds());
        Long lastUpload = LAST_UPLOAD_NANOS.get(playerUuid);
        if (lastUpload != null && now - lastUpload < cooldownNanos) {
            long remainingNanos = cooldownNanos - (now - lastUpload);
            int retryAfter = (int) Math.max(1L, (remainingNanos + 999_999_999L) / 1_000_000_000L);
            sendUploadResult(player, actualSnapshotHash, false, retryAfter);
            return;
        }

        CompletableFuture<Boolean> upload = CompletableFuture.supplyAsync(() ->
                ApiServiceClient.uploadCosmeticSnapshot(playerUuid, snapshot.skinWide(), snapshot.skinSlim(),
                        snapshot.cloak()));
        UPLOADING_SNAPSHOTS.put(playerUuid, upload);
        upload.whenComplete((accepted, error) -> server.execute(() -> {
            UPLOADING_SNAPSHOTS.remove(playerUuid, upload);
            if (error != null) {
                DebugLogger.SERVER.exception(MODULE, "保存装扮", error);
            }
            ServerPlayer connected = server.getPlayerList().getPlayer(playerUuid);
            if (connected == null || !isCurrentCustomSession(connected, session)) {
                return;
            }
            if (!Boolean.TRUE.equals(accepted) || error != null) {
                sendUploadResult(connected, actualSnapshotHash, false, 0);
                return;
            }
            LAST_UPLOAD_NANOS.put(playerUuid, System.nanoTime());
            publishSnapshot(connected, snapshot);
            sendUploadResult(connected, actualSnapshotHash, true, 0);
        }));
    }

    /** 本人请求可重试 Api 读取，其他玩家按冷却从当前会话的快照获取纹理。 */
    public static void handleRequest(ServerPlayer requester, UUID targetUuid) {
        MinecraftServer server = requester.level().getServer();
        if (server == null || !isServerActive(server)) {
            return;
        }
        UUID requesterUuid = requester.getUUID();
        if (requesterUuid.equals(targetUuid)) {
            String session = ACTIVE_APPEARANCE_SESSIONS.get(requesterUuid);
            if (isCurrentCustomSession(requester, session)) {
                CosmeticSnapshot snapshot = ONLINE_COSMETICS.get(requesterUuid);
                if (snapshot == null) {
                    loadCustomAppearance(requester, session);
                } else {
                    sendSnapshot(requester, requesterUuid, snapshot);
                }
            }
            return;
        }
        if (CosmeticModuleSettings.isRequireAuthenticated() && !isAuthenticated(requester)) {
            return;
        }
        ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
        CosmeticSnapshot snapshot = ONLINE_COSMETICS.get(targetUuid);
        if (target == null || !isAuthenticated(target) || snapshot == null || !snapshot.hasAny()) {
            return;
        }

        long now = System.nanoTime();
        long cooldownNanos = secondsToNanos(CosmeticModuleSettings.getRequestCooldownSeconds());
        pruneRequestCooldowns(now, cooldownNanos);
        LinkedHashMap<UUID, Long> requesterRequests = LAST_REQUEST_NANOS.computeIfAbsent(
                requesterUuid, ignored -> new LinkedHashMap<>(16, 0.75F, true));
        Long lastRequest = requesterRequests.get(targetUuid);
        if (lastRequest != null && now - lastRequest < cooldownNanos) {
            return;
        }
        requesterRequests.put(targetUuid, now);
        while (requesterRequests.size() > MAX_REQUEST_TARGETS_PER_PLAYER) {
            requesterRequests.remove(requesterRequests.keySet().iterator().next());
        }
        sendSnapshot(requester, targetUuid, snapshot);
    }

    /** 删除指定账户的全部自定义外观数据并广播清除。 */
    public static void deletePlayerData(MinecraftServer server, String uuidText) {
        if (uuidText == null || uuidText.isBlank()) {
            DebugLogger.SERVER.warn(MODULE, "账户缺少 UUID，无法删除自定义外观数据");
            return;
        }
        try {
            deletePlayerData(server, UUID.fromString(uuidText));
        } catch (IllegalArgumentException e) {
            DebugLogger.SERVER.warn(MODULE, "账户 UUID 非法，拒绝删除自定义外观数据：{}", uuidText);
        }
    }

    /** 删除指定 UUID 的全部自定义外观数据并广播清除。 */
    public static void deletePlayerData(MinecraftServer server, UUID playerUuid) {
        if (!ApiServiceClient.deleteCosmetics(playerUuid)) {
            DebugLogger.SERVER.warn(MODULE, "Api 删除玩家 {} 的外观数据失败", playerUuid);
            return;
        }
        LAST_UPLOAD_NANOS.remove(playerUuid);
        clearRequestCooldowns(playerUuid);
        ONLINE_COSMETICS.remove(playerUuid);
        if (server != null)
            broadcastInfo(server, playerUuid, emptySnapshot(), null);
        DebugLogger.SERVER.debug(MODULE, "已删除玩家 {} 的自定义外观数据", playerUuid);
    }

    private static boolean isServerActive(MinecraftServer server) {
        return CosmeticModuleSettings.isEnabled()
                && (server.isSingleplayer() || !server.usesAuthentication());
    }

    private static void requestMojangVerification(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        String challenge = UUID.randomUUID().toString().replace("-", "");
        ACTIVE_APPEARANCE_SESSIONS.put(player.getUUID(), challenge);
        PENDING_MOJANG_CHALLENGES.put(player.getUUID(), new MojangChallenge(
                challenge, System.nanoTime() + MOJANG_VERIFICATION_TIMEOUT_SECONDS * 1_000_000_000L));
        ServerPlayNetworking.send(player, new MojangAuthChallengePayload(challenge));
        CompletableFuture.delayedExecutor(MOJANG_VERIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(
                () -> server.execute(() -> {
                    MojangChallenge pending = PENDING_MOJANG_CHALLENGES.get(player.getUUID());
                    if (pending == null || !pending.challenge().equals(challenge)) {
                        return;
                    }
                    PENDING_MOJANG_CHALLENGES.remove(player.getUUID());
                    if (player.hasDisconnected() || !isAuthenticated(player)) {
                        return;
                    }
                    handleMojangVerificationFailed(player, "客户端未完成 Mojang 会话核验");
                }));
    }

    private static MojangProfile verifyMojangProfile(
            MinecraftServer server, String challenge, MojangProfileRequestPayload payload) {
        try {
            var sessionService = server.services().sessionService();
            var result = sessionService.hasJoinedServer(payload.profileName(), challenge, null);
            if (result == null || result.profile() == null
                    || !payload.profileId().equals(result.profile().id())
                    || !payload.profileName().equalsIgnoreCase(result.profile().name())) {
                return null;
            }
            Property textures = findTexturesProperty(result.profile());
            String textureValue = textures == null ? "" : sessionService.getSecurePropertyValue(textures);
            return new MojangProfile(result.profile().id(), result.profile().name(),
                    textureValue,
                    textures == null ? "" : textures.signature());
        } catch (Exception e) {
            DebugLogger.SERVER.warn(MODULE, "Mojang 正版外观核验失败：{}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static Property findTexturesProperty(GameProfile profile) {
        Collection<Property> properties = profile.properties().get("textures");
        for (Property property : properties) {
            if (property != null && !property.value().isBlank()) {
                return property;
            }
        }
        return null;
    }

    private static void handleMojangVerificationSucceeded(ServerPlayer player, MojangProfile profile) {
        MinecraftServer server = player.level().getServer();
        if (server == null || player.hasDisconnected() || !isAuthenticated(player)) {
            return;
        }
        VERIFIED_MOJANG_PROFILES.put(player.getUUID(), profile);
        VERIFYING_MOJANG_PROFILES.remove(player.getUUID());
        ONLINE_COSMETICS.remove(player.getUUID());
        broadcastInfo(server, player.getUUID(), emptySnapshot(), null);
        broadcastMojang(server, new MojangSkinPayload(player.getUUID(), profile.profileId(),
                profile.profileName(), profile.textureValue(), profile.textureSignature()));
        ServerPlayNetworking.send(player, new CosmeticReadyPayload(
                true, CosmeticModuleSettings.getRequestCooldownSeconds()));
        sendVerifiedMojangAppearances(player);
        sendOnlineCustomAppearances(player);
        DebugLogger.SERVER.debug(MODULE, "玩家 {} 已核验为 Mojang 正版账户，已启用正版皮肤与披风", player.getScoreboardName());
    }

    private static void handleMojangVerificationFailed(ServerPlayer player, String reason) {
        if (player.hasDisconnected() || !isAuthenticated(player)) {
            return;
        }
        VERIFIED_MOJANG_PROFILES.remove(player.getUUID());
        VERIFYING_MOJANG_PROFILES.remove(player.getUUID());
        DebugLogger.SERVER.debug(MODULE, "玩家 {} 未通过 Mojang 正版核验（{}），启用离线自定义外观", player.getScoreboardName(), reason);
        syncCustomAppearance(player);
    }

    private static void syncCustomAppearance(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String session = ACTIVE_APPEARANCE_SESSIONS.get(uuid);
        if (!isCurrentCustomSession(player, session)) {
            return;
        }
        ServerPlayNetworking.send(player, new CosmeticReadyPayload(
                true, CosmeticModuleSettings.getRequestCooldownSeconds()));
        sendOnlineCustomAppearances(player);
        sendVerifiedMojangAppearances(player);
        loadCustomAppearance(player, session);
    }

    private static void loadCustomAppearance(ServerPlayer player, String session) {
        UUID uuid = player.getUUID();
        if (LOADING_SNAPSHOTS.putIfAbsent(uuid, session) != null) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        // A new login must read after any upload still finishing for an earlier connection.
        CompletableFuture<?> previous = UPLOADING_SNAPSHOTS.getOrDefault(uuid, CompletableFuture.completedFuture(false));
        previous.handle((accepted, error) -> null)
                .thenApplyAsync(ignored -> readSnapshot(uuid))
                .whenComplete((snapshot, error) -> server.execute(() -> {
                    ServerPlayer connected = server.getPlayerList().getPlayer(uuid);
                    if (!LOADING_SNAPSHOTS.remove(uuid, session)
                            || connected == null || !isCurrentCustomSession(connected, session)) {
                        return;
                    }
                    if (error != null) {
                        DebugLogger.SERVER.exception(MODULE, "读取装扮", error);
                    }
                    if (snapshot == null || error != null) {
                        // An empty hash denotes a failed read. A saved empty appearance still has a SHA-256 hash.
                        ServerPlayNetworking.send(connected, new CosmeticDataPayload(uuid, "", EMPTY, EMPTY, EMPTY));
                        return;
                    }
                    publishSnapshot(connected, snapshot);
                }));
    }

    private static boolean isCurrentCustomSession(ServerPlayer player, String session) {
        MinecraftServer server = player.level().getServer();
        UUID uuid = player.getUUID();
        return server != null && isServerActive(server) && session != null
                && session.equals(ACTIVE_APPEARANCE_SESSIONS.get(uuid))
                && !player.hasDisconnected() && server.getPlayerList().getPlayer(uuid) == player
                && isAuthenticated(player) && !VERIFIED_MOJANG_PROFILES.containsKey(uuid)
                && !PENDING_MOJANG_CHALLENGES.containsKey(uuid) && !VERIFYING_MOJANG_PROFILES.containsKey(uuid);
    }

    private static void publishSnapshot(ServerPlayer player, CosmeticSnapshot snapshot) {
        UUID uuid = player.getUUID();
        ONLINE_COSMETICS.put(uuid, snapshot);
        clearTargetRequestCooldowns(uuid);
        sendSnapshot(player, uuid, snapshot);
        broadcastInfo(player.level().getServer(), uuid, snapshot, uuid);
    }

    private static void sendSnapshot(ServerPlayer receiver, UUID ownerUuid, CosmeticSnapshot snapshot) {
        ServerPlayNetworking.send(receiver, new CosmeticDataPayload(
                ownerUuid, snapshot.snapshotHash(), snapshot.skinWide(), snapshot.skinSlim(), snapshot.cloak()));
    }

    private static void sendOnlineCustomAppearances(ServerPlayer receiver) {
        ONLINE_COSMETICS.forEach((uuid, snapshot) -> {
            if (!uuid.equals(receiver.getUUID()) && snapshot.hasAny()) {
                CosmeticState state = snapshot.state();
                ServerPlayNetworking.send(receiver,
                        new CosmeticInfoPayload(uuid, state.hasSkin(), state.hasCloak(), snapshot.snapshotHash()));
            }
        });
    }

    private static void sendVerifiedMojangAppearances(ServerPlayer receiver) {
        for (Map.Entry<UUID, MojangProfile> entry : VERIFIED_MOJANG_PROFILES.entrySet()) {
            if (entry.getKey().equals(receiver.getUUID())) {
                continue;
            }
            MojangProfile profile = entry.getValue();
            ServerPlayNetworking.send(receiver, new MojangSkinPayload(
                    entry.getKey(), profile.profileId(), profile.profileName(),
                    profile.textureValue(), profile.textureSignature()));
        }
    }

    private static void broadcastMojang(MinecraftServer server, MojangSkinPayload payload) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (isAuthenticated(online)) {
                ServerPlayNetworking.send(online, payload);
            }
        }
    }

    private static boolean isAuthenticated(ServerPlayer player) {
        return AuthPlayerHelper.isAuthenticated(player) || AuthPlayerHelper.canSkipAuth(player);
    }

    private static CosmeticSnapshot readSnapshot(UUID playerUuid) {
        var remote = ApiServiceClient.fetchCosmeticSnapshot(playerUuid);
        if (remote.isPresent()) {
            ApiServiceClient.CosmeticSnapshot snapshot = remote.get();
            return new CosmeticSnapshot(snapshot.skinWide(), snapshot.skinSlim(), snapshot.cloak(),
                    CosmeticSnapshotHasher.hash(snapshot.skinWide(), snapshot.skinSlim(), snapshot.cloak()));
        }
        DebugLogger.SERVER.warn(MODULE, "Api 无法读取玩家 {} 的外观数据", playerUuid);
        return null;
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
            if (isAuthenticated(online)
                    && (!VERIFIED_MOJANG_PROFILES.containsKey(ownerUuid) || !snapshot.hasAny())) {
                ServerPlayNetworking.send(online, payload);
            }
        }
    }

    private static long secondsToNanos(int seconds) {
        return seconds <= 0 ? 0L : seconds * 1_000_000_000L;
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

    private record MojangChallenge(String challenge, long expiresAtNanos) {
    }

    private record MojangProfile(UUID profileId, String profileName, String textureValue, String textureSignature) {
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
