package top.csituka.youzaiworldcore.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import top.csituka.youzaiworldcore.config.ModPaths;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** 使用实际 SLF4J / Log4j 验证日志行为；由 verify_logging.py 在临时目录运行。 */
public final class LoggingRuntimeChecks {
    private static int checks;
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    private record Event(Level level, String message) { }
    private static final class Capture extends AbstractAppender {
        final List<Event> events = new ArrayList<>();
        Capture(String name) {
            super(name, null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY);
        }
        @Override public void append(LogEvent event) {
            events.add(new Event(event.getLevel(), event.getMessage().getFormattedMessage()));
        }
    }
    private static String clientLog() throws Exception { return Files.readString(DebugLogger.CLIENT.logFile()); }
    private static String serverLog() throws Exception { return Files.readString(DebugLogger.SERVER.logFile()); }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--formats")) {
            checkMigration(java.nio.file.Path.of(args[1]));
            return;
        }
        if (args.length > 0 && args[0].equals("--parse")) {
            parseSources(java.nio.file.Path.of(args[1]));
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        DefaultConfiguration configuration = new DefaultConfiguration();
        var root = configuration.getRootLogger();
        for (String name : List.copyOf(root.getAppenders().keySet())) root.removeAppender(name);
        root.setLevel(Level.INFO);
        Capture game = new Capture("Game");
        game.start();
        configuration.addAppender(game);
        root.addAppender(game, Level.INFO, null);
        context.start(configuration);

        check(!DebugLogger.CLIENT.isEnabled(), "客户端默认关闭");
        check(!DebugLogger.SERVER.isEnabled(), "服务端默认关闭");
        check(!DebugLogger.CLIENT.isEnabled(0), "关闭档不能被视为启用");
        DebugLogger.CLIENT.notice("Audit", "ALWAYS_NOTICE");
        DebugLogger.CLIENT.warn("Audit", "ALWAYS_WARN");
        DebugLogger.CLIENT.error("Audit", "ALWAYS_ERROR");
        DebugLogger.CLIENT.exception("Audit", "ALWAYS_EXCEPTION", new IllegalStateException("STACK_SENTINEL"));
        check(clientLog().contains("ALWAYS_NOTICE"), "关闭时保留普通信息");
        check(clientLog().contains("ALWAYS_WARN"), "关闭时保留警告");
        check(clientLog().contains("ALWAYS_ERROR"), "关闭时保留错误");
        check(clientLog().contains("IllegalStateException: STACK_SENTINEL"), "关闭时保留完整堆栈");

        Capture own = new Capture("ModEvents");
        own.start();
        configuration.addAppender(own);
        configuration.getLoggerConfig("YouzaiWorldCore.CLIENT").addAppender(own, Level.TRACE, null);
        context.updateLoggers();
        for (boolean developer : new boolean[]{false, true}) {
            for (int level = 0; level <= 3; level++) {
                DebugLogger.CLIENT.configure(developer, level);
                String id = "CASE_" + developer + "_" + level;
                int before = own.events.size();
                DebugLogger.CLIENT.info("Audit", id + "_INFO");
                DebugLogger.CLIENT.stateChange("Audit", id, "state", 1, 2);
                DebugLogger.CLIENT.debug("Audit", id + "_DEBUG");
                DebugLogger.CLIENT.branch("Audit", id + "_BRANCH", true);
                DebugLogger.CLIENT.debugStateChange("Audit", id, "detail", 1, 2);
                DebugLogger.CLIENT.trace("Audit", id + "_TRACE");
                DebugLogger.CLIENT.entering("Audit", id + "_ENTER");
                DebugLogger.CLIENT.exiting("Audit", id + "_EXIT");
                DebugLogger.CLIENT.traceBranch("Audit", id + "_TBRANCH", true);
                DebugLogger.CLIENT.traceStateChange("Audit", id, "frame", 1, 2);
                List<Event> events = own.events.subList(before, own.events.size());
                int expected = developer ? (level >= 1 ? 2 : 0) + (level >= 2 ? 3 : 0) + (level >= 3 ? 5 : 0) : 0;
                check(events.size() == expected, "档位门槛 " + id);
                for (Event event : events) {
                    check(event.message().contains("[" + event.level() + "]"), "文本等级与真实事件一致");
                }
                check(clientLog().contains(id + "_TRACE") == (developer && level == 3), "TRACE 文件过滤 " + id);
            }
        }

        DebugLogger.SERVER.configure(false, 0);
        DebugLogger.CLIENT.configure(true, 3);
        DebugLogger.CLIENT.trace("Audit", "CLIENT_ONLY_TRACE");
        DebugLogger.SERVER.trace("Audit", "SERVER_DISABLED_TRACE");
        DebugLogger.SERVER.warn("Audit", "SERVER_ALWAYS_WARN");
        DebugLogger.SERVER.configure(true, 2);
        DebugLogger.SERVER.debug("Audit", "SERVER_DETAIL");
        DebugLogger.SERVER.trace("Audit", "SERVER_FILTERED_TRACE");
        check(DebugLogger.CLIENT.isEnabled(3), "服务端更新不覆盖客户端");
        check(clientLog().contains("CLIENT_ONLY_TRACE"), "客户端 TRACE 落盘");
        check(!serverLog().contains("CLIENT_ONLY_TRACE"), "文件不会混端");
        check(serverLog().contains("SERVER_ALWAYS_WARN"), "服务端关闭时保留警告");
        check(serverLog().contains("SERVER_DETAIL"), "服务端详细档落盘");
        check(!serverLog().contains("SERVER_FILTERED_TRACE"), "服务端详细档过滤 TRACE");
        DebugLogger.CLIENT.configure(false, 3);
        check(DebugLogger.SERVER.isEnabled(2), "客户端关闭不影响服务端");
        DebugLogger.CLIENT.module("Legacy").error("对象 {}", "value", new IllegalArgumentException("LEGACY_STACK"));
        check(clientLog().contains("对象 value"), "保留 {} 格式参数");
        check(clientLog().contains("IllegalArgumentException: LEGACY_STACK"), "保留末尾 Throwable");
        DebugLogger.CLIENT.warn("Unified", "参数 {}，比例 {}%，色值 {}", "value", 24,
                LogValues.hex(0x00abcdef, 8));
        DebugLogger.CLIENT.error("Unified", "直接接口异常 {}", "value", new IllegalStateException("DIRECT_STACK"));
        check(clientLog().contains("参数 value，比例 24%，色值 00ABCDEF"), "统一参数格式与十六进制补零");
        check(clientLog().contains("IllegalStateException: DIRECT_STACK"), "直接接口保留末尾异常");
        check(LogValues.decimal(1.235, 2).toString().equals("1.24"), "小数精度与舍入");
        check(LogValues.decimal(Double.NaN, 2).toString().equals("NaN"), "特殊浮点数");
        DebugLogger.CLIENT.debug("Unified", "关闭时不格式化 {}", new Object() {
            @Override public String toString() { throw new AssertionError("日志关闭时不应求值"); }
        });

        var fileAppender = configuration.getAppender("YouzaiWorldCore.CLIENT.File");
        DebugLogger.CLIENT.configure(true, 3);
        DebugLogger.CLIENT.configure(true, 1);
        check(configuration.getAppender("YouzaiWorldCore.CLIENT.File") == fileAppender, "实时切换复用输出器");
        check(DebugLogger.forFile(ModPaths.clientRoot().resolve("map_module/map.db")) == DebugLogger.CLIENT, "共享存储归属客户端");
        check(DebugLogger.forFile(ModPaths.clientRoot().resolve("../server/config/global_settings.json")) == DebugLogger.SERVER, "归一化后按服务端归属");
        check(DebugLogger.bootstrap() == DebugLogger.CLIENT, "公共注册选择物理客户端");
        LogManager.getLogger("OtherMod").debug("UNRELATED_DEBUG");
        check(root.getLevel() == Level.INFO, "根日志级别保持原值");
        check(root.getAppenders().size() == 1 && root.getAppenders().get("Game") == game, "游戏输出器保持原值");
        check(game.events.stream().noneMatch(e -> e.message().contains("UNRELATED_DEBUG")), "其它模组未被提高阈值");
        check(game.events.stream().allMatch(e -> e.level().isMoreSpecificThan(Level.INFO)), "游戏通道仍只接收 INFO 以上");
        context.stop();
        System.out.println("实际日志行为通过 " + checks + " 项检查。");
    }

    /** 对迁移前后的全部模板进行真实格式化对比，覆盖百分号、精度与补零。 */
    private static void checkMigration(java.nio.file.Path file) throws Exception {
        var templates = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonArray();
        for (var item : templates) {
            var record = item.getAsJsonObject();
            var formats = record.getAsJsonArray("specifiers");
            Object[] before = new Object[formats.size()], after = new Object[formats.size()];
            for (int i = 0; i < formats.size(); i++) {
                String format = formats.get(i).getAsString();
                Object value;
                if (format.equals("%s")) value = "sample";
                else if (format.equals("%b")) value = Boolean.TRUE;
                else if (format.endsWith("f")) value = Double.valueOf(1.234567);
                else value = Integer.valueOf(37);
                before[i] = value;
                after[i] = format.endsWith("f") ? LogValues.decimal(value, Integer.parseInt(format.substring(2, format.length()-1)))
                        : format.endsWith("X") ? LogValues.hex(value, Integer.parseInt(format.substring(2, format.length()-1))) : value;
            }
            String expected = String.format(java.util.Locale.ROOT, record.get("before").getAsString(), before);
            String actual = org.slf4j.helpers.MessageFormatter.arrayFormat(record.get("after").getAsString(), after).getMessage();
            check(expected.equals(actual), "迁移输出不同：" + expected + " / " + actual);
        }
        System.out.println("占位符迁移文本对比通过：" + checks + " 个模板。");
    }

    /** 仅解析完整源码的语法树，不执行整包编译、注解处理或符号解析。 */
    private static void parseSources(java.nio.file.Path root) throws Exception {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, java.nio.charset.StandardCharsets.UTF_8);
             var paths = Files.walk(root.resolve("src"))) {
            var sources = paths.filter(p -> p.toString().endsWith(".java")).toList();
            var task = (com.sun.source.util.JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("--release", "25", "-proc:none"), null, files.getJavaFileObjectsFromPaths(sources));
            for (var tree : task.parse()) { /* 强制解析全部源文件。 */ }
            var errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR).toList();
            if (!errors.isEmpty()) throw new AssertionError(errors.toString());
            System.out.println("完整源码语法解析通过：" + sources.size() + " 个 Java 文件。");
        }
    }
}
