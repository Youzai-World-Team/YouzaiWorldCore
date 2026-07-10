package top.csituka.youzaiworldcore.config;

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
 * 服务端外部设置持久化配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/server_external_settings.json}
 * <p>
 * 当前支持设置：
 * <ul>
 *   <li>{@code devModeEnabled} — 启用开发者模式（服务端专用）</li>
 *   <li>{@code logToFile} — 输出详细日志到 latest.log</li>
 * </ul>
 */
public final class ServerExternalSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ServerExternalSettings");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("server_external_settings.json");

    private static boolean devModeEnabled = false;
    private static boolean logToFile = false;

    private ServerExternalSettings() {}

    // ===== 读取 =====

    public static boolean isDevModeEnabled() {
        return devModeEnabled;
    }

    public static boolean isLogToFile() {
        return logToFile;
    }

    // ===== 持久化 =====

    /** 从文件加载配置并同步到 DebugLogger（不存在则创建默认文件） */
    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            syncToDebugLogger();
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

            syncToDebugLogger();
        } catch (Exception e) {
            LOGGER.error("加载服务端外部设置失败: {}", e.getMessage());
        }
    }

    /** 保存配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("devModeEnabled", devModeEnabled);
            root.addProperty("logToFile", logToFile);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存服务端外部设置失败: {}", e.getMessage());
        }
    }

    /** 将当前设置同步到 DebugLogger */
    private static void syncToDebugLogger() {
        DebugLogger.setDevModeEnabled(devModeEnabled);
        DebugLogger.setLogToFile(logToFile);
    }
}
