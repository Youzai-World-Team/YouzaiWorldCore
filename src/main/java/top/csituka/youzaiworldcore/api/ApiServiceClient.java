package top.csituka.youzaiworldcore.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.config.ApiModuleSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Api-only 账户、会话和外观 HTTP 客户端。 */
public final class ApiServiceClient {
    private static final String MODULE = "ApiServiceClient";
    // Cloudflare 公网 Api 使用 HTTP/1.1，避免不同代理对 HTTP/2 的协商差异。
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private ApiServiceClient() {
    }

    public record CosmeticSnapshot(byte[] skinWide, byte[] skinSlim, byte[] cloak) {
    }

    public record AccountSettings(int loginCooldown) {
    }

    public record AccountResult(boolean success, int statusCode, String message, PlayerAccount account, String token) {
    }

    public record LoginResult(boolean success, int statusCode, String message, PlayerAccount account,
            String reason, int loginTries, int remainingTries, long retryAfterSeconds,
            String token) {
    }

    public enum SessionValidationState {
        VALID, INVALID, DELETED, UNAVAILABLE
    }

    public record SessionValidationResult(SessionValidationState state, PlayerAccount account) {
    }

    public static Optional<Map<String, PlayerAccount>> loadAccounts() {
        HttpResponse<String> response = request("GET", "/api/game/accounts", null);
        if (!successful(response))
            return Optional.empty();
        try {
            JsonObject root = parse(response.body());
            var type = new TypeToken<List<PlayerAccount>>() {
            }.getType();
            List<PlayerAccount> list = PlayerAccount.GSON.fromJson(root.get("accounts"), type);
            Map<String, PlayerAccount> accounts = new LinkedHashMap<>();
            if (list != null)
                for (PlayerAccount account : list) {
                    if (account != null && account.usernameLowerCase != null)
                        accounts.put(account.usernameLowerCase, account);
                }
            return Optional.of(accounts);
        } catch (RuntimeException e) {
            DebugLogger.exception(MODULE, "loadAccounts", e);
            return Optional.empty();
        }
    }

    public static AccountResult getAccount(String username) {
        HttpResponse<String> response = request("GET", "/api/game/account?username=" + encode(username), null);
        if (response == null)
            return new AccountResult(false, 0, "Api 服务端不可用", null, null);
        JsonObject root = parse(response.body());
        if (response.statusCode() / 100 != 2) {
            return new AccountResult(false, response.statusCode(), responseMessage(root), null, null);
        }
        try {
            PlayerAccount account = PlayerAccount.GSON.fromJson(root, PlayerAccount.class);
            return new AccountResult(true, response.statusCode(), "", account, stringValue(root, "token"));
        } catch (RuntimeException e) {
            DebugLogger.exception(MODULE, "getAccount", e);
            return new AccountResult(false, response.statusCode(), "Api 账户数据格式无效", null, null);
        }
    }

    public static AccountResult ensureAccount(String username, UUID uuid) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        if (uuid != null)
            body.addProperty("uuid", uuid.toString());
        return accountRequest("POST", "/api/game/account-ensure", body);
    }

    public static AccountResult registerAccount(String username, UUID uuid, String password, String ip,
            boolean startSession) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        if (uuid != null)
            body.addProperty("uuid", uuid.toString());
        body.addProperty("password", password);
        body.addProperty("last_ip", ip == null ? "" : ip);
        body.addProperty("start_session", startSession);
        return accountRequest("POST", "/api/game/account", body);
    }

    public static LoginResult login(String username, String password, String ip) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        body.addProperty("ip", ip == null ? "" : ip);
        HttpResponse<String> response = request("POST", "/api/game/login", body.toString());
        if (response == null)
            return new LoginResult(false, 0, "Api 服务端不可用", null, "", 0, 0, 0, null);
        JsonObject root = parse(response.body());
        return new LoginResult(response.statusCode() / 100 == 2, response.statusCode(), responseMessage(root),
                parseAccount(root), nestedStringValue(root, "reason"), intValue(root, "loginTries", 0),
                intValue(root, "remainingTries", 0),
                longValue(root, "retryAfterSeconds", 0), stringValue(root, "token"));
    }

    public static AccountResult logout(String username, String lastPosition) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        if (lastPosition == null)
            body.add("lastPosition", JsonNull.INSTANCE);
        else
            body.addProperty("lastPosition", lastPosition);
        return accountRequest("POST", "/api/game/logout", body);
    }

    public static AccountResult changePassword(String username, String oldPassword, String newPassword) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("oldPassword", oldPassword);
        body.addProperty("newPassword", newPassword);
        return accountRequest("POST", "/api/game/change-password", body);
    }

    public static AccountResult deactivate(String username, String password) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return accountRequest("POST", "/api/game/deactivate", body);
    }

    public static AccountResult resetPassword(String username, String newPassword) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", newPassword);
        return accountRequest("PATCH", "/api/game/account", body);
    }

    public static AccountResult unlockAccount(String username) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("unlock", true);
        return accountRequest("PATCH", "/api/game/account", body);
    }

    public static Optional<PlayerAccount> updateAccount(PlayerAccount account) {
        if (account == null || account.username == null)
            return Optional.empty();
        JsonObject body = new JsonObject();
        body.addProperty("username", account.username);
        if (account.uuid != null)
            body.addProperty("uuid", account.uuid);
        if (account.lastPositionJson == null)
            body.add("last_position", JsonNull.INSTANCE);
        else
            body.addProperty("last_position", account.lastPositionJson);
        body.addProperty("in_place_respawn_count", Math.max(0, account.inPlaceRespawnCount));
        AccountResult result = accountRequest("PATCH", "/api/game/account", body);
        return result.success() ? Optional.ofNullable(result.account()) : Optional.empty();
    }

    /** 玩家断线时只同步账户状态，不创建跨连接登录凭据。 */
    public static Optional<PlayerAccount> updateAccountForDisconnect(PlayerAccount account) {
        if (account == null || account.username == null)
            return Optional.empty();
        JsonObject body = new JsonObject();
        body.addProperty("username", account.username);
        if (account.uuid != null)
            body.addProperty("uuid", account.uuid);
        body.addProperty("last_ip", account.lastIp == null ? "" : account.lastIp);
        if (account.lastAuthenticatedDate == null)
            body.add("last_authenticated_date", JsonNull.INSTANCE);
        else
            body.addProperty("last_authenticated_date", account.lastAuthenticatedDate.toInstant().toString());
        if (account.lastPositionJson == null)
            body.add("last_position", JsonNull.INSTANCE);
        else
            body.addProperty("last_position", account.lastPositionJson);
        body.addProperty("in_place_respawn_count", Math.max(0, account.inPlaceRespawnCount));
        AccountResult result = accountRequest("PATCH", "/api/game/account", body);
        return result.success() ? Optional.ofNullable(result.account()) : Optional.empty();
    }

    /** 仅更新位置，不触碰 Api 保存的认证 IP、认证时间和会话。 */
    public static Optional<PlayerAccount> updateAccountPosition(PlayerAccount account) {
        if (account == null || account.username == null)
            return Optional.empty();
        JsonObject body = new JsonObject();
        body.addProperty("username", account.username);
        if (account.uuid != null)
            body.addProperty("uuid", account.uuid);
        if (account.lastPositionJson == null)
            body.add("last_position", JsonNull.INSTANCE);
        else
            body.addProperty("last_position", account.lastPositionJson);
        AccountResult result = accountRequest("PATCH", "/api/game/account", body);
        return result.success() ? Optional.ofNullable(result.account()) : Optional.empty();
    }

    public static AccountResult deleteAccount(String username) {
        return resultFrom(request("DELETE", "/api/game/account?username=" + encode(username), null));
    }

    public static SessionValidationResult validateSession(String token, String username) {
        if (token == null || token.isBlank()) {
            return new SessionValidationResult(SessionValidationState.INVALID, null);
        }
        HttpResponse<String> response = request("GET", "/api/game/session", null, token);
        if (successful(response)) {
            try {
                PlayerAccount account = PlayerAccount.GSON.fromJson(parse(response.body()), PlayerAccount.class);
                return new SessionValidationResult(SessionValidationState.VALID, account);
            } catch (RuntimeException e) {
                DebugLogger.exception(MODULE, "validateSession", e);
                return new SessionValidationResult(SessionValidationState.UNAVAILABLE, null);
            }
        }
        if (response == null)
            return new SessionValidationResult(SessionValidationState.UNAVAILABLE, null);
        AccountResult lookup = getAccount(username);
        if (lookup.success())
            return new SessionValidationResult(SessionValidationState.INVALID, lookup.account());
        if (lookup.statusCode() == 404)
            return new SessionValidationResult(SessionValidationState.DELETED, null);
        return new SessionValidationResult(SessionValidationState.UNAVAILABLE, null);
    }

    /** 主动撤销当前游戏会话。 */
    public static void deleteSession(String token) {
        if (token == null || token.isBlank())
            return;
        request("DELETE", "/api/game/session", null, token);
    }

    public static Optional<AccountSettings> getAccountSettings() {
        HttpResponse<String> response = request("GET", "/api/game/account-settings", null);
        if (!successful(response))
            return Optional.empty();
        JsonObject root = parse(response.body());
        return Optional.of(new AccountSettings(intValue(root, "loginCooldown", 300)));
    }

    public static Optional<AccountSettings> setLoginCooldown(int seconds) {
        JsonObject body = new JsonObject();
        body.addProperty("loginCooldown", Math.min(86_400, Math.max(-1, seconds)));
        return updateSettings(body);
    }

    public static Optional<CosmeticSnapshot> fetchCosmeticSnapshot(UUID uuid) {
        HttpResponse<String> response = request("GET", "/api/game/cosmetic-snapshot?uuid=" + encode(uuid.toString()),
                null);
        if (!successful(response))
            return Optional.empty();
        try {
            JsonObject files = parse(response.body()).getAsJsonObject("files");
            return Optional.of(new CosmeticSnapshot(decode(files, "skin.png"), decode(files, "skin_slim.png"),
                    decode(files, "cloak.png")));
        } catch (RuntimeException e) {
            DebugLogger.exception(MODULE, "fetchCosmeticSnapshot", e);
            return Optional.empty();
        }
    }

    public static boolean uploadCosmeticSnapshot(UUID uuid, byte[] skinWide, byte[] skinSlim, byte[] cloak) {
        JsonObject files = new JsonObject();
        files.addProperty("skin.png", Base64.getEncoder().encodeToString(skinWide == null ? new byte[0] : skinWide));
        files.addProperty("skin_slim.png",
                Base64.getEncoder().encodeToString(skinSlim == null ? new byte[0] : skinSlim));
        files.addProperty("cloak.png", Base64.getEncoder().encodeToString(cloak == null ? new byte[0] : cloak));
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid.toString());
        body.add("files", files);
        return successful(request("POST", "/api/game/cosmetic-snapshot", body.toString()));
    }

    public static boolean deleteCosmetics(UUID uuid) {
        return successful(request("DELETE", "/api/game/cosmetic?uuid=" + encode(uuid.toString()), null));
    }

    private static Optional<AccountSettings> updateSettings(JsonObject body) {
        HttpResponse<String> response = request("PATCH", "/api/game/account-settings", body.toString());
        if (!successful(response))
            return Optional.empty();
        JsonObject root = parse(response.body());
        return Optional.of(new AccountSettings(intValue(root, "loginCooldown", 300)));
    }

    private static AccountResult accountRequest(String method, String path, JsonObject body) {
        return resultFrom(request(method, path, body == null ? null : body.toString()));
    }

    private static AccountResult resultFrom(HttpResponse<String> response) {
        if (response == null)
            return new AccountResult(false, 0, "Api 服务端不可用", null, null);
        JsonObject root = parse(response.body());
        return new AccountResult(response.statusCode() / 100 == 2, response.statusCode(), responseMessage(root),
                parseAccount(root), stringValue(root, "token"));
    }

    private static PlayerAccount parseAccount(JsonObject root) {
        JsonElement account = root.get("account");
        if (account == null || !account.isJsonObject())
            return null;
        try {
            return PlayerAccount.GSON.fromJson(account, PlayerAccount.class);
        } catch (RuntimeException e) {
            DebugLogger.exception(MODULE, "parseAccount", e);
            return null;
        }
    }

    private static String responseMessage(JsonObject root) {
        String message = stringValue(root, "statusMessage");
        if (!message.isBlank())
            return message;
        message = stringValue(root, "message");
        return message.isBlank() ? "Api 请求失败" : message;
    }

    private static int intValue(JsonObject root, String key, int fallback) {
        return (int) longValue(root, key, fallback);
    }

    private static long longValue(JsonObject root, String key, long fallback) {
        JsonElement value = nestedValue(root, key);
        try {
            return value == null ? fallback : value.getAsLong();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static JsonElement nestedValue(JsonObject root, String key) {
        if (root.has(key))
            return root.get(key);
        JsonObject data = root.has("data") && root.get("data").isJsonObject() ? root.getAsJsonObject("data") : null;
        return data != null && data.has(key) ? data.get(key) : null;
    }

    private static String stringValue(JsonObject root, String key) {
        JsonElement value = root.get(key);
        try {
            return value == null || value.isJsonNull() ? "" : value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String nestedStringValue(JsonObject root, String key) {
        JsonElement value = nestedValue(root, key);
        try {
            return value == null || value.isJsonNull() ? "" : value.getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static JsonObject parse(String body) {
        try {
            return body == null || body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static byte[] decode(JsonObject files, String key) {
        String value = files != null && files.has(key) ? files.get(key).getAsString() : "";
        return value.isBlank() ? new byte[0] : Base64.getDecoder().decode(value);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean successful(HttpResponse<?> response) {
        return response != null && response.statusCode() / 100 == 2;
    }

    private static HttpResponse<String> request(String method, String path, String body) {
        return request(method, path, body, null);
    }

    private static HttpResponse<String> request(String method, String path, String body, String sessionToken) {
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
                    .timeout(Duration.ofSeconds(ApiModuleSettings.getTimeoutSeconds()))
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

    private static final SecureRandom NONCE_RANDOM = new SecureRandom();

    private static String randomNonce() {
        byte[] bytes = new byte[24];
        NONCE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
            out.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }
}
