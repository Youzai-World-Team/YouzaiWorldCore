package top.csituka.youzaiworldcore.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.csituka.youzaiworldcore.config.ApiModuleSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Api 服务端 HTTP 传输层：统一的 HMAC-SHA256 请求签名与 JSON 读取工具。
 * <p>
 * 签名规则与 Api 端 {@code authenticateGameApiRequest} 一致：
 * {@code HMAC(密钥, "<时间戳>.<nonce>.<方法>.<含查询串的路径>.<请求体 SHA-256>")}，
 * 分别放在 {@code X-Yzwc-Timestamp} / {@code X-Yzwc-Nonce} / {@code X-Yzwc-Signature} 头里。
 * </p>
 * <p>
 * 所有调用 Api 的模块（{@link ApiServiceClient} 账户 / 外观、
 * {@link top.csituka.youzaiworldcore.mail.MailApiClient} 邮件）都必须走本类，
 * 密钥与签名逻辑只保留这一份实现。
 * </p>
 */
public final class ApiHttp {

    private static final String MODULE = "ApiHttp";

    // Cloudflare 公网 Api 使用 HTTP/1.1，避免不同代理对 HTTP/2 的协商差异。
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final SecureRandom NONCE_RANDOM = new SecureRandom();

    private ApiHttp() {
    }

    // ===== 请求 =====

    public static HttpResponse<String> request(String method, String path, String body) {
        return request(method, path, body, null, ApiModuleSettings.getTimeoutSeconds());
    }

    public static HttpResponse<String> request(String method, String path, String body, String sessionToken) {
        return request(method, path, body, sessionToken, ApiModuleSettings.getTimeoutSeconds());
    }

    /**
     * 发送一次已签名的 Api 请求。
     *
     * @param method         HTTP 方法
     * @param path           以 {@code /} 开头的路径，含查询串（签名覆盖查询串，必须与实际请求一致）
     * @param body           请求体 JSON；{@code null} 表示无请求体
     * @param sessionToken   游戏会话令牌；非空时附加 {@code Authorization: Bearer}
     * @param timeoutSeconds 单次请求超时
     * @return 响应；网桥关闭、密钥缺失或请求失败时返回 {@code null}
     */
    public static HttpResponse<String> request(
            String method, String path, String body, String sessionToken, int timeoutSeconds) {
        if (!ApiModuleSettings.isEnabled()) {
            DebugLogger.warn(MODULE, "Api 网桥已关闭，拒绝执行 %s %s", method, path);
            return null;
        }
        String key = ApiModuleSettings.getServerKey();
        if (key == null || key.length() < 32) {
            DebugLogger.warn(MODULE, "Api 网桥密钥未配置或长度不足，拒绝发送请求");
            return null;
        }
        try {
            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
            String nonce = randomNonce();
            String bodyHash = hex(MessageDigest.getInstance("SHA-256").digest(bodyBytes));
            String canonical = timestamp + "." + nonce + "." + method.toUpperCase(java.util.Locale.ROOT)
                    + "." + path + "." + bodyHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = hex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(ApiModuleSettings.getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/json")
                    .header("X-Yzwc-Timestamp", timestamp)
                    .header("X-Yzwc-Nonce", nonce)
                    .header("X-Yzwc-Signature", signature);
            if (sessionToken != null && !sessionToken.isBlank()) {
                builder.header("Authorization", "Bearer " + sessionToken);
            }
            if (body == null)
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            else
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes)).header("Content-Type",
                        "application/json");
            return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (e.getCause() != null) {
                detail += " (cause=" + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage() + ")";
            }
            DebugLogger.warn(MODULE, "Api 请求失败 %s %s：%s", method, path, detail);
            return null;
        }
    }

    // ===== 响应读取 =====

    public static boolean successful(HttpResponse<?> response) {
        return response != null && response.statusCode() / 100 == 2;
    }

    public static JsonObject parse(String body) {
        try {
            return body == null || body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    /** 依次尝试 Nuxt 的 {@code statusMessage} / {@code message} / {@code msg} 字段。 */
    public static String responseMessage(JsonObject root) {
        String message = stringValue(root, "statusMessage");
        if (!message.isBlank())
            return message;
        message = stringValue(root, "message");
        if (!message.isBlank())
            return message;
        message = stringValue(root, "msg");
        return message.isBlank() ? "Api 请求失败" : message;
    }

    public static String stringValue(JsonObject root, String key) {
        JsonElement value = root.get(key);
        try {
            return value == null || value.isJsonNull() ? "" : value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public static String nestedStringValue(JsonObject root, String key) {
        JsonElement value = nestedValue(root, key);
        try {
            return value == null || value.isJsonNull() ? "" : value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public static boolean booleanValue(JsonObject root, String key, boolean fallback) {
        JsonElement value = nestedValue(root, key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public static int intValue(JsonObject root, String key, int fallback) {
        return (int) longValue(root, key, fallback);
    }

    public static long longValue(JsonObject root, String key, long fallback) {
        JsonElement value = nestedValue(root, key);
        try {
            return value == null || value.isJsonNull() ? fallback : value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /** Nuxt 的 {@code createError} 会把附加字段放进 {@code data} 里，这里两层都查。 */
    public static JsonElement nestedValue(JsonObject root, String key) {
        if (root.has(key))
            return root.get(key);
        JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : null;
        return data != null && data.has(key) ? data.get(key) : null;
    }

    public static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
            out.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    public static String randomNonce() {
        byte[] bytes = new byte[24];
        NONCE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
