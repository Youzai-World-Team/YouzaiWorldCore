package top.csituka.youzaiworldcore.respawn;

import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 原地重生配置管理器。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code respawn_module} 分节。
 * 可按维度池或具体维度开启；满足任意一项即视为启用。
 */
@SuppressWarnings("null")
public final class InPlaceRespawnConfig {

    private static final String MODULE = "InPlaceRespawnConfig";

    /** 默认启用原地重生的维度池 */
    private static final String DEFAULT_POOL = "survival_world_pool";

    private static Set<String> enabledDimensionPools = defaultPools();
    private static Set<String> enabledDimensions = new LinkedHashSet<>();

    private InPlaceRespawnConfig() {
    }

    /** 从全局配置的 {@code respawn_module} 分节加载；分节缺失时写出默认配置。 */
    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.RESPAWN_MODULE);
        if (section.isEmpty()) {
            enabledDimensionPools = defaultPools();
            enabledDimensions = new LinkedHashSet<>();
            save();
            DebugLogger.info(MODULE, "respawn_module 分节不存在，已写入默认原地重生配置");
            DebugLogger.exiting(MODULE, "load", "default_created");
            return;
        }

        enabledDimensionPools = section.getStringSet("enabled_dimension_pools", defaultPools());
        enabledDimensions = section.getStringSet("enabled_dimensions", new LinkedHashSet<>());

        DebugLogger.info(MODULE, "已加载配置：维度池 %d 个，独立维度 %d 个",
                enabledDimensionPools.size(), enabledDimensions.size());
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重新从磁盘加载配置。 */
    public static void reload() {
        load();
    }

    /** 判断玩家当前死亡维度是否启用原地重生。 */
    public static boolean isEnabled(ServerPlayer player) {
        String dimensionId = player.level().dimension().identifier().toString();
        if (enabledDimensions.contains(dimensionId)) {
            return true;
        }
        return DimensionPoolSettings.getPoolByDimension(dimensionId)
                .map(pool -> enabledDimensionPools.contains(pool.id()))
                .orElse(false);
    }

    /** 重置为默认值并写入 {@code respawn_module} 分节（新开服 / 坏文件恢复用）。 */
    public static void writeDefaults() {
        enabledDimensionPools = defaultPools();
        enabledDimensions = new LinkedHashSet<>();
        save();
    }

    /** 保存当前配置到全局配置文件的 {@code respawn_module} 分节。 */
    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.RESPAWN_MODULE);
        section.setStringCollection("enabled_dimension_pools", enabledDimensionPools);
        section.setStringCollection("enabled_dimensions", enabledDimensions);
        GlobalSettings.save();
    }

    /** @return 默认启用原地重生的维度池集合 */
    private static Set<String> defaultPools() {
        Set<String> defaults = new LinkedHashSet<>();
        defaults.add(DEFAULT_POOL);
        return defaults;
    }
}
