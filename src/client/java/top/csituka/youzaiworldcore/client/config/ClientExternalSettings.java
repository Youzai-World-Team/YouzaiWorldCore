package top.csituka.youzaiworldcore.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>{@code logToFile} — 输出日志到 latest.log</li>
 *   <li>{@code debugAddress} — 调试服务器地址</li>
 *   <li>{@code debugPort} — 调试服务器端口</li>
 * </ul>
 */
public final class ClientExternalSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("ClientExternalSettings");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("client_external_settings.json");

    // ===== 运行时状态 =====
    private static boolean devModeEnabled = false;
    private static boolean logToFile = false;
    private static String debugAddress = "localhost";
    private static String debugPort = "25565";

    private ClientExternalSettings() {}

    // ===== 读取 =====

    public static boolean isDevModeEnabled() {
        return devModeEnabled;
    }

    public static boolean isLogToFile() {
        return logToFile;
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
        save();
    }

    public static void setLogToFile(boolean value) {
        logToFile = value;
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

            if (root.has("logToFile") && !root.get("logToFile").isJsonNull())
                logToFile = root.get("logToFile").getAsBoolean();

            if (root.has("debugAddress") && !root.get("debugAddress").isJsonNull())
                debugAddress = root.get("debugAddress").getAsString();

            if (root.has("debugPort") && !root.get("debugPort").isJsonNull())
                debugPort = root.get("debugPort").getAsString();

            LOGGER.info("已从 {} 加载客户端外部设置", CONFIG_FILE);
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
            root.addProperty("logToFile", logToFile);
            root.addProperty("debugAddress", debugAddress);
            root.addProperty("debugPort", debugPort);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存客户端外部设置失败: {}", e.getMessage());
        }
    }
}
