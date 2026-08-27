package top.csituka.youzaiworldcore.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.config.ApiModuleSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Api-only 账户、会话和外观 HTTP 客户端。请求签名与传输由 {@link ApiHttp} 统一处理。 */
public final class ApiServiceClient {
    private static final String MODULE = "ApiServiceClient";
    // SMTP 投递需要等待远端邮件服务器响应，不能沿用普通 Api 的 3 秒默认超时。
    private static final int EMAIL_REQUEST_TIMEOUT_SECONDS = 30;

    private ApiServiceClient() {
    }

    public record CosmeticSnapshot(byte[] skinWide, byte[] skinSlim, byte[] cloak) {
    }

    public record AccountSettings(int loginCooldown, boolean emailVerificationRequired) {
    }

    public record AccountResult(boolean success, int statusCode, String message, PlayerAccount account, String token) {
    }

    public record RegistrationResult(
            boolean success,
            boolean emailVerificationRequired,
            int statusCode,
            String message,
            PlayerAccount account,
            String token,
            String sessionId,
            long expiresInSeconds) {
    }

    public record RegistrationEmailCodeResult(
            boolean success,
            int statusCode,
            String message,
            long expiresInSeconds,
            long resendAfterSeconds) {
    }

    public record PasswordResetCodeResult(
            boolean success,
            int statusCode,
            String message,
            String sessionId,
            long expiresInSeconds,
            long resendAfterSeconds) {
    }

    public record PasswordResetResult(boolean success, int statusCode, String message) {
    }

    public record EmailChangeCodeResult(
            boolean success,
            int statusCode,
            String message,
            String sessionId,
            long expiresInSeconds,
            long resendAfterSeconds) {
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
            @SuppressWarnings("null")
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

    public static RegistrationResult registerAccount(String username, UUID uuid, String password, String ip,
            boolean startSession) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        if (uuid != null)
            body.addProperty("uuid", uuid.toString());
        body.addProperty("password", password);
        body.addProperty("last_ip", ip == null ? "" : ip);
        body.addProperty("start_session", startSession);
        HttpResponse<String> response = request("POST", "/api/game/account", body.toString());
        if (response == null) {
            return new RegistrationResult(
                    false, false, 0, "Api 服务端不可用", null, null, "", 0);
        }
        JsonObject root = parse(response.body());
        boolean httpSuccess = response.statusCode() / 100 == 2;
        boolean accepted = httpSuccess && booleanValue(root, "ok", true);
        String sessionId = stringValue(root, "session_id");
        String message = responseMessage(root);
        boolean emailRequired = httpSuccess && !accepted && !sessionId.isBlank()
                && "需要邮箱注册".equals(message);
        return new RegistrationResult(
                accepted,
                emailRequired,
                response.statusCode(),
                message,
                parseAccount(root),
                stringValue(root, "token"),
                sessionId,
                longValue(root, "expires_in", 900));
    }

    /** 请求 Api 向指定邮箱发送注册验证码。 */
    public static RegistrationEmailCodeResult sendRegistrationEmailCode(String sessionId, String email) {
        JsonObject body = new JsonObject();
        body.addProperty("session_id", sessionId);
        body.addProperty("email", email);
        int timeoutSeconds = Math.max(
                ApiModuleSettings.getTimeoutSeconds(), EMAIL_REQUEST_TIMEOUT_SECONDS);
        HttpResponse<String> response = request(
                "POST", "/api/game/account-email/send", body.toString(), null, timeoutSeconds);
        if (response == null) {
            return new RegistrationEmailCodeResult(
                    false, 0, "验证码发送请求未收到响应；若邮件已到达，可直接输入验证码", 0, 0);
        }
        JsonObject root = parse(response.body());
        boolean success = response.statusCode() / 100 == 2 && booleanValue(root, "ok", false);
        long resendAfter = success
                ? longValue(root, "resend_after", 0)
                : longValue(root, "retryAfterSeconds", 0);
        return new RegistrationEmailCodeResult(
                success,
                response.statusCode(),
                responseMessage(root),
                longValue(root, "expires_in", 0),
                resendAfter);
    }

    /** 校验邮箱验证码并取得最终创建的账户和当前连接令牌。 */
    public static AccountResult verifyRegistrationEmailCode(String sessionId, String code) {
        JsonObject body = new JsonObject();
        body.addProperty("session_id", sessionId);
        body.addProperty("code", code);
        return accountRequest("POST", "/api/game/account-email/verify", body);
    }

    /** 向当前游戏账户已经绑定的邮箱发送找回密码验证码。 */
    public static PasswordResetCodeResult requestPasswordResetCode(String username, String email) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("email", email);
        int timeoutSeconds = Math.max(ApiModuleSettings.getTimeoutSeconds(), EMAIL_REQUEST_TIMEOUT_SECONDS);
        HttpResponse<String> response = request(
                "POST", "/api/game/account-password-reset/send", body.toString(), null, timeoutSeconds);
        if (response == null) {
            return new PasswordResetCodeResult(
                    false, 0, "验证码发送请求未收到响应，请等待一分钟后重试", "", 0, 0);
        }
        JsonObject root = parse(response.body());
        boolean success = response.statusCode() / 100 == 2 && booleanValue(root, "ok", false);
        long resendAfter = success
                ? longValue(root, "resend_after", 0)
                : longValue(root, "retryAfterSeconds", 0);
        return new PasswordResetCodeResult(
                success,
                response.statusCode(),
                responseMessage(root),
                stringValue(root, "session_id"),
                longValue(root, "expires_in", 0),
                resendAfter);
    }

    /** 校验找回密码验证码并设置新密码。 */
    public static PasswordResetResult resetPasswordWithEmailCode(
            String sessionId, String code, String newPassword) {
        JsonObject body = new JsonObject();
        body.addProperty("session_id", sessionId);
        body.addProperty("code", code);
        body.addProperty("new_password", newPassword);
        int timeoutSeconds = Math.max(ApiModuleSettings.getTimeoutSeconds(), EMAIL_REQUEST_TIMEOUT_SECONDS);
        HttpResponse<String> response = request(
                "POST", "/api/game/account-password-reset/verify", body.toString(), null, timeoutSeconds);
        if (response == null) {
            return new PasswordResetResult(false, 0, "Api 服务端不可用");
        }
        JsonObject root = parse(response.body());
        return new PasswordResetResult(
                response.statusCode() / 100 == 2 && booleanValue(root, "ok", false),
                response.statusCode(),
                responseMessage(root));
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

    public static AccountResult changePassword(
            String username, String oldPassword, String newPassword, String sessionToken) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("oldPassword", oldPassword);
        body.addProperty("newPassword", newPassword);
        return accountRequest("POST", "/api/game/change-password", body, sessionToken);
    }

    public static AccountResult deactivate(String username, String password, String sessionToken) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return accountRequest("POST", "/api/game/deactivate", body, sessionToken);
    }

    /** 向新邮箱发送换绑验证码；当前密码与游戏会话均由 Api 再次校验。 */
    public static EmailChangeCodeResult requestEmailChangeCode(
            String sessionToken, String currentPassword, String email) {
        JsonObject body = new JsonObject();
        body.addProperty("password", currentPassword);
        body.addProperty("email", email);
        int timeoutSeconds = Math.max(ApiModuleSettings.getTimeoutSeconds(), EMAIL_REQUEST_TIMEOUT_SECONDS);
        HttpResponse<String> response = request(
                "POST", "/api/game/account-email-change/send",
                body.toString(), sessionToken, timeoutSeconds);
        if (response == null) {
            return new EmailChangeCodeResult(
                    false, 0, "未收到 Api 响应；若邮件已经到达，请等待一分钟后重新发送", "", 0, 0);
        }
        JsonObject root = parse(response.body());
        boolean success = response.statusCode() / 100 == 2 && booleanValue(root, "ok", false);
        long resendAfter = success
                ? longValue(root, "resend_after", 0)
                : longValue(root, "retryAfterSeconds", 0);
        return new EmailChangeCodeResult(
                success,
                response.statusCode(),
                responseMessage(root),
                stringValue(root, "session_id"),
                longValue(root, "expires_in", 0),
                resendAfter);
    }

    /** 校验新邮箱验证码，并读取 Api 返回的已更新账户。 */
    public static AccountResult verifyEmailChangeCode(
            String sessionToken, String sessionId, String code) {
        JsonObject body = new JsonObject();
        body.addProperty("session_id", sessionId);
        body.addProperty("code", code);
        int timeoutSeconds = Math.max(ApiModuleSettings.getTimeoutSeconds(), EMAIL_REQUEST_TIMEOUT_SECONDS);
        return resultFrom(request(
                "POST", "/api/game/account-email-change/verify",
                body.toString(), sessionToken, timeoutSeconds));
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
                @SuppressWarnings("null")
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

    @SuppressWarnings("null")
    public static Optional<AccountSettings> getAccountSettings() {
        HttpResponse<String> response = request("GET", "/api/game/account-settings", null);
        if (!successful(response))
            return Optional.empty();
        JsonObject root = parse(response.body());
        return Optional.of(new AccountSettings(
                intValue(root, "loginCooldown", 300),
                booleanValue(root, "emailVerificationRequired", false)));
    }

    public static Optional<AccountSettings> setLoginCooldown(int seconds) {
        JsonObject body = new JsonObject();
        body.addProperty("loginCooldown", Math.min(86_400, Math.max(-1, seconds)));
        return updateSettings(body);
    }

    @SuppressWarnings("null")
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

    @SuppressWarnings("null")
    private static Optional<AccountSettings> updateSettings(JsonObject body) {
        HttpResponse<String> response = request("PATCH", "/api/game/account-settings", body.toString());
        if (!successful(response))
            return Optional.empty();
        JsonObject root = parse(response.body());
        return Optional.of(new AccountSettings(
                intValue(root, "loginCooldown", 300),
                booleanValue(root, "emailVerificationRequired", false)));
    }

    private static AccountResult accountRequest(String method, String path, JsonObject body) {
        return resultFrom(request(method, path, body == null ? null : body.toString()));
    }

    private static AccountResult accountRequest(
            String method, String path, JsonObject body, String sessionToken) {
        return resultFrom(request(
                method, path, body == null ? null : body.toString(), sessionToken));
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
        return ApiHttp.responseMessage(root);
    }

    private static boolean booleanValue(JsonObject root, String key, boolean fallback) {
        return ApiHttp.booleanValue(root, key, fallback);
    }

    private static int intValue(JsonObject root, String key, int fallback) {
        return ApiHttp.intValue(root, key, fallback);
    }

    private static long longValue(JsonObject root, String key, long fallback) {
        return ApiHttp.longValue(root, key, fallback);
    }

    private static String stringValue(JsonObject root, String key) {
        return ApiHttp.stringValue(root, key);
    }

    private static String nestedStringValue(JsonObject root, String key) {
        return ApiHttp.nestedStringValue(root, key);
    }

    private static JsonObject parse(String body) {
        return ApiHttp.parse(body);
    }

    private static byte[] decode(JsonObject files, String key) {
        String value = files != null && files.has(key) ? files.get(key).getAsString() : "";
        return value.isBlank() ? new byte[0] : Base64.getDecoder().decode(value);
    }

    private static String encode(String value) {
        return ApiHttp.encode(value);
    }

    private static boolean successful(HttpResponse<?> response) {
        return ApiHttp.successful(response);
    }

    private static HttpResponse<String> request(String method, String path, String body) {
        return ApiHttp.request(method, path, body);
    }

    private static HttpResponse<String> request(String method, String path, String body, String sessionToken) {
        return ApiHttp.request(method, path, body, sessionToken);
    }

    private static HttpResponse<String> request(
            String method, String path, String body, String sessionToken, int timeoutSeconds) {
        return ApiHttp.request(method, path, body, sessionToken, timeoutSeconds);
    }
}
