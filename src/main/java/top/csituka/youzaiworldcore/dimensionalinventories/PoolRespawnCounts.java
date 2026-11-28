package top.csituka.youzaiworldcore.dimensionalinventories;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;

/** 每个池的原地重生累计次数，保存在原版玩家档案中。 */
public final class PoolRespawnCounts {
    private static final String SAVE_KEY = "youzaiworldcore:pool_respawn_counts";
    private static final Codec<Map<String, Integer>> CODEC = Codec.unboundedMap(Codec.STRING,
            Codec.intRange(0, Integer.MAX_VALUE));

    private @Nullable Map<String, Integer> counts;

    public static OptionalInt get(ServerPlayer player) {
        var data = ((Access) player).youzaiworldcore$getPoolRespawnCounts();
        var counts = data.counts;
        if (counts == null) {
            var account = AccountDataStorage.getOrCreate(player.getName().getString(), player.getUUID());
            if (account == null) {
                return OptionalInt.empty();
            }
            counts = new HashMap<>();
            counts.put(DimensionPoolSettings.SURVIVAL_POOL_ID, Math.max(0, account.inPlaceRespawnCount));
            data.counts = counts;
        }
        return OptionalInt.of(counts.getOrDefault(poolId(player), 0));
    }

    public static int increment(ServerPlayer player) {
        var counts = ((Access) player).youzaiworldcore$getPoolRespawnCounts().counts;
        if (counts == null) {
            throw new IllegalStateException("原地重生次数尚未初始化");
        }
        return counts.compute(poolId(player),
                (pool, count) -> count == null ? 1 : (int) Math.min(Integer.MAX_VALUE, (long) count + 1));
    }

    private static String poolId(ServerPlayer player) {
        return DimensionPoolSettings.statePoolId(player.level().dimension());
    }

    @SuppressWarnings("null")
    public void load(ValueInput input) {
        counts = input.read(SAVE_KEY, CODEC).map(HashMap::new).orElse(null);
    }

    @SuppressWarnings("null")
    public void save(ValueOutput output) {
        if (counts != null) {
            output.store(SAVE_KEY, CODEC, counts);
        }
    }

    public void copyFrom(PoolRespawnCounts old) {
        counts = old.counts == null ? null : new HashMap<>(old.counts);
    }

    public interface Access {
        PoolRespawnCounts youzaiworldcore$getPoolRespawnCounts();
    }
}
