package top.csituka.youzaiworldcore.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import top.csituka.youzaiworldcore.api.ApiHttp;
import top.csituka.youzaiworldcore.config.ApiModuleSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/** 地图到 YouzaiWorldApi 的 HMAC 网桥；按配置周期同步增量，HTTP 与数据库扫描均在后台。 */
public final class MapApiUploader {
    private static final long RETRY_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final int BATCH_TILES = 128;
    private static final int BATCH_PLAYERS = 256;
    private static final String ENDPOINT = "/api/game/maps/upload";
    private static final String PLAYERS_ENDPOINT = "/api/game/maps/players";
    private final MinecraftServer server;
    private final MapTerrainStore terrain;
    private final MapDatabase metadata;
    private final UUID worldId;
    private final String worldName;
    private final MapPlayerPositionStore playerPositions;
    private final Runnable capturePlayers;
    private long lastSuccess, cutoff, nextAttempt, configuredInterval;
    private String destination;
    private volatile String configuredDestination;
    private volatile boolean uploadAllowed;
    private boolean running;
    private boolean playersRunning, playersDirty = true;
    private long nextPlayersAttempt;
    private String dataId;
    private volatile boolean stopped;
    private final AtomicBoolean checkQueued = new AtomicBoolean();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("yzwc-map-clock").factory());

    /** 使用世界 map_module 主数据记录同步时间，失败不推进增量基线。 */
    public MapApiUploader(MinecraftServer server, MapTerrainStore terrain, MapDatabase metadata, UUID worldId,
                          MapPlayerPositionStore playerPositions, Runnable capturePlayers) {
        this.server = server; this.terrain = terrain; this.metadata = metadata; this.worldId = worldId;
        this.playerPositions = playerPositions; this.capturePlayers = capturePlayers;
        String name = server.getWorldData().getLevelName();
        worldName = name.isBlank() ? "Minecraft" : name.substring(0, Math.min(128, name.length()));
        var section = new MapSqlSettings(metadata, "upload");
        lastSuccess = Long.parseLong(section.getString("last_success", "0"));
        cutoff = Long.parseLong(section.getString("revision", "0"));
        destination = section.getString("destination", "");
        dataId = section.getString("data_id", "");
        configuredDestination = ApiModuleSettings.getBaseUrl();
        if (lastSuccess < 0 || cutoff < 0) invalidProgress();
        configuredInterval = intervalMillis();
        nextAttempt = Math.max(System.currentTimeMillis() + 60000,
                destination.equals(configuredDestination) ? lastSuccess + intervalMillis() : 0);
        if (dataId.isBlank()) nextAttempt = Math.min(nextAttempt, System.currentTimeMillis() + 60000);
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
    private static long intervalMillis() { return TimeUnit.MINUTES.toMillis(MapServerSettings.uploadIntervalMinutes); }
    private static void invalidProgress() { throw new IllegalStateException("地图 SQLite 同步进度无效"); }

    private void tick() {
        uploadAllowed = MapServerSettings.enabled && MapServerSettings.apiUpload && ApiModuleSettings.isEnabled();
        if (!configuredDestination.equals(ApiModuleSettings.getBaseUrl())) {
            configuredDestination = ApiModuleSettings.getBaseUrl(); nextAttempt = System.currentTimeMillis();
        }
        if (configuredInterval != intervalMillis()) {
            configuredInterval = intervalMillis();
            nextAttempt = Math.max(System.currentTimeMillis(), lastSuccess + configuredInterval);
        }
        if (System.currentTimeMillis() >= nextAttempt) upload();
        if (uploadAllowed && !running && !playersRunning && playersDirty && !dataId.isBlank()
                && destination.equals(configuredDestination) && System.currentTimeMillis() >= nextPlayersAttempt) {
            uploadPlayerPositions();
        }
    }

    /** 主线程通知位置发生变化，合并为一次异步上传任务，不影响地形上传周期。 */
    public void playerPositionsChanged() { playersDirty = true; }

    private void uploadPlayerPositions() {
        String target = configuredDestination, identity = dataId;
        var available = new java.util.HashSet<String>();
        for (var level : server.getAllLevels()) available.add(level.dimension().identifier().toString());
        playersRunning = true; playersDirty = false;
        nextPlayersAttempt = System.currentTimeMillis() + 2000;
        CompletableFuture.supplyAsync(() -> transferPlayers(identity, target, available, false))
                .whenComplete((count, error) -> server.execute(() -> {
                    if (stopped) return;
                    playersRunning = false;
                    if (error != null) {
                        playersDirty = true; nextPlayersAttempt = System.currentTimeMillis() + RETRY_MILLIS;
                        Throwable cause = error instanceof java.util.concurrent.CompletionException ? error.getCause() : error;
                        if (cause instanceof PositionsResetException) {
                            dataId = ""; nextAttempt = System.currentTimeMillis();
                        }
                        DebugLogger.exception("MapApiUploader", "玩家位置上传失败，保留快照等待重试", error);
                    } else if (count > 0) {
                        DebugLogger.info("MapApiUploader", "玩家位置快照已同步：%d 位玩家", count);
                    }
                }));
    }

    /** 立即安排上传；返回 false 表示关闭、正在上传或已经关服。 */
    public boolean upload() {
        if (stopped || running || playersRunning || !MapServerSettings.enabled || !MapServerSettings.apiUpload || !ApiModuleSettings.isEnabled()) return false;
        capturePlayers.run();
        long through = terrain.revision();
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
        CompletableFuture.supplyAsync(() -> transfer(since, through, target, dimensions)).whenComplete((result, error) -> server.execute(() -> {
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
            cutoff = through; destination = target;
            dataId = result.dataId(); nextPlayersAttempt = 0;
            nextAttempt = lastSuccess + intervalMillis();
            metadata.settings("upload", java.util.Map.of("last_success", Long.toString(lastSuccess),
                    "revision", Long.toString(cutoff), "destination", destination, "data_id", dataId));
            DebugLogger.info("MapApiUploader", "地图同步完成：%d 个瓦片、%d 位玩家快照，修订号=%d", result.tiles(), result.players(), cutoff);
        }));
        return true;
    }

    private record Transfer(int tiles, int players, String dataId) { }

    private Transfer transfer(long since, long through, String destination, JsonArray dimensions) {
        var available = new java.util.HashSet<String>();
        dimensions.forEach(value -> available.add(value.getAsJsonObject().get("id").getAsString()));
        UUID run = UUID.randomUUID();
        var begin = envelope(run, "begin", 0, through);
        begin.addProperty("name", worldName); begin.add("dimensions", dimensions);
        JsonObject reply = send(begin, destination);
        if (!reply.has("full_required") || !reply.get("full_required").isJsonPrimitive()
                || !reply.get("full_required").getAsJsonPrimitive().isBoolean()) throw new IllegalStateException("Api 地图响应格式无效");
        if (!reply.has("accepted_revision")) throw new IllegalStateException("Api 未返回地图修订进度");
        long accepted = reply.get("accepted_revision").getAsLong();
        if (accepted < 0 || accepted > through) throw new IllegalStateException("Api 地图修订进度不一致");
        long baseline = reply.get("full_required").getAsBoolean() ? 0 : Math.min(since, accepted);
        String identity = ApiHttp.stringValue(reply, "data_id");
        if (identity == null || identity.isBlank() || !UUID.fromString(identity).toString().equals(identity)) {
            throw new IllegalStateException("Api 未返回有效的地图数据标识，请同步更新 Api");
        }
        class Batch {
            private JsonArray tiles = new JsonArray();
            private int sequence, total;
            private void flush() {
                if (tiles.isEmpty()) return;
                var body = envelope(run, "tiles", sequence, through);
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
        try { terrain.visitSaved(baseline, through, batch::add); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        batch.flush();
        int players = transferPlayers(identity, destination, available, true);
        send(envelope(run, "complete", batch.sequence, through), destination);
        return new Transfer(batch.total, players, identity);
    }

    private int transferPlayers(String identity, String target, java.util.Set<String> dimensions, boolean all) {
        var positions = playerPositions.read(all).stream().filter(value -> dimensions.contains(value.dimension())).toList();
        for (int offset = 0; offset < positions.size(); offset += BATCH_PLAYERS) {
            var batch = positions.subList(offset, Math.min(positions.size(), offset + BATCH_PLAYERS));
            var body = new JsonObject(); var values = new JsonArray();
            batch.forEach(position -> values.add(position.json()));
            body.addProperty("version", 2); body.addProperty("source", "minecraft_server");
            body.addProperty("world_id", worldId.toString()); body.addProperty("data_id", identity);
            body.add("players", values); send(body, target, PLAYERS_ENDPOINT);
            playerPositions.acknowledge(batch);
        }
        return positions.size();
    }

    private static final class PositionsResetException extends IllegalStateException {
        private PositionsResetException() { super("Api 地图已清空，需重新同步数据标识"); }
    }

    private JsonObject envelope(UUID run, String phase, int batch, long revision) {
        JsonObject body = new JsonObject(); body.addProperty("version", 2);
        body.addProperty("source", "minecraft_chunk_storage"); body.addProperty("revision", revision);
        body.addProperty("world_id", worldId.toString()); body.addProperty("run_id", run.toString());
        body.addProperty("phase", phase); body.addProperty("batch", batch); return body;
    }

    private JsonObject send(JsonObject body, String destination) {
        return send(body, destination, ENDPOINT);
    }

    private JsonObject send(JsonObject body, String destination, String endpoint) {
        if (stopped || !uploadAllowed || Thread.currentThread().isInterrupted() || !destination.equals(configuredDestination)) {
            throw new IllegalStateException("地图上传已停止、关闭或 Api 地址已更改");
        }
        var response = ApiHttp.request("POST", endpoint, body.toString(), null, 30);
        if (response != null && response.statusCode() == 409 && PLAYERS_ENDPOINT.equals(endpoint)) {
            throw new PositionsResetException();
        }
        if (!ApiHttp.successful(response)) throw new IllegalStateException(response == null
                ? ApiHttp.failureMessage() : "Api 地图上传返回 HTTP " + response.statusCode());
        JsonObject result = ApiHttp.parse(response.body());
        if (!ApiHttp.booleanValue(result, "ok", false)) throw new IllegalStateException("Api 未确认地图上传");
        return result;
    }

    /** 版本 2：1024 字节 RGBA、512 字节大端有符号高度、256 字节光照，统一 Base64。 */
    private static JsonObject encode(MapTile tile) {
        var key = tile.key(); var value = new JsonObject();
        value.addProperty("revision", tile.revision());
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
