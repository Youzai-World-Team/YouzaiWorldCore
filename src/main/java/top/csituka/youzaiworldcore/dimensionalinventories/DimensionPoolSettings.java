package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.lang.reflect.Type;
import java.util.*;

/**
 * 维度池配置管理器。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code dimensional_inventories_module} 分节，池列表写在其 {@code pools} 键下。
 * <p>
 * 管理 7 个预定义维度池的 JSON 持久化配置。玩家在各池中的背包 / 状态数据是
 * 随存档走的，落在 {@code <world_name>/data/yzwc/data/dimensional_inventories_module/}，
 * 见 {@link DimensionPoolManager}。
 */
public final class DimensionPoolSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/DimensionPoolSettings");

    /** 池列表在分节里的键名 */
    private static final String KEY_POOLS = "pools";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(GameType.class, new GameTypeSerializer())
            .create();

    /** 所有注册的维度池，按 ID 索引 */
    private static final Map<String, DimensionPool> POOLS = new LinkedHashMap<>();

    /** 维度到池的快速查找映射 */
    private static final Map<String, String> DIMENSION_TO_POOL = new HashMap<>();

    private DimensionPoolSettings() {}

    // ===== 公共 API =====

    /** 获取所有维度池 */
    public static Collection<DimensionPool> getAllPools() {
        DebugLogger.entering("DimPoolSettings", "getAllPools");
        Collection<DimensionPool> result = Collections.unmodifiableCollection(POOLS.values());
        DebugLogger.exiting("DimPoolSettings", "getAllPools", "size=" + result.size());
        return result;
    }

    /** 按 ID 获取维度池 */
    public static DimensionPool getPool(String poolId) {
        DebugLogger.entering("DimPoolSettings", "getPool", "poolId=" + poolId);
        DimensionPool result = POOLS.get(poolId);
        DebugLogger.exiting("DimPoolSettings", "getPool", result != null ? "found" : "null");
        return result;
    }

    /** 获取包含指定维度的维度池 */
    public static Optional<DimensionPool> getPoolByDimension(String dimensionId) {
        DebugLogger.entering("DimPoolSettings", "getPoolByDimension", "dimensionId=" + dimensionId);
        String poolId = DIMENSION_TO_POOL.get(dimensionId);
        if (poolId == null) {
            DebugLogger.exiting("DimPoolSettings", "getPoolByDimension", "not found");
            return Optional.empty();
        }
        Optional<DimensionPool> result = Optional.ofNullable(POOLS.get(poolId));
        DebugLogger.exiting("DimPoolSettings", "getPoolByDimension", result.isPresent() ? "found" : "null");
        return result;
    }

    /** 判断两个维度是否在同一个池中 */
    public static boolean dimensionsInSamePool(String dimA, String dimB) {
        DebugLogger.entering("DimPoolSettings", "dimensionsInSamePool", "dimA=" + dimA + ", dimB=" + dimB);
        if (dimA.equals(dimB)) {
            DebugLogger.exiting("DimPoolSettings", "dimensionsInSamePool", "true (same dimension)");
            return true;
        }
        String poolA = DIMENSION_TO_POOL.get(dimA);
        String poolB = DIMENSION_TO_POOL.get(dimB);
        boolean result = poolA != null && poolA.equals(poolB);
        DebugLogger.exiting("DimPoolSettings", "dimensionsInSamePool", String.valueOf(result));
        return result;
    }

    /** 检查维度是否属于任何池 */
    public static boolean isDimensionInAnyPool(String dimensionId) {
        DebugLogger.entering("DimPoolSettings", "isDimensionInAnyPool", "dimensionId=" + dimensionId);
        boolean result = DIMENSION_TO_POOL.containsKey(dimensionId);
        DebugLogger.exiting("DimPoolSettings", "isDimensionInAnyPool", String.valueOf(result));
        return result;
    }

    // ===== 加载/保存 =====

    /** 从全局配置加载，分节缺失则创建默认配置 */
    public static void load() {
        DebugLogger.entering("DimPoolSettings", "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.DIMENSIONAL_INVENTORIES_MODULE);
        JsonArray poolArray = section.getArray(KEY_POOLS);
        if (poolArray == null) {
            DebugLogger.branch("DimPoolSettings", "pools 已配置", false, "分节缺失，创建默认配置");
            LOGGER.info("维度池配置不存在，正在创建默认配置...");
            createDefaultConfig();
            save();
            DebugLogger.exiting("DimPoolSettings", "load", "default config created");
            return;
        }
        DebugLogger.branch("DimPoolSettings", "pools 已配置", true, "共 " + poolArray.size() + " 项");

        Type listType = new TypeToken<List<DimensionPool>>() {}.getType();
        List<DimensionPool> poolList;
        try {
            poolList = GSON.fromJson(poolArray, listType);
        } catch (RuntimeException e) {
            section.fail(KEY_POOLS, "维度池列表解析失败：" + e.getMessage());
            return; // 不可达
        }
        if (poolList == null) {
            section.fail(KEY_POOLS, "维度池列表解析结果为空");
            return; // 不可达
        }

        POOLS.clear();
        DIMENSION_TO_POOL.clear();

        for (int i = 0; i < poolList.size(); i++) {
            DimensionPool pool = poolList.get(i);
            if (pool == null) {
                section.fail(KEY_POOLS + "[" + i + "]", "维度池条目不能为 null");
                continue; // 不可达，fail 一定抛异常
            }
            if (pool.id() == null || pool.id().isBlank()) {
                section.fail(KEY_POOLS + "[" + i + "].id", "维度池必须有非空的 id");
            }
            if (pool.gameMode() == null) {
                section.fail(KEY_POOLS + "[" + i + "].gameMode",
                        "维度池必须指定合法的游戏模式（survival / creative / adventure / spectator）");
            }
            if (POOLS.containsKey(pool.id())) {
                section.fail(KEY_POOLS + "[" + i + "].id", "维度池 id 重复：" + pool.id());
            }
            POOLS.put(pool.id(), pool);
            DebugLogger.trace("DimPoolSettings", "加载维度池: id=%s, dims=%s", pool.id(), pool.dimensions());
            for (String dim : pool.dimensions()) {
                String previous = DIMENSION_TO_POOL.put(dim, pool.id());
                if (previous != null && !previous.equals(pool.id())) {
                    section.fail(KEY_POOLS + "[" + i + "].dimensions",
                            "维度 " + dim + " 同时属于维度池 " + previous + " 与 " + pool.id()
                                    + "，一个维度只能属于一个池");
                }
            }
        }

        LOGGER.info("已加载 {} 个维度池", POOLS.size());
        DebugLogger.info("DimPoolSettings", "已加载 %d 个维度池", POOLS.size());
        DebugLogger.exiting("DimPoolSettings", "load");
    }

    /** 重建 7 个默认维度池并写入 {@code dimensional_inventories_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        createDefaultConfig();
        save();
    }

    /** 保存配置到全局配置文件的 {@code dimensional_inventories_module} 分节 */
    public static void save() {
        DebugLogger.entering("DimPoolSettings", "save");
        ConfigSection section = GlobalSettings.section(GlobalSettings.DIMENSIONAL_INVENTORIES_MODULE);
        List<DimensionPool> poolList = new ArrayList<>(POOLS.values());
        section.set(KEY_POOLS, GSON.toJsonTree(poolList));
        GlobalSettings.save();
        LOGGER.info("维度池配置已保存");
        DebugLogger.info("DimPoolSettings", "维度池配置已保存到 %s, 共 %d 个池",
                GlobalSettings.file(), POOLS.size());
        DebugLogger.exiting("DimPoolSettings", "save");
    }

    /** 刷新配置（从文件重载） */
    public static void reload() {
        DebugLogger.entering("DimPoolSettings", "reload");
        load();
        DebugLogger.exiting("DimPoolSettings", "reload");
    }

    // ===== 默认配置 =====

    /**
     * 创建默认的 7 个维度池。
     * "生存世界"包含原版三维度，其余池为空（等待管理员配置维度）。
     */
    private static void createDefaultConfig() {
        DebugLogger.entering("DimPoolSettings", "createDefaultConfig");
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
        DebugLogger.info("DimPoolSettings", "创建维度池: survival_world_pool (生存世界) 含3维度");

        // 2. 主城（空池）
        POOLS.put("main_city_pool", new DimensionPool(
                "main_city_pool", "主城",
                GameType.ADVENTURE, false, false
        ));
        DebugLogger.info("DimPoolSettings", "创建维度池: main_city_pool (主城) 空池");

        // 3. 玩法（空池）
        POOLS.put("gameplay_pool", new DimensionPool(
                "gameplay_pool", "玩法",
                GameType.ADVENTURE, false, false
        ));
        DebugLogger.info("DimPoolSettings", "创建维度池: gameplay_pool (玩法) 空池");

        // 4. 创造（空池）
        POOLS.put("creation_pool", new DimensionPool(
                "creation_pool", "创造",
                GameType.CREATIVE, false, false
        ));
        DebugLogger.info("DimPoolSettings", "创建维度池: creation_pool (创造) 空池");

        // 5. 建筑（空池）
        POOLS.put("building_pool", new DimensionPool(
                "building_pool", "建筑",
                GameType.CREATIVE, false, false
        ));
        DebugLogger.info("DimPoolSettings", "创建维度池: building_pool (建筑) 空池");

        // 6. 指令区（空池）
        POOLS.put("commands_pool", new DimensionPool(
                "commands_pool", "指令区",
                GameType.ADVENTURE, false, false
        ));
        DebugLogger.info("DimPoolSettings", "创建维度池: commands_pool (指令区) 空池");

        // 7. 教程世界（空池）
        POOLS.put("tutorial_world_pool", new DimensionPool(
                "tutorial_world_pool", "教程世界",
                GameType.ADVENTURE, false, false
        ));
        DebugLogger.info("DimPoolSettings", "创建维度池: tutorial_world_pool (教程世界) 空池");

        // 建立维度->池映射
        for (DimensionPool pool : POOLS.values()) {
            for (String dim : pool.dimensions()) {
                DIMENSION_TO_POOL.put(dim, pool.id());
            }
        }
        DebugLogger.exiting("DimPoolSettings", "createDefaultConfig", "共创建 " + POOLS.size() + " 个维度池");
    }

    // ===== 管理操作（供指令使用） =====

    /** 获取维度池的显示文本（用于 /yzwc world_pool list） */
    public static String formatPoolInfo(DimensionPool pool) {
        DebugLogger.entering("DimPoolSettings", "formatPoolInfo", "poolId=" + pool.id());
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
        String result = sb.toString();
        DebugLogger.exiting("DimPoolSettings", "formatPoolInfo", result);
        return result;
    }
}
