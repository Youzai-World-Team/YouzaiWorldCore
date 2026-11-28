package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
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

    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("DimensionPoolSettings");

    private static final String KEY_POOLS = "pools";
    static final String SURVIVAL_POOL_ID = "survival_world_pool";
    static final String UNASSIGNED_POOL_ID = "_unassigned";

    /** 隐藏池：登录大厅池 ID（内部使用，不写入配置文件） */
    private static final String HIDDEN_LOGIN_HALL_POOL_ID = "_hide_login_hall";

    /** 隐藏池：登录大厅池包含的维度（本项目注册的账号隔离维度） */
    private static final String LOGIN_HALL_DIMENSION = "youzaiworldcore:login_hall";

    public static String statePoolId(ResourceKey<Level> dimension) {
        return DIMENSION_TO_POOL.getOrDefault(dimension.identifier().toString(), UNASSIGNED_POOL_ID);
    }

    public static Optional<GlobalPos> deathLocationInPool(Optional<GlobalPos> location, ResourceKey<Level> dimension) {
        String pool = statePoolId(dimension);
        return location.filter(pos -> statePoolId(pos.dimension()).equals(pool));
    }

    /** 隐藏池 ID 集合：这些池仅存在于内存（内部使用），不写入配置文件、不出现在命令列表/补全中 */
    private static final Set<String> HIDDEN_POOL_IDS = Set.of(HIDDEN_LOGIN_HALL_POOL_ID);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(GameType.class, new GameTypeSerializer())
            .create();

    private static final Map<String, DimensionPool> POOLS = new LinkedHashMap<>();

    private static final Map<String, String> DIMENSION_TO_POOL = new HashMap<>();

    private DimensionPoolSettings() {}

    // ===== 公共 API =====

    /**
     * 获取所有可见（非隐藏）维度池。
     * <p>隐藏池（内部使用）不会出现在结果中，见 {@link #isHiddenPool(String)}。</p>
     */
    public static Collection<DimensionPool> getAllPools() {
        DebugLogger.SERVER.entering("DimPoolSettings", "getAllPools");
        Collection<DimensionPool> result = POOLS.values().stream()
                .filter(pool -> !isHiddenPool(pool.id()))
                .toList();
        DebugLogger.SERVER.exiting("DimPoolSettings", "getAllPools", "size=" + result.size());
        return result;
    }

    public static DimensionPool getPool(String poolId) {
        DebugLogger.SERVER.entering("DimPoolSettings", "getPool", "poolId=" + poolId);
        DimensionPool result = POOLS.get(poolId);
        DebugLogger.SERVER.exiting("DimPoolSettings", "getPool", result != null ? "found" : "null");
        return result;
    }

    public static Optional<DimensionPool> getPoolByDimension(String dimensionId) {
        DebugLogger.SERVER.entering("DimPoolSettings", "getPoolByDimension", "dimensionId=" + dimensionId);
        String poolId = DIMENSION_TO_POOL.get(dimensionId);
        if (poolId == null) {
            DebugLogger.SERVER.exiting("DimPoolSettings", "getPoolByDimension", "not found");
            return Optional.empty();
        }
        Optional<DimensionPool> result = Optional.ofNullable(POOLS.get(poolId));
        DebugLogger.SERVER.exiting("DimPoolSettings", "getPoolByDimension", result.isPresent() ? "found" : "null");
        return result;
    }

    public static boolean dimensionsInSamePool(String dimA, String dimB) {
        DebugLogger.SERVER.entering("DimPoolSettings", "dimensionsInSamePool", "dimA=" + dimA + ", dimB=" + dimB);
        if (dimA.equals(dimB)) {
            DebugLogger.SERVER.exiting("DimPoolSettings", "dimensionsInSamePool", "true (same dimension)");
            return true;
        }
        String poolA = DIMENSION_TO_POOL.get(dimA);
        String poolB = DIMENSION_TO_POOL.get(dimB);
        boolean result = poolA != null && poolA.equals(poolB);
        DebugLogger.SERVER.exiting("DimPoolSettings", "dimensionsInSamePool", String.valueOf(result));
        return result;
    }

    public static boolean isDimensionInAnyPool(String dimensionId) {
        DebugLogger.SERVER.entering("DimPoolSettings", "isDimensionInAnyPool", "dimensionId=" + dimensionId);
        boolean result = DIMENSION_TO_POOL.containsKey(dimensionId);
        DebugLogger.SERVER.exiting("DimPoolSettings", "isDimensionInAnyPool", String.valueOf(result));
        return result;
    }

    // ===== 加载/保存 =====

    /** 启动时加载本次服务器运行期间使用的维度池。 */
    public static void load() {
        DebugLogger.SERVER.entering("DimPoolSettings", "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.DIMENSIONAL_INVENTORIES_MODULE);
        JsonArray poolArray = section.getArray(KEY_POOLS);
        if (poolArray == null) {
            DebugLogger.SERVER.branch("DimPoolSettings", "pools 已配置", false, "分节缺失，创建默认配置");
            LOGGER.info("维度池配置不存在，正在创建默认配置...");
            poolArray = defaultPoolArray();
            section.set(KEY_POOLS, poolArray);
            GlobalSettings.save();
        }
        DebugLogger.SERVER.branch("DimPoolSettings", "pools 已配置", true, "共 " + poolArray.size() + " 项");

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

        Map<String, DimensionPool> loadedPools = new LinkedHashMap<>();
        Map<String, String> loadedDimensions = new HashMap<>();
        Map<String, String> storageNames = new HashMap<>();
        storageNames.put(storageKey(HIDDEN_LOGIN_HALL_POOL_ID), HIDDEN_LOGIN_HALL_POOL_ID);
        storageNames.put(storageKey(UNASSIGNED_POOL_ID), UNASSIGNED_POOL_ID);
        storageNames.put(storageKey(DimensionPoolManager.RECOVERY_DIRECTORY), DimensionPoolManager.RECOVERY_DIRECTORY);
        for (int i = 0; i < poolList.size(); i++) {
            DimensionPool pool = poolList.get(i);
            if (pool == null) {
                section.fail(KEY_POOLS + "[" + i + "]", "维度池条目不能为 null");
                continue; // 不可达，fail 一定抛异常
            }
            if (pool.id() == null || !pool.id().matches("[a-zA-Z0-9_-][a-zA-Z0-9_.:-]*")
                    || pool.id().endsWith(".") || storageKey(pool.id()).isBlank()) {
                section.fail(KEY_POOLS + "[" + i + "].id", "维度池 ID 不是有效的存档目录名");
            }
            if (pool.gameMode() == null) {
                section.fail(KEY_POOLS + "[" + i + "].gameMode",
                        "维度池必须指定合法的游戏模式（survival / creative / adventure / spectator）");
            }
            if (loadedPools.containsKey(pool.id())) {
                section.fail(KEY_POOLS + "[" + i + "].id", "维度池 id 重复：" + pool.id());
            }
            String previousStorage = storageNames.putIfAbsent(storageKey(pool.id()), pool.id());
            if (previousStorage != null) {
                section.fail(KEY_POOLS + "[" + i + "].id",
                        "维度池 " + pool.id() + " 与 " + previousStorage + " 共用存档目录");
            }
            loadedPools.put(pool.id(), pool);
            DebugLogger.SERVER.trace("DimPoolSettings", "加载维度池: id={}, dims={}", pool.id(), pool.dimensions());
            for (String dim : pool.dimensions()) {
                String previous = loadedDimensions.put(dim, pool.id());
                if (previous != null && !previous.equals(pool.id())) {
                    section.fail(KEY_POOLS + "[" + i + "].dimensions",
                            "维度 " + dim + " 同时属于维度池 " + previous + " 与 " + pool.id()
                                    + "，一个维度只能属于一个池");
                }
            }
        }

        POOLS.clear();
        POOLS.putAll(loadedPools);
        DIMENSION_TO_POOL.clear();
        DIMENSION_TO_POOL.putAll(loadedDimensions);
        // 注册隐藏池（内部使用，不写入配置文件），必须在配置池加载完成后执行
        registerHiddenPools();


        DebugLogger.SERVER.info("DimPoolSettings", "已加载 {} 个维度池（含隐藏池 {} 个）", POOLS.size(), HIDDEN_POOL_IDS.size());
        DebugLogger.SERVER.exiting("DimPoolSettings", "load");
    }

    /** 写出默认配置。运行中的池归属由启动时加载的配置决定。 */
    public static void writeDefaults() {
        GlobalSettings.section(GlobalSettings.DIMENSIONAL_INVENTORIES_MODULE).set(KEY_POOLS, defaultPoolArray());
        GlobalSettings.save();
    }

    // ===== 默认配置 =====

    /**
     * 创建默认的 7 个维度池。
     * "生存世界"包含原版三维度，其余池为空（等待管理员配置维度）。
     */
    private static JsonArray defaultPoolArray() {
        DimensionPool survival = new DimensionPool(
                SURVIVAL_POOL_ID,
                "生存世界",
                GameType.SURVIVAL,
                true,
                true
        );
        survival.addDimension("minecraft:overworld");
        survival.addDimension("minecraft:the_nether");
        survival.addDimension("minecraft:the_end");
        return GSON.toJsonTree(List.of(
                survival,
                new DimensionPool("main_city_pool", "主城", GameType.ADVENTURE, false, false),
                new DimensionPool("gameplay_pool", "玩法", GameType.ADVENTURE, false, false),
                new DimensionPool("creation_pool", "创造", GameType.CREATIVE, false, false),
                new DimensionPool("building_pool", "建筑", GameType.CREATIVE, false, false),
                new DimensionPool("commands_pool", "指令区", GameType.ADVENTURE, false, false),
                new DimensionPool("tutorial_world_pool", "教程世界", GameType.ADVENTURE, false, false)
        )).getAsJsonArray();
    }

    // ===== 隐藏池（内部使用，不写入配置文件） =====

    /**
     * 注册隐藏维度池（仅存在于内存，不写入配置文件）。
     * <p>
     * 当前隐藏池 {@code _hide_login_hall} 仅包含 {@code youzaiworldcore:login_hall}，
     * 用于把未认证玩家的登录大厅纳入维度池体系：
     * <ul>
     *   <li>玩家进出登录大厅自动触发跨池状态保存/加载（背包、经验等与其余世界隔离）</li>
     *   <li>登录大厅强制冒险模式，禁止推进度与统计</li>
     *   <li>若管理员在配置文件中手动把 {@code youzaiworldcore:login_hall} 配给了某个可见池，
     *       以隐藏池为准（覆盖归属并告警）</li>
     * </ul>
     * 在 {@link #load()} 应用配置后注册。
     */
    private static void registerHiddenPools() {
        DebugLogger.SERVER.entering("DimPoolSettings", "registerHiddenPools");
        DimensionPool loginHallPool = new DimensionPool(
                HIDDEN_LOGIN_HALL_POOL_ID,
                "Login Hall",
                GameType.ADVENTURE,
                false,
                false
        );
        loginHallPool.addDimension(LOGIN_HALL_DIMENSION);
        loginHallPool.setDefaultSpawn(new DimensionPool.DefaultSpawn(
                LOGIN_HALL_DIMENSION, 0.0, 0.0, 0.0, 90.0f, 0.0f));

        // 若配置文件中恰好存在同名池，覆盖并清理其残留维度归属（避免残留维度错误指向隐藏池）
        DimensionPool previousPool = POOLS.put(loginHallPool.id(), loginHallPool);
        if (previousPool != null) {

            DebugLogger.SERVER.warn("DimPoolSettings", "隐藏池 {} 覆盖了配置中的同名可见池，该配置将被忽略且不会写回文件", loginHallPool.id());
            DIMENSION_TO_POOL.entrySet().removeIf(e -> loginHallPool.id().equals(e.getValue()));
        }

        // 隐藏池维度归属（若被其他可见池占用，隐藏池优先级更高）
        String previous = DIMENSION_TO_POOL.put(LOGIN_HALL_DIMENSION, loginHallPool.id());
        if (previous != null && !previous.equals(loginHallPool.id())) {

            DebugLogger.SERVER.warn("DimPoolSettings", "维度 {} 原本属于可见池 {}，隐藏池 {} 优先级更高，已覆盖该归属", LOGIN_HALL_DIMENSION, previous, loginHallPool.id());
        }
        DebugLogger.SERVER.debug("DimPoolSettings", "已注册隐藏池 {}（维度 {}，不写入配置文件）", HIDDEN_LOGIN_HALL_POOL_ID, LOGIN_HALL_DIMENSION);
        DebugLogger.SERVER.exiting("DimPoolSettings", "registerHiddenPools");
    }

    /** 判断指定池是否为隐藏池（内部使用：不写入配置文件、不出现在命令列表/补全中） */
    public static boolean isHiddenPool(String poolId) {
        boolean result = HIDDEN_POOL_IDS.contains(poolId);
        DebugLogger.SERVER.trace("DimPoolSettings", "isHiddenPool: poolId={}, result={}", poolId, result);
        return result;
    }

    /** 保持已部署目录名，加载时检查同名及跨平台大小写冲突。 */
    static String storageKey(String poolId) {
        return storageDirectory(poolId).toLowerCase(Locale.ROOT);
    }

    static String storageDirectory(String poolId) {
        return poolId.replace(':', '_');
    }

    // ===== 管理操作（供指令使用） =====

    /** 获取维度池的显示文本（用于 /yzwc world_pool list） */
    public static String formatPoolInfo(DimensionPool pool) {
        DebugLogger.SERVER.entering("DimPoolSettings", "formatPoolInfo", "poolId=" + pool.id());
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
        DebugLogger.SERVER.exiting("DimPoolSettings", "formatPoolInfo", result);
        return result;
    }
}
