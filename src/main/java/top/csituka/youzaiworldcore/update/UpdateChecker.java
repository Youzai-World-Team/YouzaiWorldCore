package top.csituka.youzaiworldcore.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 更新检查器核心逻辑（客户端与服务端共用）。
 * <p>
 * 职责：
 * <ul>
 *   <li>读取当前模组版本（来自 Fabric 元数据，不硬编码）</li>
 *   <li>异步拉取固定更新 API 并解析为 {@link RemoteVersionInfo}</li>
 *   <li>基于 {@link SemanticVersion} 比较当前与最新版本，得到 {@link UpdateResult}</li>
 *   <li>构造下载页地址 {@code https://mcyzw.top/yzwc?version=<当前版本数字>&type=<当前类型>}</li>
 * </ul>
 * 检查采用双端点（均为固定地址，不再支持自定义）：
 * <ul>
 *   <li>{@link #API_OPTIONAL_URL}：可选更新端点（强制标记 false）</li>
 *   <li>{@link #API_FORCED_URL}：强制更新端点（强制标记 true）</li>
 * </ul>
 * 合并规则：任一端点成功且存在新版本即视为有更新；强制端点优先（有强制更新时
 * 以强制端点的数据为准）。仅当两个端点均拉取失败时才返回错误。
 * <p>
 * 所有网络请求在独立守护线程执行，绝不阻塞主线程 / 渲染线程；任何异常均被捕获并
 * 以 {@link UpdateResult#errorMessage()} 形式返回，保证不影响服务器或客户端运行。
 */
@SuppressWarnings({"null", "unused"})
public final class UpdateChecker {

    public static final String MODULE = "UpdateChecker";

    /** 可选更新 API（固定端点） */
    public static final String API_OPTIONAL_URL = "https://api.mcyzw.top/api/update/core";

    /** 强制更新 API（固定端点） */
    public static final String API_FORCED_URL = "https://api.mcyzw.top/api/update/core_force";

    /** 下载页地址默认基址（版本号与类型作为查询参数附加） */
    public static final String DEFAULT_BASE = "https://mcyzw.top/yzwc";

    private static final Gson GSON = new GsonBuilder().create();

    /** 专用单线程执行器（守护线程），用于异步网络请求 */
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "YZWC-UpdateChecker");
        t.setDaemon(true);
        return t;
    });

    private UpdateChecker() {
    }

    // ==================== 当前版本 ====================

    /** @return 当前模组版本字符串（如 "1.19.0-indev"），读取失败时回退 "0.0.0" */
    public static String getCurrentVersionString() {
        try {
            Optional<net.fabricmc.loader.api.ModContainer> container =
                    FabricLoader.getInstance().getModContainer(YouzaiworldCore.MOD_ID);
            if (container.isPresent()) {
                return container.get().getMetadata().getVersion().getFriendlyString();
            }
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "getCurrentVersionString", e);
        }
        return "0.0.0";
    }

    /**
     * 构造下载页（跳转）地址。
     * <p>固定使用 {@link #DEFAULT_BASE}，自动附加 {@code ?version=<数字>&type=<类型>}。</p>
     */
    public static String buildJumpUrl(String fullVersion) {
        // 统一从 fullVersion 提取数字和类型
        int dash = fullVersion.indexOf('-');
        String number = dash >= 0 ? fullVersion.substring(0, dash) : fullVersion;
        String type = dash >= 0 ? fullVersion.substring(dash + 1) : "release";
        String base = DEFAULT_BASE;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "?version=" + number + "&type=" + type;
    }

    // ==================== 远程拉取 ====================

    /**
     * 用指定 HTTP 版本发送请求并获取响应体，超时时返回 null。
     */
    private static HttpResponse<String> tryFetch(HttpClient.Version version, String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(version)
                .proxy(HttpClient.Builder.NO_PROXY)
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "YouzaiWorldCore-UpdateChecker")
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** 同步拉取并解析远程更新 JSON（调用方需确保不在主线程执行） */
    public static RemoteVersionInfo fetchRemoteVersion(String url) throws Exception {
        DebugLogger.entering(MODULE, "fetchRemoteVersion", "url=" + url);

        // 先尝试 HTTP/2；若因协议版本不支持超时则降级到 HTTP/1.1 重试
        HttpResponse<String> response;
        try {
            response = tryFetch(HttpClient.Version.HTTP_2, url);
        } catch (java.net.http.HttpTimeoutException e) {
            DebugLogger.info(MODULE, "HTTP/2 超时，降级到 HTTP/1.1 重试: url=%s", url);
            response = tryFetch(HttpClient.Version.HTTP_1_1, url);
        }
        if (response.statusCode() != 200) {
            throw new java.io.IOException("HTTP " + response.statusCode());
        }

        JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
        if (root == null) {
            throw new java.io.IOException("响应体为空或不是合法 JSON");
        }

        String latestVersion = root.has("latestVersion") && !root.get("latestVersion").isJsonNull()
                ? root.get("latestVersion").getAsString() : "";
        String type = root.has("type") && !root.get("type").isJsonNull()
                ? root.get("type").getAsString() : "";
        boolean forcedUpdate = root.has("forcedUpdate") && !root.get("forcedUpdate").isJsonNull()
                && root.get("forcedUpdate").getAsBoolean();
        String releaseDate = root.has("release_date") && !root.get("release_date").isJsonNull()
                ? root.get("release_date").getAsString() : "";
        String releaseTime = root.has("release_time") && !root.get("release_time").isJsonNull()
                ? root.get("release_time").getAsString() : "";

        List<String> changelog = new ArrayList<>();
        if (root.has("changelog") && root.get("changelog").isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray("changelog")) {
                if (element != null && !element.isJsonNull()) {
                    changelog.add(element.getAsString());
                }
            }
        }

        DebugLogger.exiting(MODULE, "fetchRemoteVersion", "latest=" + latestVersion);
        return new RemoteVersionInfo(latestVersion, type, forcedUpdate, releaseDate, releaseTime, changelog);
    }

    // ==================== 检查主流程 ====================

    /**
     * 判断 {@code latest} 是否严格新于 {@code current}（版本号比较失败时视为无更新）。
     */
    private static boolean isNewerThan(String current, String latest) {
        if (current == null || latest == null || latest.isEmpty()) {
            return false;
        }
        try {
            SemanticVersion currentVersion = SemanticVersion.parse(current);
            SemanticVersion latestVersion = SemanticVersion.parse(latest);
            @SuppressWarnings("deprecation")
            int cmp = currentVersion.compareTo(latestVersion);
            return cmp < 0;
        } catch (Exception e) {
            DebugLogger.warn(MODULE, "版本号比较失败（%s vs %s）: %s",
                    current, latest, e.getMessage());
            return false;
        }
    }

    /**
     * 执行一次完整的更新检查（同步，应在异步线程调用）。
     * <p>固定请求两个端点（可选 / 强制），合并规则见类注释。</p>
     *
     * @return 检查结果；任何失败均以 {@link UpdateResult#errorMessage()} 体现，不会抛出异常
     */
    public static UpdateResult check() {
        DebugLogger.entering(MODULE, "check",
                "optional=" + API_OPTIONAL_URL + ", forced=" + API_FORCED_URL);
        String current = getCurrentVersionString();
        String downloadUrl = buildJumpUrl(current);

        // 1. 先查强制更新端点（更关键，优先）
        RemoteVersionInfo forced = null;
        try {
            forced = fetchRemoteVersion(API_FORCED_URL);
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "check:forced-endpoint", e);
        }

        // 2. 再查可选更新端点
        RemoteVersionInfo optional = null;
        try {
            optional = fetchRemoteVersion(API_OPTIONAL_URL);
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "check:optional-endpoint", e);
        }

        // 两个端点均失败 → 返回错误结果
        if (forced == null && optional == null) {
            DebugLogger.warn(MODULE, "更新检查失败：可选与强制更新端点均不可用");
            UpdateResult failed = new UpdateResult(false, current, null, null, false,
                    null, null, List.of(), downloadUrl,
                    "拉取失败: 可选与强制更新端点均不可用");
            DebugLogger.exiting(MODULE, "check", "both-endpoints-failed");
            return failed;
        }

        // 3. 合并判定：强制端点有更新 > 可选端点有更新 > 无更新
        boolean forcedAvailable = forced != null && isNewerThan(current, forced.latestVersion());
        boolean optionalAvailable = optional != null && isNewerThan(current, optional.latestVersion());
        boolean updateAvailable = forcedAvailable || optionalAvailable;

        RemoteVersionInfo remote;
        if (forcedAvailable) {
            remote = forced;
            DebugLogger.info(MODULE, "存在强制更新: %s -> %s", current, forced.latestVersion());
        } else if (optionalAvailable) {
            remote = optional;
            DebugLogger.info(MODULE, "存在可选更新: %s -> %s", current, optional.latestVersion());
        } else {
            // 无更新：展示任一成功拉取的端点信息（优先可选端点）
            remote = optional != null ? optional : forced;
            DebugLogger.info(MODULE, "已是最新版本 (%s)", current);
        }

        UpdateResult result = new UpdateResult(
                updateAvailable,
                current,
                remote.latestVersion(),
                remote.type(),
                remote.forcedUpdate(),
                remote.releaseDate(),
                remote.releaseTime(),
                remote.changelog(),
                downloadUrl,
                null
        );
        DebugLogger.exiting(MODULE, "check", "updateAvailable=" + updateAvailable
                + ", latest=" + remote.latestVersion() + ", forced=" + remote.forcedUpdate());
        return result;
    }

    /**
     * 异步执行检查（在专用守护线程池中运行），通过 {@link CompletableFuture} 返回结果。
     *
     * @return 承载 {@link UpdateResult} 的 Future；回调中始终会收到一个结果对象
     */
    public static CompletableFuture<UpdateResult> checkAsync() {
        return CompletableFuture.supplyAsync(UpdateChecker::check, EXECUTOR);
    }
}
