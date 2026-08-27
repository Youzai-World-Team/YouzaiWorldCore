package top.csituka.youzaiworldcore.config;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Api 服务端网桥配置。
 * <p>默认通过 HTTPS 连接公网 Api {@code https://api.mcyzw.top}。</p>
 */
public final class ApiModuleSettings {

    private static final String MODULE = "ApiModuleSettings";
    private static final String DEFAULT_BASE_URL = "https://api.mcyzw.top";
    private static final String DEFAULT_SERVER_KEY = "";
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

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
        serverKey = section.getString("server_key", DEFAULT_SERVER_KEY).trim();
        timeoutSeconds = section.getInt("timeout_seconds", DEFAULT_TIMEOUT_SECONDS, 1, 30);
        if (enabled && serverKey.length() < 32) {
            ConfigCrash.fail(GlobalSettings.file(), GlobalSettings.API_MODULE + ".server_key",
                    "公网 Api HMAC 密钥必须配置为至少 32 位，并与 YZWC_GAME_API_KEY 保持一致");
        }
        DebugLogger.info(MODULE,
                "Api 网桥已加载: enabled=%s, baseUrl=%s, serverKeyConfigured=%s, timeoutSeconds=%d",
                enabled, baseUrl, serverKey.length() >= 32, timeoutSeconds);
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
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        try {
            java.net.URI uri = java.net.URI.create(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath() == null || uri.getPath().isEmpty())) {
                ConfigCrash.fail(GlobalSettings.file(), GlobalSettings.API_MODULE + ".base_url",
                        "Api 地址必须是无路径、无凭据、无查询参数的 HTTPS 源站地址");
            }
        } catch (IllegalArgumentException e) {
            ConfigCrash.fail(GlobalSettings.file(), GlobalSettings.API_MODULE + ".base_url",
                    "Api 地址格式无效", e);
        }
        return trimmed;
    }
}
