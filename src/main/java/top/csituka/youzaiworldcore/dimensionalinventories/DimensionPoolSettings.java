package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 维度池配置管理器。
 * <p>
 * 配置文件位置：{@code config/youzaiworldcore/dimensional_inventories/pool_settings.json}
 * <p>
 * 管理 7 个预定义维度池的 JSON 持久化配置。
 */
public final class DimensionPoolSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/DimensionPoolSettings");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(GameType.class, new GameTypeSerializer())
            .create();

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("dimensional_inventories");

    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("pool_settings.json");

    /** 所有注册的维度池，按 ID 索引 */
    private static final Map<String, DimensionPool> POOLS = new LinkedHashMap<>();

    /** 维度到池的快速查找映射 */
    private static final Map<String, String> DIMENSION_TO_POOL = new HashMap<>();

    private DimensionPoolSettings() {}

    // ===== 公共 API =====

    /** 获取所有维度池 */
    public static Collection<DimensionPool> getAllPools() {
        return Collections.unmodifiableCollection(POOLS.values());
    }

    /** 按 ID 获取维度池 */
    public static DimensionPool getPool(String poolId) {
        return POOLS.get(poolId);
    }

    /** 获取包含指定维度的维度池 */
    public static Optional<DimensionPool> getPoolByDimension(String dimensionId) {
        String poolId = DIMENSION_TO_POOL.get(dimensionId);
        if (poolId == null) return Optional.empty();
        return Optional.ofNullable(POOLS.get(poolId));
    }

    /** 判断两个维度是否在同一个池中 */
    public static boolean dimensionsInSamePool(String dimA, String dimB) {
        if (dimA.equals(dimB)) return true;
        String poolA = DIMENSION_TO_POOL.get(dimA);
        String poolB = DIMENSION_TO_POOL.get(dimB);
        return poolA != null && poolA.equals(poolB);
    }

    /** 检查维度是否属于任何池 */
    public static boolean isDimensionInAnyPool(String dimensionId) {
        return DIMENSION_TO_POOL.containsKey(dimensionId);
    }

    // ===== 加载/保存 =====

    /** 从文件加载配置，不存在则创建默认配置 */
    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            LOGGER.info("维度池配置文件不存在，正在创建默认配置...");
            createDefaultConfig();
            save();
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            Type listType = new TypeToken<List<DimensionPool>>() {}.getType();
            List<DimensionPool> poolList = GSON.fromJson(json, listType);
            if (poolList == null) return;

            POOLS.clear();
            DIMENSION_TO_POOL.clear();

            for (DimensionPool pool : poolList) {
                POOLS.put(pool.id(), pool);
                for (String dim : pool.dimensions()) {
                    DIMENSION_TO_POOL.put(dim, pool.id());
                }
            }

            LOGGER.info("已加载 {} 个维度池", POOLS.size());
        } catch (Exception e) {
            LOGGER.error("加载维度池配置失败: {}", e.getMessage());
        }
    }

    /** 保存配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            List<DimensionPool> poolList = new ArrayList<>(POOLS.values());
            String json = GSON.toJson(poolList);
            Files.writeString(CONFIG_FILE, json);
            LOGGER.info("维度池配置已保存");
        } catch (IOException e) {
            LOGGER.error("保存维度池配置失败: {}", e.getMessage());
        }
    }

    /** 刷新配置（从文件重载） */
    public static void reload() {
        load();
    }

    // ===== 默认配置 =====

    /**
     * 创建默认的 7 个维度池。
     * "生存世界"包含原版三维度，其余池为空（等待管理员配置维度）。
     */
    private static void createDefaultConfig() {
        POOLS.clear();
        DIMENSION_TO_POOL.clear();

        // 1. 生存世界 — 包含原版三维度
        DimensionPool survival = new DimensionPool(
                "survival_world_pool",
                "生存世界",
                GameType.SURVIVAL,
                true,   // 允许进度
                true    // 允许统计
        );
        survival.addDimension("minecraft:overworld");
        survival.addDimension("minecraft:the_nether");
        survival.addDimension("minecraft:the_end");
        POOLS.put(survival.id(), survival);

        // 2. 主城（空池）
        POOLS.put("main_city_pool", new DimensionPool(
                "main_city_pool", "主城",
                GameType.ADVENTURE, false, false
        ));

        // 3. 玩法（空池）
        POOLS.put("gameplay_pool", new DimensionPool(
                "gameplay_pool", "玩法",
                GameType.ADVENTURE, false, false
        ));

        // 4. 创造（空池）
        POOLS.put("creation_pool", new DimensionPool(
                "creation_pool", "创造",
                GameType.CREATIVE, false, false
        ));

        // 5. 建筑（空池）
        POOLS.put("building_pool", new DimensionPool(
                "building_pool", "建筑",
                GameType.CREATIVE, false, false
        ));

        // 6. 指令区（空池）
        POOLS.put("commands_pool", new DimensionPool(
                "commands_pool", "指令区",
                GameType.ADVENTURE, false, false
        ));

        // 7. 教程世界（空池）
        POOLS.put("tutorial_world_pool", new DimensionPool(
                "tutorial_world_pool", "教程世界",
                GameType.ADVENTURE, false, false
        ));

        // 建立维度->池映射
        for (DimensionPool pool : POOLS.values()) {
            for (String dim : pool.dimensions()) {
                DIMENSION_TO_POOL.put(dim, pool.id());
            }
        }
    }

    // ===== 管理操作（供指令使用） =====

    /** 获取维度池的显示文本（用于 /yzwc world_pool list） */
    public static String formatPoolInfo(DimensionPool pool) {
        StringBuilder sb = new StringBuilder();
        sb.append("  §6").append(pool.id()).append("§r");
        sb.append(" §7(").append(pool.displayName()).append(")§r");
        sb.append(" §e").append(pool.gameMode().getName()).append("§r");
        if (pool.dimensions().isEmpty()) {
            sb.append(" §c[无维度]§r");
        } else {
            sb.append(" §a[").append(String.join(", ", pool.dimensions())).append("]§r");
        }
        if (pool.progressAdvancements()) sb.append(" §2[进度开]§r");
        else sb.append(" §8[进度关]§r");
        if (pool.incrementStatistics()) sb.append(" §2[统计开]§r");
        else sb.append(" §8[统计关]§r");
        return sb.toString();
    }
}
