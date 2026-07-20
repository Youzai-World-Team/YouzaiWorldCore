package top.csituka.youzaiworldcore.client.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 运行时环境检测工具 — 区分 PC（Windows/Linux/macOS）与 Android（FCL/ZL2）。
 * <p>
 * 判定标准：尝试加载 {@code android.os.Build} 类。
 * 加载成功 → Android；抛出 {@link ClassNotFoundException} → PC。
 * 结果在首次调用后缓存，避免每次重复反射。
 * </p>
 */
public final class PlatformDetector {

    private static final String LOG_MODULE = "PlatformDetector";

    /** 缓存的环境判定结果（null=未初始化） */
    private static volatile Boolean androidCache = null;

    private PlatformDetector() {}

    /**
     * 判断当前运行环境是否为 Android。
     *
     * @return {@code true} 表示 Android（FCL/ZL2），{@code false} 表示 PC
     */
    public static boolean isAndroid() {
        if (androidCache != null) {
            return androidCache;
        }
        try {
            Class.forName("android.os.Build");
            androidCache = true;
            DebugLogger.info(LOG_MODULE, "平台检测: Android 环境");
        } catch (ClassNotFoundException e) {
            androidCache = false;
            DebugLogger.info(LOG_MODULE, "平台检测: PC 环境");
        }
        return androidCache;
    }

    /**
     * 重置缓存（极少需要，主要用于测试场景）。
     */
    public static void resetCache() {
        androidCache = null;
        DebugLogger.debug(LOG_MODULE, "平台检测缓存已重置");
    }
}
