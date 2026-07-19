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
 * 更新检查器配置（专用服务端生效，通过配置文件）。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/update_checker.json}
 * </p>
 * <p>客户端（含内嵌服务端）的地址在「开发者」设置中配置，本配置仅用于专用服务端。</p>
 */
@SuppressWarnings({"null", "unused"})
public final class UpdateCheckerConfig {

    public static final String MODULE = "UpdateCheckerConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/UpdateCheckerConfig");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("update_checker.json");

    /** 总开关，默认 true */
    private static boolean enabled = true;

    /** 检查更新基址（自动附加 /version.json；空值回退默认 https://mcyzw.top/yzwc） */
    private static String checkAddress = "https://mcyzw.top/yzwc";

    /** 下载页（跳转）基址（自动附加 ?version=&type=；空值回退默认 https://mcyzw.top/yzwc） */
    private static String jumpAddress = "https://mcyzw.top/yzwc";

    /** 服务器启动后是否检查（控制台日志） */
    private static boolean checkOnStartupServer = true;

    /** 客户端启动后是否检查（标题界面公告） */
    private static boolean checkOnStartupClient = true;

    /** 是否在服务端控制台输出更新横幅 */
    private static boolean announceToConsole = true;

    /** 是否在客户端标题界面右侧面板显示更新信息 */
    private static boolean showOnTitleScreen = true;

    private UpdateCheckerConfig() {
    }

    // ===== 读取 =====

    public static boolean isEnabled() {
        return enabled;
    }

    /** @return 检查更新基址（可能为空，调用方应交给 UpdateChecker.buildCheckUrl 处理默认） */
    public static String getCheckAddress() {
        return checkAddress;
    }

    /** @return 下载页（跳转）基址（可能为空，调用方应交给 UpdateChecker.buildJumpUrl 处理默认） */
    public static String getJumpAddress() {
        return jumpAddress;
    }

    public static boolean isCheckOnStartupServer() {
        return checkOnStartupServer;
    }

    public static boolean isCheckOnStartupClient() {
        return checkOnStartupClient;
    }

    public static boolean isAnnounceToConsole() {
        return announceToConsole;
    }

    public static boolean isShowOnTitleScreen() {
        return showOnTitleScreen;
    }

    // ===== 写入 =====

    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
    }

    public static void setCheckAddress(String value) {
        DebugLogger.entering(MODULE, "setCheckAddress", "value=" + value);
        checkAddress = (value == null) ? "" : value;
        save();
        DebugLogger.exiting(MODULE, "setCheckAddress", "1");
    }

    public static void setJumpAddress(String value) {
        DebugLogger.entering(MODULE, "setJumpAddress", "value=" + value);
        jumpAddress = (value == null) ? "" : value;
        save();
        DebugLogger.exiting(MODULE, "setJumpAddress", "1");
    }

    // ===== 持久化 =====

    /** 从文件加载配置（不存在则写入默认配置） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");
        if (!Files.exists(CONFIG_FILE)) {
            DebugLogger.info(MODULE, "配置文件不存在，写入默认配置");
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                return;
            }
            if (root.has("enabled") && !root.get("enabled").isJsonNull()) {
                enabled = root.get("enabled").getAsBoolean();
            }
            if (root.has("checkAddress") && !root.get("checkAddress").isJsonNull()) {
                checkAddress = root.get("checkAddress").getAsString();
            }
            if (root.has("jumpAddress") && !root.get("jumpAddress").isJsonNull()) {
                jumpAddress = root.get("jumpAddress").getAsString();
            }
            if (root.has("checkOnStartupServer") && !root.get("checkOnStartupServer").isJsonNull()) {
                checkOnStartupServer = root.get("checkOnStartupServer").getAsBoolean();
            }
            if (root.has("checkOnStartupClient") && !root.get("checkOnStartupClient").isJsonNull()) {
                checkOnStartupClient = root.get("checkOnStartupClient").getAsBoolean();
            }
            if (root.has("announceToConsole") && !root.get("announceToConsole").isJsonNull()) {
                announceToConsole = root.get("announceToConsole").getAsBoolean();
            }
            if (root.has("showOnTitleScreen") && !root.get("showOnTitleScreen").isJsonNull()) {
                showOnTitleScreen = root.get("showOnTitleScreen").getAsBoolean();
            }
            DebugLogger.info(MODULE, "已加载配置: enabled=%s, checkAddress=%s, jumpAddress=%s",
                    enabled, checkAddress, jumpAddress);
        } catch (Exception e) {
            LOGGER.error("加载更新检查器配置失败: {}", e.getMessage());
        }
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重新加载配置（供 /yzwc reload 调用） */
    public static void reload() {
        DebugLogger.entering(MODULE, "reload");
        load();
        DebugLogger.exiting(MODULE, "reload");
    }

    /** 保存当前配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("checkAddress", checkAddress);
            root.addProperty("jumpAddress", jumpAddress);
            root.addProperty("checkOnStartupServer", checkOnStartupServer);
            root.addProperty("checkOnStartupClient", checkOnStartupClient);
            root.addProperty("announceToConsole", announceToConsole);
            root.addProperty("showOnTitleScreen", showOnTitleScreen);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存更新检查器配置失败: {}", e.getMessage());
        }
    }
}
