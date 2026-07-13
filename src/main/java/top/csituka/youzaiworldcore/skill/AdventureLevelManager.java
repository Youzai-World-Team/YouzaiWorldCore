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

    public static int expForNextLevel(int level) {
        return 50 + level * 50;
    }

    public static int totalExpForLevel(int level) {
        if (level <= 1) return 0;
        return 50 * (level - 1) * (level + 2) / 2;
    }

    public static int getLevelFromExp(int totalExp) {
        int level = 1;
        while (totalExpForLevel(level + 1) <= totalExp) { level++; }
        return level;
    }

    public static int getCurrentLevelExp(int totalExp) {
        int level = getLevelFromExp(totalExp);
        return totalExp - totalExpForLevel(level);
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
