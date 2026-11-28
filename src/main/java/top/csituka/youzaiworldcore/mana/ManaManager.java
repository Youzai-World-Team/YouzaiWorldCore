package top.csituka.youzaiworldcore.mana;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@SuppressWarnings("null")
public class ManaManager {

    private static final ManaManager INSTANCE = new ManaManager();

    private record ManaKey(String poolId, UUID playerId) {}

    private final Map<MinecraftServer, Map<ManaKey, Integer>> playerMana = new WeakHashMap<>();

    public static final int MAX_MANA = 100;

    // ─── 客户端缓存与魔力不足标记（在客户端 use 时使用） ───

    /** 客户端缓存的当前玩家魔力值（由 ManaSyncPayload 更新） */
    private static int clientMana = MAX_MANA;

    /** 上次魔力不足尝试的时间戳 */
    private static long lastInsufficientManaTime = -10000;

    public static int getClientMana() {
        return clientMana;
    }

    public static void setClientMana(int mana) {
        clientMana = Math.max(0, Math.min(MAX_MANA, mana));
    }

    /** 标记一次魔力不足（由物品类的客户端 use 调用） */
    public static void markInsufficientMana() {
        lastInsufficientManaTime = System.currentTimeMillis();
    }

    public static long getLastInsufficientManaTime() {
        return lastInsufficientManaTime;
    }

    private ManaManager() {
    }

    public static ManaManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取玩家的当前魔力值。如果玩家没有记录，返回最大魔力。
     */
    public int getMana(ServerLevel level, UUID playerId) {
        var balances = playerMana.get(level.getServer());
        return balances == null ? MAX_MANA : balances.getOrDefault(key(level, playerId), MAX_MANA);
    }

    private static ManaKey key(ServerLevel level, UUID playerId) {
        return new ManaKey(DimensionPoolSettings.statePoolId(level.dimension()), playerId);
    }

    /**
     * 消耗魔力。如果剩余魔力不足，返回 false；否则扣除并返回 true。
     */
    public boolean consumeMana(ServerLevel level, UUID playerId, int cost) {
        int current = getMana(level, playerId);
        if (current < cost) {
            return false;
        }
        playerMana.computeIfAbsent(level.getServer(), server -> new HashMap<>())
                .put(key(level, playerId), Math.min(MAX_MANA, current - cost));
        return true;
    }

    void recover(MinecraftServer server) {
        var balances = playerMana.get(server);
        if (balances != null) {
            balances.values().removeIf(mana -> mana >= MAX_MANA - 1);
            balances.replaceAll((key, mana) -> mana + 1);
        }
    }
}
