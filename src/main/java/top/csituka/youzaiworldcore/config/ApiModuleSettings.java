package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Api 服务端网桥配置。
 * <p>默认连接本机 {@code http://localhost:3000}，用于开发期联调。</p>
 */
public final class ApiModuleSettings {

    private static final String MODULE = "ApiModuleSettings";
    private static final String DEFAULT_BASE_URL = "http://localhost:3000";
    private static final String DEFAULT_SERVER_KEY = "youzai-local-development";
    private static final int DEFAULT_TIMEOUT_SECONDS = 3;

    private static boolean enabled = true;
    private static String baseUrl = DEFAULT_BASE_URL;
    private static String serverKey = DEFAULT_SERVER_KEY;
    private static int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    private ApiModuleSettings() {
    }

    public static boolean isEnabled() { return enabled; }
    public static String getBaseUrl() { return baseUrl; }
    public static String getServerKey() { return serverKey; }
    public static int getTimeoutSeconds() { return timeoutSeconds; }

    public static void load() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.API_MODULE);
        if (section.isEmpty()) {
            writeDefaults();
            return;
        }
        enabled = section.getBoolean("enabled", enabled);
        baseUrl = normalizeUrl(section.getString("base_url", DEFAULT_BASE_URL));
        serverKey = section.getString("server_key", DEFAULT_SERVER_KEY);
        timeoutSeconds = section.getInt("timeout_seconds", DEFAULT_TIMEOUT_SECONDS, 1, 30);
        DebugLogger.info(MODULE, "Api 网桥已加载: enabled=%s, baseUrl=%s", enabled, baseUrl);
    }

    public static void writeDefaults() {
        enabled = true;
        baseUrl = DEFAULT_BASE_URL;
        serverKey = DEFAULT_SERVER_KEY;
        timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        save();
    }

    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.API_MODULE);
        section.set("enabled", enabled);
        section.set("base_url", baseUrl);
        section.set("server_key", serverKey);
        section.set("timeout_seconds", timeoutSeconds);
        GlobalSettings.save();
    }

    private static String normalizeUrl(String value) {
        String trimmed = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
