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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 双开门（Double Doors）玩家状态管理。
 * <p>
 * 功能已精简为：仅「同材质木门 / 栅栏门」的点击双开，按玩家独立开关。
 * 每个玩家的启用状态由指令 {@code /yzwc function double_doors [true|false]}
 * 控制（缺省为查询自身状态）。
 * </p>
 * <p>
 * 数据持久化于 {@code config/youzaiworldcore/double_doors_players.json}，
 * 仅保存被指令显式设置过的玩家；未设置的玩家使用 {@link #DEFAULT_ENABLED} 默认值。
 * </p>
 */
@SuppressWarnings({"null", "unused"})
public final class DoubleDoorsState {

    public static final String MODULE = "DoubleDoorsState";

    /** 默认状态：新玩家默认开启双开门（与原全局默认一致） */
    private static final boolean DEFAULT_ENABLED = true;

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/DoubleDoorsState");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("youzaiworldcore").resolve("double_doors_players.json");

    /** 玩家 UUID -> 是否启用双开门（仅保存被指令显式设置过的玩家） */
    private static final Map<String, Boolean> playerEnabled = new HashMap<>();

    private DoubleDoorsState() {
    }

    /**
     * 判断某玩家是否启用双开门。
     * 未显式设置过的玩家返回 {@link #DEFAULT_ENABLED}。
     */
    public static boolean isEnabled(UUID playerUuid) {
        if (playerUuid == null) {
            return DEFAULT_ENABLED;
        }
        Boolean v = playerEnabled.get(playerUuid.toString());
        return v == null ? DEFAULT_ENABLED : v;
    }

    /**
     * 判断某玩家是否曾被指令显式设置过。
     * 用于查询时区分「默认启用」与「显式启用」。
     */
    public static boolean isExplicitlySet(UUID playerUuid) {
        return playerUuid != null && playerEnabled.containsKey(playerUuid.toString());
    }

    /** 设置某玩家的双开门启用状态并立即持久化 */
    public static void setEnabled(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) {
            return;
        }
        playerEnabled.put(playerUuid.toString(), enabled);
        save();
        DebugLogger.info(MODULE, "玩家 %s 双开门状态已设置为 %s", playerUuid, enabled);
    }

    /** 从文件加载玩家状态（不存在则使用默认：全部启用） */
    public static void load() {
        DebugLogger.entering(MODULE, "load");

        if (!Files.exists(DATA_FILE)) {
            DebugLogger.info(MODULE, "玩家状态文件不存在，使用默认（全部启用）");
            DebugLogger.exiting(MODULE, "load", "no file");
            return;
        }

        try {
            String json = Files.readString(DATA_FILE);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                DebugLogger.warn(MODULE, "玩家状态文件为空，使用默认");
                return;
            }
            if (root.has("players") && root.get("players").isJsonObject()) {
                JsonObject players = root.getAsJsonObject("players");
                for (Map.Entry<String, com.google.gson.JsonElement> entry : players.entrySet()) {
                    String uuid = entry.getKey();
                    if (entry.getValue().isJsonPrimitive()
                            && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                        playerEnabled.put(uuid, entry.getValue().getAsBoolean());
                    }
                }
            }
            DebugLogger.info(MODULE, "已加载 %d 名玩家的双开门状态", playerEnabled.size());
        } catch (Exception e) {
            LOGGER.error("加载双开门玩家状态失败: {}", e.getMessage());
        }

        DebugLogger.exiting(MODULE, "load");
    }

    /** 保存玩家状态到文件 */
    public static void save() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            JsonObject root = new JsonObject();
            JsonObject players = new JsonObject();
            for (Map.Entry<String, Boolean> entry : playerEnabled.entrySet()) {
                players.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("players", players);
            Files.writeString(DATA_FILE, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("保存双开门玩家状态失败: {}", e.getMessage());
        }
    }
}
