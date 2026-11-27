package top.csituka.youzaiworldcore.client.cosmetic;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;
import top.csituka.youzaiworldcore.cosmetic.CosmeticPngValidator;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.cosmetic.CosmeticSnapshotHasher;
import top.csituka.youzaiworldcore.network.CosmeticDataPayload;
import top.csituka.youzaiworldcore.network.CosmeticInfoPayload;
import top.csituka.youzaiworldcore.network.CosmeticReadyPayload;
import top.csituka.youzaiworldcore.network.CosmeticRequestPayload;
import top.csituka.youzaiworldcore.network.CosmeticUploadPayload;
import top.csituka.youzaiworldcore.network.CosmeticUploadResultPayload;
import top.csituka.youzaiworldcore.network.MojangAuthChallengePayload;
import top.csituka.youzaiworldcore.network.MojangProfileRequestPayload;
import top.csituka.youzaiworldcore.network.MojangSkinPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 自定义皮肤与披风客户端管理器。
 * <p>
 * 负责已保存外观同步、手动上传、动态纹理注册与客户端缓存。
 * </p>
 */
@SuppressWarnings("null")
public final class CosmeticClientManager {

    private static final String MODULE = "CosmeticClientManager";
    private static final int CACHE_LIMIT = 64;
    private static final int MAX_REQUEST_ATTEMPTS = 3;
    private static final int UPLOAD_ACK_TIMEOUT_SECONDS = 45;
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    private static final byte[] EMPTY = new byte[0];
    private static final CosmeticDataPayload EMPTY_TEST_SNAPSHOT =
            new CosmeticDataPayload(EMPTY_UUID, "", EMPTY, EMPTY, EMPTY);

    private static final Slot SKIN_WIDE = new Slot("skin.png", "skin", "wide", false);
    private static final Slot SKIN_SLIM = new Slot("skin_slim.png", "skin", "slim", false);
    private static final Slot CLOAK = new Slot("cloak.png", "cloak", "default", true);

    private static final LinkedHashMap<UUID, CachedAppearance> CACHE =
            new LinkedHashMap<>(16, 0.75F, true);
    private static final LinkedHashMap<UUID, PlayerSkin> MOJANG_SKINS =
            new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<UUID> VERIFIED_MOJANG_OWNERS = new HashSet<>();
    private static final Map<UUID, MojangSkinKey> MOJANG_SKIN_KEYS = new LinkedHashMap<>();
    private static final Map<UUID, PendingRequest> PENDING_REQUESTS = new LinkedHashMap<>();

    private static boolean initialized;
    private static boolean serverReady;
    private static int requestRetrySeconds = 11;
    private static CosmeticDataPayload ownSnapshot;
    private static LocalTestAppearance localTestAppearance;
    private static boolean ownSnapshotFailed;
    private static PendingUpload pendingUpload;
    private static String mojangChallenge = "";
    private static long sessionEpoch;

    private CosmeticClientManager() {
    }

    /** 注册断线清理回调并准备本地外观目录。 */
    public static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModPaths.ensureDir(ModPaths.clientConfig(ClientGlobalSettings.COSMETIC_MODULE));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            if (localTestAppearance != null) {
                releaseTextures(localTestAppearance.textures());
                localTestAppearance = null;
            }
            clearSession();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
        DebugLogger.CLIENT.debug(MODULE, "客户端自定义皮肤与披风管理器已初始化");
    }

    /** 认证后只准备接收服务器上已保存的外观。 */
    public static void onReady(CosmeticReadyPayload payload) {
        if (!payload.ready()) {
            clearSession();
            return;
        }
        serverReady = true;
        requestRetrySeconds = Math.max(1, Math.min(3600, payload.requestCooldownSeconds()) + 1);
    }

    /** 开发者单人世界或开发者选项中配置的专用测试连接。 */
    public static boolean isLocalTestEnvironment() {
        Minecraft client = Minecraft.getInstance();
        if (!ClientExternalSettings.isDevModeEnabled() || client.player == null) {
            return false;
        }
        if (client.hasSingleplayerServer()) {
            return true;
        }
        var server = client.getCurrentServer();
        return "dedicated".equals(ClientExternalSettings.getDebugModeType())
                && server != null
                && ClientExternalSettings.getDebugServerAddress().equalsIgnoreCase(server.ip);
    }

    public static boolean usesOfficialAppearance() {
        return !isLocalTestEnvironment() && (isLocalMojangVerified() || !isOfflineSession());
    }

    public static boolean canEditAppearance() {
        return isLocalTestEnvironment()
                || serverReady && !usesOfficialAppearance() && ClientPlayNetworking.canSend(CosmeticUploadPayload.ID);
    }

    public static CosmeticDataPayload ownSnapshot() {
        if (isLocalTestEnvironment()) {
            return localTestAppearance == null ? EMPTY_TEST_SNAPSHOT : localTestAppearance.snapshot();
        }
        return ownSnapshot;
    }

    public static boolean ownSnapshotFailed() {
        return !isLocalTestEnvironment() && ownSnapshotFailed;
    }

    public static void requestOwnSnapshot() {
        var player = Minecraft.getInstance().player;
        if (player != null && !isLocalTestEnvironment() && canEditAppearance()
                && ClientPlayNetworking.canSend(CosmeticRequestPayload.ID)) {
            ownSnapshotFailed = false;
            ClientPlayNetworking.send(new CosmeticRequestPayload(player.getUUID()));
        }
    }

    /** 使用预览过的字节提交装扮，开发者测试时只更新本机的渲染。 */
    public static CompletableFuture<CosmeticUploadResultPayload> applyAppearance(
            byte[] skinWide, byte[] skinSlim, byte[] cloak) {
        String hash = CosmeticSnapshotHasher.hash(skinWide, skinSlim, cloak);
        if (isLocalTestEnvironment()) {
            UUID owner = Minecraft.getInstance().player.getUUID();
            boolean applied = applyLocalTestAppearance(new CosmeticDataPayload(owner, hash, skinWide, skinSlim, cloak));
            return CompletableFuture.completedFuture(new CosmeticUploadResultPayload(hash, applied, 0));
        }
        if (!canEditAppearance() || pendingUpload != null) {
            return CompletableFuture.completedFuture(new CosmeticUploadResultPayload(hash, false, 0));
        }
        var result = new CompletableFuture<CosmeticUploadResultPayload>();
        pendingUpload = new PendingUpload(hash,
                System.nanoTime() + UPLOAD_ACK_TIMEOUT_SECONDS * 1_000_000_000L, result);
        ClientPlayNetworking.send(new CosmeticUploadPayload(true, hash, skinWide, skinSlim, cloak));
        return result;
    }

    private static boolean applyLocalTestAppearance(CosmeticDataPayload snapshot) {
        LocalTestAppearance previous = localTestAppearance;
        if (previous != null && previous.snapshot().ownerUuid().equals(snapshot.ownerUuid())
                && previous.snapshot().snapshotHash().equals(snapshot.snapshotHash())) {
            return true;
        }
        CachedAppearance textures = createAppearance(snapshot, "cosmetic/local_test/");
        if (textures == null) {
            return false;
        }
        localTestAppearance = new LocalTestAppearance(snapshot, textures);
        if (previous != null) {
            releaseTextures(previous.textures());
        }
        return true;
    }

    /** 响应服务端的一次性 Mojang 会话挑战，不上传访问令牌。 */
    public static void onMojangAuthChallenge(MojangAuthChallengePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload.challenge().isBlank()) {
            return;
        }
        mojangChallenge = payload.challenge();
        if (isOfflineSession()) {
            ClientPlayNetworking.send(new MojangProfileRequestPayload(payload.challenge(), EMPTY_UUID, ""));
            DebugLogger.CLIENT.debug(MODULE, "当前客户端不是正版会话，跳过 Mojang 外观核验");
            return;
        }
        long challengeEpoch = sessionEpoch;
        User user = client.getUser();
        CompletableFuture.runAsync(() -> {
            try {
                client.services().sessionService().joinServer(
                        user.getProfileId(), user.getAccessToken(), payload.challenge());
                client.execute(() -> {
                    if (challengeEpoch != sessionEpoch
                            || !payload.challenge().equals(mojangChallenge)
                            || client.getConnection() == null
                            || isOfflineSession()) {
                        return;
                    }
                    ClientPlayNetworking.send(new MojangProfileRequestPayload(
                            payload.challenge(), user.getProfileId(), user.getName()));
                    DebugLogger.CLIENT.debug(MODULE, "已完成 Mojang 会话挑战并提交档案标识");
                });
            } catch (Exception e) {
                DebugLogger.CLIENT.warn(MODULE, "Mojang 会话挑战失败：{}", e.getClass().getSimpleName());
                client.execute(() -> {
                    if (challengeEpoch == sessionEpoch
                            && payload.challenge().equals(mojangChallenge)
                            && client.getConnection() != null) {
                        ClientPlayNetworking.send(new MojangProfileRequestPayload(
                                payload.challenge(), EMPTY_UUID, ""));
                    }
                });
            }
        });
    }

    /** 接收服务端核验后的签名纹理属性，并交给原版 SkinManager 下载与缓存。 */
    public static void onMojangSkin(MojangSkinPayload payload) {
        UUID ownerUuid = payload.ownerUuid();
        if (!payload.verified()) {
            VERIFIED_MOJANG_OWNERS.remove(ownerUuid);
            MOJANG_SKIN_KEYS.remove(ownerUuid);
            MOJANG_SKINS.remove(ownerUuid);
            DebugLogger.CLIENT.debug(MODULE, "已清除玩家 {} 的正版外观缓存", ownerUuid);
            return;
        }
        MojangSkinKey skinKey = new MojangSkinKey(
                payload.profileId(), payload.textureValue(), payload.textureSignature());
        VERIFIED_MOJANG_OWNERS.add(ownerUuid);
        MOJANG_SKIN_KEYS.put(ownerUuid, skinKey);
        PENDING_REQUESTS.remove(ownerUuid);
        release(ownerUuid);
        long skinEpoch = sessionEpoch;
        try {
            // 当前 Authlib 的属性表不可变，必须在构造档案时完整提供签名纹理。
            PropertyMap properties = payload.hasTextureProperty()
                    ? new PropertyMap(ImmutableMultimap.of("textures",
                            new Property("textures", payload.textureValue(), payload.textureSignature())))
                    : PropertyMap.EMPTY;
            GameProfile profile = new GameProfile(payload.profileId(), payload.profileName(), properties);
            Minecraft client = Minecraft.getInstance();
            client.getSkinManager().get(profile).whenComplete((skin, error) -> client.execute(() -> {
                if (skinEpoch != sessionEpoch
                        || !VERIFIED_MOJANG_OWNERS.contains(ownerUuid)
                        || !skinKey.equals(MOJANG_SKIN_KEYS.get(ownerUuid))
                        || client.getConnection() == null) {
                    return;
                }
                if (error != null || skin == null || skin.isEmpty()) {
                    DebugLogger.CLIENT.warn(MODULE, "玩家 {} 的 Mojang 纹理下载失败", ownerUuid);
                    return;
                }
                MOJANG_SKINS.put(ownerUuid, skin.get());
                DebugLogger.CLIENT.debug(MODULE, "已缓存玩家 {} 的 Mojang 正版皮肤与披风", ownerUuid);
            }));
        } catch (RuntimeException e) {
            DebugLogger.CLIENT.exception(MODULE, "onMojangSkin", e);
        }
    }

    /** 收到目标玩家外观状态后保留可用旧缓存，并按快照哈希拉取新版本。 */
    public static void onInfo(CosmeticInfoPayload payload) {
        if (!serverReady || !ClientExternalSettings.isCosmeticEnabled()) {
            return;
        }

        UUID ownerUuid = payload.ownerUuid();
        if (isLocalPlayer(ownerUuid) || VERIFIED_MOJANG_OWNERS.contains(ownerUuid)) {
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
        if (!serverReady) {
            return;
        }
        UUID ownerUuid = payload.ownerUuid();
        if (isLocalPlayer(ownerUuid)) {
            acceptOwnSnapshot(payload);
            return;
        }
        if (!ClientExternalSettings.isCosmeticEnabled() || VERIFIED_MOJANG_OWNERS.contains(ownerUuid)) {
            return;
        }

        PendingRequest pending = PENDING_REQUESTS.get(ownerUuid);
        if (pending == null) {
            DebugLogger.CLIENT.debug(MODULE, "忽略玩家 {} 的未请求外观快照", ownerUuid);
            return;
        }
        String actualHash = CosmeticSnapshotHasher.hash(
                payload.skinWide(), payload.skinSlim(), payload.cloak());
        if (!actualHash.equals(payload.snapshotHash())) {
            DebugLogger.CLIENT.warn(MODULE, "丢弃玩家 {} 的外观数据：快照哈希不匹配", ownerUuid);
            scheduleRequestRetry(ownerUuid, pending.snapshotHash());
            return;
        }

        if (pending != null && !pending.snapshotHash().equals(payload.snapshotHash())) {
            DebugLogger.CLIENT.debug(MODULE, "忽略玩家 {} 的过期外观快照", ownerUuid);
            return;
        }

        if (cacheSnapshot(ownerUuid, payload.snapshotHash(), payload.skinWide(), payload.skinSlim(),
                payload.cloak())) {
            PENDING_REQUESTS.remove(ownerUuid);
        } else {
            scheduleRequestRetry(ownerUuid, payload.snapshotHash());
        }
    }

    private static void acceptOwnSnapshot(CosmeticDataPayload payload) {
        if (isLocalMojangVerified()) {
            return;
        }
        String hash = CosmeticSnapshotHasher.hash(payload.skinWide(), payload.skinSlim(), payload.cloak());
        if (!hash.equals(payload.snapshotHash())) {
            ownSnapshotFailed = true;
            return;
        }
        ownSnapshot = payload;
        ownSnapshotFailed = false;
        UUID owner = payload.ownerUuid();
        CachedAppearance cached = CACHE.get(owner);
        if (cached != null && cached.snapshotHash().equals(hash)) {
            return;
        }
        release(owner);
        cacheSnapshot(owner, hash, payload.skinWide(), payload.skinSlim(), payload.cloak());
    }

    public static void onUploadResult(CosmeticUploadResultPayload payload) {
        PendingUpload pending = pendingUpload;
        if (pending != null && pending.hash().equals(payload.snapshotHash())) {
            pendingUpload = null;
            pending.result().complete(payload);
        }
    }

    /** 将缓存中的自定义纹理合并到原版皮肤记录中。 */
    public static PlayerSkin apply(UUID ownerUuid, PlayerSkin original) {
        if (original == null) {
            return original;
        }
        LocalTestAppearance test = localTestAppearance;
        if (test != null && test.snapshot().ownerUuid().equals(ownerUuid)
                && isLocalPlayer(ownerUuid) && isLocalTestEnvironment()) {
            return test.textures().apply(original);
        }
        PlayerSkin mojangSkin = MOJANG_SKINS.get(ownerUuid);
        if (mojangSkin != null) {
            return mojangSkin;
        }
        if (VERIFIED_MOJANG_OWNERS.contains(ownerUuid)) {
            return original;
        }
        if (!ClientExternalSettings.isCosmeticEnabled()) {
            return original;
        }
        if (!serverReady) {
            return original;
        }
        CachedAppearance appearance = CACHE.get(ownerUuid);
        return appearance == null ? original : appearance.apply(original);
    }

    /** 清理当前服务器会话注册的全部动态纹理。 */
    public static void clearSession() {
        serverReady = false;
        mojangChallenge = "";
        sessionEpoch++;
        ownSnapshot = null;
        ownSnapshotFailed = false;
        failPendingUpload();
        PENDING_REQUESTS.clear();
        VERIFIED_MOJANG_OWNERS.clear();
        MOJANG_SKIN_KEYS.clear();
        MOJANG_SKINS.clear();
        releaseAll();
    }

    private static boolean cacheSnapshot(
            UUID ownerUuid,
            String snapshotHash,
            byte[] skinWide,
            byte[] skinSlim,
            byte[] cloak) {
        CachedAppearance appearance = createAppearance(
                new CosmeticDataPayload(ownerUuid, snapshotHash, skinWide, skinSlim, cloak), "cosmetic/");
        if (appearance == null) {
            return false;
        }
        CachedAppearance previous = CACHE.put(ownerUuid, appearance);
        if (previous != null) {
            releaseTextures(previous);
        }
        trimCache();
        return true;
    }

    private static CachedAppearance createAppearance(CosmeticDataPayload snapshot, String textureRoot) {
        UUID owner = snapshot.ownerUuid();
        String hash = snapshot.snapshotHash();
        String path = textureRoot + owner + "/" + hash.substring(0, 12);
        List<Identifier> ids = new ArrayList<>(3);
        ClientAsset.Texture wideSkin = registerTexture(owner, path, SKIN_WIDE, snapshot.skinWide(), ids);
        ClientAsset.Texture slimSkin = registerTexture(owner, path, SKIN_SLIM, snapshot.skinSlim(), ids);
        ClientAsset.Texture cloakTexture = registerTexture(owner, path, CLOAK, snapshot.cloak(), ids);
        if (registrationFailed(snapshot.skinWide(), wideSkin)
                || registrationFailed(snapshot.skinSlim(), slimSkin)
                || registrationFailed(snapshot.cloak(), cloakTexture)) {
            releaseTextureIds(ids);
            return null;
        }
        if (ids.isEmpty()) {
            return null;
        }
        return new CachedAppearance(hash, wideSkin, slimSkin, cloakTexture, ids);
    }

    private static ClientAsset.Texture registerTexture(
            UUID ownerUuid, String texturePath, Slot slot, byte[] data, List<Identifier> textureIds) {
        if (data == null || data.length == 0) {
            return null;
        }

        CosmeticPngValidator.Validation validation = slot.cloak()
                ? CosmeticPngValidator.validateCloak(data, CosmeticPngValidator.ABSOLUTE_MAX_FILE_BYTES)
                : CosmeticPngValidator.validateSkin(data, CosmeticPngValidator.ABSOLUTE_MAX_FILE_BYTES);
        if (!validation.valid()) {
            DebugLogger.CLIENT.warn(MODULE, "丢弃玩家 {} 的非法 {}：{}", ownerUuid, slot.fileName(), validation.reason());
            return null;
        }

        Identifier identifier = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID,
                texturePath + "/" + slot.category() + "/" + slot.model());
        NativeImage image = null;
        DynamicTexture texture = null;
        try {
            image = NativeImage.read(data);
            if (image.getWidth() != validation.width() || image.getHeight() != validation.height()) {
                DebugLogger.CLIENT.warn(MODULE, "玩家 {} 的 {} 解码尺寸与 IHDR 不一致", ownerUuid, slot.fileName());
                return null;
            }
            if (!slot.cloak()) {
                image = CosmeticSkinImages.prepare(image);
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
            DebugLogger.CLIENT.exception(MODULE, "registerTexture", e);
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
                || "FabricMC".equalsIgnoreCase(accessToken);
    }

    private static boolean isLocalMojangVerified() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null
                && VERIFIED_MOJANG_OWNERS.contains(client.player.getUUID());
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
        if (pendingUpload != null && now >= pendingUpload.expiresAtNanos()) {
            failPendingUpload();
        }

        Iterator<Map.Entry<UUID, PendingRequest>> iterator = PENDING_REQUESTS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRequest> entry = iterator.next();
            PendingRequest request = entry.getValue();
            if (now < request.nextAttemptNanos()) {
                continue;
            }
            if (request.attempts() >= MAX_REQUEST_ATTEMPTS) {
                DebugLogger.CLIENT.warn(MODULE, "玩家 {} 的外观数据连续请求失败，停止本轮重试", entry.getKey());
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

    private static void failPendingUpload() {
        if (pendingUpload != null) {
            PendingUpload pending = pendingUpload;
            pendingUpload = null;
            pending.result().complete(new CosmeticUploadResultPayload(pending.hash(), false, 0));
        }
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
            if (isLocalPlayer(eldest.getKey())) {
                continue;
            }
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

    private record MojangSkinKey(UUID profileId, String textureValue, String textureSignature) {
    }

    private record LocalTestAppearance(CosmeticDataPayload snapshot, CachedAppearance textures) {
    }

    private record PendingUpload(String hash, long expiresAtNanos,
                                 CompletableFuture<CosmeticUploadResultPayload> result) {
    }
}
