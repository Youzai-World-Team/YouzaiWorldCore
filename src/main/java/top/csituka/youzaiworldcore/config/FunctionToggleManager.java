package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单玩家功能开关管理器。
 * <p>
 * 这是<b>玩家个人配置</b>，存放于
 * {@code yzwc/server/config/user_settings/<玩家UUID>.json} 的
 * {@code function_module} 分节：
 * </p>
 *
 * <pre>
 * {
 *   "function_module": {
 *     "ladder_extend_downward": true,
 *     "damage_numbers": false
 *   }
 * }
 * </pre>
 *
 * <p>
 * 未写过的键默认全部开启（{@code true}）。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class FunctionToggleManager {

    public static final String MODULE = "FunctionToggleManager";

    public static final String KEY_LADDER = "ladder_extend_downward";
    public static final String KEY_CROP_XP = "crop_xp_drop";
    public static final String KEY_TOOL_INFO = "tool_info_overlay";
    public static final String KEY_BLOCK_ANIM = "block_animation";
    public static final String KEY_CRAFT_SOUND = "crafting_sound";
    public static final String KEY_ITEM_SPARKLE = "item_sparkle";
    public static final String KEY_DAMAGE_NUMBERS = "damage_numbers";

    /** 全部功能键，用于生成默认的个人配置分节（新增开关时记得加进来） */
    public static final List<String> ALL_KEYS = List.of(
            KEY_LADDER, KEY_CROP_XP, KEY_TOOL_INFO, KEY_BLOCK_ANIM,
            KEY_CRAFT_SOUND, KEY_ITEM_SPARKLE, KEY_DAMAGE_NUMBERS);

    private FunctionToggleManager() {
    }

    /**
     * 查询玩家某项功能的开关状态。未设置时默认 true。
     */
    public static boolean isEnabled(UUID uuid, String key) {
        if (uuid == null) {
            return true;
        }
        return UserSettings.section(uuid, GlobalSettings.FUNCTION_MODULE).getBoolean(key, true);
    }

    /**
     * 设置玩家某项功能的开关状态并持久化到该玩家的个人配置文件。
     */
    public static void setEnabled(UUID uuid, String key, boolean value) {
        if (uuid == null) {
            return;
        }
        UserSettings.section(uuid, GlobalSettings.FUNCTION_MODULE).set(key, value);
        UserSettings.save(uuid);
        DebugLogger.info(MODULE, "玩家 %s 功能 %s = %s", uuid, key, value);
    }

    /**
     * 取某玩家已显式设置过的全部功能状态，用于网络同步。
     * <p>未显式设置过的键不在返回值中，客户端按默认 true 处理。</p>
     */
    public static Map<String, Boolean> getAllForPlayer(UUID uuid) {
        if (uuid == null) {
            return Map.of();
        }
        ConfigSection section = UserSettings.section(uuid, GlobalSettings.FUNCTION_MODULE);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String key : section.keys()) {
            result.put(key, section.getBoolean(key, true));
        }
        return result;
    }
}
