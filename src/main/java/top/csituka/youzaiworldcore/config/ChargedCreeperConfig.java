package top.csituka.youzaiworldcore.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 天然带电苦力怕功能配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code charged_creeper_module} 分节。
 * <p>
 * 参考 Serilum 的 Naturally Charged Creepers 的设计与行为实现（原生重写，不依赖其前置 Collective）
 * 本类仅负责配置的持久化与读取，充电逻辑见
 * {@link top.csituka.youzaiworldcore.event.ChargedCreeperHandler}。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class ChargedCreeperConfig {

    public static final String MODULE = "ChargedCreeperConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ChargedCreeperConfig");

    /** 默认值：启用 */
    private static final boolean DEFAULT_ENABLED = true;
    /** 默认值：10% 带电概率 */
    private static final double DEFAULT_CHANCE = 0.1;

    /** 功能总开关，默认 true（启用）。设为 false 时处理器直接跳过，不判定任何苦力怕 */
    private static boolean enabled = DEFAULT_ENABLED;

    /** 苦力怕天然带电的概率，取值范围 [0.0, 1.0]，默认 0.1（10%） */
    private static double chance = DEFAULT_CHANCE;

    private ChargedCreeperConfig() {
    }

    /**
     * @return 功能是否启用（由 {@code /yzwc event naturally_charged_creepers enable} 控制）
     */
    public static boolean isEnabled() {
        return enabled;
    }

    public static double getChance() {
        return chance;
    }

    // ===== 运行时修改（供 /yzwc event naturally_charged_creepers 命令调用）=====

    /** 设置功能总开关并持久化到配置文件 */
    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.info(MODULE, "enabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "ChargedCreeperConfig", "enabled", enabled, value);
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
    }

    /** 设置带电概率（自动钳制到 [0.0, 1.0]）并持久化到配置文件 */
    public static void setChance(double value) {
        DebugLogger.entering(MODULE, "setChance", "value=" + value);
        double clamped = value;
        if (Double.isNaN(clamped)) {
            DebugLogger.info(MODULE, "setChance 收到 NaN，钳制为 0");
            clamped = 0.0;
        } else if (clamped < 0.0) {
            clamped = 0.0;
        } else if (clamped > 1.0) {
            clamped = 1.0;
        }
        if (chance == clamped) {
            DebugLogger.info(MODULE, "chance 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setChance", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "ChargedCreeperConfig", "chance", chance, clamped);
        chance = clamped;
        save();
        DebugLogger.exiting(MODULE, "setChance", "1");
    }

    // ===== 持久化 =====

    /** 从全局配置的 {@code charged_creeper_module} 分节加载（分节缺失则写入默认配置） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        ConfigSection section = GlobalSettings.section(GlobalSettings.CHARGED_CREEPER_MODULE);
        if (section.isEmpty()) {
            DebugLogger.info(MODULE, "charged_creeper_module 分节不存在，写入默认配置 (chance=%.2f)", chance);
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        enabled = section.getBoolean("enabled", enabled);
        chance = section.getDouble("chance", chance, 0.0, 1.0);

        DebugLogger.info(MODULE, "已加载配置: enabled=%s, chance=%.4f", enabled, chance);
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code charged_creeper_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED;
        chance = DEFAULT_CHANCE;
        save();
    }

    /** 保存当前配置到全局配置文件的 {@code charged_creeper_module} 分节 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.CHARGED_CREEPER_MODULE);
        section.set("enabled", enabled);
        section.set("chance", chance);
        GlobalSettings.save();
    }
}
