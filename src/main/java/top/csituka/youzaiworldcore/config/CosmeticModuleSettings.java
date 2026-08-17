package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 自定义皮肤与披风模块的服务端配置。
 * <p>存放位置：{@code yzwc/server/config/global_settings.json} 的 {@code cosmetic_module} 分节。</p>
 */
public final class CosmeticModuleSettings {

    private static final String MODULE = "CosmeticModuleSettings";

    /** 网络解码与服务端配置共同使用的硬上限，避免超大数组在进入处理器前分配。 */
    public static final int ABSOLUTE_MAX_FILE_BYTES = 512 * 1024;

    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_MAX_FILE_BYTES = ABSOLUTE_MAX_FILE_BYTES;
    private static final int DEFAULT_UPLOAD_COOLDOWN_SECONDS = 30;
    private static final int DEFAULT_REQUEST_COOLDOWN_SECONDS = 10;
    private static final boolean DEFAULT_REQUIRE_AUTHENTICATED = true;

    private static boolean enabled = DEFAULT_ENABLED;
    private static int maxFileBytes = DEFAULT_MAX_FILE_BYTES;
    private static int uploadCooldownSeconds = DEFAULT_UPLOAD_COOLDOWN_SECONDS;
    private static int requestCooldownSeconds = DEFAULT_REQUEST_COOLDOWN_SECONDS;
    private static boolean requireAuthenticated = DEFAULT_REQUIRE_AUTHENTICATED;

    private CosmeticModuleSettings() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getMaxFileBytes() {
        return maxFileBytes;
    }

    public static int getUploadCooldownSeconds() {
        return uploadCooldownSeconds;
    }

    public static int getRequestCooldownSeconds() {
        return requestCooldownSeconds;
    }

    public static boolean isRequireAuthenticated() {
        return requireAuthenticated;
    }

    /** 从 {@code cosmetic_module} 分节加载配置。 */
    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.COSMETIC_MODULE);
        if (section.isEmpty()) {
            writeDefaults();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        enabled = section.getBoolean("enabled", enabled);
        maxFileBytes = section.getInt("max_file_bytes", maxFileBytes, 1, ABSOLUTE_MAX_FILE_BYTES);
        uploadCooldownSeconds = section.getInt(
                "upload_cooldown_seconds", uploadCooldownSeconds, 0, 3600);
        requestCooldownSeconds = section.getInt(
                "request_cooldown_seconds", requestCooldownSeconds, 0, 3600);
        requireAuthenticated = section.getBoolean("require_authenticated", requireAuthenticated);
        DebugLogger.info(MODULE,
                "已加载配置: enabled=%s, maxFileBytes=%d, uploadCooldown=%ds, requestCooldown=%ds, requireAuthenticated=%s",
                enabled, maxFileBytes, uploadCooldownSeconds, requestCooldownSeconds, requireAuthenticated);
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置并写入模块默认值。 */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED;
        maxFileBytes = DEFAULT_MAX_FILE_BYTES;
        uploadCooldownSeconds = DEFAULT_UPLOAD_COOLDOWN_SECONDS;
        requestCooldownSeconds = DEFAULT_REQUEST_COOLDOWN_SECONDS;
        requireAuthenticated = DEFAULT_REQUIRE_AUTHENTICATED;
        save();
    }

    /** 保存当前配置。 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.COSMETIC_MODULE);
        section.set("enabled", enabled);
        section.set("max_file_bytes", maxFileBytes);
        section.set("upload_cooldown_seconds", uploadCooldownSeconds);
        section.set("request_cooldown_seconds", requestCooldownSeconds);
        section.set("require_authenticated", requireAuthenticated);
        GlobalSettings.save();
    }
}
