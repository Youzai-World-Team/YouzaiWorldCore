package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.mail.Mail;
import top.csituka.youzaiworldcore.mail.MailRef;
import top.csituka.youzaiworldcore.network.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 玩家信箱屏幕（手动渲染列表，避免 AbstractSelectionList 26.2 API 兼容问题）。
 */
@SuppressWarnings("null")
public class MailScreen extends Screen {

    private static final int LIST_X = 5;
    private static final int LIST_W = 190;
    private static final int LIST_TOP = 40;
    private static final int ITEM_H = 22;
    private static final int DETAIL_X = LIST_X + LIST_W + 5;

    private Mail selectedMail;
    private MailRef selectedRef;
    private int scrollOff = 0;
    private List<MailStreamCodecs.MailRefAndMail> entries = List.of();
    private int detailClaimBtnY = 0;

    public MailScreen() {
        super(Component.translatable("youzaiworldcore.message.gui.mail.title"));
    }

    @Override
    protected void init() {
        super.init();
        ClientPlayNetworking.send(new MailOpenPayload());
        entries = MailClientState.currentInbox;

        // 发布邮件按钮
        addRenderableWidget(Button.builder(
                Component.literal("\uD83D\uDCEE 发布邮件"),
                btn -> {
                    if (MailClientState.canSend)
                        ClientPlayNetworking.send(new MailComposeOpenPayload());
                }).bounds(width - 120, 10, 110, 20).build());

        // 已发送按钮
        addRenderableWidget(Button.builder(
                Component.literal("\uD83D\uDCE8 已发送"),
                btn -> ClientPlayNetworking.send(new MailSentListRequestPayload())).bounds(width - 120, 35, 110, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        extractBackground(g, mx, my, pt);
        super.extractRenderState(g, mx, my, pt);

        // 标题
        g.text(font, title, 10, 15, 0xFFFFFF);

        // 未读计数
        int unread = MailClientState.unreadCount;
        g.text(font, Component.literal("未读: " + unread), 10, 28, unread > 0 ? 0xFF5555 : 0x888888);

        // 邮件列表
        renderList(g, mx, my);

        // 详情面板
        if (selectedMail != null)
            renderDetail(g);
    }

    private void renderList(GuiGraphicsExtractor g, int mx, int my) {
        entries = MailClientState.currentInbox;
        int y = LIST_TOP;
        int max = Math.min(entries.size() - scrollOff, 20);

        for (int i = scrollOff; i < scrollOff + max && i < entries.size(); i++) {
            var pair = entries.get(i);
            MailRef ref = pair.ref();
            Mail mail = pair.mail();
            int ey = y;

            boolean hover = mx >= LIST_X && mx <= LIST_X + LIST_W && my >= ey && my <= ey + ITEM_H;
            if (hover)
                g.fill(LIST_X, ey, LIST_X + LIST_W, ey + ITEM_H, 0x33FFFFFF);
            if (ref.isStarred())
                g.fill(LIST_X, ey, LIST_X + LIST_W, ey + ITEM_H, 0x22FFFF00);
            if (!ref.isRead())
                g.fill(LIST_X + 2, ey + 5, LIST_X + 7, ey + 11, 0xFFFF5555);

            String t = mail.getTitle();
            if (t == null || t.isEmpty())
                t = "(无主题)";
            if (t.length() > 18)
                t = t.substring(0, 18) + "...";
            g.text(Minecraft.getInstance().font, t, LIST_X + 10, ey + 3, ref.isRead() ? 0xCCCCCC : 0xFFFFFF);
            g.text(Minecraft.getInstance().font, mail.getSender() != null ? mail.getSender() : "?", LIST_X + 10,
                    ey + 13, 0x888888);
            if (ref.isStarred())
                g.text(Minecraft.getInstance().font, "\u2605", LIST_X + LIST_W - 12, ey + 3, 0xFFFFAA00);
            if (ref.isClaimed())
                g.text(Minecraft.getInstance().font, "\u2713", LIST_X + LIST_W - 12, ey + 13, 0xFF55FF55);
            y += ITEM_H;
        }
    }

    private void renderDetail(GuiGraphicsExtractor g) {
        int x = DETAIL_X, y = LIST_TOP, w = width - x - 5;
        g.fill(x, y, x + w, height - 5, 0x44000000);
        if (selectedMail == null)
            return;

        g.text(font, "\u00A7l" + selectedMail.getTitle(), x + 5, y + 5, 0xFFFFFF);
        y += 14;
        g.text(font, "发件人: " + selectedMail.getSender(), x + 5, y, 0xAAAAAA);
        y += 11;
        g.text(font, "类型: " + selectedMail.getType().name(), x + 5, y, 0xAAAAAA);
        y += 11;
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(selectedMail.getCreatedTime()));
        g.text(font, "时间: " + ts, x + 5, y, 0xAAAAAA);
        y += 11;

        if (selectedMail.isExpired()) {
            g.text(font, "\u00A7c\u00A7l已过期", x + 5, y, 0xFF5555);
        } else if (selectedMail.getExpireTime() != null) {
            long remain = selectedMail.getExpireTime() - System.currentTimeMillis();
            g.text(font, "剩余: " + fmt(remain), x + 5, y, 0x55FF55);
        } else {
            g.text(font, "永久有效", x + 5, y, 0x55FF55);
        }
        y += 14;

        g.text(font, "\u00A7n正文:", x + 5, y, 0xFFFFFF);
        y += 11;
        String body = selectedMail.getBody();
        if (body != null && !body.isEmpty()) {
            for (String line : body.split("\n")) {
                g.text(font, line, x + 5, y, 0xCCCCCC);
                y += 10;
                if (y > height - 30)
                    break;
            }
        } else {
            g.text(font, "(无正文)", x + 5, y, 0x666666);
            y += 11;
        }
        y += 5;
        if (selectedMail.getAttachments() != null && !selectedMail.getAttachments().isEmpty()) {
            g.text(font, "\u00A7e[含 " + selectedMail.getAttachments().size() + " 个附件]", x + 5, y, 0xFFFF55);
        }
        y += 16;

        // 操作按钮区域
        if (selectedRef != null) {
            boolean canClaim = selectedMail.getType() == top.csituka.youzaiworldcore.mail.MailType.REWARD
                    && !selectedMail.isExpired() && !selectedRef.isClaimed();
            if (canClaim) {
                g.text(font, "\u00A7a[\u2192 领取奖励]", x + 5, y, selectedRef.isClaimed() ? 0x555555 : 0x55FF55);
            }
            g.text(font, selectedRef.isStarred() ? "\u00A7e[\u2605 取消星标]" : "\u00A77[\u2606 星标]", x + 90, y, 0xFFFFAA);
            g.text(font, "\u00A77[\u2716 删除]", x + 170, y, 0xFF5555);
            // 记录按钮位置供点击检测
            detailClaimBtnY = y;
        }
    }

    private static String fmt(long ms) {
        if (ms <= 0)
            return "已过期";
        long d = ms / 86400000L, h = (ms % 86400000L) / 3600000L;
        if (d > 0)
            return d + "天" + h + "小时";
        return h + "小时" + (ms % 3600000L) / 60000L + "分";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean bl) {
        if (super.mouseClicked(ev, bl))
            return true;
        double mx = ev.x(), my = ev.y();

        if (mx >= LIST_X && mx <= LIST_X + LIST_W && my >= LIST_TOP) {
            int idx = scrollOff + (int) ((my - LIST_TOP) / ITEM_H);
            if (idx >= 0 && idx < entries.size()) {
                var pair = entries.get(idx);
                if (ev.button() == 0) {
                    selectEntry(pair.mail(), pair.ref());
                } else if (ev.button() == 1) {
                    boolean ns = !pair.ref().isStarred();
                    pair.ref().setStarred(ns);
                    ClientPlayNetworking.send(new MailActionPayload(pair.mail().getId(),
                            ns ? MailActionPayload.ACTION_STAR : MailActionPayload.ACTION_UNSTAR));
                }
                return true;
            }
        }

        // 详情面板按钮
        if (selectedMail != null && selectedRef != null && detailClaimBtnY > 0) {
            if (mx >= DETAIL_X + 5 && mx <= DETAIL_X + 85 && my >= detailClaimBtnY && my <= detailClaimBtnY + 14) {
                // 领取
                if (selectedMail.getType() == top.csituka.youzaiworldcore.mail.MailType.REWARD
                        && !selectedMail.isExpired() && !selectedRef.isClaimed()) {
                    ClientPlayNetworking.send(new MailActionPayload(selectedMail.getId(), MailActionPayload.ACTION_CLAIM));
                }
                return true;
            }
            if (mx >= DETAIL_X + 90 && mx <= DETAIL_X + 165 && my >= detailClaimBtnY && my <= detailClaimBtnY + 14) {
                // 星标
                boolean ns = !selectedRef.isStarred();
                selectedRef.setStarred(ns);
                ClientPlayNetworking.send(new MailActionPayload(selectedMail.getId(),
                        ns ? MailActionPayload.ACTION_STAR : MailActionPayload.ACTION_UNSTAR));
                return true;
            }
            if (mx >= DETAIL_X + 170 && mx <= DETAIL_X + 220 && my >= detailClaimBtnY && my <= detailClaimBtnY + 14) {
                // 删除
                ClientPlayNetworking.send(new MailActionPayload(selectedMail.getId(), MailActionPayload.ACTION_DELETE));
                selectedMail = null;
                selectedRef = null;
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta, double d) {
        if (delta > 0)
            scrollOff = Math.max(0, scrollOff - 1);
        else
            scrollOff = Math.min(Math.max(0, entries.size() - 1), scrollOff + 1);
        return true;
    }

    public void selectEntry(Mail mail, MailRef ref) {
        selectedMail = mail;
        if (ref != null && !ref.isRead()) {
            ref.setRead(true);
            ClientPlayNetworking.send(new MailActionPayload(mail.getId(), MailActionPayload.ACTION_READ));
            if (MailClientState.unreadCount > 0)
                MailClientState.unreadCount--;
        }
    }
}
