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
 * AFK（挂机）功能配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/afk.json}
 * <p>
 * 由 {@code /yzwc afk settings <key> <value>} 命令在运行时修改并持久化。
 * 检测架构：客户端输入检测（精确，需客户端装模组）+ 服务端近似检测（位置 /
 * 视角变化，兜底原版客户端），由 {@link #detectMode} 控制。
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class AfkConfig {

    public static final String MODULE = "AfkConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/AfkConfig");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("afk.json");

    /** AFK 检测模式 */
    public enum DetectMode {
        /** 仅客户端心跳检测（原版客户端玩家永不判定 AFK） */
        CLIENT,
        /** 仅服务端近似检测（位置/视角变化） */
        SERVER,
        /** 双通道：任一通道判定活动即不算 AFK（默认） */
        BOTH
    }

    /** 功能总开关，默认 true */
    private static boolean enabled = true;
    /** 检测模式，默认 BOTH */
    private static DetectMode detectMode = DetectMode.BOTH;
    /** 触发 AFK 的无活动时长（秒），默认 300，下限 {@link #MIN_THRESHOLD_SECONDS} */
    private static int thresholdSeconds = 300;
    /** 触发阈值下限（秒）：至少 30 秒 */
    public static final int MIN_THRESHOLD_SECONDS = 30;
    /** 是否在 Tab 列表显示 [AFK] 前缀，默认 true */
    private static boolean tabPrefixEnabled = true;
    /** 进入/退出 AFK 是否向全体广播，默认 true */
    private static boolean broadcastEnabled = true;
    /** AFK 期间是否无敌（无限时长的抗性提升 V），默认 false */
    private static boolean invulnerableEnabled = false;
    /** 超过该时长（秒）自动踢出，0 = 禁用（默认），须 >= 触发阈值 */
    private static int autoKickSeconds = 0;
    /** 是否允许玩家用 /yzwc afk 手动切换，默认 true */
    private static boolean manualToggleEnabled = true;

    private AfkConfig() {
    }

    // ===== 读取 =====

    /** @return 功能是否启用（由 {@code /yzwc afk settings enabled} 控制） */
    public static boolean isEnabled() {
        return enabled;
    }

    /** @return 当前检测模式 */
    public static DetectMode getDetectMode() {
        return detectMode;
    }

    /** @return 触发 AFK 的无活动时长（秒） */
    public static int getThresholdSeconds() {
        return thresholdSeconds;
    }

    /** @return 是否在 Tab 列表显示 [AFK] 前缀 */
    public static boolean isTabPrefixEnabled() {
        return tabPrefixEnabled;
    }

    /** @return 进入/退出 AFK 是否广播 */
    public static boolean isBroadcastEnabled() {
        return broadcastEnabled;
    }

    /** @return AFK 期间是否无敌 */
    public static boolean isInvulnerableEnabled() {
        return invulnerableEnabled;
    }

    /** @return 自动踢出时长（秒），0 = 禁用 */
    public static int getAutoKickSeconds() {
        return autoKickSeconds;
    }

    /** @return 是否允许 /yzwc afk 手动切换 */
    public static boolean isManualToggleEnabled() {
        return manualToggleEnabled;
    }

    // ===== 运行时修改（供 /yzwc afk settings 命令调用）=====

    /** 设置功能总开关并持久化（disabled 时立即清除全部 AFK 状态） */
    public static void setEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setEnabled", "value=" + value);
        if (enabled == value) {
            DebugLogger.info(MODULE, "enabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "enabled", enabled, value);
        enabled = value;
        save();
        DebugLogger.exiting(MODULE, "setEnabled", "1");
    }

    /** 设置检测模式并持久化 */
    public static void setDetectMode(DetectMode mode) {
        DebugLogger.entering(MODULE, "setDetectMode", "mode=" + mode);
        if (mode == null || detectMode == mode) {
            DebugLogger.info(MODULE, "detectMode 未变化或非法，跳过保存");
            DebugLogger.exiting(MODULE, "setDetectMode", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "detectMode", detectMode, mode);
        detectMode = mode;
        save();
        DebugLogger.exiting(MODULE, "setDetectMode", "1");
    }

    /** 设置触发阈值（秒，至少 {@link #MIN_THRESHOLD_SECONDS}）并持久化 */
    public static void setThresholdSeconds(int seconds) {
        DebugLogger.entering(MODULE, "setThresholdSeconds", "seconds=" + seconds);
        if (seconds < MIN_THRESHOLD_SECONDS) {
            DebugLogger.warn(MODULE, "触发阈值非法: %d（必须 >= %d），忽略", seconds, MIN_THRESHOLD_SECONDS);
            DebugLogger.exiting(MODULE, "setThresholdSeconds", "invalid");
            return;
        }
        if (thresholdSeconds == seconds) {
            DebugLogger.info(MODULE, "thresholdSeconds 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setThresholdSeconds", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "thresholdSeconds", thresholdSeconds, seconds);
        thresholdSeconds = seconds;
        // 自动踢出时长若小于新阈值则同步抬升（踢出必须晚于判定 AFK）
        if (autoKickSeconds > 0 && autoKickSeconds < thresholdSeconds) {
            DebugLogger.stateChange(MODULE, "AfkConfig", "autoKickSeconds", autoKickSeconds, thresholdSeconds);
            autoKickSeconds = thresholdSeconds;
        }
        save();
        DebugLogger.exiting(MODULE, "setThresholdSeconds", "1");
    }

    /** 设置 Tab 前缀开关并持久化 */
    public static void setTabPrefixEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setTabPrefixEnabled", "value=" + value);
        if (tabPrefixEnabled == value) {
            DebugLogger.info(MODULE, "tabPrefixEnabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setTabPrefixEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "tabPrefixEnabled", tabPrefixEnabled, value);
        tabPrefixEnabled = value;
        save();
        DebugLogger.exiting(MODULE, "setTabPrefixEnabled", "1");
    }

    /** 设置广播开关并持久化 */
    public static void setBroadcastEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setBroadcastEnabled", "value=" + value);
        if (broadcastEnabled == value) {
            DebugLogger.info(MODULE, "broadcastEnabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setBroadcastEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "broadcastEnabled", broadcastEnabled, value);
        broadcastEnabled = value;
        save();
        DebugLogger.exiting(MODULE, "setBroadcastEnabled", "1");
    }

    /** 设置无敌开关并持久化 */
    public static void setInvulnerableEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setInvulnerableEnabled", "value=" + value);
        if (invulnerableEnabled == value) {
            DebugLogger.info(MODULE, "invulnerableEnabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setInvulnerableEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "invulnerableEnabled", invulnerableEnabled, value);
        invulnerableEnabled = value;
        save();
        DebugLogger.exiting(MODULE, "setInvulnerableEnabled", "1");
    }

    /** 设置自动踢出时长（秒，0 = 禁用，须 >= 触发阈值）并持久化 */
    public static void setAutoKickSeconds(int seconds) {
        DebugLogger.entering(MODULE, "setAutoKickSeconds", "seconds=" + seconds);
        if (seconds < 0 || (seconds > 0 && seconds < thresholdSeconds)) {
            DebugLogger.warn(MODULE, "自动踢出时长非法: %d（必须 0 或 >= 阈值 %d），忽略", seconds, thresholdSeconds);
            DebugLogger.exiting(MODULE, "setAutoKickSeconds", "invalid");
            return;
        }
        if (autoKickSeconds == seconds) {
            DebugLogger.info(MODULE, "autoKickSeconds 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setAutoKickSeconds", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "autoKickSeconds", autoKickSeconds, seconds);
        autoKickSeconds = seconds;
        save();
        DebugLogger.exiting(MODULE, "setAutoKickSeconds", "1");
    }

    /** 设置手动切换开关并持久化 */
    public static void setManualToggleEnabled(boolean value) {
        DebugLogger.entering(MODULE, "setManualToggleEnabled", "value=" + value);
        if (manualToggleEnabled == value) {
            DebugLogger.info(MODULE, "manualToggleEnabled 未变化，跳过保存");
            DebugLogger.exiting(MODULE, "setManualToggleEnabled", "no-change");
            return;
        }
        DebugLogger.stateChange(MODULE, "AfkConfig", "manualToggleEnabled", manualToggleEnabled, value);
        manualToggleEnabled = value;
        save();
        DebugLogger.exiting(MODULE, "setManualToggleEnabled", "1");
    }

    // ===== 持久化 =====

    /** 从文件加载配置（不存在则写入默认配置） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        if (!Files.exists(CONFIG_FILE)) {
            DebugLogger.info(MODULE, "配置文件不存在，写入默认配置 (enabled=%s, mode=%s, threshold=%ds)",
                    enabled, detectMode, thresholdSeconds);
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root != null) {
                if (root.has("enabled") && !root.get("enabled").isJsonNull()) {
                    enabled = root.get("enabled").getAsBoolean();
                }
                if (root.has("detect_mode") && !root.get("detect_mode").isJsonNull()) {
                    try {
                        detectMode = DetectMode.valueOf(root.get("detect_mode").getAsString().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        DebugLogger.warn(MODULE, "未知 detect_mode: %s，回退 BOTH",
                                root.get("detect_mode").getAsString());
                        detectMode = DetectMode.BOTH;
                    }
                }
                if (root.has("threshold_seconds") && !root.get("threshold_seconds").isJsonNull()) {
                    thresholdSeconds = Math.max(MIN_THRESHOLD_SECONDS,
                            root.get("threshold_seconds").getAsInt());
                }
                if (root.has("tab_prefix_enabled") && !root.get("tab_prefix_enabled").isJsonNull()) {
                    tabPrefixEnabled = root.get("tab_prefix_enabled").getAsBoolean();
                }
                if (root.has("broadcast_enabled") && !root.get("broadcast_enabled").isJsonNull()) {
                    broadcastEnabled = root.get("broadcast_enabled").getAsBoolean();
                }
                if (root.has("invulnerable_enabled") && !root.get("invulnerable_enabled").isJsonNull()) {
                    invulnerableEnabled = root.get("invulnerable_enabled").getAsBoolean();
                }
                if (root.has("auto_kick_seconds") && !root.get("auto_kick_seconds").isJsonNull()) {
                    autoKickSeconds = Math.max(0, root.get("auto_kick_seconds").getAsInt());
                    if (autoKickSeconds > 0 && autoKickSeconds < thresholdSeconds) {
                        DebugLogger.warn(MODULE, "auto_kick_seconds 小于阈值，抬升至 %d", thresholdSeconds);
                        autoKickSeconds = thresholdSeconds;
                    }
                }
                if (root.has("manual_toggle_enabled") && !root.get("manual_toggle_enabled").isJsonNull()) {
                    manualToggleEnabled = root.get("manual_toggle_enabled").getAsBoolean();
                }
            }
            DebugLogger.info(MODULE,
                    "已加载配置: enabled=%s, detect_mode=%s, threshold_seconds=%d, tab_prefix=%s, "
                            + "broadcast=%s, invulnerable=%s, auto_kick=%ds, manual_toggle=%s",
                    enabled, detectMode, thresholdSeconds, tabPrefixEnabled,
                    broadcastEnabled, invulnerableEnabled, autoKickSeconds, manualToggleEnabled);
        } catch (Exception e) {
            LOGGER.error("加载 AFK 配置失败: {}", e.getMessage());
            DebugLogger.exception(MODULE, "load", e);
        }

        DebugLogger.exiting(MODULE, "load");
    }

    /** 保存当前配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", enabled);
            root.addProperty("detect_mode", detectMode.name());
            root.addProperty("threshold_seconds", thresholdSeconds);
            root.addProperty("tab_prefix_enabled", tabPrefixEnabled);
            root.addProperty("broadcast_enabled", broadcastEnabled);
            root.addProperty("invulnerable_enabled", invulnerableEnabled);
            root.addProperty("auto_kick_seconds", autoKickSeconds);
            root.addProperty("manual_toggle_enabled", manualToggleEnabled);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存 AFK 配置失败: {}", e.getMessage());
            DebugLogger.exception(MODULE, "save", e);
        }
    }
}
