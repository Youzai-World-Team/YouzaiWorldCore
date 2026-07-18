package top.csituka.youzaiworldcore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 双开门（Double Doors）功能配置。
 * <p>
 * 文件位置：{@code config/youzaiworldcore/double_doors_settings.json}
 * <p>
 * 功能移植自 Serilum 的 Double Doors（已取得作者许可，无需署名）。
 * 允许双击门／活板门／栅栏门同时打开相邻的同类方块，
 * 同时支持红石信号连锁触发。
 * </p>
 */
@SuppressWarnings({"null", "unused"})
public final class DoubleDoorsConfig {

    public static final String MODULE = "DoubleDoorsConfig";

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/DoubleDoorsConfig");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("double_doors_settings.json");

    // ===== 配置字段 =====

    /** 是否启用连锁（递归）开门功能。连锁开后，已连接的同类型方块将一并开启 */
    private static boolean enableRecursiveOpening = true;

    /** 连锁搜索的最大方块距离（曼哈顿距离）。取值范围 [1, 64] */
    private static int recursiveOpeningMaxBlocksDistance = 10;

    /** 是否对门（DoorBlock）启用双开功能 */
    private static boolean enableDoors = true;

    /** 是否对栅栏门（FenceGateBlock）启用双开功能（默认关闭） */
    private static boolean enableFenceGates = false;

    /** 是否对活板门（TrapDoorBlock）启用双开功能（默认关闭） */
    private static boolean enableTrapdoors = false;

    /** 是否启用模组兼容性检查。检查到其他具有双开门功能的模组（如 Quark）时，
     * 自动禁用其双开门配置以避免冲突（本版本暂未实现检查逻辑） */
    private static boolean enableModIncompatibilityCheck = false;

    private DoubleDoorsConfig() {
    }

    // ===== Getter =====

    /** @return 是否启用连锁开门 */
    public static boolean isEnableRecursiveOpening() {
        return enableRecursiveOpening;
    }

    /** @return 连锁搜索的最大方块距离 */
    public static int getRecursiveOpeningMaxBlocksDistance() {
        return recursiveOpeningMaxBlocksDistance;
    }

    /** @return 是否对门启用双开 */
    public static boolean isEnableDoors() {
        return enableDoors;
    }

    /** @return 是否对栅栏门启用双开 */
    public static boolean isEnableFenceGates() {
        return enableFenceGates;
    }

    /** @return 是否对活板门启用双开 */
    public static boolean isEnableTrapdoors() {
        return enableTrapdoors;
    }

    /** @return 是否启用模组兼容性检查 */
    public static boolean isEnableModIncompatibilityCheck() {
        return enableModIncompatibilityCheck;
    }

    // ===== 持久化 =====

    /** 从文件加载配置（不存在则写入默认配置），值钳制到合法范围 */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        if (!Files.exists(CONFIG_FILE)) {
            DebugLogger.info(MODULE, "配置文件不存在，写入默认配置");
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }

        try {
            String json = Files.readString(CONFIG_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                DebugLogger.warn(MODULE, "配置文件为空，使用默认配置");
                return;
            }

            if (root.has("enableRecursiveOpening") && !root.get("enableRecursiveOpening").isJsonNull()) {
                enableRecursiveOpening = root.get("enableRecursiveOpening").getAsBoolean();
            }
            if (root.has("recursiveOpeningMaxBlocksDistance") && !root.get("recursiveOpeningMaxBlocksDistance").isJsonNull()) {
                recursiveOpeningMaxBlocksDistance = root.get("recursiveOpeningMaxBlocksDistance").getAsInt();
                // 钳制到 [1, 64]
                if (recursiveOpeningMaxBlocksDistance < 1) {
                    DebugLogger.info(MODULE, "recursiveOpeningMaxBlocksDistance=%d 低于最小值 1，钳制为 1",
                            recursiveOpeningMaxBlocksDistance);
                    recursiveOpeningMaxBlocksDistance = 1;
                } else if (recursiveOpeningMaxBlocksDistance > 64) {
                    DebugLogger.info(MODULE, "recursiveOpeningMaxBlocksDistance=%d 高于最大值 64，钳制为 64",
                            recursiveOpeningMaxBlocksDistance);
                    recursiveOpeningMaxBlocksDistance = 64;
                }
            }
            if (root.has("enableDoors") && !root.get("enableDoors").isJsonNull()) {
                enableDoors = root.get("enableDoors").getAsBoolean();
            }
            if (root.has("enableFenceGates") && !root.get("enableFenceGates").isJsonNull()) {
                enableFenceGates = root.get("enableFenceGates").getAsBoolean();
            }
            if (root.has("enableTrapdoors") && !root.get("enableTrapdoors").isJsonNull()) {
                enableTrapdoors = root.get("enableTrapdoors").getAsBoolean();
            }
            if (root.has("enableModIncompatibilityCheck") && !root.get("enableModIncompatibilityCheck").isJsonNull()) {
                enableModIncompatibilityCheck = root.get("enableModIncompatibilityCheck").getAsBoolean();
            }

            DebugLogger.info(MODULE, "已加载配置: recursiveOpening=%s, maxDist=%d, doors=%s, fenceGates=%s, trapdoors=%s",
                    enableRecursiveOpening, recursiveOpeningMaxBlocksDistance,
                    enableDoors, enableFenceGates, enableTrapdoors);
        } catch (Exception e) {
            LOGGER.error("加载双开门配置失败: {}", e.getMessage());
        }

        DebugLogger.exiting(MODULE, "load");
    }

    /** 保存当前配置到文件 */
    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enableRecursiveOpening", enableRecursiveOpening);
            root.addProperty("recursiveOpeningMaxBlocksDistance", recursiveOpeningMaxBlocksDistance);
            root.addProperty("enableDoors", enableDoors);
            root.addProperty("enableFenceGates", enableFenceGates);
            root.addProperty("enableTrapdoors", enableTrapdoors);
            root.addProperty("enableModIncompatibilityCheck", enableModIncompatibilityCheck);
            Files.writeString(CONFIG_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存双开门配置失败: {}", e.getMessage());
        }
    }
}
