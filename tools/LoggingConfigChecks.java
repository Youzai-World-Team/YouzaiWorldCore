package top.csituka.youzaiworldcore.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import top.csituka.youzaiworldcore.config.ConfigCrash;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.config.ServerExternalSettings;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.ClientGlobalSettings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 使用真实配置存储与读取代码，验证默认值、两端重读、落盘和非法档位隔离。 */
public final class LoggingConfigChecks {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    private static void write(Path path, String fields) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "{\"core_module\":{" + fields + "}}");
    }
    private static JsonObject core(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonObject("core_module");
    }

    public static void main(String[] args) throws Exception {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        DefaultConfiguration configuration = new DefaultConfiguration();
        var root = configuration.getRootLogger();
        for (String name : List.copyOf(root.getAppenders().keySet())) root.removeAppender(name);
        context.start(configuration);

        GlobalSettings.load();
        ClientGlobalSettings.load();
        check(core(ModPaths.globalSettingsFile()).get("log_level").getAsInt() == 0, "新服务端完整写出默认档位");
        check(core(ModPaths.clientSettingsFile()).get("log_level").getAsInt() == 0, "新客户端完整写出默认档位");
        check(!DebugLogger.SERVER.isEnabled() && !DebugLogger.CLIENT.isEnabled(), "两端默认关闭");
        for (int level = 0; level <= 3; level++) {
            write(ModPaths.globalSettingsFile(), "\"dev_mode_enabled\":true,\"log_level\":" + level);
            GlobalSettings.load();
            check(ServerExternalSettings.getLogLevel() == level && DebugLogger.SERVER.getLogLevel() == level, "服务端四档重读 " + level);
            check(DebugLogger.CLIENT.getLogLevel() == 0, "服务端重读不覆盖客户端 " + level);
        }
        ClientExternalSettings.setDevModeEnabled(true);
        ClientExternalSettings.setLogLevel(3);
        check(DebugLogger.CLIENT.isEnabled(3), "客户端设置立即生效");
        check(core(ModPaths.clientSettingsFile()).get("log_level").getAsInt() == 3, "客户端设置落盘");
        write(ModPaths.globalSettingsFile(), "\"dev_mode_enabled\":true,\"log_level\":2,\"log_to_file\":false");
        GlobalSettings.load();
        check(DebugLogger.SERVER.isEnabled(2) && !DebugLogger.SERVER.isEnabled(3), "新整数档位独立于旧布尔字段");
        check(!core(ModPaths.globalSettingsFile()).has("log_to_file"), "保存时移除旧字段");
        check(DebugLogger.CLIENT.isEnabled(3), "服务端重载保留客户端3档");
        write(ModPaths.globalSettingsFile(), "\"dev_mode_enabled\":true,\"log_to_file\":true");
        GlobalSettings.load();
        check(DebugLogger.SERVER.getLogLevel() == 0, "缺少新字段时默认0，不迁移旧值");
        check(core(ModPaths.globalSettingsFile()).get("log_level").getAsInt() == 0, "缺失字段补全到磁盘");

        for (String value : List.of("-1", "4", "1.5", "4294967297", "true", "\"3\"", "{}")) {
            write(ModPaths.globalSettingsFile(), "\"dev_mode_enabled\":true,\"log_level\":" + value);
            try {
                GlobalSettings.load();
                throw new AssertionError("非法档位未拒绝：" + value);
            } catch (ConfigCrash.ConfigFormatException expected) {
                check(core(ModPaths.globalSettingsFile()).get("log_level").getAsInt() == 0, "非法档位恢复默认 " + value);
                check(DebugLogger.CLIENT.isEnabled(3), "服务端错误恢复不影响客户端 " + value);
            }
        }
        check(Files.readString(DebugLogger.SERVER.logFile()).contains("log_level"), "关闭诊断后仍记录配置故障位置");
        write(ModPaths.clientSettingsFile(), "\"dev_mode_enabled\":false,\"log_level\":1");
        ClientGlobalSettings.load();
        check(!DebugLogger.CLIENT.isEnabled(), "客户端文件重读立即关闭开发者日志");
        check(DebugLogger.SERVER.getLogLevel() == 0, "客户端文件重读不修改服务端");
        context.stop();
        System.out.println("真实配置读写通过 " + checks + " 项检查。");
    }
}
