package top.csituka.youzaiworldcore.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 调试日志工具类 — 受开发者模式 + 日志级别双维度控制。
 * <p>
 * 日志输出的先决条件：
 * <ol>
 *   <li>{@link #devModeEnabled}（开发者模式）为 {@code true}</li>
 *   <li>{@link #logLevel}（日志输出丰富度）{@code > 0}</li>
 * </ol>
 * 开发者关闭或日志级别为「关闭」时，不产生任何日志输出。
 * </p>
 * <p>
 * 日志级别定义：
 * <ul>
 *   <li><b>0 — 关闭</b>：不输出任何日志</li>
 *   <li><b>1 — 基本</b>：仅输出 INFO / WARN / ERROR 及 stateChange</li>
 *   <li><b>2 — 详细</b>：额外输出 DEBUG 及 branch</li>
 *   <li><b>3 — 调试</b>：额外输出 TRACE 及 entering/exiting</li>
 * </ul>
 * </p>
 * <p>
 * 日志格式：{@code [yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] [Module] 描述信息}
 * </p>
 */
public final class DebugLogger {

    /** 日志级别常量：关闭 */
    public static final int LEVEL_OFF = 0;
    /** 日志级别常量：基本 */
    public static final int LEVEL_BASIC = 1;
    /** 日志级别常量：详细 */
    public static final int LEVEL_DETAILED = 2;
    /** 日志级别常量：调试 */
    public static final int LEVEL_DEBUG = 3;

    private static final Logger FALLBACK_LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/Debug");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 开发者模式开关（需 {@link #logLevel} > 0 才输出） */
    private static volatile boolean devModeEnabled = false;

    /** 日志输出丰富度等级（0=关闭, 1=基本, 2=详细, 3=调试） */
    private static volatile int logLevel = LEVEL_OFF;

    private DebugLogger() {}

    // ===== 开关控制 =====

    /** 设置开发者模式状态 */
    public static void setDevModeEnabled(boolean enabled) {
        devModeEnabled = enabled;
    }

    /** 获取开发者模式状态 */
    public static boolean isDevModeEnabled() {
        return devModeEnabled;
    }

    /** 设置日志输出丰富度等级（0-3） */
    public static void setLogLevel(int level) {
        logLevel = Math.max(LEVEL_OFF, Math.min(LEVEL_DEBUG, level));
    }

    /** 获取日志输出丰富度等级 */
    public static int getLogLevel() {
        return logLevel;
    }

    /**
     * 兼容旧版调用：将布尔值映射为等级（true → 基本, false → 关闭）。
     *
     * @deprecated 请改用 {@link #setLogLevel(int)}
     */
    @Deprecated
    public static void setLogToFile(boolean enabled) {
        setLogLevel(enabled ? LEVEL_BASIC : LEVEL_OFF);
    }

    /**
     * 兼容旧版调用：等级 > 0 表示日志输出到文件开启。
     *
     * @deprecated 请改用 {@link #getLogLevel()}
     */
    @Deprecated
    public static boolean isLogToFile() {
        return logLevel > LEVEL_OFF;
    }

    /**
     * 检查调试日志是否启用。
     * 仅当「开发者模式」开启且「日志输出丰富度」不低于指定级别时才返回 {@code true}。
     *
     * @param requiredLevel 所需的最低日志级别
     */
    public static boolean isEnabled(int requiredLevel) {
        return devModeEnabled && logLevel >= requiredLevel;
    }

    /**
     * 检查调试日志是否启用（最低要求 {@link #LEVEL_BASIC}）。
     */
    public static boolean isEnabled() {
        return isEnabled(LEVEL_BASIC);
    }

    // ===== 内部格式化 =====

    private static String timestamp() {
        return LocalDateTime.now().format(FORMATTER);
    }

    private static String formatMessage(String level, String module, String message) {
        return "[" + timestamp() + "] [" + level + "] [" + module + "] " + message;
    }

    private static String formatMessage(String level, String module, String message, Object... args) {
        return formatMessage(level, module, args.length > 0 ? String.format(message, args) : message);
    }

    // ===== 通用日志方法 =====
    //
    // 关于 varargs 重载：
    // 形如 debug(module, msg, args...) 的可变参数方法，即使日志级别未开启，
    // JVM 也会在<b>调用前</b>先分配一个 Object[] 并把基本类型装箱进去——
    // 方法体内的 isEnabled 短路救不了这部分开销。
    // 因此为「无参数」这一最常见情形提供非 varargs 重载：编译期即选中它，
    // 不产生数组分配。带参数的调用点若位于每 tick / 每帧路径，
    // 请在调用点外层自行包一层 isEnabled 判定（项目内已按此约定改造热路径）。

    /** 输出 TRACE 级别日志（无格式参数，不分配 varargs 数组） */
    public static void trace(String module, String message) {
        if (!isEnabled(LEVEL_DEBUG)) return;
        FALLBACK_LOGGER.trace(formatMessage("TRACE", module, message));
    }

    /** 输出 TRACE 级别日志（需要日志级别 {@code >= LEVEL_DEBUG}） */
    public static void trace(String module, String message, Object... args) {
        if (!isEnabled(LEVEL_DEBUG)) return;
        FALLBACK_LOGGER.trace(formatMessage("TRACE", module, message, args));
    }

    /** 输出 DEBUG 级别日志（无格式参数，不分配 varargs 数组） */
    public static void debug(String module, String message) {
        if (!isEnabled(LEVEL_DETAILED)) return;
        FALLBACK_LOGGER.debug(formatMessage("DEBUG", module, message));
    }

    /** 输出 DEBUG 级别日志（需要日志级别 {@code >= LEVEL_DETAILED}） */
    public static void debug(String module, String message, Object... args) {
        if (!isEnabled(LEVEL_DETAILED)) return;
        FALLBACK_LOGGER.debug(formatMessage("DEBUG", module, message, args));
    }

    /** 输出 INFO 级别日志（无格式参数，不分配 varargs 数组） */
    public static void info(String module, String message) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.info(formatMessage("INFO ", module, message));
    }

    /** 输出 INFO 级别日志（需要日志级别 {@code >= LEVEL_BASIC}） */
    public static void info(String module, String message, Object... args) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.info(formatMessage("INFO ", module, message, args));
    }

    /** 输出 WARN 级别日志（无格式参数，不分配 varargs 数组） */
    public static void warn(String module, String message) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.warn(formatMessage("WARN ", module, message));
    }

    /** 输出 WARN 级别日志（需要日志级别 {@code >= LEVEL_BASIC}） */
    public static void warn(String module, String message, Object... args) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.warn(formatMessage("WARN ", module, message, args));
    }

    /** 输出 ERROR 级别日志（无格式参数，不分配 varargs 数组） */
    public static void error(String module, String message) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.error(formatMessage("ERROR", module, message));
    }

    /** 输出 ERROR 级别日志（需要日志级别 {@code >= LEVEL_BASIC}） */
    public static void error(String module, String message, Object... args) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.error(formatMessage("ERROR", module, message, args));
    }

    // ===== 方法入口/出口（需要日志级别 >= LEVEL_DEBUG） =====

    /**
     * 记录方法入口（无参数）。
     *
     * @param module 模块名称
     * @param method 方法名称
     */
    public static void entering(String module, String method) {
        if (!isEnabled(LEVEL_DEBUG)) return;
        FALLBACK_LOGGER.debug(formatMessage("TRACE", module,
                ">> entering " + method));
    }

    /**
     * 记录方法入口（含参数）。
     *
     * @param module 模块名称
     * @param method 方法名称
     * @param params 参数字典，格式如 "param1=value1, param2=value2"
     */
    public static void entering(String module, String method, String params) {
        if (!isEnabled(LEVEL_DEBUG)) return;
        FALLBACK_LOGGER.debug(formatMessage("TRACE", module,
                ">> entering " + method + " [" + params + "]"));
    }

    /**
     * 记录方法出口（无返回值）。
     *
     * @param module 模块名称
     * @param method 方法名称
     */
    public static void exiting(String module, String method) {
        if (!isEnabled(LEVEL_DEBUG)) return;
        FALLBACK_LOGGER.debug(formatMessage("TRACE", module,
                "<< exiting " + method));
    }

    /**
     * 记录方法出口（含返回值）。
     *
     * @param module 模块名称
     * @param method 方法名称
     * @param result 返回值描述
     */
    public static void exiting(String module, String method, String result) {
        if (!isEnabled(LEVEL_DEBUG)) return;
        FALLBACK_LOGGER.debug(formatMessage("TRACE", module,
                "<< exiting " + method + " => " + result));
    }

    // ===== 条件分支（需要日志级别 >= LEVEL_DETAILED） =====

    /**
     * 记录条件分支判断。
     *
     * @param module     模块名称
     * @param description 分支描述
     * @param decision   分支结果（true/false）
     */
    public static void branch(String module, String description, boolean decision) {
        if (!isEnabled(LEVEL_DETAILED)) return;
        FALLBACK_LOGGER.debug(formatMessage("DEBUG", module,
                "BRANCH: " + description + " => " + decision));
    }

    /**
     * 记录条件分支判断（含额外上下文）。
     *
     * @param module     模块名称
     * @param description 分支描述
     * @param decision   分支结果
     * @param context    额外上下文信息
     */
    public static void branch(String module, String description, boolean decision, String context) {
        if (!isEnabled(LEVEL_DETAILED)) return;
        FALLBACK_LOGGER.debug(formatMessage("DEBUG", module,
                "BRANCH: " + description + " => " + decision + " | " + context));
    }

    // ===== 异常捕获（需要日志级别 >= LEVEL_BASIC） =====

    /**
     * 记录异常捕获块。
     *
     * @param module    模块名称
     * @param context   异常发生的上下文描述
     * @param throwable 捕获的异常对象
     */
    public static void exception(String module, String context, Throwable throwable) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.error(formatMessage("ERROR", module,
                "EXCEPTION in " + context + ": " + throwable.getClass().getSimpleName()
                        + " - " + throwable.getMessage()), throwable);
    }

    /**
     * 记录异常捕获块（不含完整堆栈，仅摘要）。
     *
     * @param module    模块名称
     * @param context   异常发生的上下文描述
     * @param message   异常摘要信息
     */
    public static void exceptionSummary(String module, String context, String message) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.warn(formatMessage("WARN ", module,
                "EXCEPTION in " + context + ": " + message));
    }

    // ===== 状态变更（需要日志级别 >= LEVEL_BASIC） =====

    /**
     * 记录状态变更。
     *
     * @param module    模块名称
     * @param target    状态所属对象标识
     * @param field     变更的字段名
     * @param oldValue  旧值
     * @param newValue  新值
     */
    public static void stateChange(String module, String target, String field,
                                    Object oldValue, Object newValue) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.info(formatMessage("INFO ", module,
                "STATE: " + target + "." + field + ": " + oldValue + " -> " + newValue));
    }

    /**
     * 记录状态变更（简洁版，不显示旧值）。
     *
     * @param module   模块名称
     * @param target   状态所属对象标识
     * @param field    变更的字段名
     * @param newValue 新值
     */
    public static void stateChange(String module, String target, String field, Object newValue) {
        if (!isEnabled(LEVEL_BASIC)) return;
        FALLBACK_LOGGER.info(formatMessage("INFO ", module,
                "STATE: " + target + "." + field + " = " + newValue));
    }
}
