package top.csituka.youzaiworldcore.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 调试日志工具类 — 受双开关控制。
 * <p>
 * 日志输出的先决条件：
 * <ol>
 *   <li>{@link #devModeEnabled}（开发者模式）为 {@code true}</li>
 *   <li>{@link #logToFile}（输出日志到文件）为 {@code true}</li>
 * </ol>
 * 任一开关关闭则不产生任何日志输出。
 * </p>
 * <p>
 * 日志格式：{@code [yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] [Module] 描述信息}
 * </p>
 * <p>
 * 提供以下专用方法：
 * <ul>
 *   <li>{@link #entering}/{@link #exiting} — 方法进入/退出跟踪</li>
 *   <li>{@link #branch} — 条件分支判断记录</li>
 *   <li>{@link #exception} — 异常捕获块记录</li>
 *   <li>{@link #stateChange} — 状态变更记录</li>
 *   <li>{@link #trace}/{@link #debug}/{@link #info}/{@link #warn}/{@link #error} — 通用日志</li>
 * </ul>
 * </p>
 */
public final class DebugLogger {

    private static final Logger FALLBACK_LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/Debug");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 开发者模式开关（需与 {@link #logToFile} 同时为 true 才输出） */
    private static volatile boolean devModeEnabled = false;

    /** 日志输出到文件开关（需与 {@link #devModeEnabled} 同时为 true 才输出） */
    private static volatile boolean logToFile = false;

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

    /** 设置日志输出到文件状态 */
    public static void setLogToFile(boolean enabled) {
        logToFile = enabled;
    }

    /** 获取日志输出到文件状态 */
    public static boolean isLogToFile() {
        return logToFile;
    }

    /**
     * 检查调试日志是否启用。
     * 仅当「开发者模式」与「输出日志到文件」同时启用时才返回 {@code true}。
     */
    public static boolean isEnabled() {
        return devModeEnabled && logToFile;
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

    /** 输出 TRACE 级别日志 */
    public static void trace(String module, String message, Object... args) {
        if (!isEnabled()) return;
        FALLBACK_LOGGER.trace(formatMessage("TRACE", module, message, args));
    }

    /** 输出 DEBUG 级别日志 */
    public static void debug(String module, String message, Object... args) {
        if (!isEnabled()) return;
        FALLBACK_LOGGER.debug(formatMessage("DEBUG", module, message, args));
    }

    /** 输出 INFO 级别日志 */
    public static void info(String module, String message, Object... args) {
        if (!isEnabled()) return;
        FALLBACK_LOGGER.info(formatMessage("INFO ", module, message, args));
    }

    /** 输出 WARN 级别日志 */
    public static void warn(String module, String message, Object... args) {
        if (!isEnabled()) return;
        FALLBACK_LOGGER.warn(formatMessage("WARN ", module, message, args));
    }

    /** 输出 ERROR 级别日志 */
    public static void error(String module, String message, Object... args) {
        if (!isEnabled()) return;
        FALLBACK_LOGGER.error(formatMessage("ERROR", module, message, args));
    }

    // ===== 方法入口/出口 =====

    /**
     * 记录方法入口（无参数）。
     *
     * @param module 模块名称
     * @param method 方法名称
     */
    public static void entering(String module, String method) {
        if (!isEnabled()) return;
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
        if (!isEnabled()) return;
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
        if (!isEnabled()) return;
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
        if (!isEnabled()) return;
        FALLBACK_LOGGER.debug(formatMessage("TRACE", module,
                "<< exiting " + method + " => " + result));
    }

    // ===== 条件分支 =====

    /**
     * 记录条件分支判断。
     *
     * @param module     模块名称
     * @param description 分支描述
     * @param decision   分支结果（true/false）
     */
    public static void branch(String module, String description, boolean decision) {
        if (!isEnabled()) return;
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
        if (!isEnabled()) return;
        FALLBACK_LOGGER.debug(formatMessage("DEBUG", module,
                "BRANCH: " + description + " => " + decision + " | " + context));
    }

    // ===== 异常捕获 =====

    /**
     * 记录异常捕获块。
     *
     * @param module    模块名称
     * @param context   异常发生的上下文描述
     * @param throwable 捕获的异常对象
     */
    public static void exception(String module, String context, Throwable throwable) {
        if (!isEnabled()) return;
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
        if (!isEnabled()) return;
        FALLBACK_LOGGER.warn(formatMessage("WARN ", module,
                "EXCEPTION in " + context + ": " + message));
    }

    // ===== 状态变更 =====

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
        if (!isEnabled()) return;
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
        if (!isEnabled()) return;
        FALLBACK_LOGGER.info(formatMessage("INFO ", module,
                "STATE: " + target + "." + field + " = " + newValue));
    }
}
