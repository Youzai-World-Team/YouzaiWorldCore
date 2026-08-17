package top.csituka.youzaiworldcore.client.cosmetic;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.config.CosmeticModuleSettings;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.cosmetic.CosmeticPngValidator;
import top.csituka.youzaiworldcore.cosmetic.CosmeticSnapshotHasher;
import top.csituka.youzaiworldcore.network.CosmeticDataPayload;
import top.csituka.youzaiworldcore.network.CosmeticInfoPayload;
import top.csituka.youzaiworldcore.network.CosmeticReadyPayload;
import top.csituka.youzaiworldcore.network.CosmeticRequestPayload;
import top.csituka.youzaiworldcore.network.CosmeticUploadPayload;
import top.csituka.youzaiworldcore.network.CosmeticUploadResultPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 自定义皮肤与披风客户端管理器。
 * <p>
 * 负责扫描 {@code yzwc/client/config/cosmetic_module/} 下的两个皮肤候选与单一披风 PNG、上传去重、按需下载、
 * 动态纹理注册以及最多 64 名玩家的 LRU 缓存。
 * </p>
 */
@SuppressWarnings("null")
public final class CosmeticClientManager {

    private static final String MODULE = "CosmeticClientManager";
    private static final int CACHE_LIMIT = 64;
    private static final int MAX_REQUEST_ATTEMPTS = 3;
    private static final int UPLOAD_ACK_TIMEOUT_SECONDS = 10;
    private static final byte[] EMPTY = new byte[0];

    private static final Slot SKIN_WIDE = new Slot("skin.png", "skin", "wide", false);
    private static final Slot SKIN_SLIM = new Slot("skin_slim.png", "skin", "slim", false);
    private static final Slot CLOAK = new Slot("cloak.png", "cloak", "default", true);

    private static final LinkedHashMap<UUID, CachedAppearance> CACHE =
            new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<UUID, PendingRequest> PENDING_REQUESTS = new LinkedHashMap<>();

    private static boolean initialized;
    private static boolean serverReady;
    private static UUID serverInstanceId;
    private static int requestRetrySeconds = 11;
    private static PendingUpload pendingUpload;
    private static long nextUploadRetryNanos;
    private static boolean skinConflictNotified;

    private CosmeticClientManager() {
    }

    /** 注册断线清理回调并准备本地外观目录。 */
    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModPaths.ensureDir(ModPaths.clientConfig(GlobalSettings.COSMETIC_MODULE));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(CosmeticClientManager::clearSession));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        DebugLogger.info(MODULE, "客户端自定义皮肤与披风管理器已初始化");
    }

    /** 处理服务端在账户认证完成后下发的就绪状态。 */
    public static void onReady(CosmeticReadyPayload payload) {
        DebugLogger.entering(MODULE, "onReady", "ready=" + payload.ready());
        serverReady = payload.ready() && ClientExternalSettings.isCosmeticEnabled();
        if (!serverReady) {
            PENDING_REQUESTS.clear();
            pendingUpload = null;
            serverInstanceId = null;
            skinConflictNotified = false;
            releaseAll();
            DebugLogger.exiting(MODULE, "onReady", "disabled");
            return;
        }

        serverInstanceId = payload.serverInstanceId();
        requestRetrySeconds = Math.max(1, Math.min(3600, payload.requestCooldownSeconds()) + 1);
        PENDING_REQUESTS.clear();

        LocalSnapshot snapshot = scanLocalFiles();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            DebugLogger.exiting(MODULE, "onReady", "player unavailable");
            return;
        }

        UUID selfUuid = client.player.getUUID();
        if (!isOfflineSession()) {
            release(selfUuid);
            pendingUpload = null;
            DebugLogger.exiting(MODULE, "onReady", "online session");
            return;
        }

        if (snapshot.skinConflict() && !skinConflictNotified) {
            client.player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.cosmetic.skin_conflict").withStyle(ChatFormatting.YELLOW));
            skinConflictNotified = true;
            DebugLogger.info(MODULE, "同时检测到 skin.png 和 skin_slim.png，已优先使用 skin.png");
        }

        release(selfUuid);
        cacheSnapshot(selfUuid, snapshot.snapshotHash(), snapshot.skinWide(), snapshot.skinSlim(),
                snapshot.cloak());

        Map<String, CosmeticUploadState.FileState> previous =
                CosmeticUploadState.load(serverInstanceId, selfUuid);
        if (payload.ownSnapshotHash().equals(snapshot.snapshotHash())) {
            if (!previous.equals(snapshot.states())) {
                CosmeticUploadState.save(serverInstanceId, selfUuid, snapshot.states());
            }
            pendingUpload = null;
            DebugLogger.debug(MODULE, "服务端外观快照已与本地一致，跳过上传");
        } else {
            pendingUpload = new PendingUpload(serverInstanceId, selfUuid, snapshot);
            sendPendingUpload();
        }
        DebugLogger.exiting(MODULE, "onReady");
    }

    /** 收到目标玩家外观状态后保留可用旧缓存，并按快照哈希拉取新版本。 */
    public static void onInfo(CosmeticInfoPayload payload) {
        if (!serverReady || !ClientExternalSettings.isCosmeticEnabled()) {
            return;
        }

        UUID ownerUuid = payload.ownerUuid();
        if (isLocalPlayer(ownerUuid)) {
            return;
        }
        if (!payload.hasSkin() && !payload.hasCloak()) {
            PENDING_REQUESTS.remove(ownerUuid);
            release(ownerUuid);
            return;
        }

        CachedAppearance cached = CACHE.get(ownerUuid);
        if (cached != null && cached.snapshotHash().equals(payload.snapshotHash())) {
            PENDING_REQUESTS.remove(ownerUuid);
            return;
        }

        PendingRequest pending = PENDING_REQUESTS.get(ownerUuid);
        if (pending != null && pending.snapshotHash().equals(payload.snapshotHash())) {
            return;
        }
        requestSnapshot(ownerUuid, payload.snapshotHash());
    }

    /** 二次校验并注册服务端回传的动态纹理。 */
    public static void onData(CosmeticDataPayload payload) {
        if (!serverReady || !ClientExternalSettings.isCosmeticEnabled()) {
            return;
        }

        UUID ownerUuid = payload.ownerUuid();
        if (isLocalPlayer(ownerUuid)) {
            return;
        }

        PendingRequest pending = PENDING_REQUESTS.get(ownerUuid);
        if (pending == null) {
            DebugLogger.debug(MODULE, "忽略玩家 %s 的未请求外观快照", ownerUuid);
            return;
        }
        String actualHash = CosmeticSnapshotHasher.hash(
                payload.skinWide(), payload.skinSlim(), payload.cloak());
        if (!actualHash.equals(payload.snapshotHash())) {
            DebugLogger.warn(MODULE, "丢弃玩家 %s 的外观数据：快照哈希不匹配", ownerUuid);
            scheduleRequestRetry(ownerUuid, pending.snapshotHash());
            return;
        }

        if (pending != null && !pending.snapshotHash().equals(payload.snapshotHash())) {
            DebugLogger.debug(MODULE, "忽略玩家 %s 的过期外观快照", ownerUuid);
            return;
        }

        if (cacheSnapshot(ownerUuid, payload.snapshotHash(), payload.skinWide(), payload.skinSlim(),
                payload.cloak())) {
            PENDING_REQUESTS.remove(ownerUuid);
        } else {
            scheduleRequestRetry(ownerUuid, payload.snapshotHash());
        }
    }

    /** 服务端确认上传成功后才持久化该作用域的去重状态。 */
    public static void onUploadResult(CosmeticUploadResultPayload payload) {
        PendingUpload pending = pendingUpload;
        if (!serverReady || pending == null || !pending.snapshot().snapshotHash().equals(payload.snapshotHash())) {
            return;
        }
        if (payload.accepted()) {
            CosmeticUploadState.save(
                    pending.serverInstanceId(), pending.playerUuid(), pending.snapshot().states());
            pendingUpload = null;
            nextUploadRetryNanos = 0L;
            DebugLogger.info(MODULE, "服务端已确认自定义外观快照");
            return;
        }
        if (payload.retryAfterSeconds() > 0) {
            nextUploadRetryNanos = System.nanoTime()
                    + payload.retryAfterSeconds() * 1_000_000_000L;
            DebugLogger.debug(MODULE, "服务端暂未接受外观快照，将在 %d 秒后重试",
                    payload.retryAfterSeconds());
        } else {
            pendingUpload = null;
            nextUploadRetryNanos = 0L;
            DebugLogger.warn(MODULE, "服务端拒绝了本次自定义外观快照");
        }
    }

    /** 将缓存中的自定义纹理合并到原版皮肤记录中。 */
    public static PlayerSkin apply(UUID ownerUuid, PlayerSkin original) {
        if (original == null || !serverReady || !ClientExternalSettings.isCosmeticEnabled()) {
            return original;
        }
        if (isOnlineLocalPlayer(ownerUuid)) {
            return original;
        }
        CachedAppearance appearance = CACHE.get(ownerUuid);
        return appearance == null ? original : appearance.apply(original);
    }

    /** 清理当前服务器会话注册的全部动态纹理。 */
    public static void clearSession() {
        serverReady = false;
        serverInstanceId = null;
        pendingUpload = null;
        nextUploadRetryNanos = 0L;
        skinConflictNotified = false;
        PENDING_REQUESTS.clear();
        releaseAll();
    }

    private static LocalSnapshot scanLocalFiles() {
        Path directory = ModPaths.clientConfig(GlobalSettings.COSMETIC_MODULE);
        Path wideSkinPath = directory.resolve(SKIN_WIDE.fileName());
        Path slimSkinPath = directory.resolve(SKIN_SLIM.fileName());
        boolean skinConflict = Files.isRegularFile(wideSkinPath) && Files.isRegularFile(slimSkinPath);

        ScannedFile wideSkin = scanFile(wideSkinPath, SKIN_WIDE);
        ScannedFile slimSkin = scanFile(slimSkinPath, SKIN_SLIM);
        ScannedFile cloak = scanFile(directory.resolve(CLOAK.fileName()), CLOAK);
        Map<String, CosmeticUploadState.FileState> states = new LinkedHashMap<>();
        states.put(SKIN_WIDE.fileName(), wideSkin.state());
        states.put(SKIN_SLIM.fileName(), slimSkin.state());
        states.put(CLOAK.fileName(), cloak.state());

        byte[] selectedSlimSkin = skinConflict ? EMPTY : slimSkin.data();
        return new LocalSnapshot(
                wideSkin.data(),
                selectedSlimSkin,
                cloak.data(),
                CosmeticSnapshotHasher.hash(wideSkin.data(), selectedSlimSkin, cloak.data()),
                states,
                skinConflict);
    }

    private static ScannedFile scanFile(Path file, Slot slot) {
        if (!Files.isRegularFile(file)) {
            return new ScannedFile(EMPTY, new CosmeticUploadState.FileState("", 0L, false));
        }
        try {
            long size = Files.size(file);
            if (size <= 0 || size > CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES) {
                DebugLogger.warn(MODULE, "忽略大小不合法的本地外观文件：%s（%d 字节）", file, size);
                return new ScannedFile(EMPTY, new CosmeticUploadState.FileState("", size, false));
            }

            byte[] bytes = Files.readAllBytes(file);
            String sha256 = sha256(bytes);
            CosmeticPngValidator.Validation validation = slot.cloak()
                    ? CosmeticPngValidator.validateCloak(bytes, CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES)
                    : CosmeticPngValidator.validateSkin(bytes, CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES);
            if (!validation.valid()) {
                DebugLogger.warn(MODULE, "忽略不合法的本地外观文件 %s：%s", file, validation.reason());
                return new ScannedFile(EMPTY,
                        new CosmeticUploadState.FileState(sha256, bytes.length, false));
            }
            return new ScannedFile(bytes,
                    new CosmeticUploadState.FileState(sha256, bytes.length, true));
        } catch (IOException e) {
            DebugLogger.exception(MODULE, "scanFile", e);
            return new ScannedFile(EMPTY, new CosmeticUploadState.FileState("", -1L, false));
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256", e);
        }
    }

    private static boolean cacheSnapshot(
            UUID ownerUuid,
            String snapshotHash,
            byte[] skinWide,
            byte[] skinSlim,
            byte[] cloak) {
        List<Identifier> textureIds = new ArrayList<>(3);
        ClientAsset.Texture wideSkin = registerTexture(ownerUuid, snapshotHash, SKIN_WIDE, skinWide, textureIds);
        ClientAsset.Texture slimSkin = registerTexture(ownerUuid, snapshotHash, SKIN_SLIM, skinSlim, textureIds);
        ClientAsset.Texture cloakTexture = registerTexture(ownerUuid, snapshotHash, CLOAK, cloak, textureIds);
        if (registrationFailed(skinWide, wideSkin)
                || registrationFailed(skinSlim, slimSkin)
                || registrationFailed(cloak, cloakTexture)) {
            releaseTextureIds(textureIds);
            return false;
        }
        if (wideSkin == null && slimSkin == null && cloakTexture == null) {
            return false;
        }

        CachedAppearance previous = CACHE.put(ownerUuid,
                new CachedAppearance(snapshotHash, wideSkin, slimSkin, cloakTexture, textureIds));
        if (previous != null) {
            releaseTextures(previous);
        }
        trimCache();
        return true;
    }

    private static ClientAsset.Texture registerTexture(
            UUID ownerUuid, String snapshotHash, Slot slot, byte[] data, List<Identifier> textureIds) {
        if (data == null || data.length == 0) {
            return null;
        }

        CosmeticPngValidator.Validation validation = slot.cloak()
                ? CosmeticPngValidator.validateCloak(data, CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES)
                : CosmeticPngValidator.validateSkin(data, CosmeticModuleSettings.ABSOLUTE_MAX_FILE_BYTES);
        if (!validation.valid()) {
            DebugLogger.warn(MODULE, "丢弃玩家 %s 的非法 %s：%s", ownerUuid, slot.fileName(), validation.reason());
            return null;
        }

        Identifier identifier = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID,
                "cosmetic/" + ownerUuid + "/" + snapshotHash.substring(0, 12)
                        + "/" + slot.category() + "/" + slot.model());
        NativeImage image = null;
        DynamicTexture texture = null;
        try {
            image = NativeImage.read(data);
            if (image.getWidth() != validation.width() || image.getHeight() != validation.height()) {
                DebugLogger.warn(MODULE, "玩家 %s 的 %s 解码尺寸与 IHDR 不一致", ownerUuid, slot.fileName());
                return null;
            }
            NativeImage ownedImage = image;
            texture = new DynamicTexture(
                    () -> "YouzaiWorldCore cosmetic " + ownerUuid + " " + slot.fileName(), ownedImage);
            image = null;
            Minecraft.getInstance().getTextureManager().register(identifier, texture);
            texture = null;
            textureIds.add(identifier);
            return new CosmeticTexture(identifier, identifier);
        } catch (IOException | RuntimeException e) {
            DebugLogger.exception(MODULE, "registerTexture", e);
            return null;
        } finally {
            if (texture != null) {
                texture.close();
            } else if (image != null) {
                image.close();
            }
        }
    }

    private static boolean isOfflineSession() {
        User user = Minecraft.getInstance().getUser();
        String accessToken = user.getAccessToken();
        return accessToken == null
                || accessToken.isBlank()
                || "0".equals(accessToken)
                || "FabricMC".equalsIgnoreCase(accessToken)
                || user.getXuid().isEmpty() && user.getClientId().isEmpty();
    }

    private static boolean isOnlineLocalPlayer(UUID ownerUuid) {
        Minecraft client = Minecraft.getInstance();
        return client.player != null
                && client.player.getUUID().equals(ownerUuid)
                && !isOfflineSession();
    }

    private static boolean isLocalPlayer(UUID ownerUuid) {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.player.getUUID().equals(ownerUuid);
    }

    private static void tick() {
        if (!serverReady) {
            return;
        }
        long now = System.nanoTime();
        if (pendingUpload != null && nextUploadRetryNanos > 0L && now >= nextUploadRetryNanos) {
            sendPendingUpload();
        }

        Iterator<Map.Entry<UUID, PendingRequest>> iterator = PENDING_REQUESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRequest> entry = iterator.next();
            PendingRequest request = entry.getValue();
            if (now < request.nextAttemptNanos()) {
                continue;
            }
            if (request.attempts() >= MAX_REQUEST_ATTEMPTS) {
                DebugLogger.warn(MODULE, "玩家 %s 的外观数据连续请求失败，停止本轮重试", entry.getKey());
                iterator.remove();
                continue;
            }
            ClientPlayNetworking.send(new CosmeticRequestPayload(entry.getKey()));
            entry.setValue(new PendingRequest(
                    request.snapshotHash(),
                    now + requestRetrySeconds * 1_000_000_000L,
                    request.attempts() + 1));
        }
    }

    private static void sendPendingUpload() {
        PendingUpload pending = pendingUpload;
        if (pending == null) {
            return;
        }
        LocalSnapshot snapshot = pending.snapshot();
        ClientPlayNetworking.send(new CosmeticUploadPayload(
                true,
                snapshot.snapshotHash(),
                snapshot.skinWide(),
                snapshot.skinSlim(),
                snapshot.cloak()));
        nextUploadRetryNanos = System.nanoTime() + UPLOAD_ACK_TIMEOUT_SECONDS * 1_000_000_000L;
        DebugLogger.info(MODULE, "本地外观快照已发送，等待服务端确认");
    }

    private static void requestSnapshot(UUID ownerUuid, String snapshotHash) {
        ClientPlayNetworking.send(new CosmeticRequestPayload(ownerUuid));
        PENDING_REQUESTS.put(ownerUuid, new PendingRequest(
                snapshotHash, System.nanoTime() + requestRetrySeconds * 1_000_000_000L, 1));
    }

    private static void scheduleRequestRetry(UUID ownerUuid, String snapshotHash) {
        PendingRequest current = PENDING_REQUESTS.get(ownerUuid);
        int attempts = current != null && current.snapshotHash().equals(snapshotHash)
                ? current.attempts()
                : 1;
        PENDING_REQUESTS.put(ownerUuid, new PendingRequest(
                snapshotHash, System.nanoTime() + requestRetrySeconds * 1_000_000_000L, attempts));
    }

    private static boolean registrationFailed(byte[] data, ClientAsset.Texture texture) {
        return data != null && data.length > 0 && texture == null;
    }

    private static void trimCache() {
        Iterator<Map.Entry<UUID, CachedAppearance>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > CACHE_LIMIT && iterator.hasNext()) {
            Map.Entry<UUID, CachedAppearance> eldest = iterator.next();
            iterator.remove();
            PENDING_REQUESTS.remove(eldest.getKey());
            releaseTextures(eldest.getValue());
        }
    }

    private static void release(UUID ownerUuid) {
        CachedAppearance removed = CACHE.remove(ownerUuid);
        if (removed != null) {
            releaseTextures(removed);
        }
    }

    private static void releaseAll() {
        CACHE.values().forEach(CosmeticClientManager::releaseTextures);
        CACHE.clear();
    }

    private static void releaseTextures(CachedAppearance appearance) {
        releaseTextureIds(appearance.textureIds());
    }

    private static void releaseTextureIds(List<Identifier> textureIds) {
        var textureManager = Minecraft.getInstance().getTextureManager();
        textureIds.forEach(textureManager::release);
    }

    private record Slot(String fileName, String category, String model, boolean cloak) {
    }

    private record ScannedFile(byte[] data, CosmeticUploadState.FileState state) {
    }

    private record LocalSnapshot(
            byte[] skinWide,
            byte[] skinSlim,
            byte[] cloak,
            String snapshotHash,
            Map<String, CosmeticUploadState.FileState> states,
            boolean skinConflict) {
    }

    private static final class CachedAppearance {
        private final String snapshotHash;
        private final ClientAsset.Texture skinWide;
        private final ClientAsset.Texture skinSlim;
        private final ClientAsset.Texture cloak;
        private final List<Identifier> textureIds;
        private PlayerSkin lastOriginal;
        private PlayerSkin lastApplied;

        private CachedAppearance(String snapshotHash, ClientAsset.Texture skinWide,
                                 ClientAsset.Texture skinSlim, ClientAsset.Texture cloak,
                                 List<Identifier> textureIds) {
            this.snapshotHash = snapshotHash;
            this.skinWide = skinWide;
            this.skinSlim = skinSlim;
            this.cloak = cloak;
            this.textureIds = textureIds;
        }

        private String snapshotHash() {
            return snapshotHash;
        }

        private List<Identifier> textureIds() {
            return textureIds;
        }

        private PlayerSkin apply(PlayerSkin original) {
            if (original.equals(lastOriginal)) {
                return lastApplied;
            }
            ClientAsset.Texture body = skinWide != null
                    ? skinWide
                    : skinSlim != null ? skinSlim : original.body();
            PlayerModelType model = skinWide != null
                    ? PlayerModelType.WIDE
                    : skinSlim != null ? PlayerModelType.SLIM : original.model();
            ClientAsset.Texture cape = cloak != null ? cloak : original.cape();
            lastOriginal = original;
            lastApplied = PlayerSkin.insecure(body, cape, original.elytra(), model);
            return lastApplied;
        }
    }

    private record PendingRequest(String snapshotHash, long nextAttemptNanos, int attempts) {
    }

    private record PendingUpload(UUID serverInstanceId, UUID playerUuid, LocalSnapshot snapshot) {
    }
}
