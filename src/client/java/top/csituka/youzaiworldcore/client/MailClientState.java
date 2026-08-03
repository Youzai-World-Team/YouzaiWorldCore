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

    /** 已注册玩家代号名单（由 {@code MailPlayerListPayload} 更新，供发布页「选取玩家」弹窗使用） */
    public static List<String> registeredPlayers = new ArrayList<>();

    /** 编辑预填数据（由 MailUpdatePayload MODE_EDIT_PREFILL 设置，MailComposeScreen 读取后清空） */
    public static MailStreamCodecs.MailRefAndMail pendingEditData;

    /**
     * 按当前收件箱快照重算未读数。
     * <p>
     * 服务端会在每次邮件操作后回推权威未读数；此方法用于收到列表 / 单条更新包后
     * 立即让主菜单徽标与列表保持一致，避免出现「红点数与列表对不上」的过渡帧。
     * </p>
     */
    public static void recalculateUnreadCount() {
        unreadCount = (int) currentInbox.stream()
                .filter(pair -> pair.mail() != null && !pair.mail().isHidden())
                .filter(pair -> pair.ref() != null && !pair.ref().isRead())
                .count();
    }

    private MailClientState() {
    }
}
