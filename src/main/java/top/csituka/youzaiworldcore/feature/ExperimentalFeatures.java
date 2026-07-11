package top.csituka.youzaiworldcore.feature;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

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
@SuppressWarnings({"null", "unused"})
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
        DebugLogger.entering("ExperimentalFeatures", "register",
                "id=" + id + ", name=" + name + ", defaultEnabled=" + defaultEnabled);
        register(id, name, provider, providerUrl, description, source, sourceUrl,
                defaultEnabled, false);
        DebugLogger.exiting("ExperimentalFeatures", "register");
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
        DebugLogger.entering("ExperimentalFeatures", "register",
                "id=" + id + ", name=" + name + ", defaultEnabled=" + defaultEnabled + ", serverSide=" + serverSide);
        if (REGISTRY.containsKey(id)) {
            DebugLogger.branch("ExperimentalFeatures", "功能重复注册: " + id, true);
            LOGGER.warn("实验性功能 '{}' 重复注册", id);
            DebugLogger.exiting("ExperimentalFeatures", "register");
            return;
        }
        DebugLogger.branch("ExperimentalFeatures", "功能首次注册: " + id, false);
        REGISTRY.put(id, new FeatureEntry(
                id, name, provider, providerUrl, description, source, sourceUrl,
                defaultEnabled, serverSide
        ));
        GLOBAL_STATE.putIfAbsent(id, defaultEnabled);
        DebugLogger.info("ExperimentalFeatures", "注册实验性功能: %s (%s)，默认: %s，服务端控制: %s",
                name, id, defaultEnabled, serverSide);
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("注册实验性功能: {} ({})，默认: {}，服务端控制: {}",
                    name, id, defaultEnabled, serverSide);
        }
        DebugLogger.exiting("ExperimentalFeatures", "register");
    }

    public static void loadDefaults() {
        DebugLogger.entering("ExperimentalFeatures", "loadDefaults");
        for (FeatureEntry entry : REGISTRY.values()) {
            GLOBAL_STATE.putIfAbsent(entry.id(), entry.defaultEnabled());
        }
        DebugLogger.exiting("ExperimentalFeatures", "loadDefaults");
    }

    public static FeatureEntry getEntry(String id) {
        DebugLogger.entering("ExperimentalFeatures", "getEntry", "id=" + id);
        FeatureEntry result = REGISTRY.get(id);
        DebugLogger.exiting("ExperimentalFeatures", "getEntry", result != null ? result.id() : "null");
        return result;
    }

    public static Map<String, FeatureEntry> getAllEntries() {
        DebugLogger.entering("ExperimentalFeatures", "getAllEntries");
        Map<String, FeatureEntry> result = Collections.unmodifiableMap(REGISTRY);
        DebugLogger.exiting("ExperimentalFeatures", "getAllEntries", "size=" + result.size());
        return result;
    }

    // ==================== 状态查询（服务端）====================

    public static boolean isEnabled(String id, UUID playerUuid) {
        DebugLogger.entering("ExperimentalFeatures", "isEnabled",
                "id=" + id + ", playerUuid=" + playerUuid);
        if (!REGISTRY.containsKey(id)) {
            DebugLogger.branch("ExperimentalFeatures", "功能未注册: " + id, true);
            DebugLogger.exiting("ExperimentalFeatures", "isEnabled", "false (未注册)");
            return false;
        }
        FeatureEntry entry = REGISTRY.get(id);
        // 服务端控制的功能：跳过玩家覆写，只看全局
        if (entry.serverSide()) {
            DebugLogger.branch("ExperimentalFeatures", "功能为服务端控制，跳过玩家覆写", true, id);
            boolean result = GLOBAL_STATE.getOrDefault(id, false);
            DebugLogger.exiting("ExperimentalFeatures", "isEnabled", String.valueOf(result));
            return result;
        }
        DebugLogger.branch("ExperimentalFeatures", "功能非服务端控制，检查玩家覆写", false);
        Map<UUID, Boolean> playerOverrides = PLAYER_STATE.get(id);
        if (playerOverrides != null) {
            DebugLogger.branch("ExperimentalFeatures", "玩家覆写数据存在，检查具体值", true);
            Boolean playerVal = playerOverrides.get(playerUuid);
            if (playerVal != null) {
                DebugLogger.branch("ExperimentalFeatures", "玩家覆写值存在", true, "value=" + playerVal);
                DebugLogger.exiting("ExperimentalFeatures", "isEnabled", String.valueOf(playerVal));
                return playerVal;
            }
            DebugLogger.branch("ExperimentalFeatures", "玩家覆写值不存在，使用全局状态", false);
        } else {
            DebugLogger.branch("ExperimentalFeatures", "玩家覆写数据不存在，使用全局状态", false);
        }
        boolean result = GLOBAL_STATE.getOrDefault(id, false);
        DebugLogger.exiting("ExperimentalFeatures", "isEnabled", String.valueOf(result));
        return result;
    }

    public static boolean isGlobalEnabled(String id) {
        DebugLogger.entering("ExperimentalFeatures", "isGlobalEnabled", "id=" + id);
        boolean result = GLOBAL_STATE.getOrDefault(id, false);
        DebugLogger.exiting("ExperimentalFeatures", "isGlobalEnabled", String.valueOf(result));
        return result;
    }

    // ==================== 状态设置（服务端）====================

    public static boolean setGlobal(String id, boolean enabled) {
        DebugLogger.entering("ExperimentalFeatures", "setGlobal", "id=" + id + ", enabled=" + enabled);
        if (!REGISTRY.containsKey(id)) {
            DebugLogger.branch("ExperimentalFeatures", "功能未注册，无法设置全局状态", true, id);
            DebugLogger.exiting("ExperimentalFeatures", "setGlobal", "false");
            return false;
        }
        DebugLogger.branch("ExperimentalFeatures", "功能已注册，设置全局状态", false);
        GLOBAL_STATE.put(id, enabled);
        PLAYER_STATE.remove(id);
        DebugLogger.stateChange("ExperimentalFeatures", id, "global", enabled);
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("实验性功能 '{}' 全局状态: {}", id, enabled);
        }
        saveServerSettings();
        DebugLogger.exiting("ExperimentalFeatures", "setGlobal", "true");
        return true;
    }

    public static boolean setForPlayer(String id, UUID playerUuid, boolean enabled) {
        DebugLogger.entering("ExperimentalFeatures", "setForPlayer",
                "id=" + id + ", playerUuid=" + playerUuid + ", enabled=" + enabled);
        if (!REGISTRY.containsKey(id)) {
            DebugLogger.branch("ExperimentalFeatures", "功能未注册，无法设置玩家覆写", true, id);
            DebugLogger.exiting("ExperimentalFeatures", "setForPlayer", "false");
            return false;
        }
        DebugLogger.branch("ExperimentalFeatures", "功能已注册", false);
        // 服务端控制的功能不允许玩家覆写
        if (REGISTRY.get(id).serverSide()) {
            DebugLogger.branch("ExperimentalFeatures", "服务端控制功能不允许玩家覆写", true, id);
            DebugLogger.exiting("ExperimentalFeatures", "setForPlayer", "false");
            return false;
        }
        DebugLogger.branch("ExperimentalFeatures", "非服务端控制功能，允许玩家覆写", false);
        PLAYER_STATE.computeIfAbsent(id, k -> new HashMap<>()).put(playerUuid, enabled);
        DebugLogger.stateChange("ExperimentalFeatures", id, "playerOverride(" + playerUuid + ")", enabled);
        if (YouzaiworldCore.logToFile) {
            LOGGER.info("实验性功能 '{}' 玩家 {} 覆写: {}", id, playerUuid, enabled);
        }
        saveServerSettings();
        DebugLogger.exiting("ExperimentalFeatures", "setForPlayer", "true");
        return true;
    }

    public static void clearPlayerOverride(String id, UUID playerUuid) {
        DebugLogger.entering("ExperimentalFeatures", "clearPlayerOverride",
                "id=" + id + ", playerUuid=" + playerUuid);
        Map<UUID, Boolean> overrides = PLAYER_STATE.get(id);
        if (overrides != null) {
            overrides.remove(playerUuid);
        }
        DebugLogger.exiting("ExperimentalFeatures", "clearPlayerOverride");
    }

    // ==================== 客户端 API ====================

    public static void setClientPlayerUuid(UUID uuid) {
        DebugLogger.entering("ExperimentalFeatures", "setClientPlayerUuid", "uuid=" + uuid);
        clientPlayerUuid = uuid;
        DebugLogger.exiting("ExperimentalFeatures", "setClientPlayerUuid");
    }

    public static UUID getClientPlayerUuid() {
        DebugLogger.entering("ExperimentalFeatures", "getClientPlayerUuid");
        UUID result = clientPlayerUuid;
        DebugLogger.exiting("ExperimentalFeatures", "getClientPlayerUuid", String.valueOf(result));
        return result;
    }

    public static boolean isEnabled(String id) {
        DebugLogger.entering("ExperimentalFeatures", "isEnabled", "id=" + id);
        Boolean personal = CLIENT_PERSONAL.get(id);
        if (personal != null) {
            DebugLogger.branch("ExperimentalFeatures", "客户端玩家覆写存在", true, "value=" + personal);
            DebugLogger.exiting("ExperimentalFeatures", "isEnabled", String.valueOf(personal));
            return personal;
        }
        DebugLogger.branch("ExperimentalFeatures", "客户端玩家覆写不存在，使用客户端全局值", false);
        boolean result = CLIENT_GLOBAL.getOrDefault(id, false);
        DebugLogger.exiting("ExperimentalFeatures", "isEnabled", String.valueOf(result));
        return result;
    }

    /**
     * 仅检查客户端的全局状态（忽略玩家个人覆写）。
     * <p>
     * 用于需要严格由服务端控制的场景，防止玩家通过自切换覆写全局开关。
     */
    public static boolean isGlobalOnClient(String id) {
        DebugLogger.entering("ExperimentalFeatures", "isGlobalOnClient", "id=" + id);
        boolean result = CLIENT_GLOBAL.getOrDefault(id, false);
        DebugLogger.exiting("ExperimentalFeatures", "isGlobalOnClient", String.valueOf(result));
        return result;
    }

    public static void applyGlobalSync(String id, boolean enabled) {
        DebugLogger.entering("ExperimentalFeatures", "applyGlobalSync", "id=" + id + ", enabled=" + enabled);
        boolean oldGlobal = CLIENT_GLOBAL.getOrDefault(id, false);
        CLIENT_GLOBAL.put(id, enabled);
        CLIENT_PERSONAL.remove(id);
        DebugLogger.stateChange("ExperimentalFeatures", id, "clientGlobal", oldGlobal, enabled);
        saveClientSettings();
        DebugLogger.exiting("ExperimentalFeatures", "applyGlobalSync");
    }

    public static void applyPersonalSync(UUID targetPlayer, String id, boolean enabled) {
        DebugLogger.entering("ExperimentalFeatures", "applyPersonalSync",
                "targetPlayer=" + targetPlayer + ", id=" + id + ", enabled=" + enabled);
        if (clientPlayerUuid != null && clientPlayerUuid.equals(targetPlayer)) {
            // 服务端控制的功能不接受个人同步
            FeatureEntry entry = REGISTRY.get(id);
            if (entry != null && entry.serverSide()) {
                DebugLogger.branch("ExperimentalFeatures", "服务端控制功能不接受个人同步", true, id);
                DebugLogger.exiting("ExperimentalFeatures", "applyPersonalSync");
                return;
            }
            DebugLogger.branch("ExperimentalFeatures", "接受个人同步", false);
            boolean oldPersonal = CLIENT_PERSONAL.getOrDefault(id, false);
            CLIENT_PERSONAL.put(id, enabled);
            DebugLogger.stateChange("ExperimentalFeatures", id, "clientPersonal", oldPersonal, enabled);
            saveClientSettings();
        }
        DebugLogger.exiting("ExperimentalFeatures", "applyPersonalSync");
    }

    public static void resetClientState() {
        DebugLogger.entering("ExperimentalFeatures", "resetClientState");
        clientPlayerUuid = null;
        CLIENT_GLOBAL.clear();
        CLIENT_PERSONAL.clear();
        DebugLogger.stateChange("ExperimentalFeatures", "client", "state", "reset");
        DebugLogger.exiting("ExperimentalFeatures", "resetClientState");
    }

    // ==================== 服务端配置持久化 ====================

    /** 加载服务端配置（不存在则创建默认；自动清理已删除功能的残留配置） */
    public static void loadServerSettings() {
        DebugLogger.entering("ExperimentalFeatures", "loadServerSettings");
        Path file = CONFIG_DIR.resolve("server_settings.json");
        if (!Files.exists(file)) {
            DebugLogger.branch("ExperimentalFeatures", "服务端配置文件不存在，创建默认配置", true);
            saveServerSettings();
            DebugLogger.exiting("ExperimentalFeatures", "loadServerSettings");
            return;
        }
        DebugLogger.branch("ExperimentalFeatures", "服务端配置文件存在，开始加载", false);
        boolean cleaned = false;
        try {
            String json = Files.readString(file);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                DebugLogger.exiting("ExperimentalFeatures", "loadServerSettings");
                return;
            }

            JsonObject features = root.getAsJsonObject("features");
            if (features == null) {
                DebugLogger.exiting("ExperimentalFeatures", "loadServerSettings");
                return;
            }

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
                DebugLogger.branch("ExperimentalFeatures", "发现已删除功能的残留配置，清理", true, stale);
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
            DebugLogger.exception("ExperimentalFeatures", "loadServerSettings", e);
            LOGGER.error("加载服务端配置失败: {}", e.getMessage());
        }
        DebugLogger.exiting("ExperimentalFeatures", "loadServerSettings");
    }

    /** 保存服务端配置 */
    public static void saveServerSettings() {
        DebugLogger.entering("ExperimentalFeatures", "saveServerSettings");
        if (suppressSave) {
            DebugLogger.branch("ExperimentalFeatures", "suppressSave 为 true，跳过保存", true);
            DebugLogger.exiting("ExperimentalFeatures", "saveServerSettings");
            return;
        }
        DebugLogger.branch("ExperimentalFeatures", "正常保存服务端配置", false);
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
            DebugLogger.exception("ExperimentalFeatures", "saveServerSettings", e);
            LOGGER.error("保存服务端配置失败: {}", e.getMessage());
        }
        DebugLogger.exiting("ExperimentalFeatures", "saveServerSettings");
    }

    // ==================== 客户端配置持久化 ====================

    /** 加载客户端配置（自动清理已删除功能的残留配置） */
    public static void loadClientSettings() {
        DebugLogger.entering("ExperimentalFeatures", "loadClientSettings");
        // 先将 REGISTRY 中的默认值填入 CLIENT_GLOBAL（register 只写入服务端的 GLOBAL_STATE）
        for (FeatureEntry entry : REGISTRY.values()) {
            CLIENT_GLOBAL.putIfAbsent(entry.id(), entry.defaultEnabled());
        }

        Path file = CONFIG_DIR.resolve("client_settings.json");
        if (!Files.exists(file)) {
            DebugLogger.branch("ExperimentalFeatures", "客户端配置文件不存在，创建默认配置", true);
            saveClientSettings();
            DebugLogger.exiting("ExperimentalFeatures", "loadClientSettings");
            return;
        }
        DebugLogger.branch("ExperimentalFeatures", "客户端配置文件存在，开始加载", false);
        boolean cleaned = false;
        try {
            String json = Files.readString(file);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                DebugLogger.exiting("ExperimentalFeatures", "loadClientSettings");
                return;
            }

            if (root.has("client_player_uuid") && !root.get("client_player_uuid").isJsonNull()) {
                try {
                    clientPlayerUuid = UUID.fromString(root.get("client_player_uuid").getAsString());
                } catch (IllegalArgumentException ignored) {}
            }

            JsonObject features = root.getAsJsonObject("features");
            if (features == null) {
                DebugLogger.exiting("ExperimentalFeatures", "loadClientSettings");
                return;
            }

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
                DebugLogger.branch("ExperimentalFeatures", "发现已删除功能的残留配置，清理", true, stale);
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
            DebugLogger.exception("ExperimentalFeatures", "loadClientSettings", e);
            LOGGER.error("加载客户端配置失败: {}", e.getMessage());
        }
        DebugLogger.exiting("ExperimentalFeatures", "loadClientSettings");
    }

    /** 保存客户端配置 */
    public static void saveClientSettings() {
        DebugLogger.entering("ExperimentalFeatures", "saveClientSettings");
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
            DebugLogger.exception("ExperimentalFeatures", "saveClientSettings", e);
            LOGGER.error("保存客户端配置失败: {}", e.getMessage());
        }
        DebugLogger.exiting("ExperimentalFeatures", "saveClientSettings");
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
