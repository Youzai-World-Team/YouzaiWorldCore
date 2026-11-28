package top.csituka.youzaiworldcore.dimensionalinventories;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayer.RespawnConfig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** 每个玩家档案内各维度池的个人重生点。 */
public final class PoolRespawnPoints {
    private static final String SAVE_KEY = "youzaiworldcore:pool_respawn_points";
    private static final Codec<Map<String, RespawnConfig>> CODEC = Codec.unboundedMap(Codec.STRING,
            RespawnConfig.CODEC);

    private Map<String, RespawnConfig> points = Map.of();

    public @Nullable RespawnConfig get(ResourceKey<Level> dimension) {
        String poolId = poolId(dimension);
        RespawnConfig point = points.get(poolId);
        // 池配置调整后，仍只允许使用属于本池的重生点。
        return point != null && poolId.equals(poolId(point.respawnData().dimension())) ? point : null;
    }

    public void set(ResourceKey<Level> currentDimension, @Nullable RespawnConfig point) {
        // /spawnpoint 可以为身处其他维度的玩家设置重生点，按目标坐标的维度归档。
        String poolId = poolId(point == null ? currentDimension : point.respawnData().dimension());
        var updated = new HashMap<>(points);
        if (point == null) {
            updated.remove(poolId);
        } else {
            updated.put(poolId, point);
        }
        points = Map.copyOf(updated);
    }

    @SuppressWarnings("null")
    public void load(ValueInput input, @Nullable RespawnConfig legacyPoint) {
        points = input.read(SAVE_KEY, CODEC).map(Map::copyOf).orElseGet(() -> legacyPoint == null
                ? Map.of()
                : Map.of(poolId(legacyPoint.respawnData().dimension()), legacyPoint));
    }

    @SuppressWarnings("null")
    public void save(ValueOutput output) {
        output.store(SAVE_KEY, CODEC, points);
    }

    public void copyFrom(PoolRespawnPoints old) {
        points = old.points;
    }

    public void clear() {
        points = Map.of();
    }

    public static void clearAll(ServerPlayer player) {
        ((Access) player).youzaiworldcore$clearAllRespawnPoints();
    }

    public interface Access {
        void youzaiworldcore$clearAllRespawnPoints();
    }

    @SuppressWarnings("null")
    private static String poolId(ResourceKey<Level> dimension) {
        return DimensionPoolSettings.statePoolId(dimension);
    }
}
