package top.csituka.youzaiworldcore.client.screen;

import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.mail.MailType;
import top.csituka.youzaiworldcore.network.MailAdminEditPayload;
import top.csituka.youzaiworldcore.network.MailComposeOpenPayload;
import top.csituka.youzaiworldcore.network.MailFetchPayload;
import top.csituka.youzaiworldcore.network.MailSentListRequestPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 邮件切页意图只在发起页面和当前连接有效时消费；普通列表刷新仅更新缓存。 */
public final class MailNavigationRequests {
    public enum Kind { COMPOSE, SENT, EDIT }
    public record Request(Kind kind, Screen origin, UUID mailId, Object connection, long startedAt) { }
    private static Request pending;
    private static final long TIMEOUT_MILLIS = 15000;

    private MailNavigationRequests() { }

    /** 申请发布页；命令入口传 null，表示返回游戏。 */
    public static boolean compose(Screen origin) {
        return begin(Kind.COMPOSE, origin, null, new MailComposeOpenPayload());
    }

    /** 申请已发送列表；后台刷新无需设置切页意图。 */
    public static boolean sent(Screen origin) {
        return begin(Kind.SENT, origin, null, new MailSentListRequestPayload());
    }

    /** 申请指定邮件的编辑数据。 */
    public static boolean edit(Screen origin, UUID mailId) {
        return begin(Kind.EDIT, origin, mailId, new MailFetchPayload(mailId));
    }

    private static boolean begin(Kind kind, Screen origin, UUID mailId, CustomPacketPayload payload) {
        tick();
        if (pending != null) return false;
        var client = Minecraft.getInstance();
        if (client.getConnection() == null || !ClientPlayNetworking.canSend(payload.type())) {
            feedback(origin, "request_unavailable", false);
            return false;
        }
        pending = new Request(kind, origin, mailId, client.getConnection(), System.currentTimeMillis());
        ClientPlayNetworking.send(payload);
        feedback(origin, "requesting", true);
        DebugLogger.debug("MailNavigation", "请求邮件页面：%s，mailId=%s", kind, mailId);
        return true;
    }

    /** 页面已经离开、连接已换或邮件 ID 不匹配时，响应不得替换当前界面。 */
    public static Request take(Kind kind, UUID mailId) {
        tick();
        if (pending == null || pending.kind() != kind || !Objects.equals(pending.mailId(), mailId)) return null;
        Request request = pending;
        pending = null;
        var client = Minecraft.getInstance();
        if (request.connection() != client.getConnection() || request.origin() != client.gui.screen()) return null;
        MailToast.clear();
        return request;
    }

    /** 离开邮件页面时撤销切页意图；窗口重排不调用 removed，不会撤销请求。 */
    public static void cancel(Screen origin) {
        if (pending != null && pending.origin() == origin) pending = null;
    }

    /** 失败结果仅解除相同邮件目标的等待。 */
    public static void failed(UUID mailId) {
        if (pending != null && Objects.equals(pending.mailId(), mailId)) pending = null;
    }

    /** 每帧检查连接与等待超时，不自动重试。 */
    public static void tick() {
        if (pending == null) return;
        var client = Minecraft.getInstance();
        if (pending.connection() != client.getConnection()) {
            clear();
        } else if (System.currentTimeMillis() - pending.startedAt() > TIMEOUT_MILLIS) {
            Request request = pending;
            pending = null;
            if (request.origin() == client.gui.screen()) feedback(request.origin(), "request_timeout", false);
            DebugLogger.debug("MailNavigation", "邮件页面请求超时：%s", request.kind());
        }
    }

    /** 编辑预填在用户离开后才返回时，恢复服务端暂时隐藏的邮件。 */
    public static void cancelAbandonedEdit(UUID mailId) {
        var current = Minecraft.getInstance().gui.screen();
        if (current instanceof MailComposeScreen compose && compose.isEditing(mailId)) return;
        ClientPlayNetworking.send(new MailAdminEditPayload(mailId, true, java.util.List.of(),
                MailType.ANNOUNCEMENT, "", "", (byte) 0, java.util.List.of()));
        DebugLogger.debug("MailNavigation", "恢复已离开的邮件编辑：mailId=%s", mailId);
    }

    public static void clear() {
        pending = null;
        MailClientState.pendingEditData = null;
        MailToast.clear();
    }

    private static void feedback(Screen origin, String key, boolean success) {
        Component message = Component.translatable("screen.youzaiworldcore.mail." + key);
        if (origin instanceof MailBaseScreen) MailToast.show(message.getString(), success);
        else {
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(message);
        }
    }
}
