package top.csituka.youzaiworldcore.respawn;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 原地重生配置管理器。
 * <p>
 * 配置文件：{@code config/youzaiworldcore/in_place_respawn.json}。
 * 可按维度池或具体维度开启；满足任意一项即视为启用。
 */
@SuppressWarnings("null")
public final class InPlaceRespawnConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("youzaiworldcore")
            .resolve("in_place_respawn.json");

    private static ConfigData data = createDefaultData();

    private InPlaceRespawnConfig() {
    }

    /** 加载配置；文件不存在时写出默认配置。 */
    public static void load() {
        DebugLogger.entering("InPlaceRespawnConfig", "load", "file=" + CONFIG_FILE);
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            if (!Files.exists(CONFIG_FILE)) {
                data = createDefaultData();
                save();
                DebugLogger.info("InPlaceRespawnConfig", "已创建默认原地重生配置：%s", CONFIG_FILE);
                DebugLogger.exiting("InPlaceRespawnConfig", "load", "default_created");
                return;
            }

            ConfigData loaded = GSON.fromJson(Files.readString(CONFIG_FILE), ConfigData.class);
            data = sanitize(loaded);
            DebugLogger.info("InPlaceRespawnConfig", "已加载配置：维度池 %d 个，独立维度 %d 个",
                    data.enabledDimensionPools.size(), data.enabledDimensions.size());
        } catch (Exception e) {
            data = createDefaultData();
            DebugLogger.exception("InPlaceRespawnConfig", "load", e);
        }
        DebugLogger.exiting("InPlaceRespawnConfig", "load");
    }

    /** 重新从磁盘加载配置。 */
    public static void reload() {
        load();
    }

    /** 判断玩家当前死亡维度是否启用原地重生。 */
    public static boolean isEnabled(ServerPlayer player) {
        String dimensionId = player.level().dimension().identifier().toString();
        if (data.enabledDimensions.contains(dimensionId)) {
            return true;
        }
        return DimensionPoolSettings.getPoolByDimension(dimensionId)
                .map(pool -> data.enabledDimensionPools.contains(pool.id()))
                .orElse(false);
    }

    private static void save() throws IOException {
        Files.writeString(CONFIG_FILE, GSON.toJson(data));
    }

    private static ConfigData createDefaultData() {
        ConfigData defaults = new ConfigData();
        defaults.enabledDimensionPools.add("survival_world_pool");
        return defaults;
    }

    private static ConfigData sanitize(ConfigData loaded) {
        if (loaded == null) {
            return createDefaultData();
        }
        if (loaded.enabledDimensionPools == null) {
            loaded.enabledDimensionPools = new LinkedHashSet<>();
        }
        if (loaded.enabledDimensions == null) {
            loaded.enabledDimensions = new LinkedHashSet<>();
        }
        loaded.enabledDimensionPools.removeIf(value -> value == null || value.isBlank());
        loaded.enabledDimensions.removeIf(value -> value == null || value.isBlank());
        return loaded;
    }

    private static final class ConfigData {
        private Set<String> enabledDimensionPools = new LinkedHashSet<>();
        private Set<String> enabledDimensions = new LinkedHashSet<>();
    }
}
