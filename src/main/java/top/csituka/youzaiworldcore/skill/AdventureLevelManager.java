package top.csituka.youzaiworldcore.skill;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import top.csituka.youzaiworldcore.network.LevelExpSyncPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冒险等级系统核心管理器。
 *
 * <p>负责：</p>
 * <ul>
 *   <li>等级与经验值计算</li>
 *   <li>玩家行为计数器（挖掘/放置）</li>
 *   <li>经验值发放与网络同步</li>
 *   <li>事件注册（方块破坏、玩家加入）</li>
 * </ul>
 *
 * <h3>升级公式</h3>
 * <pre>
 *   升至下一级所需经验 = 50 + 当前等级 × 50
 *   例：
 *     Lv.1 → Lv.2：100 经验
 *     Lv.2 → Lv.3：150 经验
 *     Lv.5 → Lv.6：300 经验
 *     Lv.10 → Lv.11：550 经验
 * </pre>
 */
@SuppressWarnings("null")
public class AdventureLevelManager {

    // ─── 升级公式 ───

    /** 升至下一级所需经验 = 50 + level × 50 */
    public static int expForNextLevel(int level) {
        return 50 + level * 50;
    }

    /** 从 1 级升到指定等级所需的累积总经验 */
    public static int totalExpForLevel(int level) {
        if (level <= 1) return 0;
        // sum of (50 + i*50) for i=1 to level-1
        // = 50*(level-1) + 50*(level-1)*level/2
        // = 50*(level-1)*(level+2)/2
        return 50 * (level - 1) * (level + 2) / 2;
    }

    /** 根据累积总经验计算当前等级 */
    public static int getLevelFromExp(int totalExp) {
        int level = 1;
        while (totalExpForLevel(level + 1) <= totalExp) {
            level++;
        }
        return level;
    }

    /** 获取当前等级已积累的经验（当前等级内的进度） */
    public static int getCurrentLevelExp(int totalExp) {
        int level = getLevelFromExp(totalExp);
        return totalExp - totalExpForLevel(level);
    }

    // ─── 事件经验常量 ───

    /** 每挖掘方块 50 次 */
    public static final int EXP_MINE_BATCH = 25;
    /** 每放置方块 50 次 */
    public static final int EXP_PLACE_BATCH = 25;
    /** 每死亡 1 次 */
    public static final int EXP_DEATH = 10;
    /** 死亡时通过「守护之心」保住物品栏 */
    public static final int EXP_HEART_OF_GUARDIANSHIP = 50;
    /** 不死图腾规避死亡 */
    public static final int EXP_TOTEM_OF_UNDYING = 500;
    /** 完成进度（成就） */
    public static final int EXP_ADVANCEMENT = 50;

    /** 计数器触发阈值 */
    public static final int COUNTER_THRESHOLD = 50;

    // ─── 玩家计数器（非持久化，仅在服务器运行期间有效） ───

    private static final Map<UUID, Integer> mineCounters = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> placeCounters = new ConcurrentHashMap<>();

    // ─── 初始化 ───

    /**
     * 初始化冒险等级系统：加载数据、注册事件。
     */
    public static void initialize() {
        DebugLogger.entering("AdventureLevelManager", "initialize");

        // 加载持久化数据
        PlayerLevelStorage.initialize();

        // 注册方块破坏事件
        registerBlockBreakEvent();

        // 注册玩家加入事件（初始化数据）
        registerJoinEvent();

        DebugLogger.exiting("AdventureLevelManager", "initialize");
    }

    // ─── 事件注册 ───

    private static void registerBlockBreakEvent() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()) {
                incrementMineCounter(serverPlayer);
            }
        });
        DebugLogger.info("AdventureLevelManager", "方块破坏事件已注册");
    }

    private static void registerJoinEvent() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            PlayerLevelData data = PlayerLevelStorage.getOrCreate(player.getUUID(), player.getName().getString());
            DebugLogger.info("AdventureLevelManager",
                    "玩家 %s 加入，等级 Lv.%d，累积经验 %d",
                    player.getName().getString(), data.getLevel(), data.totalExp);
        });
        DebugLogger.info("AdventureLevelManager", "玩家加入事件已注册");
    }

    // ─── 计数器逻辑 ───

    /** 增加挖掘计数器，达到阈值时发放经验。 */
    public static void incrementMineCounter(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int count = mineCounters.merge(uuid, 1, Integer::sum);
        if (count >= COUNTER_THRESHOLD) {
            mineCounters.put(uuid, count - COUNTER_THRESHOLD);
            grantExp(player, EXP_MINE_BATCH);
        }
    }

    /** 增加放置计数器，达到阈值时发放经验。 */
    public static void incrementPlaceCounter(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int count = placeCounters.merge(uuid, 1, Integer::sum);
        if (count >= COUNTER_THRESHOLD) {
            placeCounters.put(uuid, count - COUNTER_THRESHOLD);
            grantExp(player, EXP_PLACE_BATCH);
        }
    }

    // ─── 经验发放 ───

    /**
     * 向玩家发放冒险经验。
     *
     * @param player 目标玩家
     * @param amount 经验值
     */
    public static void grantExp(ServerPlayer player, int amount) {
        if (amount <= 0) return;

        UUID uuid = player.getUUID();
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(uuid, player.getName().getString());

        int oldLevel = data.getLevel();
        data.addExp(amount);
        int newLevel = data.getLevel();

        // 持久化保存
        PlayerLevelStorage.markDirty(uuid);

        // 同步到客户端显示 HUD
        ServerPlayNetworking.send(player, new LevelExpSyncPayload(
                newLevel,
                data.getCurrentLevelExp(),
                data.getExpForNextLevel(),
                amount,
                oldLevel != newLevel
        ));

        DebugLogger.info("AdventureLevelManager",
                "玩家 %s 获得 %d 冒险经验 (总经验 %d, Lv.%d → Lv.%d)",
                player.getName().getString(), amount, data.totalExp, oldLevel, newLevel);
    }

    /**
     * 向玩家发放冒险经验（无 ServerPlayer 上下文时使用，如某些 mixin 场景）。
     * 此方法不会发送 HUD 同步包（需要在调用方自行处理）。
     */
    public static void grantExpSilent(UUID uuid, String username, int amount) {
        if (amount <= 0) return;

        PlayerLevelData data = PlayerLevelStorage.getOrCreate(uuid, username);
        data.addExp(amount);
        PlayerLevelStorage.markDirty(uuid);

        DebugLogger.info("AdventureLevelManager",
                "玩家 %s 获得 %d 冒险经验（静默）", username, amount);
    }
}
