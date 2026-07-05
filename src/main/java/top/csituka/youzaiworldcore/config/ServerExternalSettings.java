package top.csituka.youzaiworldcore.config;

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
 * 服务端外部设置持久化配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/server_external_settings.json}
 * <p>
 * 当前支持设置：
 * <ul>
 *   <li>{@code logToFile} — 输出详细日志到 latest.log（独立于客户端开发者模式，服务端专用）</li>
 * </ul>
 */
public final class ServerExternalSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ServerExternalSettings");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("server_external_settings.json");

    private static boolean logToFile = false;

    private ServerExternalSettings() {}

    // ===== 读取 =====

    public static boolean isLogToFile() {
        return logToFile;
    }

    // ===== 持久化 =====

    /** 从文件加载配置（不存在则创建默认文件并返回 false） */
    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) return;

            if (root.has("logToFile") && !root.get("logToFile").isJsonNull())
                logToFile = root.get("logToFile").getAsBoolean();
        } catch (Exception e) {
            LOGGER.error("加载服务端外部设置失败: {}", e.getMessage());
        }
    }

    /** 保存配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("logToFile", logToFile);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存服务端外部设置失败: {}", e.getMessage());
        }
    }
}
