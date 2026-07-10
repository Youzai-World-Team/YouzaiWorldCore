package top.csituka.youzaiworldcore.feature;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 实验性功能注册系统
 * <p>
 * 支持全局默认状态 + 玩家级覆写状态。
 * 服务端通过 FeatureSyncPayload 同步到客户端。
 * 状态持久化到配置文件：
 * <ul>
 *   <li>客户端：{@code config/youzaiworldcore/experimental_feature/client_settings.json}</li>
 *   <li>服务端：{@code config/youzaiworldcore/experimental_feature/server_settings.json}</li>
 * </ul>
 * </p>
 */
public final class ExperimentalFeatures {

    public static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ExperimentalFeatures");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 配置子目录 */
    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("experimental_feature");

    private static final Map<String, FeatureEntry> REGISTRY = new LinkedHashMap<>();

    // ===== 服务端数据 =====
    private static final Map<String, Boolean> GLOBAL_STATE = new HashMap<>();
    private static final Map<String, Map<UUID, Boolean>> PLAYER_STATE = new HashMap<>();

    // ===== 客户端数据 =====
    private static UUID clientPlayerUuid = null;
    private static final Map<String, Boolean> CLIENT_GLOBAL = new HashMap<>();
    private static final Map<String, Boolean> CLIENT_PERSONAL = new HashMap<>();

    // ===== 持久化开关（避免循环保存） =====
    private static boolean suppressSave = false;

    private ExperimentalFeatures() {}

    // ==================== 注册 ====================

    /**
     * 注册可由客户端自行开关的实验性功能。
     */
    public static void register(
            String id, String name, String provider, String providerUrl,
            String description, String source, String sourceUrl,
            boolean defaultEnabled
    ) {
        register(id, name, provider, providerUrl, description, source, sourceUrl,
                defaultEnabled, false);
    }

    /**
     * 注册实验性功能。
     *
     * @param serverSide 是否仅服务端控制。{@code true} 时：
     *                   <ul>
     *                     <li>不存储到客户端配置</li>
     *                     <li>只能通过全局开关控制</li>
     *                     <li>不允许玩家自切换</li>
     *                   </ul>
     */
    public static void register(
            String id, String name, String provider, String providerUrl,
            String description, String source, String sourceUrl,
            boolean defaultEnabled, boolean serverSide
    ) {
        if (REGISTRY.containsKey(id)) {
            LOGGER.warn("实验性功能 '{}' 重复注册", id);
            return;
        }
        REGISTRY.put(id, new FeatureEntry(
                id, name, provider, providerUrl, description, source, sourceUrl,
                defaultEnabled, serverSide
        ));
        GLOBAL_STATE.putIfAbsent(id, defaultEnabled);
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("注册实验性功能: {} ({})，默认: {}，服务端控制: {}",
                    name, id, defaultEnabled, serverSide);
        }
    }

    public static void loadDefaults() {
        for (FeatureEntry entry : REGISTRY.values()) {
            GLOBAL_STATE.putIfAbsent(entry.id(), entry.defaultEnabled());
        }
    }

    public static FeatureEntry getEntry(String id) {
        return REGISTRY.get(id);
    }

    public static Map<String, FeatureEntry> getAllEntries() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // ==================== 状态查询（服务端）====================

    public static boolean isEnabled(String id, UUID playerUuid) {
        if (!REGISTRY.containsKey(id)) return false;
        FeatureEntry entry = REGISTRY.get(id);
        // 服务端控制的功能：跳过玩家覆写，只看全局
        if (entry.serverSide()) {
            return GLOBAL_STATE.getOrDefault(id, false);
        }
        Map<UUID, Boolean> playerOverrides = PLAYER_STATE.get(id);
        if (playerOverrides != null) {
            Boolean playerVal = playerOverrides.get(playerUuid);
            if (playerVal != null) return playerVal;
        }
        return GLOBAL_STATE.getOrDefault(id, false);
    }

    public static boolean isGlobalEnabled(String id) {
        return GLOBAL_STATE.getOrDefault(id, false);
    }

    // ==================== 状态设置（服务端）====================

    public static boolean setGlobal(String id, boolean enabled) {
        if (!REGISTRY.containsKey(id)) return false;
        GLOBAL_STATE.put(id, enabled);
        PLAYER_STATE.remove(id);
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("实验性功能 '{}' 全局状态: {}", id, enabled);
        }
        saveServerSettings();
        return true;
    }

    public static boolean setForPlayer(String id, UUID playerUuid, boolean enabled) {
        if (!REGISTRY.containsKey(id)) return false;
        // 服务端控制的功能不允许玩家覆写
        if (REGISTRY.get(id).serverSide()) return false;
        PLAYER_STATE.computeIfAbsent(id, k -> new HashMap<>()).put(playerUuid, enabled);
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("实验性功能 '{}' 玩家 {} 覆写: {}", id, playerUuid, enabled);
        }
        saveServerSettings();
        return true;
    }

    public static void clearPlayerOverride(String id, UUID playerUuid) {
        Map<UUID, Boolean> overrides = PLAYER_STATE.get(id);
        if (overrides != null) {
            overrides.remove(playerUuid);
        }
    }

    // ==================== 客户端 API ====================

    public static void setClientPlayerUuid(UUID uuid) {
        clientPlayerUuid = uuid;
    }

    public static UUID getClientPlayerUuid() {
        return clientPlayerUuid;
    }

    public static boolean isEnabled(String id) {
        Boolean personal = CLIENT_PERSONAL.get(id);
        if (personal != null) return personal;
        return CLIENT_GLOBAL.getOrDefault(id, false);
    }

    /**
     * 仅检查客户端的全局状态（忽略玩家个人覆写）。
     * <p>
     * 用于需要严格由服务端控制的场景，防止玩家通过自切换覆写全局开关。
     */
    public static boolean isGlobalOnClient(String id) {
        return CLIENT_GLOBAL.getOrDefault(id, false);
    }

    public static void applyGlobalSync(String id, boolean enabled) {
        CLIENT_GLOBAL.put(id, enabled);
        CLIENT_PERSONAL.remove(id);
        saveClientSettings();
    }

    public static void applyPersonalSync(UUID targetPlayer, String id, boolean enabled) {
        if (clientPlayerUuid != null && clientPlayerUuid.equals(targetPlayer)) {
            // 服务端控制的功能不接受个人同步
            FeatureEntry entry = REGISTRY.get(id);
            if (entry != null && entry.serverSide()) return;
            CLIENT_PERSONAL.put(id, enabled);
            saveClientSettings();
        }
    }

    public static void resetClientState() {
        clientPlayerUuid = null;
        CLIENT_GLOBAL.clear();
        CLIENT_PERSONAL.clear();
    }

    // ==================== 服务端配置持久化 ====================

    /** 加载服务端配置（不存在则创建默认；自动清理已删除功能的残留配置） */
    public static void loadServerSettings() {
        Path file = CONFIG_DIR.resolve("server_settings.json");
        if (!Files.exists(file)) {
            saveServerSettings();
            return;
        }
        boolean cleaned = false;
        try {
            String json = Files.readString(file);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            JsonObject features = root.getAsJsonObject("features");
            if (features == null) return;

            // 先清理已经删除的功能残留
            List<String> staleKeys = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : features.entrySet()) {
                if (!REGISTRY.containsKey(entry.getKey())) {
                    staleKeys.add(entry.getKey());
                }
            }
            for (String stale : staleKeys) {
                features.remove(stale);
                cleaned = true;
                if (YouzaiworldCore.logToFile) {
                    LOGGER.info("清理服务端配置中已删除的实验性功能: {}", stale);
                }
            }

            for (Map.Entry<String, JsonElement> entry : features.entrySet()) {
                String id = entry.getKey();
                if (!REGISTRY.containsKey(id)) continue;

                JsonObject obj = entry.getValue().getAsJsonObject();
                if (obj.has("global")) {
                    GLOBAL_STATE.put(id, obj.get("global").getAsBoolean());
                }
                if (obj.has("players")) {
                    JsonObject players = obj.getAsJsonObject("players");
                    Map<UUID, Boolean> map = new HashMap<>();
                    for (Map.Entry<String, JsonElement> pEntry : players.entrySet()) {
                        try {
                            map.put(UUID.fromString(pEntry.getKey()), pEntry.getValue().getAsBoolean());
                        } catch (IllegalArgumentException ignored) {}
                    }
                    if (!map.isEmpty()) {
                        PLAYER_STATE.put(id, map);
                    }
                }
            }
            if (YouzaiworldCore.logToFile) {
                LOGGER.info("已从 {} 加载服务端配置", file);
            }

            if (cleaned) {
                // 有残留配置被清理，重新保存
                root.add("features", features);
                Files.writeString(file, GSON.toJson(root));
                if (YouzaiworldCore.logToFile) {
                    LOGGER.info("已清理服务端配置中的残留条目并重新保存");
                }
            }
        } catch (Exception e) {
            LOGGER.error("加载服务端配置失败: {}", e.getMessage());
        }
    }

    /** 保存服务端配置 */
    public static void saveServerSettings() {
        if (suppressSave) return;
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            JsonObject features = new JsonObject();

            for (FeatureEntry entry : REGISTRY.values()) {
                String id = entry.id();
                JsonObject obj = new JsonObject();
                obj.addProperty("global", GLOBAL_STATE.getOrDefault(id, entry.defaultEnabled()));

                Map<UUID, Boolean> players = PLAYER_STATE.get(id);
                if (players != null && !players.isEmpty()) {
                    JsonObject playersObj = new JsonObject();
                    for (Map.Entry<UUID, Boolean> pEntry : players.entrySet()) {
                        playersObj.addProperty(pEntry.getKey().toString(), pEntry.getValue());
                    }
                    obj.add("players", playersObj);
                }
                features.add(id, obj);
            }
            root.add("features", features);

            Path file = CONFIG_DIR.resolve("server_settings.json");
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存服务端配置失败: {}", e.getMessage());
        }
    }

    // ==================== 客户端配置持久化 ====================

    /** 加载客户端配置（自动清理已删除功能的残留配置） */
    public static void loadClientSettings() {
        // 先将 REGISTRY 中的默认值填入 CLIENT_GLOBAL（register 只写入服务端的 GLOBAL_STATE）
        for (FeatureEntry entry : REGISTRY.values()) {
            CLIENT_GLOBAL.putIfAbsent(entry.id(), entry.defaultEnabled());
        }

        Path file = CONFIG_DIR.resolve("client_settings.json");
        if (!Files.exists(file)) {
            saveClientSettings();
            return;
        }
        boolean cleaned = false;
        try {
            String json = Files.readString(file);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            if (root.has("client_player_uuid") && !root.get("client_player_uuid").isJsonNull()) {
                try {
                    clientPlayerUuid = UUID.fromString(root.get("client_player_uuid").getAsString());
                } catch (IllegalArgumentException ignored) {}
            }

            JsonObject features = root.getAsJsonObject("features");
            if (features == null) return;

            // 先清理已经删除的功能残留
            List<String> staleKeys = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : features.entrySet()) {
                if (!REGISTRY.containsKey(entry.getKey())) {
                    staleKeys.add(entry.getKey());
                }
            }
            for (String stale : staleKeys) {
                features.remove(stale);
                cleaned = true;
                if (YouzaiworldCore.logToFile) {
                    LOGGER.info("清理客户端配置中已删除的实验性功能: {}", stale);
                }
            }

            for (Map.Entry<String, JsonElement> entry : features.entrySet()) {
                String id = entry.getKey();
                if (!REGISTRY.containsKey(id)) continue;
                // 服务端控制的功能不从客户端配置加载
                if (REGISTRY.get(id).serverSide()) continue;

                JsonObject obj = entry.getValue().getAsJsonObject();
                if (obj.has("global")) {
                    CLIENT_GLOBAL.put(id, obj.get("global").getAsBoolean());
                }
                if (obj.has("personal") && !obj.get("personal").isJsonNull()) {
                    CLIENT_PERSONAL.put(id, obj.get("personal").getAsBoolean());
                }
            }
            if (YouzaiworldCore.logToFile) {
                LOGGER.info("已从 {} 加载客户端配置", file);
            }

            if (cleaned) {
                // 有残留配置被清理，重新保存
                root.add("features", features);
                Files.writeString(file, GSON.toJson(root));
                if (YouzaiworldCore.logToFile) {
                    LOGGER.info("已清理客户端配置中的残留条目并重新保存");
                }
            }
        } catch (Exception e) {
            LOGGER.error("加载客户端配置失败: {}", e.getMessage());
        }
    }

    /** 保存客户端配置 */
    public static void saveClientSettings() {
        try {
            Files.createDirectories(CONFIG_DIR);
            JsonObject root = new JsonObject();
            root.addProperty("client_player_uuid", clientPlayerUuid != null ? clientPlayerUuid.toString() : null);

            JsonObject features = new JsonObject();
            for (FeatureEntry entry : REGISTRY.values()) {
                String id = entry.id();
                // 服务端控制的功能不存储到客户端
                if (entry.serverSide()) continue;
                JsonObject obj = new JsonObject();
                obj.addProperty("global", CLIENT_GLOBAL.getOrDefault(id, entry.defaultEnabled()));
                obj.addProperty("personal", CLIENT_PERSONAL.get(id)); // null if not set
                features.add(id, obj);
            }
            root.add("features", features);

            Path file = CONFIG_DIR.resolve("client_settings.json");
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存客户端配置失败: {}", e.getMessage());
        }
    }

    // ==================== 数据定义 ====================

    public record FeatureEntry(
            String id,
            String name,
            String provider,
            String providerUrl,
            String description,
            String source,
            String sourceUrl,
            boolean defaultEnabled,
            boolean serverSide
    ) {}
}
