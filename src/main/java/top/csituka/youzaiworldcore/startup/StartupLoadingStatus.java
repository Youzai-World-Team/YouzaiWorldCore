package top.csituka.youzaiworldcore.startup;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 启动加载窗口使用的阶段状态桥接。
 * <p>
 * Fabric Loader 的模组入口点是在不同线程、不同时间段调用的，窗口本身又运行在
 * AWT 事件线程，因此这里仅保存不可变快照，不直接触碰任何 Swing 或 Minecraft 类。
 * 客户端启动窗口通过 {@link #snapshot()} 定时读取状态并刷新指示内容。
 * </p>
 */
public final class StartupLoadingStatus {

    private static final Object LOCK = new Object();
    private static volatile Class<?> sharedBridgeClass;

    private static String phase = "启动";
    private static String stage = "正在启动 Minecraft";
    private static int stageIndex;
    private static int stageCount;
    private static int progress;
    private static int maximum = 1;

    private StartupLoadingStatus() {
    }

    /** 绑定由 Fabric Loader 类加载器定义的共享状态类。 */
    static void attachBridge(Class<?> bridgeClass) {
        sharedBridgeClass = bridgeClass;
    }

    /** 重置启动状态，并显示尚未进入具体入口点时的提示。 */
    public static void reset(String initialStage) {
        if (invokeBridge("reset", new Class<?>[]{String.class}, initialStage)) {
            return;
        }
        synchronized (LOCK) {
            phase = "启动";
            stage = normalize(initialStage, "正在启动 Minecraft");
            stageIndex = 0;
            stageCount = 0;
            progress = 0;
            maximum = 1;
        }
        DebugLogger.info("StartupLoadingStatus", "启动状态已重置：%s", initialStage);
    }

    /** 开始一个阶段组，例如“公共注册”或“客户端初始化”。 */
    public static void beginPhase(String phaseName, int totalStages) {
        if (invokeBridge("beginPhase", new Class<?>[]{String.class, int.class}, phaseName, totalStages)) {
            return;
        }
        synchronized (LOCK) {
            phase = normalize(phaseName, "启动");
            stage = "准备";
            stageIndex = 0;
            stageCount = Math.max(0, totalStages);
            progress = 0;
            maximum = 1;
        }
        DebugLogger.info(
                "StartupLoadingStatus",
                "开始加载阶段：%s（共 %d 项）",
                phaseName,
                Math.max(0, totalStages)
        );
    }

    /** 开始阶段组中的下一项。 */
    public static void beginStage(String stageName) {
        if (invokeBridge("beginStage", new Class<?>[]{String.class}, stageName)) {
            return;
        }
        synchronized (LOCK) {
            stageIndex = stageCount > 0 ? Math.min(stageCount, stageIndex + 1) : stageIndex + 1;
            stage = normalize(stageName, "正在加载");
            progress = 0;
            maximum = 1;
        }
        DebugLogger.info("StartupLoadingStatus", "开始加载项目：%s", stageName);
    }

    /** 更新当前项目的细分进度，数值会自动限制在有效范围。 */
    public static void setProgress(int current, int max) {
        if (invokeBridge("setProgress", new Class<?>[]{int.class, int.class}, current, max)) {
            return;
        }
        int safeMax = Math.max(1, max);
        int safeCurrent = Math.max(0, Math.min(safeMax, current));
        synchronized (LOCK) {
            progress = safeCurrent;
            maximum = safeMax;
        }
    }

    /** 标记当前项目完成。 */
    public static void completeStage() {
        if (invokeBridge("completeStage", new Class<?>[0])) {
            return;
        }
        synchronized (LOCK) {
            progress = maximum;
        }
    }

    /** 设置一个无需分项计数的状态文字。 */
    public static void setStage(String stageName) {
        if (invokeBridge("setStage", new Class<?>[]{String.class}, stageName)) {
            return;
        }
        synchronized (LOCK) {
            stage = normalize(stageName, "正在加载");
        }
    }

    /** 返回供界面线程读取的不可变状态快照。 */
    public static Snapshot snapshot() {
        Object[] values = invokeSnapshotBridge();
        if (values != null && values.length >= 6) {
            return new Snapshot((String) values[0], (String) values[1],
                    (int) values[2], (int) values[3], (int) values[4], (int) values[5]);
        }
        synchronized (LOCK) {
            return new Snapshot(phase, stage, stageIndex, stageCount, progress, maximum);
        }
    }

    private static boolean invokeBridge(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        Class<?> bridgeClass = sharedBridgeClass;
        if (bridgeClass == null) {
            return false;
        }
        try {
            bridgeClass.getMethod(methodName, parameterTypes).invoke(null, arguments);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            sharedBridgeClass = null;
            DebugLogger.exception("StartupLoadingStatus", "调用共享启动状态桥接：" + methodName, exception);
            return false;
        }
    }

    private static Object[] invokeSnapshotBridge() {
        Class<?> bridgeClass = sharedBridgeClass;
        if (bridgeClass == null) {
            return null;
        }
        try {
            return (Object[]) bridgeClass.getMethod("snapshot").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            sharedBridgeClass = null;
            DebugLogger.exception("StartupLoadingStatus", "读取共享启动状态桥接", exception);
            return null;
        }
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** 启动加载状态的不可变视图。 */
    public record Snapshot(
            String phase,
            String stage,
            int stageIndex,
            int stageCount,
            int progress,
            int maximum
    ) {
        /** 当前项目进度（0.0 ~ 1.0）。 */
        public float progressRatio() {
            if (maximum <= 0) {
                return 0.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, progress / (float) maximum));
        }

        /** 阶段组整体进度（0.0 ~ 1.0）。 */
        public float overallRatio() {
            if (stageCount <= 0) {
                return progressRatio();
            }
            float completed = Math.max(0, stageIndex - 1);
            return Math.max(0.0F, Math.min(1.0F,
                    (completed + progressRatio()) / stageCount));
        }
    }
}
