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
 * 天然带电苦力怕功能配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/charged_creeper.json}
 * <p>
 * 功能移植自 Serilum 的 Naturally Charged Creepers
 * 本类仅负责配置的持久化与读取，充电逻辑见
 * {@link top.csituka.youzaiworldcore.event.ChargedCreeperHandler}。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class ChargedCreeperConfig {

    public static final String MODULE = "ChargedCreeperConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/ChargedCreeperConfig");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("charged_creeper.json");

    /** 功能总开关，默认 true（启用）。设为 false 时处理器直接跳过，不判定任何苦力怕 */
    private static boolean enabled = true;

    /** 苦力怕天然带电的概率，取值范围 [0.0, 1.0]，默认 0.1（10%） */
    private static double chance = 0.1;

    private ChargedCreeperConfig() {
    }

    /**
     * @return 功能是否启用（由 {@code /yzwc event naturally_charged_creepers enable} 控制）
     */
    public static boolean isEnabled() {
        return enabled;
    }

    public static double getChance() {
        return chance;
    }

    // ===== 运行时修改（供 /yzwc event naturally_charged_creepers 命令调用）=====

    /** 设置功能总开关并持久化到配置文件 */
    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.info(MODULE, "enabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "ChargedCreeperConfig", "enabled", enabled, value);
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
    }

    /** 设置带电概率（自动钳制到 [0.0, 1.0]）并持久化到配置文件 */
    public static void setChance(double value) {
        DebugLogger.entering(MODULE, "setChance", "value=" + value);
        double clamped = value;
        if (Double.isNaN(clamped)) {
            DebugLogger.info(MODULE, "setChance 收到 NaN，钳制为 0");
            clamped = 0.0;
        } else if (clamped < 0.0) {
            clamped = 0.0;
        } else if (clamped > 1.0) {
            clamped = 1.0;
        }
        if (chance == clamped) {
            DebugLogger.info(MODULE, "chance 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setChance", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "ChargedCreeperConfig", "chance", chance, clamped);
        chance = clamped;
        save();
        DebugLogger.exiting(MODULE, "setChance", "1");
    }

    // ===== 持久化 =====

    /** 从文件加载配置（不存在则写入默认配置），并钳制到合法范围 [0.0, 1.0] */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        if (!Files.exists(CONFIG_FILE)) {
            DebugLogger.info(MODULE, "配置文件不存在，写入默认配置 (chance=%.2f)", chance);
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root != null && root.has("chance") && !root.get("chance").isJsonNull()) {
                chance = root.get("chance").getAsDouble();
            }
            if (root != null && root.has("enabled") && !root.get("enabled").isJsonNull()) {
                enabled = root.get("enabled").getAsBoolean();
            }

            // 钳制到合法范围，避免误配置导致全部/零带电或 NaN
            if (Double.isNaN(chance)) {
                DebugLogger.info(MODULE, "chance 为 NaN，重置为 0");
                chance = 0.0;
            } else if (chance < 0.0) {
                DebugLogger.info(MODULE, "chance=%.4f 低于 0，钳制为 0", chance);
                chance = 0.0;
            } else if (chance > 1.0) {
                DebugLogger.info(MODULE, "chance=%.4f 高于 1，钳制为 1", chance);
                chance = 1.0;
            }

            DebugLogger.info(MODULE, "已加载配置: chance=%.4f", chance);
        } catch (Exception e) {
            LOGGER.error("加载天然带电苦力怕配置失败: {}", e.getMessage());
        }

        DebugLogger.exiting(MODULE, "load");
    }

    /** 保存当前配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("chance", chance);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存天然带电苦力怕配置失败: {}", e.getMessage());
        }
    }
}
