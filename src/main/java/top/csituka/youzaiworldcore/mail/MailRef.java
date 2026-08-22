package top.csituka.youzaiworldcore.mail;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;

/**
 * 每玩家邮件引用（由 Api 服务端的 {@code game_mail_refs} 表保存）。
 * <p>只存轻量状态，不含正文；正文与附件随 {@link Mail} 一起由 {@link MailApiClient} 从 Api 取回。</p>
 */
@SuppressWarnings("null")
public class MailRef {

    private static final String MODULE = "MailRef";

    /** 对应全局仓库的邮件 ID */
    private UUID mailId;

    /** 是否已读 */
    private boolean read;

    /** 是否星标收藏 */
    private boolean starred;

    /** 是否已领取奖励 */  
    private boolean claimed;

    // ===== 无参构造（Gson） =====
    public MailRef() {
    }

    public MailRef(UUID mailId) {
        DebugLogger.entering(MODULE, "MailRef", "mailId=" + mailId);
        this.mailId = mailId;
        this.read = false;
        this.starred = false;
        this.claimed = false;
        DebugLogger.exiting(MODULE, "MailRef");
    }

    public MailRef(UUID mailId, boolean read, boolean starred, boolean claimed) {
        this.mailId = mailId;
        this.read = read;
        this.starred = starred;
        this.claimed = claimed;
    }

    // ===== Getters & Setters =====

    public UUID getMailId() { return mailId; }
    public void setMailId(UUID mailId) { this.mailId = mailId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) {
        DebugLogger.stateChange(MODULE, "MailRef", "read", String.valueOf(read));
        this.read = read;
    }

    public boolean isStarred() { return starred; }
    public void setStarred(boolean starred) {
        DebugLogger.stateChange(MODULE, "MailRef", "starred", String.valueOf(starred));
        this.starred = starred;
    }

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) {
        DebugLogger.stateChange(MODULE, "MailRef", "claimed", String.valueOf(claimed));
        this.claimed = claimed;
    }

    @Override
    public String toString() {
        return "MailRef{mailId=" + mailId + ", read=" + read + ", starred=" + starred + ", claimed=" + claimed + "}";
    }
}
