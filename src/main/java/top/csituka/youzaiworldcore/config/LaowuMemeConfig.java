package top.csituka.youzaiworldcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 老吴贴贴事件（laowu meme）配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code laowu_meme_module} 分节。
 * <p>
 * 由 {@code /yzwc event laowu enable [true|false]} 控制，为<b>服务器全局</b>开关：
 * 启用时全体玩家的猫都可能触发老吴贴贴，禁用时对所有玩家立即停止并释放配对。
 * 本类仅负责配置的持久化与读取，状态机见
 * {@link top.csituka.youzaiworldcore.event.LaowuMemeHandler}。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class LaowuMemeConfig {

    public static final String MODULE = "LaowuMemeConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/LaowuMemeConfig");

    /** 默认值：启用 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 事件总开关，默认 true（启用）。设为 false 时状态机直接释放全部配对并停止扫描 */
    private static boolean enabled = DEFAULT_ENABLED;
    /** 冷却时长下限（秒）：至少 60 秒 */
    public static final int MIN_COOLDOWN_SECONDS = 60;
    /** 冷却时长默认值（秒）：3 分钟 */
    private static final int DEFAULT_COOLDOWN_SECONDS = 180;
    /** 释放后的冷却时长（秒），范围 {@link #MIN_COOLDOWN_SECONDS} 起，默认 {@link #DEFAULT_COOLDOWN_SECONDS} */
    private static int cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;

    private LaowuMemeConfig() {
    }

    /**
     * @return 事件是否启用（由 {@code /yzwc event laowu enable [true|false]} 控制）
     */
    public static boolean isEnabled() {
        return enabled;
    }

    // ===== 运行时修改（供 /yzwc event laowu enable / settings cd 命令调用）=====

    /** 设置事件总开关并持久化到配置文件 */
    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.info(MODULE, "enabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "LaowuMemeConfig", "enabled", enabled, value);
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
    }

    /** 获取释放后的冷却时长（秒） */
    public static int getCooldownSeconds() {
        return cooldownSeconds;
    }

    /** 设置释放后的冷却时长（秒，至少 {@link #MIN_COOLDOWN_SECONDS}）并持久化 */
    public static void setCooldownSeconds(int seconds) {
        DebugLogger.entering(MODULE, "setCooldownSeconds", "seconds=" + seconds);
        if (seconds < MIN_COOLDOWN_SECONDS) {
            DebugLogger.warn(MODULE, "冷却时长非法: %d（必须 >= %d），忽略", seconds, MIN_COOLDOWN_SECONDS);
            DebugLogger.exiting(MODULE, "setCooldownSeconds", "invalid");
            return;
        }
        if (cooldownSeconds == seconds) {
            DebugLogger.info(MODULE, "cooldownSeconds 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setCooldownSeconds", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "LaowuMemeConfig", "cooldownSeconds", cooldownSeconds, seconds);
        cooldownSeconds = seconds;
        save();
        DebugLogger.exiting(MODULE, "setCooldownSeconds", "1");
    }

    // ===== 持久化 =====

    /** 从全局配置的 {@code laowu_meme_module} 分节加载（分节缺失则写入默认配置） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        ConfigSection section = GlobalSettings.section(GlobalSettings.LAOWU_MEME_MODULE);
        if (section.isEmpty()) {
            DebugLogger.info(MODULE, "laowu_meme_module 分节不存在，写入默认配置 (enabled=%s)", enabled);
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        enabled = section.getBoolean("enabled", enabled);
        cooldownSeconds = section.getInt("cooldown_seconds", cooldownSeconds,
                MIN_COOLDOWN_SECONDS, Integer.MAX_VALUE);

        DebugLogger.info(MODULE, "已加载配置: enabled=%s, cooldown_seconds=%d", enabled, cooldownSeconds);
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code laowu_meme_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED;
        cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
        save();
    }

    /** 保存当前配置到全局配置文件的 {@code laowu_meme_module} 分节 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.LAOWU_MEME_MODULE);
        section.set("enabled", enabled);
        section.set("cooldown_seconds", cooldownSeconds);
        GlobalSettings.save();
    }
}
