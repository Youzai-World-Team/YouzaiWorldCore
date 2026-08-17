package top.csituka.youzaiworldcore.client.config;

import top.csituka.youzaiworldcore.highlightitem.Configurator;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 客户端配置默认值的集中生成入口。
 * <p>
 * 「首次安装还没存过配置」与「读到坏文件后重建」两种场景都走这里：
 * 依次让每个客户端模块把自己的字段<b>重置为默认值</b>并写进
 * {@link ClientGlobalSettings} 的对应分节，最后由调用方统一落盘一次。
 * </p>
 * <p>
 * <b>新增客户端模块时必须在 {@link #writeAllDefaults()} 里加一行</b>，
 * 否则新生成的默认配置里会缺这个模块的分节。
 * </p>
 */
public final class ClientDefaultSettingsWriter {

    private static final String MODULE = "ClientDefaultSettingsWriter";

    private ClientDefaultSettingsWriter() {
    }

    /**
     * 把全部客户端模块的默认配置写进 {@link ClientGlobalSettings}（只改内存，不落盘）。
     * <p>顺序即生成文件里分节的出现顺序。</p>
     */
    public static void writeAllDefaults() {
        DebugLogger.entering(MODULE, "writeAllDefaults");
        ClientGlobalSettings.runBatched(() -> {
            ClientExternalSettings.writeDefaults();
            YzHudSettings.writeDefaults();
            Configurator.writeDefaults();
        });
        DebugLogger.exiting(MODULE, "writeAllDefaults");
    }
}
