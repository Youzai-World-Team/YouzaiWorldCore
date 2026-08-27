package top.csituka.youzaiworldcore.startup;

/**
 * Fabric Loader 插桩代码与启动窗口之间的无依赖状态桥接。
 * <p>
 * 该类会在启动早期定义到 Fabric Loader 所在的类加载器中，因此只能依赖 JDK 类。
 * 普通模组代码通过父级类加载器解析到同一个类，从而读取当前正在执行的模组入口点。
 * </p>
 */
public final class StartupEntrypointBridge {

    private static final Object LOCK = new Object();

    private static String phase = "启动";
    private static String stage = "正在启动 Minecraft";
    private static int stageIndex;
    private static int stageCount;
    private static int progress;
    private static int maximum = 1;
    private static boolean failed;

    private static String activeEntrypointKey = "";
    private static String activeEntrypointType = "";
    private static int activeEntrypointIndex;
    private static int activeEntrypointCount;

    private StartupEntrypointBridge() {
    }

    /** 重置全部显示状态。 */
    public static void reset(String initialStage) {
        synchronized (LOCK) {
            phase = "启动";
            stage = normalize(initialStage, "正在启动 Minecraft");
            stageIndex = 0;
            stageCount = 0;
            progress = 0;
            maximum = 1;
            failed = false;
            activeEntrypointKey = "";
            activeEntrypointType = "";
            activeEntrypointIndex = 0;
            activeEntrypointCount = 0;
        }
    }

    /** 开始本模组内部的一个初始化阶段组。 */
    public static void beginPhase(String phaseName, int totalStages) {
        synchronized (LOCK) {
            phase = normalize(phaseName, "启动");
            stage = "准备";
            stageIndex = 0;
            stageCount = Math.max(0, totalStages);
            progress = 0;
            maximum = 1;
            failed = false;
        }
    }

    /** 开始本模组内部阶段组中的下一项。 */
    public static void beginStage(String stageName) {
        synchronized (LOCK) {
            stageIndex = stageCount > 0 ? Math.min(stageCount, stageIndex + 1) : stageIndex + 1;
            stage = normalize(stageName, "正在加载");
            progress = 0;
            maximum = 1;
        }
    }

    /** 更新本模组内部项目的细分进度。 */
    public static void setProgress(int current, int max) {
        int safeMaximum = Math.max(1, max);
        int safeCurrent = Math.max(0, Math.min(safeMaximum, current));
        synchronized (LOCK) {
            progress = safeCurrent;
            maximum = safeMaximum;
        }
    }

    /** 标记当前显示项目完成。 */
    public static void completeStage() {
        synchronized (LOCK) {
            progress = maximum;
        }
    }

    /** 设置一个无需分项计数的显示状态。 */
    public static void setStage(String stageName) {
        synchronized (LOCK) {
            stage = normalize(stageName, "正在加载");
        }
    }

    /** 标记启动失败，供 Fabric Loader 错误窗口显示前通知加载窗口。 */
    public static void markFailed(String failureMessage) {
        synchronized (LOCK) {
            phase = "启动失败";
            stage = normalize(failureMessage, "启动失败，请查看日志以获取更多信息。");
            progress = maximum;
            failed = true;
        }
    }

    /**
     * 在 Fabric Loader 开始调用一类入口点时更新总数。
     *
     * @param key        入口点键，例如 {@code main} 或 {@code client}
     * @param type       入口点接口类型
     * @param totalCount 该类入口点的总数
     */
    public static void beforeEntrypointType(String key, Class<?> type, int totalCount) {
        synchronized (LOCK) {
            activeEntrypointKey = normalize(key, "unknown");
            activeEntrypointType = type == null ? "" : type.getSimpleName();
            activeEntrypointIndex = 0;
            activeEntrypointCount = Math.max(0, totalCount);
            publishEntrypointState("准备加载模组入口");
        }
    }

    /**
     * 在 Fabric Loader 调用单个模组入口点前发布模组元数据。
     *
     * @param key      入口点键
     * @param typeName 入口点接口名称
     * @param modId    模组 ID
     * @param modName  模组显示名称
     */
    public static void beforeSingleEntrypoint(
            String key,
            String typeName,
            String modId,
            String modName
    ) {
        synchronized (LOCK) {
            activeEntrypointKey = normalize(key, activeEntrypointKey);
            activeEntrypointType = normalize(typeName, activeEntrypointType);
            activeEntrypointIndex = activeEntrypointCount > 0
                    ? Math.min(activeEntrypointCount, activeEntrypointIndex + 1)
                    : activeEntrypointIndex + 1;

            String safeId = normalize(modId, "unknown");
            String safeName = normalize(modName, safeId);
            publishEntrypointState(safeName);
        }
    }

    /** 在一类 Fabric 入口点全部执行完毕后填满当前进度。 */
    public static void afterEntrypointType(String key) {
        synchronized (LOCK) {
            if (!normalize(key, "unknown").equals(activeEntrypointKey)) {
                return;
            }
            activeEntrypointIndex = activeEntrypointCount;
            publishEntrypointState("已完成 " + describeEntrypoint(activeEntrypointKey, activeEntrypointType));
            progress = maximum;
        }
    }

    /**
     * 返回只包含 JDK 类型的不可变值数组，供模组类加载器中的状态包装类读取。
     */
    public static Object[] snapshot() {
        synchronized (LOCK) {
            return new Object[] {phase, stage, stageIndex, stageCount, progress, maximum, failed};
        }
    }

    private static void publishEntrypointState(String currentStage) {
        phase = "加载模组 · " + describeEntrypoint(activeEntrypointKey, activeEntrypointType);
        stage = currentStage;
        stageIndex = activeEntrypointIndex;
        stageCount = activeEntrypointCount;
        progress = 0;
        maximum = 1;
    }

    private static String describeEntrypoint(String key, String typeName) {
        return switch (key) {
            case "preLaunch" -> "预启动入口";
            case "main" -> "公共入口";
            case "client" -> "客户端入口";
            case "server" -> "服务端入口";
            case "fabric-datagen" -> "数据生成入口";
            default -> normalize(typeName, normalize(key, "未知入口"));
        };
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
