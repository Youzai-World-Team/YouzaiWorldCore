package top.csituka.youzaiworldcore.skill;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import top.csituka.youzaiworldcore.network.LevelExpSyncPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冒险等级系统核心管理器。
 *
 * <h3>升级公式</h3>
 * 升至下一级所需经验 = 50 + 当前等级 × 50
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

    // ─── 主表（已实现） ───
    public static final int EXP_MINE_BATCH            = 25;   // 挖掘 50 方块
    public static final int EXP_PLACE_BATCH           = 25;   // 放置 50 方块
    public static final int EXP_DEATH                 = 10;   // 死亡
    public static final int EXP_HEART_OF_GUARDIANSHIP = 50;   // 守护之心保护
    public static final int EXP_TOTEM_OF_UNDYING      = 500;  // 不死图腾
    public static final int EXP_ADVANCEMENT           = 50;   // 完成进度（通用）

    // ─── 续表 1 ───
    public static final int EXP_ADVANCEMENT_GOAL      = 100;  // 完成任务（成就分类 goal）
    public static final int EXP_ADVANCEMENT_CHALLENGE = 250;  // 完成挑战
    public static final int EXP_VANILLA_LEVEL_UP      = 5;    // 原版等级升级
    public static final int EXP_PICKUP_XP_ORB         = 1;    // 拾取经验球
    public static final int EXP_FIRST_JOIN            = 2;    // 服务器首次进服
    public static final int EXP_CRAFT_ITEM            = 2;    // 合成物品
    public static final int EXP_ITEM_BREAK            = 20;   // 工具耐久耗尽
    public static final int EXP_STATUS_EFFECT         = 2;    // 获得状态 buff
    public static final int EXP_EAT_FOOD              = 1;    // 消耗食物进食
    public static final int EXP_KILL_NORMAL           = 2;    // 击杀普通生物
    public static final int EXP_KILL_PVP              = 5;    // PVP 击杀
    public static final int EXP_KILL_BOSS             = 500;  // 击杀 BOSS
    public static final int EXP_SLEEP                 = 50;   // 睡觉跳过夜晚
    public static final int EXP_DIMENSION_CHANGE      = 10;   // 完成维度切换
    public static final int EXP_FIRST_NETHER          = 200;  // 首次下界
    public static final int EXP_FIRST_END             = 300;  // 首次末地
    public static final int EXP_PICKUP_BATCH          = 10;   // 拾取物品 50 次
    public static final int EXP_DROP_BATCH            = 10;   // 丢弃物品 50 次
    public static final int EXP_ENCHANT               = 20;   // 附魔操作
    public static final int EXP_ANVIL                 = 10;   // 铁砧操作
    public static final int EXP_SMITHING              = 30;   // 锻造台加工

    // ─── 续表 2 ───
    public static final int EXP_BREWING               = 5;    // 炼制药水
    public static final int EXP_LOOM                  = 20;   // 织布机编辑
    public static final int EXP_GRINDSTONE            = 15;   // 砂轮打磨
    public static final int EXP_CARTOGRAPHY           = 10;   // 制图台加工
    public static final int EXP_WALK_1KM              = 50;   // 步行满 1km
    public static final int EXP_ONLINE_10MIN          = 10;   // 在线 10 分钟
    public static final int EXP_ONLINE_1HOUR          = 30;   // 在线 1 小时
    public static final int EXP_ORE_COPPER            = 40;   // 铜矿 ×20
    public static final int EXP_ORE_DEEPSLATE_COPPER  = 30;   // 深层铜矿 ×20
    public static final int EXP_ORE_IRON              = 50;   // 铁矿 ×20
    public static final int EXP_ORE_DEEPSLATE_IRON    = 60;   // 深层铁矿 ×20
    public static final int EXP_ORE_GOLD              = 70;   // 金矿 ×20
    public static final int EXP_ORE_DEEPSLATE_GOLD    = 100;  // 深层金矿 ×20
    public static final int EXP_ORE_ANCIENT_DEBRIS    = 50;   // 远古残骸 ×1
    public static final int EXP_ORE_NETHER_QUARTZ     = 25;   // 下界石英 ×20
    public static final int EXP_ORE_NETHER_GOLD       = 30;   // 下界金矿 ×20
    public static final int EXP_THROW_ITEM            = 1;    // 投掷物品
    public static final int EXP_BEACON_FIRST          = 1000; // 信标首次激活
    public static final int EXP_FIREWORK_FIRST        = 1000; // 烟花首次燃放
    public static final int EXP_SHOOT_PROJECTILE      = 2;    // 投掷弹射物

    // ─── 续表 3 ───
    public static final double EXP_MELEE_DAMAGE       = 0.5;  // 近战每点伤害
    public static final double EXP_RANGED_DAMAGE      = 0.5;  // 远程每点伤害
    public static final double EXP_TAKE_DAMAGE        = 0.5;  // 承受每点伤害
    public static final int EXP_TOOL_SKILL            = 20;   // 手动释放工具技能
    public static final int EXP_TOOL_SKILL_UNLOCK     = 100;  // 解锁新工具技能
    public static final int EXP_ATTRIBUTE_POINT       = 50;   // 消耗属性点
    public static final int EXP_LEVEL_MILESTONE       = 200;  // 等级里程碑
    public static final int EXP_DAILY_FIRST_LOGIN     = 150;  // 每日首次上线
    public static final int EXP_DAILY_SIGNIN          = 200;  // 跨自然日签到

    // ==================== 里程碑等级节点 ====================
    private static final int[] MILESTONE_LEVELS = {5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    // ==================== 计数器阈值 ====================
    public static final int COUNTER_THRESHOLD     = 50;
    public static final int ORE_COUNTER_COPPER    = 20;
    public static final int ORE_COUNTER_DEBRIS    = 1;

    // ==================== 玩家计数器（非持久化） ====================
    private static final Map<UUID, Integer> mineCounters    = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> placeCounters   = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> pickupCounters  = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> dropCounters    = new ConcurrentHashMap<>();
    // 矿石专用计数器
    private static final Map<UUID, Integer> oreCopper       = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> oreDeepCopper   = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> oreIron         = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> oreDeepIron     = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> oreGold         = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> oreDeepGold     = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> oreDebris       = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> oreQuartz       = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> oreNetherGold   = new ConcurrentHashMap<>();
    // 在线/移动计数器
    private static final Map<UUID, Long> lastOnlineCheck    = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> online10MinTick = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> online1HourTick = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> walkDistance     = new ConcurrentHashMap<>();

    // ==================== 初始化 ====================

    public static void initialize() {
        DebugLogger.entering("AdventureLevelManager", "initialize");
        PlayerLevelStorage.initialize();
        registerBlockBreakEvent();
        registerJoinEvent();
        registerTickEvent();
        DebugLogger.exiting("AdventureLevelManager", "initialize");
    }

    private static void registerBlockBreakEvent() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer sp) || sp.isCreative()) return;
            // 通用挖掘计数
            incrementMineCounter(sp);
            // 矿石专项计数
            String blockName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            switch (blockName) {
                case "copper_ore"            -> incrementOre(sp, oreCopper,     ORE_COUNTER_COPPER, EXP_ORE_COPPER);
                case "deepslate_copper_ore"  -> incrementOre(sp, oreDeepCopper, ORE_COUNTER_COPPER, EXP_ORE_DEEPSLATE_COPPER);
                case "iron_ore"              -> incrementOre(sp, oreIron,       ORE_COUNTER_COPPER, EXP_ORE_IRON);
                case "deepslate_iron_ore"    -> incrementOre(sp, oreDeepIron,   ORE_COUNTER_COPPER, EXP_ORE_DEEPSLATE_IRON);
                case "gold_ore"              -> incrementOre(sp, oreGold,       ORE_COUNTER_COPPER, EXP_ORE_GOLD);
                case "deepslate_gold_ore"    -> incrementOre(sp, oreDeepGold,   ORE_COUNTER_COPPER, EXP_ORE_DEEPSLATE_GOLD);
                case "ancient_debris"        -> incrementOre(sp, oreDebris,     ORE_COUNTER_DEBRIS, EXP_ORE_ANCIENT_DEBRIS);
                case "nether_quartz_ore"     -> incrementOre(sp, oreQuartz,     ORE_COUNTER_COPPER, EXP_ORE_NETHER_QUARTZ);
                case "nether_gold_ore"       -> incrementOre(sp, oreNetherGold, ORE_COUNTER_COPPER, EXP_ORE_NETHER_GOLD);
            }
        });
    }

    private static void registerTickEvent() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            onServerTick();
        });
    }

    private static void registerJoinEvent() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            UUID uuid = player.getUUID();
            PlayerLevelData data = PlayerLevelStorage.getOrCreate(uuid, player.getName().getString());

            // 首次进服
            if (data.consumeFirstJoin()) {
                grantExp(player, EXP_FIRST_JOIN);
                PlayerLevelStorage.markDirty(uuid);
            }

            // 每日首次上线
            if (data.isDailyFirstLogin()) {
                grantExp(player, EXP_DAILY_FIRST_LOGIN);
                PlayerLevelStorage.markDirty(uuid);
            }

            // 初始化在线计时器
            lastOnlineCheck.put(uuid, System.currentTimeMillis());
            online10MinTick.putIfAbsent(uuid, 0);
            online1HourTick.putIfAbsent(uuid, 0);

            DebugLogger.info("AdventureLevelManager",
                    "玩家 %s 加入，等级 Lv.%d，累积经验 %d",
                    player.getName().getString(), data.getLevel(), data.totalExp);
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

    public static void incrementPickupCounter(ServerPlayer player) {
        int count = pickupCounters.merge(player.getUUID(), 1, Integer::sum);
        if (count >= COUNTER_THRESHOLD) {
            pickupCounters.put(player.getUUID(), count - COUNTER_THRESHOLD);
            grantExp(player, EXP_PICKUP_BATCH);
        }
    }

    public static void incrementDropCounter(ServerPlayer player) {
        int count = dropCounters.merge(player.getUUID(), 1, Integer::sum);
        if (count >= COUNTER_THRESHOLD) {
            dropCounters.put(player.getUUID(), count - COUNTER_THRESHOLD);
            grantExp(player, EXP_DROP_BATCH);
        }
    }

    private static void incrementOre(ServerPlayer player, Map<UUID, Integer> counter, int threshold, int exp) {
        int count = counter.merge(player.getUUID(), 1, Integer::sum);
        if (count >= threshold) {
            counter.put(player.getUUID(), count - threshold);
            grantExp(player, exp);
        }
    }

    // ==================== 在线 / 移动距离 ====================

    public static void onServerTick() {
        long now = System.currentTimeMillis();
        for (var entry : lastOnlineCheck.entrySet()) {
            UUID uuid = entry.getKey();
            long last = entry.getValue();
            int elapsedSec = (int) ((now - last) / 1000);
            if (elapsedSec < 10) continue; // 每 10 秒检查一次
            entry.setValue(now);

            // 10 分钟在线 → +10
            int tick10 = online10MinTick.merge(uuid, elapsedSec, Integer::sum);
            if (tick10 >= 600) { // 600 秒 = 10 分钟
                online10MinTick.put(uuid, tick10 - 600);
                grantExpByUuid(uuid, EXP_ONLINE_10MIN);
            }

            // 1 小时在线 → +30
            int tick1h = online1HourTick.merge(uuid, elapsedSec, Integer::sum);
            if (tick1h >= 3600) { // 3600 秒 = 1 小时
                online1HourTick.put(uuid, tick1h - 3600);
                grantExpByUuid(uuid, EXP_ONLINE_1HOUR);
            }
        }
    }

    public static void addWalkDistance(ServerPlayer player, double distance) {
        double total = walkDistance.merge(player.getUUID(), distance, Double::sum);
        if (total >= 1000.0) { // 1km = 1000 blocks
            walkDistance.put(player.getUUID(), total - 1000.0);
            grantExp(player, EXP_WALK_1KM);
        }
    }

    // ==================== 每日签到 ====================

    public static void dailySignIn(ServerPlayer player) {
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(player.getUUID(), player.getName().getString());
        if (data.consumeDailySignIn()) {
            grantExp(player, EXP_DAILY_SIGNIN);
            PlayerLevelStorage.markDirty(player.getUUID());
        }
    }

    // ==================== 首次标记方法 ====================

    public static void checkFirstNether(ServerPlayer player) {
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(player.getUUID(), player.getName().getString());
        if (data.firstNetherVisit) {
            data.firstNetherVisit = false;
            grantExp(player, EXP_FIRST_NETHER);
            PlayerLevelStorage.markDirty(player.getUUID());
        }
    }

    public static void checkFirstEnd(ServerPlayer player) {
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(player.getUUID(), player.getName().getString());
        if (data.firstEndVisit) {
            data.firstEndVisit = false;
            grantExp(player, EXP_FIRST_END);
            PlayerLevelStorage.markDirty(player.getUUID());
        }
    }

    public static void checkFirstBeacon(ServerPlayer player) {
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(player.getUUID(), player.getName().getString());
        if (data.firstBeaconActivated) {
            data.firstBeaconActivated = false;
            grantExp(player, EXP_BEACON_FIRST);
            PlayerLevelStorage.markDirty(player.getUUID());
        }
    }

    public static void checkFirstFirework(ServerPlayer player) {
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(player.getUUID(), player.getName().getString());
        if (data.firstFireworkUsed) {
            data.firstFireworkUsed = false;
            grantExp(player, EXP_FIREWORK_FIRST);
            PlayerLevelStorage.markDirty(player.getUUID());
        }
    }

    // ==================== Mod 特定事件（由外部调用） ====================

    /** 手动释放工具技能（由工具类调用） */
    public static void onToolSkillUsed(ServerPlayer player) {
        grantExp(player, EXP_TOOL_SKILL);
    }

    /** 解锁新工具技能（由技能系统调用） */
    public static void onToolSkillUnlocked(ServerPlayer player) {
        grantExp(player, EXP_TOOL_SKILL_UNLOCK);
    }

    /** 消耗属性点加点（由属性系统调用） */
    public static void onAttributePointSpent(ServerPlayer player) {
        grantExp(player, EXP_ATTRIBUTE_POINT);
    }

    // ==================== 伤害经验 ====================

    /** 每点近战伤害 = 0.5 经验，取整发放 */
    public static void onMeleeDamage(ServerPlayer player, float damage) {
        int raw = (int) (damage * EXP_MELEE_DAMAGE);
        if (raw > 0) grantExp(player, raw);
    }

    /** 每点远程伤害 = 0.5 经验，取整发放 */
    public static void onRangedDamage(ServerPlayer player, float damage) {
        int raw = (int) (damage * EXP_RANGED_DAMAGE);
        if (raw > 0) grantExp(player, raw);
    }

    /** 每点承受伤害 = 0.5 经验，取整发放 */
    public static void onTakeDamage(ServerPlayer player, float damage) {
        int raw = (int) (damage * EXP_TAKE_DAMAGE);
        if (raw > 0) grantExp(player, raw);
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

        // 检查里程碑
        if (leveledUp) {
            checkMilestone(player, newLevel);
        }

        DebugLogger.info("AdventureLevelManager",
                "玩家 %s 获得 %d 冒险经验 (Lv.%d → Lv.%d)",
                player.getName().getString(), amount, oldLevel, newLevel);
    }

    /** 无 ServerPlayer 上下文时使用 */
    public static void grantExpSilent(UUID uuid, String username, int amount) {
        if (amount <= 0) return;
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(uuid, username);
        data.addExp(amount);
        PlayerLevelStorage.markDirty(uuid);
    }

    private static void grantExpByUuid(UUID uuid, int amount) {
        if (amount <= 0) return;
        PlayerLevelData data = PlayerLevelStorage.get(uuid);
        if (data == null) return;
        data.addExp(amount);
        PlayerLevelStorage.markDirty(uuid);
    }

    // ==================== 里程碑 ====================

    private static void checkMilestone(ServerPlayer player, int newLevel) {
        PlayerLevelData data = PlayerLevelStorage.getOrCreate(player.getUUID(), player.getName().getString());
        for (int ml : MILESTONE_LEVELS) {
            if (newLevel >= ml && ml < data.milestonesReached.length && !data.milestonesReached[ml]) {
                data.milestonesReached[ml] = true;
                grantExp(player, EXP_LEVEL_MILESTONE);
            }
        }
    }
}
