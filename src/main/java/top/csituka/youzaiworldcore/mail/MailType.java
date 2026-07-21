package top.csituka.youzaiworldcore.mail;

/**
 * 邮件类型枚举。
 * <ul>
 *   <li>{@link #ANNOUNCEMENT} — 公告（无附件）</li>
 *   <li>{@link #NOTICE} — 通知（无附件）</li>
 *   <li>{@link #REWARD} — 含奖励（必须有至少一个附件）</li>
 * </ul>
 */
public enum MailType {
    ANNOUNCEMENT,
    NOTICE,
    REWARD
}
