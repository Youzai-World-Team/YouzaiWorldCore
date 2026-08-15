package top.csituka.youzaiworldcore.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端外部设置持久化配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/client_external_settings.json}
 * <p>
 * 保存设置：
 * <ul>
 *   <li>{@code devModeEnabled} — 启用开发者模式</li>
 *   <li>{@code logLevel} — 日志输出丰富度（0=关闭, 1=基本, 2=详细, 3=调试）</li>
 *   <li>{@code debugModeType} — 调试方式 ("embedded" 内嵌服务端 / "dedicated" 专用服务端)</li>
 *   <li>{@code debugAddress} — 调试服务器地址（专用服务端）</li>
 *   <li>{@code debugPort} — 调试服务器端口（专用服务端）</li>
 *   <li>{@code ignoredUpdateVersion} — 已忽略提示的更新版本号</li>
 *   <li>{@code yzuiEnabled} — 是否启用 YZUI 自定义 UI 样式</li>
 *   <li>{@code autoSkipExperimentalWarning} — 是否自动跳过实验性设置警告屏幕</li>
 * </ul>
 */
public final class ClientExternalSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ClientExternalSettings");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("client_external_settings.json");

    // ===== 运行时状态 =====
    private static boolean devModeEnabled = false;
    /** 日志输出丰富度：0=关闭, 1=基本, 2=详细, 3=调试 */
    private static int logLevel = 0;
    private static String debugModeType = "embedded"; // "embedded" 或 "dedicated"
    private static String debugAddress = "localhost";
    private static String debugPort = "25565";

    /** 已忽略提示的更新版本号（非空表示不再在标题界面提示该版本）；仅非强制更新时可忽略 */
    private static String ignoredUpdateVersion = "";

    /** 是否启用 YZUI（自定义 UI 样式），关闭则回退到原版 UI 供资源包替换 */
    private static boolean yzuiEnabled = true;

    /** 是否自动跳过"使用实验性设置的世界不受支持"屏幕 */
    private static boolean autoSkipExperimentalWarning = false;

    /** 自动跳过时执行的操作：{@code "skip"} = 我知道我在做什么（不备份），{@code "backup"} = 创建备份并进入 */
    private static String experimentalWarningSkipAction = "skip";

    private ClientExternalSettings() {}

    // ===== 读取 =====

    public static boolean isDevModeEnabled() {
        return devModeEnabled;
    }

    /** @deprecated 请改用 {@link #getLogLevel()} */
    @Deprecated
    public static boolean isLogToFile() {
        return logLevel > 0;
    }

    /** 获取日志输出丰富度等级（0=关闭, 1=基本, 2=详细, 3=调试） */
    public static int getLogLevel() {
        return logLevel;
    }

    public static String getDebugModeType() {
        return debugModeType;
    }

    public static String getDebugAddress() {
        return debugAddress;
    }

    public static String getDebugPort() {
        return debugPort;
    }

    /** @return 已忽略提示的更新版本号（空字符串表示未忽略任何版本） */
    public static String getIgnoredUpdateVersion() {
        return ignoredUpdateVersion;
    }

    /** @return 是否启用 YZUI 自定义 UI 样式 */
    public static boolean isYzuiEnabled() {
        return yzuiEnabled;
    }

    /** @return 是否自动跳过实验性设置警告屏幕 */
    public static boolean isAutoSkipExperimentalWarning() {
        return autoSkipExperimentalWarning;
    }

    /** @return 自动跳过时执行的操作（"skip" 或 "backup"） */
    public static String getExperimentalWarningSkipAction() {
        return experimentalWarningSkipAction;
    }

    /** @return 自动跳过时是否应创建备份 */
    public static boolean isExperimentalWarningSkipBackup() {
        return "backup".equals(experimentalWarningSkipAction);
    }

    /** 设置被忽略的更新版本号（空值将忽略为 ""）并持久化 */
    public static void setIgnoredUpdateVersion(String value) {
        ignoredUpdateVersion = (value == null) ? "" : value;
        save();
    }

    // ===== 写入 =====

    public static void setDevModeEnabled(boolean value) {
        devModeEnabled = value;
        DebugLogger.setDevModeEnabled(value);
        save();
    }

    /** @deprecated 请改用 {@link #setLogLevel(int)} */
    @Deprecated
    public static void setLogToFile(boolean value) {
        setLogLevel(value ? 1 : 0);
    }

    /** 设置日志输出丰富度等级（0=关闭, 1=基本, 2=详细, 3=调试） */
    public static void setLogLevel(int level) {
        logLevel = Math.max(0, Math.min(3, level));
        DebugLogger.setLogLevel(logLevel);
        // 单人模式集成服务器：只要日志级别 > 0 就开启服务端日志输出
        top.csituka.youzaiworldcore.YouzaiworldCore.logToFile = (logLevel > 0);
        save();
    }

    public static void setDebugModeType(String value) {
        debugModeType = value;
        save();
    }

    public static void setDebugAddress(String value) {
        debugAddress = value;
        save();
    }

    public static void setDebugPort(String value) {
        debugPort = value;
        save();
    }

    /** 设置 YZUI 启用状态并持久化 */
    public static void setYzuiEnabled(boolean value) {
        yzuiEnabled = value;
        DebugLogger.info("ClientExternalSettings", "YZUI 已" + (value ? "启用" : "禁用"));
        save();
    }

    /** 设置是否自动跳过实验性设置警告并持久化 */
    public static void setAutoSkipExperimentalWarning(boolean value) {
        autoSkipExperimentalWarning = value;
        DebugLogger.info("ClientExternalSettings", "自动跳过实验性设置警告已" + (value ? "启用" : "禁用"));
        save();
    }

    /** 设置自动跳过时的操作（"skip" 或 "backup"）并持久化 */
    public static void setExperimentalWarningSkipAction(String value) {
        experimentalWarningSkipAction = ("backup".equals(value)) ? "backup" : "skip";
        DebugLogger.info("ClientExternalSettings",
                "自动跳过操作已设为：" + experimentalWarningSkipAction);
        save();
    }

    // ===== 持久化 =====

    /** 从文件加载配置（不存在则创建默认文件） */
    @SuppressWarnings({"null", "unused"})
    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            if (root.has("devModeEnabled") && !root.get("devModeEnabled").isJsonNull())
                devModeEnabled = root.get("devModeEnabled").getAsBoolean();

            // ===== logLevel 读取（优先新字段，兼容旧版 logToFile 布尔值） =====
            if (root.has("logLevel") && !root.get("logLevel").isJsonNull()) {
                logLevel = Math.max(0, Math.min(3, root.get("logLevel").getAsInt()));
            } else if (root.has("logToFile") && !root.get("logToFile").isJsonNull()) {
                // 兼容旧版：logToFile=true → 级别 1（基本）
                logLevel = root.get("logToFile").getAsBoolean() ? 1 : 0;
            }

            if (root.has("debugModeType") && !root.get("debugModeType").isJsonNull())
                debugModeType = root.get("debugModeType").getAsString();

            if (root.has("debugAddress") && !root.get("debugAddress").isJsonNull())
                debugAddress = root.get("debugAddress").getAsString();

            if (root.has("debugPort") && !root.get("debugPort").isJsonNull())
                debugPort = root.get("debugPort").getAsString();

            if (root.has("ignoredUpdateVersion") && !root.get("ignoredUpdateVersion").isJsonNull())
                ignoredUpdateVersion = root.get("ignoredUpdateVersion").getAsString();

            if (root.has("yzuiEnabled") && !root.get("yzuiEnabled").isJsonNull())
                yzuiEnabled = root.get("yzuiEnabled").getAsBoolean();

            if (root.has("autoSkipExperimentalWarning") && !root.get("autoSkipExperimentalWarning").isJsonNull())
                autoSkipExperimentalWarning = root.get("autoSkipExperimentalWarning").getAsBoolean();

            if (root.has("experimentalWarningSkipAction") && !root.get("experimentalWarningSkipAction").isJsonNull()) {
                String v = root.get("experimentalWarningSkipAction").getAsString();
                experimentalWarningSkipAction = "backup".equals(v) ? "backup" : "skip";
            }

            if (logLevel > 0) {
                LOGGER.info("已从 {} 加载客户端外部设置", CONFIG_FILE);
            }
            // 同步到 DebugLogger 和全局标志
            DebugLogger.setDevModeEnabled(devModeEnabled);
            DebugLogger.setLogLevel(logLevel);
            top.csituka.youzaiworldcore.YouzaiworldCore.logToFile = (logLevel > 0);
        } catch (Exception e) {
            LOGGER.error("加载客户端外部设置失败: {}", e.getMessage());
        }
    }

    /** 保存配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("devModeEnabled", devModeEnabled);
            root.addProperty("logLevel", logLevel);
            root.addProperty("debugModeType", debugModeType);
            root.addProperty("debugAddress", debugAddress);
            root.addProperty("debugPort", debugPort);
            root.addProperty("ignoredUpdateVersion", ignoredUpdateVersion);
            root.addProperty("yzuiEnabled", yzuiEnabled);
            root.addProperty("autoSkipExperimentalWarning", autoSkipExperimentalWarning);
            root.addProperty("experimentalWarningSkipAction", experimentalWarningSkipAction);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存客户端外部设置失败: {}", e.getMessage());
        }
    }
}
