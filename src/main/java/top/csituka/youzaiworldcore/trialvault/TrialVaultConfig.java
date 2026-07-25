package top.csituka.youzaiworldcore.trialvault;

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
 * 试炼宝库无限领奖功能配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/trial_vault.json}
 * <p>
 * 当功能启用时，玩家可对同一试炼宝库重复插钥匙领奖（不受原版每玩家一次的限制）。
 * 参考 trial-chamber-time-removal 的设计思路，原生重写（不依赖其前置）。
 * 本类仅负责配置的持久化，逻辑实现见
 * {@link top.csituka.youzaiworldcore.mixin.trialvault.VaultServerDataMixin}。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class TrialVaultConfig {

    public static final String MODULE = "TrialVaultConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/TrialVaultConfig");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("trial_vault.json");

    /** 功能总开关，默认 true（启用关闭冷却）。设为 false 时 Mixin 放行原版行为 */
    private static boolean enabled = true;

    private TrialVaultConfig() {
    }

    /**
     * @return 功能是否启用（由 {@code /yzwc event trial_vault enable} 控制）
     */
    public static boolean isEnabled() {
        return enabled;
    }

    // ===== 运行时修改（供 /yzwc event trial_vault 命令调用）=====

    /** 设置功能总开关并持久化到配置文件 */
    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.info(MODULE, "enabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "TrialVaultConfig", "enabled", enabled, value);
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
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
            if (root != null && root.has("enabled") && !root.get("enabled").isJsonNull()) {
                enabled = root.get("enabled").getAsBoolean();
            }

            DebugLogger.info(MODULE, "已加载配置: enabled=%s", enabled);
        } catch (Exception e) {
            LOGGER.error("加载试炼宝库配置失败: {}", e.getMessage());
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
            LOGGER.error("保存试炼宝库配置失败: {}", e.getMessage());
        }
    }
}
