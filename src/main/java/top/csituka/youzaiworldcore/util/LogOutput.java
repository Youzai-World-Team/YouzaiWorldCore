package top.csituka.youzaiworldcore.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 只配置 YouzaiWorldCore 两个日志器及独立文件，不修改根日志器或游戏输出过滤器。 */
final class LogOutput {
    private record Installation(Configuration configuration, Level level) {
    }

    private static final Map<String, Installation> INSTALLED = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    private LogOutput() {
    }

    /** 级别变更复用已有输出器；Log4j 重载后在新配置中重新接入。 */
    @SuppressWarnings({ "null", "deprecation" })
    static synchronized void prepare(String name, Path file, int logLevel) {
        Level level = switch (logLevel) {
            case DebugLogger.LEVEL_DEBUG -> Level.TRACE;
            case DebugLogger.LEVEL_DETAILED -> Level.DEBUG;
            default -> Level.INFO;
        };
        try {
            if (!(LogManager.getContext(false) instanceof LoggerContext context))
                return;
            Configuration configuration = context.getConfiguration();
            Installation installed = INSTALLED.get(name);
            if (installed != null && installed.configuration() == configuration && installed.level() == level)
                return;

            LoggerConfig logger = configuration.getLoggerConfig(name);
            if (!logger.getName().equals(name)) {
                logger = new LoggerConfig(name, level, true);
                configuration.addLogger(name, logger);
            }

            String appenderName = name + ".File";
            if (configuration.getAppender(appenderName) == null) {
                Path absolute = file.toAbsolutePath().normalize();
                Files.createDirectories(absolute.getParent());
                String archive = absolute.toString().replaceFirst("\\.log$", "-%i.log.gz");
                RollingFileAppender appender = RollingFileAppender.newBuilder()
                        .setName(appenderName)
                        .setConfiguration(configuration)
                        .withFileName(absolute.toString())
                        .withFilePattern(archive)
                        .withAppend(true)
                        .withPolicy(CompositeTriggeringPolicy.createPolicy(
                                OnStartupTriggeringPolicy.createPolicy(1),
                                SizeBasedTriggeringPolicy.createPolicy("20MB")))
                        .withStrategy(DefaultRolloverStrategy.newBuilder()
                                .withMax("5").withFileIndex("min").withConfig(configuration).build())
                        .setLayout(PatternLayout.newBuilder().withConfiguration(configuration)
                                .withCharset(StandardCharsets.UTF_8).withPattern("%msg%n%throwable").build())
                        .build();
                if (appender == null)
                    throw new IllegalStateException("无法创建日志文件输出器：" + absolute);
                appender.start();
                configuration.addAppender(appender);
            }
            if (!logger.getAppenders().containsKey(appenderName)) {
                logger.addAppender(configuration.getAppender(appenderName), Level.TRACE, null);
            }
            logger.setLevel(level);
            context.updateLoggers();
            INSTALLED.put(name, new Installation(configuration, level));
            FAILED.remove(name);
        } catch (Exception error) {
            // 日志文件故障不能再让业务崩溃；普通游戏日志保留一次明确的故障及堆栈。
            if (FAILED.add(name))
                LoggerFactory.getLogger("YouzaiWorldCore.LogOutput")
                        .error("无法接入模组日志文件 {}，继续使用游戏日志", file, error);
        }
    }
}
