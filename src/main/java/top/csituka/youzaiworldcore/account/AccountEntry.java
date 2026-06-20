package top.csituka.youzaiworldcore.account;

/**
 * 玩家账户数据模型
 * 存储于 config/youzaiworldcore/account/{uuid}.json
 */
public class AccountEntry {

    /** 玩家代号（用户名） */
    private String playerName;

    /** SHA-256 哈希后的密码 */
    private String passwordHash;

    /** 连续失败次数 */
    private int failedAttempts;

    /** 累计失败次数（终身） */
    private int totalFailedAttempts;

    /** 是否被阻止登入 */
    private boolean blocked;

    public AccountEntry() {
    }

    public AccountEntry(String playerName, String passwordHash) {
        this.playerName = playerName;
        this.passwordHash = passwordHash;
        this.failedAttempts = 0;
        this.totalFailedAttempts = 0;
        this.blocked = false;
    }

    // ===== Getters & Setters =====

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public int getTotalFailedAttempts() {
        return totalFailedAttempts;
    }

    public void setTotalFailedAttempts(int totalFailedAttempts) {
        this.totalFailedAttempts = totalFailedAttempts;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    /** 增加一次失败记录 */
    public void incrementFailedAttempts() {
        this.failedAttempts++;
        this.totalFailedAttempts++;
    }

    /** 重置失败记录（登录成功后调用） */
    public void resetFailedAttempts() {
        this.failedAttempts = 0;
    }
}
