package top.csituka.youzaiworldcore.account;

/**
 * 玩家账户数据模型。
 * <p>
 * 每个账户对应一个 JSON 文件，存储于：
 * {@code config/youzaiworldcore/account/{uuid}.json}
 * 其中 uuid 为玩家的 Mojang UUID（离线模式下为基于名称生成的 UUID）。
 * </p>
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@link #playerName} — 玩家代号，即 Minecraft 中的用户名（不区分大小写存储原始值）</li>
 *   <li>{@link #passwordHash} — 经 SHA-256 哈希后的密码十六进制字符串</li>
 *   <li>{@link #failedAttempts} — 本次连接周期内连续密码错误的次数</li>
 *   <li>{@link #totalFailedAttempts} — 玩家全生命周期的累计错误次数（用于判断是否永久阻止）</li>
 *   <li>{@link #blocked} — 是否被阻止登入（超过阈值后由系统自动设置）</li>
 * </ul>
 */
public class AccountEntry {

    /** 玩家代号（用户名） */
    private String playerName;

    /** SHA-256 哈希后的密文字符串 */
    private String passwordHash;

    /** 连续失败次数（当前连接周期），达到 3 次踢出、5 次阻止 */
    private int failedAttempts;

    /** 累计失败次数（终身），达到 5 次后永久阻止登入 */
    private int totalFailedAttempts;

    /** 是否被阻止登入（被阻止后无法通过任何方式登入，需管理员解封） */
    private boolean blocked;

    /** 无参构造器，供 Gson 反序列化使用 */
    public AccountEntry() {
    }

    /**
     * 创建一个新的账户条目。
     *
     * @param playerName   玩家代号
     * @param passwordHash SHA-256 哈希后的密码
     */
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

    /**
     * 递增一次失败记录。
     * 同时增加 {@link #failedAttempts}（连续失败）和
     * {@link #totalFailedAttempts}（终身失败）。
     * 调用方根据返回值判断是否达到踢出/阻止阈值。
     */
    public void incrementFailedAttempts() {
        this.failedAttempts++;
        this.totalFailedAttempts++;
    }

    /**
     * 重置连续失败记录。
     * 仅在登录成功时调用，终身失败记录不会被重置。
     */
    public void resetFailedAttempts() {
        this.failedAttempts = 0;
    }
}
