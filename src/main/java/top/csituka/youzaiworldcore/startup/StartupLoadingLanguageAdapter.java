package top.csituka.youzaiworldcore.startup;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 利用 Fabric 语言适配器的早期加载时机安装模组入口点进度追踪。
 */
public final class StartupLoadingLanguageAdapter implements LanguageAdapter {

    private static final String DATA_GENERATION_PROPERTY = "fabric-api.datagen";

    static {
        boolean clientEnvironment = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        boolean dataGeneration = System.getProperty(DATA_GENERATION_PROPERTY) != null;
        if (clientEnvironment && !dataGeneration) {
            try {
                StartupEntrypointInstrumentation.install();
            } catch (Throwable throwable) {
                // 实时追踪属于增强能力，失败时保留原有启动窗口和本模组阶段进度。
                DebugLogger.exception("StartupLoading", "安装 Fabric Loader 入口点追踪", throwable);
            }
        }
    }

    /** 创建实际的预启动入口点实例。 */
    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        if (type != PreLaunchEntrypoint.class) {
            throw new LanguageAdapterException("启动加载适配器仅支持 PreLaunchEntrypoint");
        }
        return type.cast(new StartupSplashPreLaunch());
    }
}
