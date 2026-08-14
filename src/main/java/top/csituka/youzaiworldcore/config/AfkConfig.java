package top.csituka.youzaiworldcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * AFK（挂机）功能配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code afk_module} 分节。
 * <p>
 * 由 {@code /yzwc afk settings <key> <value>} 命令在运行时修改并持久化。
 * 检测架构：客户端输入检测（精确，需客户端装模组）+ 服务端近似检测（位置 /
 * 视角变化，兜底原版客户端），由 {@link #detectMode} 控制。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class AfkConfig {

    public static final String MODULE = "AfkConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/AfkConfig");

    /** AFK 检测模式 */
    public enum DetectMode {
        /** 仅客户端心跳检测（原版客户端玩家永不判定 AFK） */
        CLIENT,
        /** 仅服务端近似检测（位置/视角变化） */
        SERVER,
        /** 双通道：任一通道判定活动即不算 AFK（默认） */
        BOTH
    }

    /** 触发阈值下限（秒）：至少 30 秒 */
    public static final int MIN_THRESHOLD_SECONDS = 30;

    /** 默认值：启用 AFK 检测 */
    private static final boolean DEFAULT_ENABLED = true;
    /** 默认值：双通道检测 */
    private static final DetectMode DEFAULT_DETECT_MODE = DetectMode.BOTH;
    /** 默认值：无活动 300 秒判定 AFK */
    private static final int DEFAULT_THRESHOLD_SECONDS = 300;
    /** 默认值：显示 Tab 前缀 */
    private static final boolean DEFAULT_TAB_PREFIX_ENABLED = true;
    /** 默认值：进入 / 退出 AFK 广播 */
    private static final boolean DEFAULT_BROADCAST_ENABLED = true;
    /** 默认值：AFK 期间不无敌 */
    private static final boolean DEFAULT_INVULNERABLE_ENABLED = false;
    /** 默认值：不自动踢出 */
    private static final int DEFAULT_AUTO_KICK_SECONDS = 0;
    /** 默认值：允许玩家手动切换 */
    private static final boolean DEFAULT_MANUAL_TOGGLE_ENABLED = true;

    /** 功能总开关，默认 true */
    private static boolean enabled = DEFAULT_ENABLED;
    /** 检测模式，默认 BOTH */
    private static DetectMode detectMode = DEFAULT_DETECT_MODE;
    /** 触发 AFK 的无活动时长（秒），默认 300，下限 {@link #MIN_THRESHOLD_SECONDS} */
    private static int thresholdSeconds = DEFAULT_THRESHOLD_SECONDS;
    /** 是否在 Tab 列表显示 [AFK] 前缀，默认 true */
    private static boolean tabPrefixEnabled = DEFAULT_TAB_PREFIX_ENABLED;
    /** 进入/退出 AFK 是否向全体广播，默认 true */
    private static boolean broadcastEnabled = DEFAULT_BROADCAST_ENABLED;
    /** AFK 期间是否无敌（无限时长的抗性提升 V），默认 false */
    private static boolean invulnerableEnabled = DEFAULT_INVULNERABLE_ENABLED;
    /** 超过该时长（秒）自动踢出，0 = 禁用（默认），须 >= 触发阈值 */
    private static int autoKickSeconds = DEFAULT_AUTO_KICK_SECONDS;
    /** 是否允许玩家用 /yzwc afk 手动切换，默认 true */
    private static boolean manualToggleEnabled = DEFAULT_MANUAL_TOGGLE_ENABLED;

    private AfkConfig() {
    }

    // ===== 读取 =====

    /** @return 功能是否启用（由 {@code /yzwc afk settings enabled} 控制） */
    public static boolean isEnabled() {
        return enabled;
    }

    /** @return 当前检测模式 */
    public static DetectMode getDetectMode() {
        return detectMode;
    }

    /** @return 触发 AFK 的无活动时长（秒） */
    public static int getThresholdSeconds() {
        return thresholdSeconds;
    }

    /** @return 是否在 Tab 列表显示 [AFK] 前缀 */
    public static boolean isTabPrefixEnabled() {
        return tabPrefixEnabled;
    }

    /** @return 进入/退出 AFK 是否广播 */
    public static boolean isBroadcastEnabled() {
        return broadcastEnabled;
    }

    /** @return AFK 期间是否无敌 */
    public static boolean isInvulnerableEnabled() {
        return invulnerableEnabled;
    }

    /** @return 自动踢出时长（秒），0 = 禁用 */
    public static int getAutoKickSeconds() {
        return autoKickSeconds;
    }

    /** @return 是否允许 /yzwc afk 手动切换 */
    public static boolean isManualToggleEnabled() {
        return manualToggleEnabled;
    }

    // ===== 运行时修改（供 /yzwc afk settings 命令调用）=====

    /** 设置功能总开关并持久化（disabled 时立即清除全部 AFK 状态） */
    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.info(MODULE, "enabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "enabled", enabled, value);
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
    }

    /** 设置检测模式并持久化 */
    public static void setDetectMode(DetectMode mode) {
        DebugLogger.entering(MODULE, "setDetectMode", "mode=" + mode);
        if (mode == null || detectMode == mode) {
            DebugLogger.info(MODULE, "detectMode 未变化或非法，跳过保存");
            DebugLogger.exiting(MODULE, "setDetectMode", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "detectMode", detectMode, mode);
        detectMode = mode;
        save();
        DebugLogger.exiting(MODULE, "setDetectMode", "1");
    }

    /** 设置触发阈值（秒，至少 {@link #MIN_THRESHOLD_SECONDS}）并持久化 */
    public static void setThresholdSeconds(int seconds) {
        DebugLogger.entering(MODULE, "setThresholdSeconds", "seconds=" + seconds);
        if (seconds < MIN_THRESHOLD_SECONDS) {
            DebugLogger.warn(MODULE, "触发阈值非法: %d（必须 >= %d），忽略", seconds, MIN_THRESHOLD_SECONDS);
            DebugLogger.exiting(MODULE, "setThresholdSeconds", "invalid");
            return;
        }
        if (thresholdSeconds == seconds) {
            DebugLogger.info(MODULE, "thresholdSeconds 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setThresholdSeconds", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "thresholdSeconds", thresholdSeconds, seconds);
        thresholdSeconds = seconds;
        // 自动踢出时长若小于新阈值则同步抬升（踢出必须晚于判定 AFK）
        if (autoKickSeconds > 0 && autoKickSeconds < thresholdSeconds) {
            DebugLogger.stateChange(MODULE, "AfkConfig", "autoKickSeconds", autoKickSeconds, thresholdSeconds);
            autoKickSeconds = thresholdSeconds;
        }
        save();
        DebugLogger.exiting(MODULE, "setThresholdSeconds", "1");
    }

    /** 设置 Tab 前缀开关并持久化 */
    public static void setTabPrefixEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setTabPrefixEnabled", "value=" + value);
        if (tabPrefixEnabled == value) {
            DebugLogger.info(MODULE, "tabPrefixEnabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setTabPrefixEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "tabPrefixEnabled", tabPrefixEnabled, value);
        tabPrefixEnabled = value;
        save();
        DebugLogger.exiting(MODULE, "setTabPrefixEnabled", "1");
    }

    /** 设置广播开关并持久化 */
    public static void setBroadcastEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setBroadcastEnabled", "value=" + value);
        if (broadcastEnabled == value) {
            DebugLogger.info(MODULE, "broadcastEnabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setBroadcastEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "broadcastEnabled", broadcastEnabled, value);
        broadcastEnabled = value;
        save();
        DebugLogger.exiting(MODULE, "setBroadcastEnabled", "1");
    }

    /** 设置无敌开关并持久化 */
    public static void setInvulnerableEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setInvulnerableEnabled", "value=" + value);
        if (invulnerableEnabled == value) {
            DebugLogger.info(MODULE, "invulnerableEnabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setInvulnerableEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "invulnerableEnabled", invulnerableEnabled, value);
        invulnerableEnabled = value;
        save();
        DebugLogger.exiting(MODULE, "setInvulnerableEnabled", "1");
    }

    /** 设置自动踢出时长（秒，0 = 禁用，须 >= 触发阈值）并持久化 */
    public static void setAutoKickSeconds(int seconds) {
        DebugLogger.entering(MODULE, "setAutoKickSeconds", "seconds=" + seconds);
        if (seconds < 0 || (seconds > 0 && seconds < thresholdSeconds)) {
            DebugLogger.warn(MODULE, "自动踢出时长非法: %d（必须 0 或 >= 阈值 %d），忽略", seconds, thresholdSeconds);
            DebugLogger.exiting(MODULE, "setAutoKickSeconds", "invalid");
            return;
        }
        if (autoKickSeconds == seconds) {
            DebugLogger.info(MODULE, "autoKickSeconds 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setAutoKickSeconds", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "autoKickSeconds", autoKickSeconds, seconds);
        autoKickSeconds = seconds;
        save();
        DebugLogger.exiting(MODULE, "setAutoKickSeconds", "1");
    }

    /** 设置手动切换开关并持久化 */
    public static void setManualToggleEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setManualToggleEnabled", "value=" + value);
        if (manualToggleEnabled == value) {
            DebugLogger.info(MODULE, "manualToggleEnabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setManualToggleEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "manualToggleEnabled", manualToggleEnabled, value);
        manualToggleEnabled = value;
        save();
        DebugLogger.exiting(MODULE, "setManualToggleEnabled", "1");
    }

    // ===== 持久化 =====

    /** 从全局配置的 {@code afk_module} 分节加载（分节缺失则写入默认配置） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        ConfigSection section = GlobalSettings.section(GlobalSettings.AFK_MODULE);
        if (section.isEmpty()) {
            DebugLogger.info(MODULE, "afk_module 分节不存在，写入默认配置 (enabled=%s, mode=%s, threshold=%ds)",
                    enabled, detectMode, thresholdSeconds);
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        enabled = section.getBoolean("enabled", enabled);
        detectMode = section.getEnum("detect_mode", detectMode, DetectMode.class);
        thresholdSeconds = section.getInt("threshold_seconds", thresholdSeconds,
                MIN_THRESHOLD_SECONDS, Integer.MAX_VALUE);
        tabPrefixEnabled = section.getBoolean("tab_prefix_enabled", tabPrefixEnabled);
        broadcastEnabled = section.getBoolean("broadcast_enabled", broadcastEnabled);
        invulnerableEnabled = section.getBoolean("invulnerable_enabled", invulnerableEnabled);
        autoKickSeconds = section.getInt("auto_kick_seconds", autoKickSeconds, 0, Integer.MAX_VALUE);
        if (autoKickSeconds > 0 && autoKickSeconds < thresholdSeconds) {
            section.fail("auto_kick_seconds",
                    "自动踢出时长 " + autoKickSeconds + " 秒必须为 0（禁用）或不小于触发阈值 "
                            + thresholdSeconds + " 秒");
        }
        manualToggleEnabled = section.getBoolean("manual_toggle_enabled", manualToggleEnabled);

        DebugLogger.info(MODULE,
                "已加载配置: enabled=%s, detect_mode=%s, threshold_seconds=%d, tab_prefix=%s, "
                        + "broadcast=%s, invulnerable=%s, auto_kick=%ds, manual_toggle=%s",
                enabled, detectMode, thresholdSeconds, tabPrefixEnabled,
                broadcastEnabled, invulnerableEnabled, autoKickSeconds, manualToggleEnabled);

        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code afk_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED;
        detectMode = DEFAULT_DETECT_MODE;
        thresholdSeconds = DEFAULT_THRESHOLD_SECONDS;
        tabPrefixEnabled = DEFAULT_TAB_PREFIX_ENABLED;
        broadcastEnabled = DEFAULT_BROADCAST_ENABLED;
        invulnerableEnabled = DEFAULT_INVULNERABLE_ENABLED;
        autoKickSeconds = DEFAULT_AUTO_KICK_SECONDS;
        manualToggleEnabled = DEFAULT_MANUAL_TOGGLE_ENABLED;
        save();
    }

    /** 保存当前配置到全局配置文件的 {@code afk_module} 分节 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.AFK_MODULE);
        section.set("enabled", enabled);
        section.set("detect_mode", detectMode);
        section.set("threshold_seconds", thresholdSeconds);
        section.set("tab_prefix_enabled", tabPrefixEnabled);
        section.set("broadcast_enabled", broadcastEnabled);
        section.set("invulnerable_enabled", invulnerableEnabled);
        section.set("auto_kick_seconds", autoKickSeconds);
        section.set("manual_toggle_enabled", manualToggleEnabled);
        GlobalSettings.save();
    }
}
