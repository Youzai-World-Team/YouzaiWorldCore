package top.csituka.youzaiworldcore.feature;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static final Logger LOGGER = LoggerFactory.getLogger("ExperimentalFeatures");

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

    public static void register(
            String id, String name, String provider, String providerUrl,
            String description, String source, String sourceUrl,
            boolean defaultEnabled
    ) {
        if (REGISTRY.containsKey(id)) {
            LOGGER.warn("实验性功能 '{}' 重复注册", id);
            return;
        }
        REGISTRY.put(id, new FeatureEntry(
                id, name, provider, providerUrl, description, source, sourceUrl, defaultEnabled
        ));
        GLOBAL_STATE.putIfAbsent(id, defaultEnabled);
        LOGGER.info("注册实验性功能: {} ({})，默认: {}", name, id, defaultEnabled);
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
        LOGGER.info("实验性功能 '{}' 全局状态: {}", id, enabled);
        saveServerSettings();
        return true;
    }

    public static boolean setForPlayer(String id, UUID playerUuid, boolean enabled) {
        if (!REGISTRY.containsKey(id)) return false;
        PLAYER_STATE.computeIfAbsent(id, k -> new HashMap<>()).put(playerUuid, enabled);
        LOGGER.info("实验性功能 '{}' 玩家 {} 覆写: {}", id, playerUuid, enabled);
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

    public static void applyGlobalSync(String id, boolean enabled) {
        CLIENT_GLOBAL.put(id, enabled);
        CLIENT_PERSONAL.remove(id);
        saveClientSettings();
    }

    public static void applyPersonalSync(UUID targetPlayer, String id, boolean enabled) {
        if (clientPlayerUuid != null && clientPlayerUuid.equals(targetPlayer)) {
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

    /** 加载服务端配置（不存在则创建默认） */
    public static void loadServerSettings() {
        Path file = CONFIG_DIR.resolve("server_settings.json");
        if (!Files.exists(file)) {
            saveServerSettings();
            return;
        }
        try {
            String json = Files.readString(file);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            JsonObject features = root.getAsJsonObject("features");
            if (features == null) return;

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
            LOGGER.info("已从 {} 加载服务端配置", file);
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

    /** 加载客户端配置 */
    public static void loadClientSettings() {
        Path file = CONFIG_DIR.resolve("client_settings.json");
        if (!Files.exists(file)) {
            saveClientSettings();
            return;
        }
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

            for (Map.Entry<String, JsonElement> entry : features.entrySet()) {
                String id = entry.getKey();
                if (!REGISTRY.containsKey(id)) continue;

                JsonObject obj = entry.getValue().getAsJsonObject();
                if (obj.has("global")) {
                    CLIENT_GLOBAL.put(id, obj.get("global").getAsBoolean());
                }
                if (obj.has("personal") && !obj.get("personal").isJsonNull()) {
                    CLIENT_PERSONAL.put(id, obj.get("personal").getAsBoolean());
                }
            }
            LOGGER.info("已从 {} 加载客户端配置", file);
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
            boolean defaultEnabled
    ) {}
}
