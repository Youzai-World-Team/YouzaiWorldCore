package top.csituka.youzaiworldcore.client.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 从 Api 公开接口读取标题页公告，不依赖游戏服务器或 Api 密钥。
 * 仅缓存于本次客户端运行的内存中；标题页每分钟尝试刷新，失败保留上次成功内容。
 */
public final class ClientAnnouncementState {
    private static final String MODULE = "ClientAnnouncementState";
    /** 标题页公告的固定公开端点。 */
    public static final String API_URL = "https://api.mcyzw.top/api/announcement/core";
    private static final long REFRESH_INTERVAL_NANOS = Duration.ofSeconds(60).toNanos();
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_SUBTITLE_LENGTH = 160;
    private static final int MAX_BODY_LINES = 120;
    private static final int MAX_LINE_LENGTH = 500;
    private static final int MAX_BODY_LENGTH = 20_000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "YZWC-Announcement");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Announcement current;
    private static boolean loading;
    private static boolean attempted;
    private static long lastAttemptNanos;

    /** 不可变公告快照，供渲染线程安全读取；所有文字按纯文本显示。 */
    public record Announcement(String title, String subtitle, List<String> body) {
        public Announcement {
            body = List.copyOf(body);
        }
    }

    private ClientAnnouncementState() { }

    /** 未获取公告或后台已停用时返回 null，标题页使用内置多语言公告。 */
    public static Announcement get() {
        return current;
    }

    /** 可在标题页逐帧调用；限制刷新频率并避免窗口重建产生重复请求。 */
    public static synchronized void refreshIfDue() {
        long now = System.nanoTime();
        if (loading || (attempted && now - lastAttemptNanos < REFRESH_INTERVAL_NANOS)) return;
        attempted = true;
        loading = true;
        lastAttemptNanos = now;
        CompletableFuture.supplyAsync(() -> {
            try {
                return fetch();
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(error);
            }
        }, EXECUTOR).whenComplete(ClientAnnouncementState::completeRefresh);
    }

    private static synchronized void completeRefresh(Announcement announcement, Throwable error) {
        if (error == null) {
            // 停用响应主动清空；失败则保留之前的公告。
            current = announcement;
            DebugLogger.info(MODULE, announcement == null ? "线上公告已停用，使用内置公告" : "已获取最新标题页公告");
        } else {
            DebugLogger.warn(MODULE, "公告获取失败，保留现有公告：%s", error.getMessage());
        }
        loading = false;
    }

    private static Announcement fetch() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "YouzaiWorldCore-Announcement")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), MAX_RESPONSE_BYTES));
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
        return parse(response.body());
    }

    /** 严格校验公开协议，拒绝错误类型和超限正文，避免接口异常影响标题页。 */
    static Announcement parse(String json) throws IOException {
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) throw new IOException("公告响应必须是对象");
        JsonObject root = element.getAsJsonObject();
        JsonElement enabled = root.get("enabled");
        if (enabled == null || !enabled.isJsonPrimitive() || !enabled.getAsJsonPrimitive().isBoolean()) {
            throw new IOException("公告启用状态格式不正确");
        }
        if (!enabled.getAsBoolean()) return null;
        String title = readText(root.get("title"), MAX_TITLE_LENGTH).strip();
        String subtitle = readText(root.get("subtitle"), MAX_SUBTITLE_LENGTH).strip();
        if (title.isBlank()) throw new IOException("公告标题不能为空");
        JsonElement body = root.get("body");
        if (body == null || !body.isJsonArray() || body.getAsJsonArray().isEmpty()
                || body.getAsJsonArray().size() > MAX_BODY_LINES) throw new IOException("公告正文行数不正确");
        List<String> lines = new ArrayList<>();
        int length = 0;
        for (JsonElement line : body.getAsJsonArray()) {
            String text = readText(line, MAX_LINE_LENGTH);
            length += text.length() + (lines.isEmpty() ? 0 : 1);
            if (length > MAX_BODY_LENGTH) throw new IOException("公告正文过长");
            lines.add(text);
        }
        if (lines.stream().allMatch(String::isBlank)) throw new IOException("公告正文不能为空");
        return new Announcement(title, subtitle, lines);
    }

    private static String readText(JsonElement element, int maxLength) throws IOException {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IOException("公告文字格式不正确");
        }
        String text = element.getAsString();
        if (text.length() > maxLength || text.chars().anyMatch(ch -> ch < 32 || ch == 127)) {
            throw new IOException("公告文字过长或包含控制字符");
        }
        return text;
    }
}
