package top.csituka.youzaiworldcore.map;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import top.csituka.youzaiworldcore.network.MapMobQueryPayload;
import top.csituka.youzaiworldcore.network.MapMobSnapshotPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 生物雷达只查询服务端现有实体索引，不读盘加载区块，不生成实体，不持久化位置。 */
public final class MapMobRadarServer {
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    private MapMobRadarServer() { }

    public static void query(ServerPlayer player, MapMobQueryPayload request) {
        if (!MapServerManager.mobRadarEligible(player) || !player.isAlive()
                || !ServerPlayNetworking.canSend(player, MapMobSnapshotPayload.ID)) return;
        var level = player.level(); var server = level.getServer();
        int tick = server.getTickCount();
        if (tick - LAST.getOrDefault(player.getUUID(), -1000) < 10) return;
        LAST.put(player.getUUID(), tick);
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(request.entityType()).orElse(null);
        if (type == null) return;
        int radius = Math.clamp(Math.min(request.radius(), server.getPlayerList().getSimulationDistance()), 1, 32);
        int cx = player.chunkPosition().x(), cz = player.chunkPosition().z();
        var bounds = new AABB((cx - radius) * 16.0, level.getMinY(), (cz - radius) * 16.0,
                (cx + radius + 1) * 16.0, level.getMaxY(), (cz + radius + 1) * 16.0);
        var targets = level.getEntitiesOfClass(Mob.class, bounds, mob -> mob.getType() == type && mob.isAlive()
                && !mob.isInvisibleTo(player) && Math.abs(mob.chunkPosition().x() - cx) <= radius
                && Math.abs(mob.chunkPosition().z() - cz) <= radius).stream()
                .map(mob -> new MapMobSnapshotPayload.Target(mob.getUUID(), mob.getX(), mob.getY(), mob.getZ())).toList();
        int parts = Math.max(1, Math.ceilDiv(targets.size(), MapMobSnapshotPayload.BATCH_SIZE));
        // 网络解码有总量边界；超出边界拒绝此次快照，不能把截断数据冒充完整查询。
        if (parts > MapMobSnapshotPayload.MAX_PARTS) { DebugLogger.warn("MapMobRadar", "生物雷达快照过大：%d", targets.size()); return; }
        for (int part = 0; part < parts; part++) {
            int from = part * MapMobSnapshotPayload.BATCH_SIZE, to = Math.min(targets.size(), from + MapMobSnapshotPayload.BATCH_SIZE);
            ServerPlayNetworking.send(player, new MapMobSnapshotPayload(request.request(), level.dimension().identifier().toString(),
                    request.entityType(), part, parts, targets.subList(from, to)));
        }
        DebugLogger.debug("MapMobRadar", "查询 %s，半径=%d 区块，生物=%d", request.entityType(), radius, targets.size());
    }
    public static void clear() { LAST.clear(); }
}
