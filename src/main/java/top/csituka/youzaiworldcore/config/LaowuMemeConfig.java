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
 * 老吴贴贴事件（laowu meme）配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/laowu_meme.json}
 * <p>
 * 由 {@code /yzwc event global laowu [true|false]} 控制，为<b>服务器全局</b>开关：
 * 启用时全体玩家的猫都可能触发老吴贴贴，禁用时对所有玩家立即停止并释放配对。
 * 本类仅负责配置的持久化与读取，状态机见
 * {@link top.csituka.youzaiworldcore.event.LaowuMemeHandler}。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class LaowuMemeConfig {

    public static final String MODULE = "LaowuMemeConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/LaowuMemeConfig");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("laowu_meme.json");

    /** 事件总开关，默认 true（启用）。设为 false 时状态机直接释放全部配对并停止扫描 */
    private static boolean enabled = true;

    private LaowuMemeConfig() {
    }

    /**
     * @return 事件是否启用（由 {@code /yzwc event global laowu [true|false]} 控制）
     */
    public static boolean isEnabled() {
        return enabled;
    }

    // ===== 运行时修改（供 /yzwc event global laowu 命令调用）=====

    /** 设置事件总开关并持久化到配置文件 */
    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.info(MODULE, "enabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "LaowuMemeConfig", "enabled", enabled, value);
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
    }

    // ===== 持久化 =====

    /** 从文件加载配置（不存在则写入默认配置） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        if (!Files.exists(CONFIG_FILE)) {
            DebugLogger.info(MODULE, "配置文件不存在，写入默认配置 (enabled=%s)", enabled);
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root != null && root.has("enabled") && !root.get("enabled").isJsonNull()) {
                enabled = root.get("enabled").getAsBoolean();
            }
            DebugLogger.info(MODULE, "已加载配置: enabled=%s", enabled);
        } catch (Exception e) {
            LOGGER.error("加载老吴贴贴事件配置失败: {}", e.getMessage());
        }

        DebugLogger.exiting(MODULE, "load");
    }

    /** 保存当前配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存老吴贴贴事件配置失败: {}", e.getMessage());
        }
    }
}
