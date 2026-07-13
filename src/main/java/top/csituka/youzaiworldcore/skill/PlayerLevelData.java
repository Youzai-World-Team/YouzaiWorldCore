package top.csituka.youzaiworldcore.skill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;

/**
 * 玩家冒险等级数据模型。
 * 存储累积经验值，等级由 {@link AdventureLevelManager} 计算。
 */
@SuppressWarnings("null")
public class PlayerLevelData {

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /** 玩家 UUID（存储 key） */
    public UUID uuid;

    /** 玩家名称（用于人类可读） */
    public String username;

    /** 累积总经验值（防溢出：0 ~ Integer.MAX_VALUE） */
    public int totalExp;

    public PlayerLevelData() {
    }

    public PlayerLevelData(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
        this.totalExp = 0;
    }

    /**
     * 增加经验值（防溢出：clamp 至 0 ~ Integer.MAX_VALUE）。
     */
    public void addExp(int amount) {
        if (amount <= 0) return;
        if (this.totalExp < 0) this.totalExp = 0;
        long result = (long) this.totalExp + amount;
        this.totalExp = (int) Math.min(result, Integer.MAX_VALUE);
    }

    /** 获取当前等级 */
    public int getLevel() {
        return AdventureLevelManager.getLevelFromExp(totalExp);
    }

    /** 获取当前等级已累积的经验 */
    public int getCurrentLevelExp() {
        return AdventureLevelManager.getCurrentLevelExp(totalExp);
    }

    /** 获取升到下一级所需总经验 */
    public int getExpForNextLevel() {
        return AdventureLevelManager.expForNextLevel(getLevel());
    }
}
