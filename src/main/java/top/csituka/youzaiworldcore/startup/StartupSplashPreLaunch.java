package top.csituka.youzaiworldcore.startup;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 在 Fabric 启动早期按客户端环境加载启动窗口。
 * <p>
 * 此入口位于通用源集，专用服务端会在加载任何客户端或 AWT 类之前直接返回。
 * </p>
 */
public final class StartupSplashPreLaunch implements PreLaunchEntrypoint {

    private static final String CLIENT_SPLASH_CLASS =
            "top.csituka.youzaiworldcore.client.startup.StartupSplashWindow";
    private static final String DATA_GENERATION_PROPERTY = "fabric-api.datagen";

    /**
     * 在 Minecraft 主入口运行前显示客户端启动窗口。
     */
    @Override
    public void onPreLaunch() {
        DebugLogger.entering("StartupSplash", "onPreLaunch");

        boolean clientEnvironment = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        DebugLogger.branch("StartupSplash", "当前为客户端环境", clientEnvironment);
        if (!clientEnvironment) {
            DebugLogger.exiting("StartupSplash", "onPreLaunch", "跳过专用服务端");
            return;
        }

        boolean dataGeneration = System.getProperty(DATA_GENERATION_PROPERTY) != null;
        DebugLogger.branch("StartupSplash", "当前为数据生成进程", dataGeneration);
        if (dataGeneration) {
            DebugLogger.exiting("StartupSplash", "onPreLaunch", "跳过数据生成进程");
            return;
        }

        try {
            Class<?> splashClass = Class.forName(
                    CLIENT_SPLASH_CLASS,
                    true,
                    StartupSplashPreLaunch.class.getClassLoader()
            );
            splashClass.getMethod("show").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | SecurityException e) {
            // 启动窗口属于非关键界面，创建失败时不能阻止游戏继续启动。
            DebugLogger.exception("StartupSplash", "加载客户端启动窗口", e);
        }

        DebugLogger.exiting("StartupSplash", "onPreLaunch");
    }
}
