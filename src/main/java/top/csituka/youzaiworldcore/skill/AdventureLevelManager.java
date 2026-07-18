package top.csituka.youzaiworldcore.skill;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.network.LevelExpSyncPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冒险等级系统核心管理器。
 *
 * <h3>事件经验表</h3>
 * | 事件 | 经验 |
 * |---|---|
 * | 每挖掘方块 50 次 | +25 |
 * | 每放置方块 50 次 | +25 |
 * | 每死亡 1 次 | +10 |
 * | 守护之心保住物品栏 | +50 |
 * | 不死图腾规避死亡 | +500 |
 * | 完成进度（成就） | +50 |
 */
@SuppressWarnings("null")
public class AdventureLevelManager {

    // ==================== 升级公式 ====================

    /**
     * 计算升到下一级所需的经验。
     * <p>
     * 公式：C = 200 + 20 × log₁₀(2n)²⁰
     * <ul>
     *   <li>C：升级所需经验数值</li>
     *   <li>n：玩家当前等级（n ≥ 1）</li>
     * </ul>
     * 等级较低时 log₁₀(2n) < 1，C ≈ 220（平坦区）；
     * 等级较高时（n ≥ 50）增长加速。
     */
    public static int expForNextLevel(int level) {
        if (level < 1) level = 1;
        double logVal = Math.log10(2.0 * level);
        double result = 200.0 + 20.0 * Math.pow(logVal, 20.0);
        // clamp 防止溢出
        if (result > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1, (int) Math.round(result));
    }

    /**
     * 升到指定等级所需的总累积经验。
     * level=1 时返回 0（初始等级）。
     */
    public static long totalExpForLevel(int level) {
        if (level <= 1) return 0;
        long total = 0;
        for (int i = 1; i < level; i++) {
            total += expForNextLevel(i);
            if (total > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return total;
    }

    /**
     * 从总经验值反算当前等级。
     */
    public static int getLevelFromExp(int totalExp) {
        if (totalExp <= 0) return 1;
        long accumulated = 0;
        int level = 1;
        while (true) {
            int needed = expForNextLevel(level);
            if ((long) accumulated + needed > totalExp) break;
            accumulated += needed;
            level++;
        }
        return level;
    }

    /**
     * 获取当前等级内已获得的经验值（从等级起点到当前的进度）。
     */
    public static int getCurrentLevelExp(int totalExp) {
        int level = getLevelFromExp(totalExp);
        long base = totalExpForLevel(level);
        return (int) (totalExp - base);
    }

    // ==================== 事件经验常量 ====================

    public static final int EXP_MINE_BATCH            = 25;   // 挖掘 50 方块
    public static final int EXP_PLACE_BATCH           = 25;   // 放置 50 方块
    public static final int EXP_DEATH                 = 10;   // 死亡
    public static final int EXP_HEART_OF_GUARDIANSHIP = 50;   // 守护之心保护
    public static final int EXP_TOTEM_OF_UNDYING      = 500;  // 不死图腾
    public static final int EXP_ADVANCEMENT           = 50;   // 完成进度（成就）

    // ==================== 计数器 ====================

    private static final int COUNTER_THRESHOLD = 50;
    private static final Map<UUID, Integer> mineCounters  = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> placeCounters = new ConcurrentHashMap<>();

    // ==================== 初始化 ====================

    public static void initialize() {
        DebugLogger.entering("AdventureLevelManager", "initialize");
        PlayerLevelStorage.initialize();
        registerBlockBreakEvent();
        DebugLogger.exiting("AdventureLevelManager", "initialize");
    }

    private static void registerBlockBreakEvent() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer sp) || sp.isCreative()) return;
            incrementMineCounter(sp);
        });
    }

    // ==================== 计数器逻辑 ====================

    public static void incrementMineCounter(ServerPlayer player) {
        int count = mineCounters.merge(player.getUUID(), 1, Integer::sum);
        if (count >= COUNTER_THRESHOLD) {
            mineCounters.put(player.getUUID(), count - COUNTER_THRESHOLD);
            grantExp(player, EXP_MINE_BATCH);
        }
    }

    public static void incrementPlaceCounter(ServerPlayer player) {
        int count = placeCounters.merge(player.getUUID(), 1, Integer::sum);
        if (count >= COUNTER_THRESHOLD) {
            placeCounters.put(player.getUUID(), count - COUNTER_THRESHOLD);
            grantExp(player, EXP_PLACE_BATCH);
        }
    }

    // ==================== 经验发放核心 ====================

    public static void grantExp(ServerPlayer player, int amount) {
        if (amount <= 0) return;
        UUID uuid = player.getUUID();
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(uuid, player.getName().getString());

        int oldLevel = data.getLevel();
        data.addExp(amount);
        int newLevel = data.getLevel();
        boolean leveledUp = oldLevel != newLevel;

        // 升级时发放 1 技能点
        if (leveledUp) {
            for (int lvl = oldLevel; lvl < newLevel; lvl++) {
                AttributeManager.grantSkillPoint(uuid, player.getName().getString());
            }
            // 同步属性数据到客户端
            AttributeManager.syncToClient(player);
        }

        PlayerLevelStorage.markDirty(uuid);

        ServerPlayNetworking.send(player, new LevelExpSyncPayload(
                newLevel, data.getCurrentLevelExp(), data.getExpForNextLevel(), amount, leveledUp));

        DebugLogger.info("AdventureLevelManager",
                "玩家 %s 获得 %d 冒险经验 (Lv.%d → Lv.%d)",
                player.getName().getString(), amount, oldLevel, newLevel);
    }

    /** 无 ServerPlayer 上下文时使用（仅持久化，不发送 HUD 同步包） */
    public static void grantExpSilent(UUID uuid, String username, int amount) {
        if (amount <= 0) return;
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(uuid, username);
        data.addExp(amount);
        PlayerLevelStorage.markDirty(uuid);
    }
}
