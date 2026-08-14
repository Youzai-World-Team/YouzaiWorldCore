package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;
import top.csituka.youzaiworldcore.mail.MailSettings;
import top.csituka.youzaiworldcore.pet.config.PetModuleConfig;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnConfig;
import top.csituka.youzaiworldcore.trialvault.TrialVaultConfig;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 全局配置默认值的集中生成入口。
 * <p>
 * 「新开服还没存过配置」与「读到坏文件后重建」两种场景都走这里：
 * 依次让每个模块把自己的字段<b>重置为默认值</b>并写进
 * {@link GlobalSettings} 的对应分节，最后由调用方统一落盘一次。
 * </p>
 * <p>
 * <b>新增模块时必须在 {@link #writeAllDefaults()} 里加一行</b>，
 * 否则新开服生成的默认配置里会缺这个模块的分节。
 * </p>
 */
public final class DefaultSettingsWriter {

    private static final String MODULE = "DefaultSettingsWriter";

    private DefaultSettingsWriter() {
    }

    /**
     * 把全部模块的默认配置写进 {@link GlobalSettings}（只改内存，不落盘）。
     * <p>顺序即生成文件里分节的出现顺序，按「核心 → 账户 → 玩法 → 运营」排列。</p>
     */
    public static void writeAllDefaults() {
        DebugLogger.entering(MODULE, "writeAllDefaults");
        GlobalSettings.runBatched(() -> {
            // 核心
            ServerExternalSettings.writeDefaults();
            // 账户
            top.csituka.youzaiworldcore.account.data.AccountDataStorage.writeDefaultSettings();
            // 玩法
            AfkConfig.writeDefaults();
            EventSettings.writeDefaults();
            ChatFormatSettings.writeDefaults();
            top.csituka.youzaiworldcore.tablist.TabListSettings.writeDefaults();
            ChargedCreeperConfig.writeDefaults();
            EndPortalConfig.writeDefaults();
            LaowuMemeConfig.writeDefaults();
            TrialVaultConfig.writeDefaults();
            InPlaceRespawnConfig.writeDefaults();
            PetModuleConfig.writeDefaults();
            MailSettings.writeDefaults();
            DimensionPoolSettings.writeDefaults();
            // 运营
            UpdateCheckerConfig.writeDefaults();
        });
        DebugLogger.exiting(MODULE, "writeAllDefaults");
    }
}
