package top.csituka.youzaiworldcore.skill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.UUID;

/**
 * 玩家冒险等级数据模型。
 * 存储累积经验值及首次/每日标记，等级由 {@link AdventureLevelManager} 计算。
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

    // ─── 首次标记（持久化） ───

    /** 是否首次抵达下界 */
    @SerializedName("first_nether")
    public boolean firstNetherVisit = true;

    /** 是否首次抵达末地 */
    @SerializedName("first_end")
    public boolean firstEndVisit = true;

    /** 信标是否已激活过（单账号仅首次） */
    @SerializedName("first_beacon")
    public boolean firstBeaconActivated = true;

    /** 烟花火箭是否已燃放过（单账号仅首次） */
    @SerializedName("first_firework")
    public boolean firstFireworkUsed = true;

    // ─── 每日标记（持久化） ───

    /** 上次登录日期（ISO yyyy-MM-dd），用于每日刷新判断 */
    @SerializedName("last_login_date")
    public String lastLoginDate = "";

    /** 上次签到日期（ISO yyyy-MM-dd） */
    @SerializedName("last_signin_date")
    public String lastSignInDate = "";

    // ─── 里程碑标记（非持久化，由 AdventureLevelManager 管理） ───
    // 用 transient 让 Gson 跳过序列化
    public transient boolean[] milestonesReached = new boolean[64];

    public PlayerLevelData() {
    }

    public PlayerLevelData(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
        this.totalExp = 0;
    }

    /**
     * 增加经验值（防溢出：clamp 至 0 ~ Integer.MAX_VALUE）。
     * 若 totalExp 已为负数（异常状态），则先从 0 开始重新积累。
     */
    public void addExp(int amount) {
        if (amount <= 0) return;
        // 修复负数异常：如果 totalExp 为负，重置为 0
        if (this.totalExp < 0) {
            this.totalExp = 0;
        }
        // 防溢出：如果加上 amount 会超过 MAX_VALUE，则取 MAX_VALUE
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

    // ─── 便捷方法 ───

    /** 检查并消耗「首次进服」标记 */
    public boolean consumeFirstJoin() {
        if (!"".equals(lastLoginDate)) return false;
        lastLoginDate = java.time.LocalDate.now().toString();
        return true;
    }

    /** 检查并消耗每日首次上线（跨自然日） */
    public boolean isDailyFirstLogin() {
        String today = java.time.LocalDate.now().toString();
        if (today.equals(lastLoginDate)) return false;
        lastLoginDate = today;
        return true;
    }

    /** 检查并消耗跨自然日签到 */
    public boolean consumeDailySignIn() {
        String today = java.time.LocalDate.now().toString();
        if (today.equals(lastSignInDate)) return false;
        lastSignInDate = today;
        return true;
    }
}
