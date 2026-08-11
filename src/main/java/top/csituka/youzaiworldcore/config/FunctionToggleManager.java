package top.csituka.youzaiworldcore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单玩家功能开关管理器。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/function_toggles.json}
 * 格式：{@code {"<UUID>": {"ladder_extend_downward": true, ...}}}
 * 默认全部开启。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class FunctionToggleManager {

    public static final String MODULE = "FunctionToggleManager";
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/FunctionToggle");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("function_toggles.json");

    private static final Type MAP_TYPE = new TypeToken<Map<String, Map<String, Boolean>>>() {}.getType();

    /** UUID字符串 → (功能键 → 开关状态) */
    private static final ConcurrentHashMap<String, Map<String, Boolean>> PLAYER_TOGGLES = new ConcurrentHashMap<>();

    public static final String KEY_LADDER = "ladder_extend_downward";
    public static final String KEY_CROP_XP = "crop_xp_drop";
    public static final String KEY_TOOL_INFO = "tool_info_overlay";
    public static final String KEY_BLOCK_ANIM = "block_animation";
    public static final String KEY_CRAFT_SOUND = "crafting_sound";
    public static final String KEY_ITEM_SPARKLE = "item_sparkle";
    public static final String KEY_DAMAGE_NUMBERS = "damage_numbers";

    private FunctionToggleManager() {
    }

    /**
     * 查询玩家某项功能的开关状态。未设置时默认 true。
     */
    public static boolean isEnabled(UUID uuid, String key) {
        var map = PLAYER_TOGGLES.get(uuid.toString());
        if (map == null) return true;
        return map.getOrDefault(key, true);
    }

    /**
     * 设置玩家某项功能的开关状态并持久化。
     */
    public static void setEnabled(UUID uuid, String key, boolean value) {
        PLAYER_TOGGLES.computeIfAbsent(uuid.toString(), k -> new ConcurrentHashMap<>()).put(key, value);
        save();
        DebugLogger.info(MODULE, "玩家 %s 功能 %s = %s", uuid, key, value);
    }

    /**
     * 将所有玩家的功能状态序列化为 Map，用于网络同步。
     */
    public static Map<String, Boolean> getAllForPlayer(UUID uuid) {
        return PLAYER_TOGGLES.getOrDefault(uuid.toString(), Map.of());
    }

    public static void load() {
        DebugLogger.entering(MODULE, "load");
        if (!Files.exists(CONFIG_FILE)) {
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            Map<String, Map<String, Boolean>> loaded = GSON.fromJson(json, MAP_TYPE);
            if (loaded != null) {
                PLAYER_TOGGLES.clear();
                PLAYER_TOGGLES.putAll(loaded);
            }
            DebugLogger.info(MODULE, "已加载 %d 个玩家的功能开关配置", PLAYER_TOGGLES.size());
        } catch (Exception e) {
            LOGGER.error("加载功能开关配置失败: {}", e.getMessage());
        }
        DebugLogger.exiting(MODULE, "load");
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            Files.writeString(CONFIG_FILE, GSON.toJson(PLAYER_TOGGLES));
        } catch (IOException e) {
            LOGGER.error("保存功能开关配置失败: {}", e.getMessage());
        }
    }
}
