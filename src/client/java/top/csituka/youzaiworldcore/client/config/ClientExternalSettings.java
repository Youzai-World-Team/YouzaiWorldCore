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
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存客户端外部设置失败: {}", e.getMessage());
        }
    }
}
