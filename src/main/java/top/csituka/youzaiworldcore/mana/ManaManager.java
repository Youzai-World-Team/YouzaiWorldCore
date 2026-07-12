package top.csituka.youzaiworldcore.mana;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("null")
public class ManaManager {

    private static final ManaManager INSTANCE = new ManaManager();

    /** 玩家魔力数据，key = UUID，value = 当前魔力值 */
    private final Map<UUID, Integer> playerMana = new HashMap<>();

    /** 最大魔力值 */
    public static final int MAX_MANA = 100;

    /** 每 tick 恢复的魔力（每 0.5 秒 = 10 tick 恢复 1 点） */
    private static final int MANA_RECOVER_INTERVAL = 10; // 0.5 秒

    /** 魔力恢复计数器 */
    private int recoverTickCounter = 0;

    private ManaManager() {
    }

    public static ManaManager getInstance() {
        return INSTANCE;
    }

    /**
     * 获取玩家的当前魔力值。如果玩家没有记录，返回最大魔力。
     */
    public int getMana(UUID playerId) {
        return playerMana.getOrDefault(playerId, MAX_MANA);
    }

    /**
     * 设置玩家的魔力值（范围限制在 0 ~ MAX_MANA）。
     */
    public void setMana(UUID playerId, int mana) {
        int clamped = Math.max(0, Math.min(MAX_MANA, mana));
        playerMana.put(playerId, clamped);
    }

    /**
     * 消耗魔力。如果剩余魔力不足，返回 false；否则扣除并返回 true。
     */
    public boolean consumeMana(UUID playerId, int cost) {
        int current = getMana(playerId);
        if (current < cost) {
            return false;
        }
        setMana(playerId, current - cost);
        return true;
    }

    /**
     * 恢复一点魔力（如果未满）。
     */
    public void recoverOne(UUID playerId) {
        int current = getMana(playerId);
        if (current < MAX_MANA) {
            setMana(playerId, current + 1);
        }
    }

    /**
     * 每个服务器 tick 调用，处理魔力恢复逻辑。
     */
    public void onServerTick() {
        recoverTickCounter++;
        if (recoverTickCounter >= MANA_RECOVER_INTERVAL) {
            recoverTickCounter = 0;
            for (UUID playerId : playerMana.keySet()) {
                recoverOne(playerId);
            }
        }
    }

    /**
     * 初始化玩家魔力（玩家加入时调用）。
     */
    public void initPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!playerMana.containsKey(playerId)) {
            playerMana.put(playerId, MAX_MANA);
        }
        DebugLogger.info("ManaManager", "初始化玩家 {} 的魔力为 {}/{}",
                player.getName().getString(), getMana(playerId), MAX_MANA);
    }

    /**
     * 注册连接事件：玩家加入时初始化魔力。
     */
    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            INSTANCE.initPlayer(handler.player);
        });
    }
}
