package top.csituka.youzaiworldcore.trialvault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 试炼宝库无限领奖功能配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code trial_vault_module} 分节。
 * <p>
 * 当功能启用时，玩家可对同一试炼宝库重复插钥匙领奖（不受原版每玩家一次的限制）。
 * 参考 trial-chamber-time-removal 的设计思路，原生重写（不依赖其前置）。
 * 本类仅负责配置的持久化，逻辑实现见
 * {@link top.csituka.youzaiworldcore.mixin.trialvault.VaultServerDataMixin}。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class TrialVaultConfig {

    public static final String MODULE = "TrialVaultConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/TrialVaultConfig");

    /** 默认值：启用 */
    private static final boolean DEFAULT_ENABLED = true;

    /** 功能总开关，默认 true（启用关闭冷却）。设为 false 时 Mixin 放行原版行为 */
    private static boolean enabled = DEFAULT_ENABLED;

    private TrialVaultConfig() {
    }

    /**
     * @return 功能是否启用（由 {@code /yzwc event trial_vault enable} 控制）
     */
    public static boolean isEnabled() {
        return enabled;
    }

    // ===== 运行时修改（供 /yzwc event trial_vault 命令调用）=====

    /** 设置功能总开关并持久化到配置文件 */
    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.info(MODULE, "enabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "TrialVaultConfig", "enabled", enabled, value);
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
    }

    // ===== 持久化 =====

    /** 从全局配置的 {@code trial_vault_module} 分节加载（分节缺失则写入默认配置） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        ConfigSection section = GlobalSettings.section(GlobalSettings.TRIAL_VAULT_MODULE);
        if (section.isEmpty()) {
            DebugLogger.info(MODULE, "trial_vault_module 分节不存在，写入默认配置");
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        enabled = section.getBoolean("enabled", enabled);

        DebugLogger.info(MODULE, "已加载配置: enabled=%s", enabled);
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code trial_vault_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED;
        save();
    }

    /** 保存当前配置到全局配置文件的 {@code trial_vault_module} 分节 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.TRIAL_VAULT_MODULE);
        section.set("enabled", enabled);
        GlobalSettings.save();
    }
}
