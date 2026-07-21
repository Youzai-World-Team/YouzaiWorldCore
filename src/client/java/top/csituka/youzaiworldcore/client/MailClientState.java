package top.csituka.youzaiworldcore.client;

import top.csituka.youzaiworldcore.network.MailStreamCodecs;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件系统客户端状态。
 * <p>存储未读数、发布权限、收件箱和已发送列表快照，供 {@code MailScreen} 等 GUI 使用。</p>
 */
public final class MailClientState {

    /** 未读邮件数量 */
    public static int unreadCount = 0;

    /** 当前玩家是否可发布邮件（持有邮件权限） */
    public static boolean canSend = false;

    /** 当前收件箱快照（由 {@code MailListPayload} / {@code MailUpdatePayload} 更新） */
    public static List<MailStreamCodecs.MailRefAndMail> currentInbox = new ArrayList<>();

    /** 当前已发送列表快照（由 {@code MailSentListPayload} 更新） */
    public static List<MailStreamCodecs.MailSummary> currentSentList = new ArrayList<>();

    /** 编辑预填数据（由 MailUpdatePayload MODE_EDIT_PREFILL 设置，MailComposeScreen 读取后清空） */
    public static MailStreamCodecs.MailRefAndMail pendingEditData;

    private MailClientState() {
    }
}
