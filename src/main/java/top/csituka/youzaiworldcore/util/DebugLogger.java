package top.csituka.youzaiworldcore.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import top.csituka.youzaiworldcore.config.ModPaths;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全模组日志入口。客户端与服务端各有独立实例，集成服务器也不共享开关。
 * <p>普通日志（notice / warn / error / exception）始终输出；诊断日志要求
 * 开发者模式开启，且达到对应丰富度：0 关闭，1 基本，2 详细，3 调试。</p>
 * <ul>
 *   <li>1：info 与重要状态变更；配置、数据加载摘要及操作结果。</li>
 *   <li>2：debug、branch 与详细状态；注册、网络操作与计算诊断。</li>
 *   <li>3：trace、entering / exiting；每帧、每 tick 和逐条处理记录。</li>
 * </ul>
 * <p>实际 SLF4J 等级与文本标记一致。独立滚动文件不受游戏根日志过滤器限制，
 * 配置与重载即时生效，游戏和其它模组的日志阈值保持不变。</p>
 */
public final class DebugLogger {
    public static final int LEVEL_OFF = 0;
    public static final int LEVEL_BASIC = 1;
    public static final int LEVEL_DETAILED = 2;
    public static final int LEVEL_DEBUG = 3;

    public static final DebugLogger SERVER = new DebugLogger("SERVER");
    public static final DebugLogger CLIENT = new DebugLogger("CLIENT");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private record Settings(boolean devModeEnabled, int logLevel) { }
    private volatile Settings settings = new Settings(false, LEVEL_OFF);
    private final String side;
    private final Logger logger;
    private volatile Path outputFile;

    private DebugLogger(String side) {
        this.side = side;
        logger = LoggerFactory.getLogger("YouzaiWorldCore." + side);
    }

    /** 原子更新本端的开关与级别；不触碰另一端和游戏根日志配置。 */
    public synchronized void configure(boolean devModeEnabled, int logLevel) {
        settings = new Settings(devModeEnabled, Math.max(LEVEL_OFF, Math.min(LEVEL_DEBUG, logLevel)));
        prepareOutput();
    }

    public boolean isDevModeEnabled() { return settings.devModeEnabled(); }
    public int getLogLevel() { return settings.logLevel(); }

    /** 0 并不是可启用的诊断级别，避免关闭时 isEnabled(0) 返回 true。 */
    public boolean isEnabled(int requiredLevel) {
        Settings current = settings;
        return requiredLevel >= LEVEL_BASIC && requiredLevel <= LEVEL_DEBUG
                && current.devModeEnabled() && current.logLevel() >= requiredLevel;
    }

    public boolean isEnabled() { return isEnabled(LEVEL_BASIC); }

    /** 通用资源注册等只在物理进程启动期执行的代码使用本进程所属端。 */
    public static DebugLogger bootstrap() {
        return ModPaths.isClientEnvironment() ? CLIENT : SERVER;
    }

    /** 共享存储工具按实际文件归属选择端，异步线程也不依赖线程名猜测。 */
    public static DebugLogger forFile(Path file) {
        return file != null && file.toAbsolutePath().normalize()
                .startsWith(ModPaths.clientRoot().toAbsolutePath().normalize()) ? CLIENT : SERVER;
    }

    /** 两端的独立日志文件；轮转由 LogOutput 统一管理。 */
    public Path logFile() {
        Path current = outputFile;
        if (current == null) {
            current = this == CLIENT ? ModPaths.clientLogFile() : ModPaths.serverLogFile();
            outputFile = current;
        }
        return current;
    }

    /** 为模块绑定名称；保留 SLF4J 的 {} 格式及末尾 Throwable 的语义。 */
    public ModuleLogger module(String name) { return new ModuleLogger(this, name); }

    private void prepareOutput() {
        Settings current = settings;
        int level = current.devModeEnabled() ? current.logLevel() : LEVEL_OFF;
        LogOutput.prepare(logger.getName(), logFile(), level);
    }

    private String formatted(String level, String module, String message) {
        return "[" + LocalDateTime.now().format(FORMATTER) + "] [" + level + "] [" + side + "] [" + module + "] " + message;
    }

    private void write(String level, String module, String message, Throwable error) {
        prepareOutput();
        String text = formatted(level, module, message);
        switch (level) {
            case "TRACE" -> logger.trace(text, error);
            case "DEBUG" -> logger.debug(text, error);
            case "WARN" -> logger.warn(text, error);
            case "ERROR" -> logger.error(text, error);
            default -> logger.info(text, error);
        }
    }

    /** 所有调用统一使用 {}；末尾 Throwable 独立输出完整堆栈。 */
    private void emit(String level, int requiredLevel, String module, String message, Object... args) {
        if (requiredLevel > 0 && !isEnabled(requiredLevel)) return;
        FormattingTuple formatted = MessageFormatter.arrayFormat(message, args);
        write(level, module, formatted.getMessage(), formatted.getThrowable());
    }

    // 无参数重载避免关闭日志时分配 varargs 数组；热路径带参数调用仍须在调用点先判 isEnabled。
    public void trace(String module, String message) {
        if (isEnabled(LEVEL_DEBUG)) write("TRACE", module, message, null);
    }
    public void trace(String module, String message, Object... args) {
        emit("TRACE", LEVEL_DEBUG, module, message, args);
    }
    public void debug(String module, String message) {
        if (isEnabled(LEVEL_DETAILED)) write("DEBUG", module, message, null);
    }
    public void debug(String module, String message, Object... args) {
        emit("DEBUG", LEVEL_DETAILED, module, message, args);
    }
    public void info(String module, String message) {
        if (isEnabled(LEVEL_BASIC)) write("INFO", module, message, null);
    }
    public void info(String module, String message, Object... args) {
        emit("INFO", LEVEL_BASIC, module, message, args);
    }

    /** 启停里程碑、更新提示和管理员操作等普通信息，不受诊断开关控制。 */
    public void notice(String module, String message) { write("INFO", module, message, null); }
    public void notice(String module, String message, Object... args) { emit("INFO", 0, module, message, args); }
    public void warn(String module, String message) { write("WARN", module, message, null); }
    public void warn(String module, String message, Object... args) { emit("WARN", 0, module, message, args); }
    public void error(String module, String message) { write("ERROR", module, message, null); }
    public void error(String module, String message, Object... args) { emit("ERROR", 0, module, message, args); }

    public void entering(String module, String method) {
        if (isEnabled(LEVEL_DEBUG)) trace(module, ">> entering " + method);
    }
    public void entering(String module, String method, String params) {
        if (isEnabled(LEVEL_DEBUG)) trace(module, ">> entering " + method + " [" + params + "]");
    }
    public void exiting(String module, String method) {
        if (isEnabled(LEVEL_DEBUG)) trace(module, "<< exiting " + method);
    }
    public void exiting(String module, String method, String result) {
        if (isEnabled(LEVEL_DEBUG)) trace(module, "<< exiting " + method + " => " + result);
    }
    public void branch(String module, String description, boolean decision) {
        if (isEnabled(LEVEL_DETAILED)) debug(module, "BRANCH: " + description + " => " + decision);
    }
    public void branch(String module, String description, boolean decision, String context) {
        if (isEnabled(LEVEL_DETAILED)) debug(module, "BRANCH: " + description + " => " + decision + " | " + context);
    }
    /** 高频分支使用 3 档，避免详细档逐帧输出。 */
    public void traceBranch(String module, String description, boolean decision) {
        if (isEnabled(LEVEL_DEBUG)) trace(module, "BRANCH: " + description + " => " + decision);
    }
    public void traceBranch(String module, String description, boolean decision, String context) {
        if (isEnabled(LEVEL_DEBUG)) trace(module, "BRANCH: " + description + " => " + decision + " | " + context);
    }

    /** 故障始终记录完整堆栈，不能因关闭开发者模式而丢失。 */
    public void exception(String module, String context, Throwable error) {
        write("ERROR", module, "EXCEPTION in " + context, error);
    }
    public void exceptionSummary(String module, String context, String message) {
        warn(module, "EXCEPTION in " + context + ": " + message);
    }
    public void stateChange(String module, String target, String field, Object oldValue, Object newValue) {
        if (isEnabled(LEVEL_BASIC)) info(module, "STATE: " + target + "." + field + ": " + oldValue + " -> " + newValue);
    }
    public void stateChange(String module, String target, String field, Object newValue) {
        if (isEnabled(LEVEL_BASIC)) info(module, "STATE: " + target + "." + field + " = " + newValue);
    }
    public void debugStateChange(String module, String target, String field, Object oldValue, Object newValue) {
        if (isEnabled(LEVEL_DETAILED)) debug(module, "STATE: " + target + "." + field + ": " + oldValue + " -> " + newValue);
    }
    public void debugStateChange(String module, String target, String field, Object newValue) {
        if (isEnabled(LEVEL_DETAILED)) debug(module, "STATE: " + target + "." + field + " = " + newValue);
    }
    public void traceStateChange(String module, String target, String field, Object oldValue, Object newValue) {
        if (isEnabled(LEVEL_DEBUG)) trace(module, "STATE: " + target + "." + field + ": " + oldValue + " -> " + newValue);
    }
    public void traceStateChange(String module, String target, String field, Object newValue) {
        if (isEnabled(LEVEL_DEBUG)) trace(module, "STATE: " + target + "." + field + " = " + newValue);
    }

    /** 仅固定模块名，使用同一个 {} 格式化出口，禁止直接创建裸日志器。 */
    public static final class ModuleLogger {
        private final DebugLogger owner;
        private final String module;
        private ModuleLogger(DebugLogger owner, String module) { this.owner = owner; this.module = module; }
        public boolean isInfoEnabled() { return owner.isEnabled(LEVEL_BASIC); }
        public boolean isDebugEnabled() { return owner.isEnabled(LEVEL_DETAILED); }
        public boolean isTraceEnabled() { return owner.isEnabled(LEVEL_DEBUG); }
        public void notice(String message) { owner.notice(module, message); }
        public void info(String message) { owner.info(module, message); }
        public void debug(String message) { owner.debug(module, message); }
        public void trace(String message) { owner.trace(module, message); }
        public void warn(String message) { owner.warn(module, message); }
        public void error(String message) { owner.error(module, message); }
        public void notice(String message, Object... args) { owner.emit("INFO", 0, module, message, args); }
        public void info(String message, Object... args) { owner.emit("INFO", LEVEL_BASIC, module, message, args); }
        public void debug(String message, Object... args) { owner.emit("DEBUG", LEVEL_DETAILED, module, message, args); }
        public void trace(String message, Object... args) { owner.emit("TRACE", LEVEL_DEBUG, module, message, args); }
        public void warn(String message, Object... args) { owner.emit("WARN", 0, module, message, args); }
        public void error(String message, Object... args) { owner.emit("ERROR", 0, module, message, args); }
    }
}
