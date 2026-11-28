package top.csituka.youzaiworldcore.dimensionalinventories;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.mixin.PlayerEnchantmentSeedAccessor;

/** 原版玩家档案中随当前维度池切换的字段。 */
record PoolPlayerState(List<String> tags, int enchantmentSeed, @Nullable DeathLocation lastDeathLocation) {
    static PoolPlayerState capture(ServerPlayer player) {
        return new PoolPlayerState(List.copyOf(player.entityTags()), player.getEnchantmentSeed(),
                player.getLastDeathLocation().map(DeathLocation::from).orElse(null));
    }

    @SuppressWarnings("null")
    static void apply(ServerPlayer player, @Nullable PoolPlayerState state, ResourceKey<Level> dimension) {
        Optional<GlobalPos> deathLocation = state == null ? player.getLastDeathLocation()
                : (state.lastDeathLocation == null ? Optional.empty() : state.lastDeathLocation.toGlobalPos());
        player.entityTags().clear();
        if (state != null && state.tags != null) {
            for (String tag : state.tags) {
                if (tag != null) {
                    player.addTag(tag);
                }
            }
        }
        ((PlayerEnchantmentSeedAccessor) player).youzaiworldcore$setEnchantmentSeed(
                state == null ? player.getRandom().nextInt(1, Integer.MAX_VALUE) : state.enchantmentSeed);
        player.setLastDeathLocation(DimensionPoolSettings.deathLocationInPool(deathLocation, dimension));
    }

    private record DeathLocation(String dimension, int x, int y, int z) {
        static DeathLocation from(GlobalPos pos) {
            return new DeathLocation(pos.dimension().identifier().toString(),
                    pos.pos().getX(), pos.pos().getY(), pos.pos().getZ());
        }

        @SuppressWarnings("null")
        Optional<GlobalPos> toGlobalPos() {
            Identifier id = dimension == null ? null : Identifier.tryParse(dimension);
            return id == null ? Optional.empty()
                    : Optional.of(
                            GlobalPos.of(ResourceKey.create(Registries.DIMENSION, id), new BlockPos(x, y, z)));
        }
    }
}
