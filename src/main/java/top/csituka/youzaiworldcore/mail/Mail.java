package top.csituka.youzaiworldcore.mail;

import org.jetbrains.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.List;
import java.util.UUID;

/**
 * 邮件（服务端权威对象，存于全局仓库）。
 * <p>包含声明字段与运行时状态（claimed / hidden），支持 Gson 序列化。</p>
 */
@SuppressWarnings("null")
public class Mail {

    private static final String MODULE = "Mail";

    /** 邮件唯一标识 */
    private UUID id;

    /** 邮件类型 */
    private MailType type;

    /** 发送者名称 */
    private String sender;

    /** 原始接收范围列表（用于编辑预填与范围变更 diff） */
    private List<TargetSpec> targets;

    /** 展示用范围摘要（如 "全体+指定 Steve"） */
    private String scopeSummary;

    /** 主题 */
    private String title;

    /** 正文 */
    private String body;

    /** 发送时间戳（毫秒） */
    private long createdTime;

    /** 过期时间戳（毫秒），null 表示永久 */
    @Nullable
    private Long expireTime;

    /** 是否已有任意接收者领取过奖励（有附件邮件编辑前置判断用，领取后置 true 不再回退） */
    private boolean claimed;

    /** 编辑中隐藏标志：true 时接收者信箱不渲染该邮件 */
    private boolean hidden;

    /** 附件列表 */
    private List<MailAttachment> attachments;

    // ===== 无参构造（Gson） =====
    public Mail() {
    }

    // ===== 全参构造 =====
    public Mail(UUID id, MailType type, String sender, List<TargetSpec> targets,
                String scopeSummary, String title, String body,
                long createdTime, @Nullable Long expireTime,
                boolean claimed, boolean hidden, List<MailAttachment> attachments) {
        DebugLogger.entering(MODULE, "Mail(parameters)",
                "id=" + id + ", type=" + type + ", sender=" + sender);
        this.id = id;
        this.type = type;
        this.sender = sender;
        this.targets = targets;
        this.scopeSummary = scopeSummary;
        this.title = title;
        this.body = body;
        this.createdTime = createdTime;
        this.expireTime = expireTime;
        this.claimed = claimed;
        this.hidden = hidden;
        this.attachments = attachments;
        DebugLogger.exiting(MODULE, "Mail(parameters)");
    }

    // ===== 简化构造（新建邮件，不含运行时状态） =====
    public Mail(UUID id, MailType type, String sender, List<TargetSpec> targets,
                String scopeSummary, String title, String body,
                long createdTime, @Nullable Long expireTime, List<MailAttachment> attachments) {
        this(id, type, sender, targets, scopeSummary, title, body,
                createdTime, expireTime, false, false, attachments);
    }

    // ===== Getters & Setters =====

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public MailType getType() { return type; }
    public void setType(MailType type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public List<TargetSpec> getTargets() { return targets; }
    public void setTargets(List<TargetSpec> targets) { this.targets = targets; }

    public String getScopeSummary() { return scopeSummary; }
    public void setScopeSummary(String scopeSummary) { this.scopeSummary = scopeSummary; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public long getCreatedTime() { return createdTime; }
    public void setCreatedTime(long createdTime) { this.createdTime = createdTime; }

    @Nullable
    public Long getExpireTime() { return expireTime; }
    public void setExpireTime(@Nullable Long expireTime) { this.expireTime = expireTime; }

    /** 是否永久有效（expireTime == null） */
    public boolean isPermanent() {
        return expireTime == null;
    }

    /** 是否已过期 */
    public boolean isExpired() {
        return expireTime != null && System.currentTimeMillis() > expireTime;
    }

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) {
        DebugLogger.stateChange(MODULE, "Mail", "claimed", String.valueOf(claimed));
        this.claimed = claimed;
    }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) {
        DebugLogger.stateChange(MODULE, "Mail", "hidden", String.valueOf(hidden));
        this.hidden = hidden;
    }

    public List<MailAttachment> getAttachments() { return attachments; }
    public void setAttachments(List<MailAttachment> attachments) { this.attachments = attachments; }

    /** 是否有附件（判断是否 REWARD 类或实际有 content） */
    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    @Override
    public String toString() {
        return "Mail{id=" + id + ", type=" + type + ", title='" + title + "', sender='" + sender + "'}";
    }
}
