package top.csituka.youzaiworldcore.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.SemanticVersion;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.config.UpdateCheckerConfig;
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
 *   <li>异步拉取远程 {@code version.json} 并解析为 {@link RemoteVersionInfo}</li>
 *   <li>基于 {@link SemanticVersion} 比较当前与最新版本，得到 {@link UpdateResult}</li>
 *   <li>构造下载页地址 {@code https://mcyzw.top/yzwc?version=<当前版本数字>&type=<当前类型>}</li>
 * </ul>
 * 所有网络请求在独立守护线程执行，绝不阻塞主线程 / 渲染线程；任何异常均被捕获并
 * 以 {@link UpdateResult#errorMessage()} 形式返回，保证不影响服务器或客户端运行。
 */
@SuppressWarnings({"null", "unused"})
public final class UpdateChecker {

    public static final String MODULE = "UpdateChecker";

    /** 检查 / 下载地址默认基址（版本号与类型作为查询参数附加） */
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
     * 构造检查更新地址。
     * <p>若提供了自定义基址（非空且不等于 {@link #DEFAULT_BASE}），直接原样使用
     * （仅补充可能的 scheme 前缀）；否则回退默认并自动附加 {@code /version.json}。</p>
     */
    public static String buildCheckUrl(String checkBase) {
        if (checkBase == null || checkBase.isEmpty() || checkBase.trim().equals(DEFAULT_BASE)) {
            return DEFAULT_BASE + "/version.json";
        }
        String base = checkBase.trim();
        // 用户自定义的地址可能不含协议前缀（如 "127.0.0.1:5500/yzwc"），补上 http://
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        return base;
    }

    /**
     * 构造下载页（跳转）地址。
     * <p>若提供了自定义基址（非空且不等于 {@link #DEFAULT_BASE}），直接原样使用
     * （仅做 {@code @version} / {@code @type} 占位符替换和 scheme 补全）；
     * 否则回退默认并自动附加 {@code ?version=<数字>&type=<类型>}。</p>
     */
    public static String buildJumpUrl(String jumpBase, String fullVersion) {
        // 统一从 fullVersion 提取数字和类型
        int dash = fullVersion.indexOf('-');
        String number = dash >= 0 ? fullVersion.substring(0, dash) : fullVersion;
        String type = dash >= 0 ? fullVersion.substring(dash + 1) : "release";

        if (jumpBase == null || jumpBase.isEmpty() || jumpBase.trim().equals(DEFAULT_BASE)) {
            String base = DEFAULT_BASE;
            if (base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            return base + "?version=" + number + "&type=" + type;
        }

        String base = jumpBase.trim()
                .replace("@version", number)
                .replace("@type", type);
        // 用户自定义的地址可能不含协议前缀，补上 http://
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        return base;
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

    /** 同步拉取并解析远程 version.json（调用方需确保不在主线程执行） */
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
     * 执行一次完整的更新检查（同步，应在异步线程调用）。
     *
     * @param checkBase 检查更新基址（自动附加 /version.json）；空值回退默认
     * @param jumpBase  下载页（跳转）基址（自动附加 ?version=&type=）；空值回退默认
     * @return 检查结果；任何失败均以 {@link UpdateResult#errorMessage()} 体现，不会抛出异常
     */
    public static UpdateResult check(String checkBase, String jumpBase) {
        String checkUrl = buildCheckUrl(checkBase);
        DebugLogger.info(MODULE, "开始检查更新: checkUrl=%s", checkUrl);
        DebugLogger.entering(MODULE, "check", "checkUrl=" + checkUrl);
        String current = getCurrentVersionString();
        String downloadUrl = buildJumpUrl(jumpBase, current);

        RemoteVersionInfo remote;
        try {
            remote = fetchRemoteVersion(checkUrl);
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "fetchRemoteVersion", e);
            UpdateResult failed = new UpdateResult(false, current, null, null, false,
                    null, null, List.of(), downloadUrl, "拉取失败: " + e.getMessage());
            DebugLogger.exiting(MODULE, "check", "fetch-error");
            return failed;
        }

        boolean updateAvailable = false;
        try {
            SemanticVersion currentVersion = SemanticVersion.parse(current);
            SemanticVersion latestVersion = SemanticVersion.parse(remote.latestVersion());
            @SuppressWarnings("deprecation")
            int cmp = currentVersion.compareTo(latestVersion);
            updateAvailable = cmp < 0;
        } catch (Exception e) {
            DebugLogger.warn(MODULE, "版本号比较失败（%s vs %s）: %s",
                    current, remote.latestVersion(), e.getMessage());
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
        DebugLogger.exiting(MODULE, "check", "updateAvailable=" + updateAvailable);
        return result;
    }

    /**
     * 异步执行检查（在专用守护线程池中运行），通过 {@link CompletableFuture} 返回结果。
     *
     * @param checkBase 检查更新基址（自动附加 /version.json）；空值回退默认
     * @param jumpBase  下载页（跳转）基址（自动附加 ?version=&type=）；空值回退默认
     * @return 承载 {@link UpdateResult} 的 Future；回调中始终会收到一个结果对象
     */
    public static CompletableFuture<UpdateResult> checkAsync(String checkBase, String jumpBase) {
        return CompletableFuture.supplyAsync(() -> check(checkBase, jumpBase), EXECUTOR);
    }

    /**
     * 根据服务端类型解析实际使用的检查 / 跳转基址。
     * <ul>
     *   <li>专用服务端（dedicated）：使用配置文件 {@link UpdateCheckerConfig} 中的基址</li>
     *   <li>内嵌（集成）服务端：跟随客户端设置 {@link UpdateAddressState}，
     *       且仅在开发者模式启用时生效（否则回退默认）</li>
     * </ul>
     */
    public static final class AddressPair {
        public final String checkBase;
        public final String jumpBase;

        public AddressPair(String checkBase, String jumpBase) {
            this.checkBase = checkBase;
            this.jumpBase = jumpBase;
        }
    }

    public static AddressPair resolveServerAddresses(net.minecraft.server.MinecraftServer server) {
        if (server.isDedicatedServer()) {
            return new AddressPair(UpdateCheckerConfig.getCheckAddress(), UpdateCheckerConfig.getJumpAddress());
        }
        if (UpdateAddressState.isClientDevMode()) {
            return new AddressPair(UpdateAddressState.getClientCheckBase(), UpdateAddressState.getClientJumpBase());
        }
        return new AddressPair("", "");
    }
}
