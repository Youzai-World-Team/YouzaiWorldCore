package top.csituka.youzaiworldcore.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import top.csituka.youzaiworldcore.api.ApiHttp;
import top.csituka.youzaiworldcore.config.ApiModuleSettings;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.JsonFileStore;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/** 地图到 YouzaiWorldApi 的 HMAC 网桥；成功后每 48 小时同步增量，HTTP 与文件扫描均在后台。 */
public final class MapApiUploader {
    public static final long INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(2);
    private static final long RETRY_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int BATCH_TILES = 128;
    private static final String ENDPOINT = "/api/game/maps/upload";
    private final MinecraftServer server;
    private final MapTerrainStore terrain;
    private final JsonFileStore metadata;
    private final UUID worldId;
    private final String worldName;
    private long lastSuccess, cutoff, nextAttempt;
    private String destination;
    private volatile String configuredDestination;
    private volatile boolean uploadAllowed;
    private boolean running;
    private volatile boolean stopped;
    private final AtomicBoolean checkQueued = new AtomicBoolean();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("yzwc-map-clock").factory());

    /** 使用世界 map_module 主数据记录同步时间，失败不推进增量基线。 */
    public MapApiUploader(MinecraftServer server, MapTerrainStore terrain, JsonFileStore metadata, UUID worldId) {
        this.server = server; this.terrain = terrain; this.metadata = metadata; this.worldId = worldId;
        String name = server.getWorldData().getLevelName();
        worldName = name.isBlank() ? "Minecraft" : name.substring(0, Math.min(128, name.length()));
        var section = metadata.section(GlobalSettings.MAP_MODULE);
        lastSuccess = section.getLong("api_last_success", 0);
        cutoff = section.getLong("api_cutoff", 0);
        destination = section.getString("api_destination", "");
        configuredDestination = ApiModuleSettings.getBaseUrl();
        if (lastSuccess < 0 || cutoff < 0) section.fail("api_last_success", "地图上传时间不能为负数");
        nextAttempt = Math.max(System.currentTimeMillis() + 60000,
                destination.equals(configuredDestination) ? lastSuccess + INTERVAL_MILLIS : 0);
        metadata.save();
        clock.scheduleWithFixedDelay(this::queueCheck, 1, 1, TimeUnit.SECONDS);
    }

    /** 空服暂停不会触发 Fabric END_SERVER_TICK，因此由真实时钟请求主线程检查。 */
    private void queueCheck() {
        if (stopped || !checkQueued.compareAndSet(false, true)) return;
        try {
            server.execute(() -> {
                try { if (!stopped) tick(); }
                finally { checkQueued.set(false); }
            });
        } catch (RuntimeException error) {
            checkQueued.set(false);
            if (!stopped) DebugLogger.exception("MapApiUploader", "安排地图定时检查", error);
        }
    }

    /** 仅主线程访问同步进度与世界元数据；排队检查最多保留一个。 */
    private void tick() {
        uploadAllowed = MapServerSettings.enabled && MapServerSettings.apiUpload && ApiModuleSettings.isEnabled();
        if (!configuredDestination.equals(ApiModuleSettings.getBaseUrl())) {
            configuredDestination = ApiModuleSettings.getBaseUrl(); nextAttempt = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() >= nextAttempt) upload();
    }

    /** 立即安排上传；返回 false 表示关闭、正在上传或已经关服。 */
    public boolean upload() {
        if (stopped || running || !MapServerSettings.enabled || !MapServerSettings.apiUpload || !ApiModuleSettings.isEnabled()) return false;
        long started = System.currentTimeMillis();
        uploadAllowed = true;
        String target = ApiModuleSettings.getBaseUrl();
        configuredDestination = target;
        long since = target.equals(destination) ? cutoff : 0;
        JsonArray dimensions = new JsonArray();
        for (var level : server.getAllLevels()) {
            var value = new JsonObject();
            value.addProperty("id", level.dimension().identifier().toString());
            value.addProperty("min_y", level.getMinY()); value.addProperty("max_y", level.getMaxY()); dimensions.add(value);
        }
        running = true;
        DebugLogger.info("MapApiUploader", "开始同步地图 %s 到 Api", worldId);
        CompletableFuture.supplyAsync(() -> transfer(since, target, dimensions)).whenComplete((count, error) -> server.execute(() -> {
            if (stopped) return;
            running = false;
            if (!target.equals(ApiModuleSettings.getBaseUrl())) {
                configuredDestination = ApiModuleSettings.getBaseUrl(); nextAttempt = System.currentTimeMillis();
                DebugLogger.info("MapApiUploader", "Api 地址已变更，将向新地址重新同步地图");
                return;
            }
            if (error != null) {
                nextAttempt = System.currentTimeMillis() + RETRY_MILLIS;
                DebugLogger.exception("MapApiUploader", "地图上传失败，十分钟后重试", error);
                return;
            }
            lastSuccess = System.currentTimeMillis();
            // 与文件时间戳留两秒重叠，避免原子替换落在同步边界而漏传。
            cutoff = Math.max(0, started - 2000); destination = target;
            nextAttempt = lastSuccess + INTERVAL_MILLIS;
            ConfigSection section = metadata.section(GlobalSettings.MAP_MODULE);
            section.set("api_last_success", lastSuccess); section.set("api_cutoff", cutoff);
            section.set("api_destination", destination); metadata.save();
            DebugLogger.info("MapApiUploader", "地图同步完成：%d 个瓦片，下次在 48 小时后", count);
        }));
        return true;
    }

    private int transfer(long since, String destination, JsonArray dimensions) {
        var available = new java.util.HashSet<String>();
        dimensions.forEach(value -> available.add(value.getAsJsonObject().get("id").getAsString()));
        UUID run = UUID.randomUUID();
        var begin = envelope(run, "begin", 0);
        begin.addProperty("name", worldName); begin.add("dimensions", dimensions);
        JsonObject reply = send(begin, destination);
        if (!reply.has("full_required") || !reply.get("full_required").isJsonPrimitive()
                || !reply.get("full_required").getAsJsonPrimitive().isBoolean()) throw new IllegalStateException("Api 地图响应格式无效");
        long baseline = reply.get("full_required").getAsBoolean() ? 0 : since;
        class Batch {
            private JsonArray tiles = new JsonArray();
            private int sequence, total;
            private void flush() {
                if (tiles.isEmpty()) return;
                var body = envelope(run, "tiles", sequence);
                body.add("tiles", tiles); send(body, destination);
                total += tiles.size(); sequence++; tiles = new JsonArray();
            }
            private void add(MapTile tile) {
                // 存档移除维度后，旧瓦片仍留在本地，但不混入当前上传会话。
                if (!available.contains(tile.key().dimension())) return;
                tiles.add(encode(tile)); if (tiles.size() >= BATCH_TILES) flush();
            }
        }
        var batch = new Batch();
        try { terrain.visitSaved(baseline, batch::add); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        batch.flush();
        send(envelope(run, "complete", batch.sequence), destination);
        return batch.total;
    }

    private JsonObject envelope(UUID run, String phase, int batch) {
        JsonObject body = new JsonObject(); body.addProperty("version", 1);
        body.addProperty("world_id", worldId.toString()); body.addProperty("run_id", run.toString());
        body.addProperty("phase", phase); body.addProperty("batch", batch); return body;
    }

    private JsonObject send(JsonObject body, String destination) {
        if (stopped || !uploadAllowed || Thread.currentThread().isInterrupted() || !destination.equals(configuredDestination)) {
            throw new IllegalStateException("地图上传已停止、关闭或 Api 地址已更改");
        }
        var response = ApiHttp.request("POST", ENDPOINT, body.toString(), null, 30);
        if (!ApiHttp.successful(response)) throw new IllegalStateException(response == null
                ? ApiHttp.failureMessage() : "Api 地图上传返回 HTTP " + response.statusCode());
        JsonObject result = ApiHttp.parse(response.body());
        if (!ApiHttp.booleanValue(result, "ok", false)) throw new IllegalStateException("Api 未确认地图上传");
        return result;
    }

    /** 版本 1：1024 字节 RGBA、512 字节大端有符号高度、256 字节光照，统一 Base64。 */
    private static JsonObject encode(MapTile tile) {
        var key = tile.key(); var value = new JsonObject();
        value.addProperty("dimension", key.dimension()); value.addProperty("layer", key.layer().name());
        value.addProperty("height", key.height()); value.addProperty("x", key.chunkX()); value.addProperty("z", key.chunkZ());
        ByteBuffer bytes = ByteBuffer.allocate(1792);
        for (int color : tile.colors()) bytes.put((byte) (color >>> 16)).put((byte) (color >>> 8)).put((byte) color).put((byte) (color >>> 24));
        for (short height : tile.heights()) bytes.putShort(height);
        bytes.put(tile.lights()); value.addProperty("data", Base64.getEncoder().encodeToString(bytes.array()));
        return value;
    }

    /** 关服只取消后续批次；后台未完成不会推进本地同步时间。 */
    public void stop() { stopped = true; clock.shutdownNow(); }
    /** 最近完整同步的时间戳（毫秒），0 表示尚未成功。 */
    public long lastSuccess() { return lastSuccess; }
}
