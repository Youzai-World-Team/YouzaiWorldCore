package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

/**
 * 双开门（Double Doors）玩家状态管理。
 * <p>
 * 功能已精简为：仅「同材质木门 / 栅栏门」的点击双开，按玩家独立开关。
 * 每个玩家的启用状态由指令 {@code /yzwc function double_doors [true|false]}
 * 控制（缺省为查询自身状态）。
 * </p>
 * <p>
 * 这是<b>玩家个人配置</b>，存放于
 * {@code yzwc/server/config/user_settings/<玩家UUID>.json} 的
 * {@code double_doors_module} 分节：
 * </p>
 *
 * <pre>
 * { "double_doors_module": { "enabled": true } }
 * </pre>
 *
 * <p>
 * 未写过该键的玩家使用 {@link #DEFAULT_ENABLED} 默认值。
 * </p>
 */
@SuppressWarnings({"null", "unused"})
public final class DoubleDoorsState {

    public static final String MODULE = "DoubleDoorsState";

    /** 该玩家是否启用双开门 */
    private static final String KEY_ENABLED = "enabled";

    /** 默认状态：新玩家默认开启双开门（与原全局默认一致） */
    private static final boolean DEFAULT_ENABLED = true;

    private DoubleDoorsState() {
    }

    /**
     * 判断某玩家是否启用双开门。
     * 未显式设置过的玩家返回 {@link #DEFAULT_ENABLED}。
     */
    public static boolean isEnabled(UUID playerUuid) {
        if (playerUuid == null) {
            return DEFAULT_ENABLED;
        }
        return UserSettings.section(playerUuid, GlobalSettings.DOUBLE_DOORS_MODULE)
                .getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }

    /**
     * 判断某玩家的双开门状态是否偏离默认值。
     * <p>
     * 用于查询时区分「默认启用」与「玩家自己改过」。个人配置文件在建档时就会写入
     * {@link #DEFAULT_ENABLED}，因此这里比的是<b>取值是否与默认不同</b>，
     * 而不是键存不存在。
     * </p>
     */
    public static boolean isExplicitlySet(UUID playerUuid) {
        return playerUuid != null && isEnabled(playerUuid) != DEFAULT_ENABLED;
    }

    /** 设置某玩家的双开门启用状态并立即持久化到该玩家的个人配置文件 */
    public static void setEnabled(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) {
            return;
        }
        UserSettings.section(playerUuid, GlobalSettings.DOUBLE_DOORS_MODULE).set(KEY_ENABLED, enabled);
        UserSettings.save(playerUuid);
        DebugLogger.info(MODULE, "玩家 %s 双开门状态已设置为 %s", playerUuid, enabled);
    }
}
