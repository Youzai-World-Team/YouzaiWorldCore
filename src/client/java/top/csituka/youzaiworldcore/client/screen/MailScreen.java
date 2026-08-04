package top.csituka.youzaiworldcore.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.mail.Mail;
import top.csituka.youzaiworldcore.mail.MailAttachment;
import top.csituka.youzaiworldcore.mail.AttachmentType;
import top.csituka.youzaiworldcore.mail.MailRef;
import top.csituka.youzaiworldcore.mail.MailType;
import top.csituka.youzaiworldcore.network.MailActionPayload;
import top.csituka.youzaiworldcore.network.MailComposeOpenPayload;
import top.csituka.youzaiworldcore.network.MailOpenPayload;
import top.csituka.youzaiworldcore.network.MailSentListRequestPayload;
import top.csituka.youzaiworldcore.network.MailStreamCodecs;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 玩家信箱屏幕：收件列表、筛选、邮件详情与批量操作。
 * <p>坐标全部为 {@link MailViewport} 的设计空间坐标，缩放与动画由 {@link MailBaseScreen} 处理。</p>
 */
@SuppressWarnings("null")
public class MailScreen extends MailBaseScreen {

    private static final String MODULE = "MailScreen";
    private static final int ROW_HEIGHT = 46;

    private Filter activeFilter = Filter.ALL;
    private UUID selectedMailId;
    private int scrollOffset;

    private MailUi.Rect pageRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect listRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect detailRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect composeRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect sentRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect backRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect closeRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect deleteReadRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect purgeExpiredRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect claimRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect starRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect deleteRect = new MailUi.Rect(0, 0, 0, 0);
    private final MailUi.Rect[] tabRects = new MailUi.Rect[Filter.values().length];

    public MailScreen() {
        super(Component.translatable("youzaiworldcore.message.gui.mail.title"));
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;
        ClientPlayNetworking.send(new MailOpenPayload());
        DebugLogger.info(MODULE, "已打开玩家信箱");
    }

    @Override
    protected void renderMailContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        pageRect = MailUi.centeredPage(822, 464);
        MailUi.drawPage(graphics, pageRect);

        List<MailStreamCodecs.MailRefAndMail> inbox = visibleInbox();
        renderHeader(graphics, mouseX, mouseY, inbox);
        if (inbox.isEmpty()) {
            renderEmptyState(graphics);
            listRect = detailRect = new MailUi.Rect(0, 0, 0, 0);
            claimRect = starRect = deleteRect = new MailUi.Rect(0, 0, 0, 0);
            deleteReadRect = purgeExpiredRect = new MailUi.Rect(0, 0, 0, 0);
        } else {
            renderTabs(graphics, mouseX, mouseY, inbox);
            renderMailbox(graphics, mouseX, mouseY, inbox);
            renderBatchActions(graphics, mouseX, mouseY, inbox);
        }
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                              List<MailStreamCodecs.MailRefAndMail> inbox) {
        int titleX = pageRect.x() + 34;
        int titleY = pageRect.y() + 34;
        graphics.text(font, "✉", titleX, titleY + 2, MailUi.TEXT_PRIMARY, false);

        graphics.pose().pushMatrix();
        graphics.pose().scale(1.45f, 1.45f);
        graphics.text(font, "邮件", (int) ((titleX + 28) / 1.45f), (int) (titleY / 1.45f),
                MailUi.TEXT_PRIMARY, false);
        graphics.pose().popMatrix();

        int unread = (int) inbox.stream().filter(pair -> !pair.ref().isRead()).count();
        if (unread > 0) {
            int badgeX = titleX + 102;
            graphics.fill(badgeX, titleY + 5, badgeX + 8, titleY + 13, MailUi.RED);
            graphics.text(font, String.valueOf(unread), badgeX + 12, titleY + 4, MailUi.RED, false);
        }

        // ===== 右上角操作区：[发布邮件] [已发送] [返回] [关闭] =====
        int buttonY = pageRect.y() + 34;
        closeRect = new MailUi.Rect(pageRect.right() - 80, buttonY, 46, 22);
        backRect = new MailUi.Rect(closeRect.x() - 54, buttonY, 46, 22);
        MailUi.button(graphics, font, backRect, "返回", 0xFF9A9A9A, 0xFF111111,
                backRect.contains(mouseX, mouseY), true);
        MailUi.button(graphics, font, closeRect, "关闭", 0xFF9A9A9A, 0xFF111111,
                closeRect.contains(mouseX, mouseY), true);

        if (MailClientState.canSend) {
            sentRect = new MailUi.Rect(backRect.x() - 64, buttonY, 56, 22);
            composeRect = new MailUi.Rect(sentRect.x() - 72, buttonY, 64, 22);
            MailUi.button(graphics, font, composeRect, "发布邮件", 0xFF9A9A9A, 0xFF111111,
                    composeRect.contains(mouseX, mouseY), true);
            MailUi.button(graphics, font, sentRect, "已发送", 0xFF9A9A9A, 0xFF111111,
                    sentRect.contains(mouseX, mouseY), true);
        } else {
            composeRect = new MailUi.Rect(0, 0, 0, 0);
            sentRect = new MailUi.Rect(0, 0, 0, 0);
        }
    }

    private void renderTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                            List<MailStreamCodecs.MailRefAndMail> inbox) {
        int unread = (int) inbox.stream().filter(pair -> !pair.ref().isRead()).count();
        int starred = (int) inbox.stream().filter(pair -> pair.ref().isStarred()).count();
        String[] labels = {"全部", "未读 (" + unread + ")", "已收藏 (" + starred + ")"};
        int x = pageRect.x() + 34;
        int y = pageRect.y() + 76;
        int[] widths = {45, 78, 92};

        for (int i = 0; i < labels.length; i++) {
            MailUi.Rect rect = new MailUi.Rect(x, y, widths[i], 26);
            tabRects[i] = rect;
            int color = activeFilter.ordinal() == i ? MailUi.TEXT_PRIMARY
                    : (rect.contains(mouseX, mouseY) ? 0xFFE0E0E0 : MailUi.TEXT_SECONDARY);
            graphics.text(font, labels[i], rect.x(), rect.y() + 5, color, false);
            if (activeFilter.ordinal() == i) {
                graphics.fill(rect.x(), rect.bottom() - 2, rect.right() - 12, rect.bottom(), MailUi.YELLOW);
            }
            x += widths[i] + 8;
        }
    }

    private void renderMailbox(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               List<MailStreamCodecs.MailRefAndMail> inbox) {
        int contentX = pageRect.x() + 34;
        int contentY = pageRect.y() + 108;
        int contentWidth = pageRect.width() - 68;
        int contentHeight = Math.max(90, pageRect.height() - 171);
        int listWidth = Math.min(275, Math.max(220, (int) (contentWidth * 0.37f)));
        listRect = new MailUi.Rect(contentX, contentY, listWidth, contentHeight);
        detailRect = new MailUi.Rect(listRect.right() + 16, contentY,
                contentWidth - listWidth - 16, contentHeight);

        List<MailStreamCodecs.MailRefAndMail> filtered = filteredInbox(inbox);
        MailStreamCodecs.MailRefAndMail selected = resolveSelection(filtered);
        renderListPanel(graphics, mouseX, mouseY, filtered);
        if (selected != null) {
            renderDetailPanel(graphics, mouseX, mouseY, selected);
        } else if (!filtered.isEmpty()) {
            renderNoSelectionHint(graphics);
        } else {
            renderNoFilteredMail(graphics);
        }
    }

    private void renderListPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                 List<MailStreamCodecs.MailRefAndMail> entries) {
        MailUi.roundedRect(graphics, listRect.x(), listRect.y(), listRect.width(), listRect.height(), 5,
                MailUi.PANEL_BACKGROUND);
        graphics.fill(listRect.x(), listRect.y(), listRect.right(), listRect.y() + 30, MailUi.PANEL_HEADER);
        graphics.text(font, "收件箱", listRect.x() + 12, listRect.y() + 10, MailUi.TEXT_PRIMARY, false);
        String count = "共 " + entries.size() + " 封";
        graphics.text(font, count, listRect.right() - font.width(count) - 12, listRect.y() + 10,
                MailUi.TEXT_SECONDARY, false);

        int bodyTop = listRect.y() + 30;
        int visibleRows = Math.max(1, (listRect.height() - 30) / ROW_HEIGHT);
        scrollOffset = Math.min(scrollOffset, Math.max(0, entries.size() - visibleRows));
        int end = Math.min(entries.size(), scrollOffset + visibleRows);

        if (entries.isEmpty()) {
            MailUi.centeredText(graphics, font, Component.literal("当前分类暂无邮件"),
                    new MailUi.Rect(listRect.x(), bodyTop, listRect.width(), listRect.height() - 30),
                    MailUi.TEXT_MUTED);
            return;
        }

        for (int i = scrollOffset; i < end; i++) {
            MailStreamCodecs.MailRefAndMail pair = entries.get(i);
            int rowY = bodyTop + (i - scrollOffset) * ROW_HEIGHT;
            MailUi.Rect rowRect = new MailUi.Rect(listRect.x(), rowY, listRect.width(), ROW_HEIGHT);
            boolean selected = pair.mail().getId().equals(selectedMailId);
            boolean hovered = rowRect.contains(mouseX, mouseY);
            int background = selected ? MailUi.ROW_SELECTED
                    : (hovered ? MailUi.ROW_HOVERED : (i % 2 == 0 ? MailUi.PANEL_BACKGROUND : MailUi.ROW_ALTERNATE));
            graphics.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), background);
            if (selected) {
                graphics.fill(rowRect.x(), rowRect.y(), rowRect.x() + 3, rowRect.bottom(), MailUi.YELLOW);
            }

            int iconColor = typeColor(pair.mail().getType());
            graphics.text(font, typeIcon(pair.mail().getType()), rowRect.x() + 12, rowRect.y() + 14, iconColor, false);
            if (!pair.ref().isRead()) {
                graphics.fill(rowRect.x() + 25, rowRect.y() + 17, rowRect.x() + 31, rowRect.y() + 23, MailUi.RED);
            }

            int textX = rowRect.x() + 38;
            int rightReserve = 48;
            String titleText = MailUi.ellipsize(font, pair.mail().getTitle(), rowRect.width() - 38 - rightReserve);
            graphics.text(font, titleText, textX, rowRect.y() + 8,
                    pair.ref().isRead() ? 0xFFE0E0E0 : MailUi.TEXT_PRIMARY, false);
            String sender = safe(pair.mail().getSender(), "系统");
            graphics.text(font, MailUi.ellipsize(font, sender, 80), textX, rowRect.y() + 25,
                    MailUi.TEXT_SECONDARY, false);
            String time = new SimpleDateFormat("MM/dd HH:mm").format(new Date(pair.mail().getCreatedTime()));
            graphics.text(font, time, rowRect.right() - font.width(time) - 10, rowRect.y() + 25,
                    0xFFB0B0B0, false);

            if (pair.ref().isStarred()) {
                graphics.text(font, "★", rowRect.right() - 17, rowRect.y() + 7, MailUi.ORANGE, false);
            }
            if (pair.mail().hasAttachments()) {
                graphics.text(font, "▣", rowRect.right() - 17, rowRect.y() + 25, 0xFFD0D0D0, false);
            }
            if (pair.ref().isClaimed()) {
                String claimed = "已领取";
                graphics.text(font, claimed, rowRect.right() - font.width(claimed) - 29, rowRect.y() + 7,
                        MailUi.GREEN, false);
            } else if (pair.mail().isExpired()) {
                String expired = "已过期";
                graphics.text(font, expired, rowRect.right() - font.width(expired) - 29, rowRect.y() + 7,
                        MailUi.RED, false);
            }
        }

        if (entries.size() > visibleRows) {
            int trackY = bodyTop + 4;
            int trackHeight = listRect.height() - 38;
            int thumbHeight = Math.max(18, trackHeight * visibleRows / entries.size());
            int thumbTravel = trackHeight - thumbHeight;
            int maxOffset = entries.size() - visibleRows;
            int thumbY = trackY + (maxOffset == 0 ? 0 : thumbTravel * scrollOffset / maxOffset);
            graphics.fill(listRect.right() - 4, trackY, listRect.right() - 2, trackY + trackHeight, 0x66404040);
            graphics.fill(listRect.right() - 4, thumbY, listRect.right() - 2, thumbY + thumbHeight, 0xFFD0D0D0);
        }
    }

    private void renderDetailPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   MailStreamCodecs.MailRefAndMail pair) {
        Mail mail = pair.mail();
        MailRef ref = pair.ref();
        MailUi.roundedRect(graphics, detailRect.x(), detailRect.y(), detailRect.width(), detailRect.height(), 5,
                MailUi.PANEL_BACKGROUND);
        int x = detailRect.x() + 14;
        int y = detailRect.y() + 14;
        int innerWidth = detailRect.width() - 28;

        String typeLabel = typeLabel(mail.getType());
        MailUi.Rect typeRect = new MailUi.Rect(x, y, font.width(typeLabel) + 20, 18);
        MailUi.roundedRect(graphics, typeRect.x(), typeRect.y(), typeRect.width(), typeRect.height(), 4,
                (typeColor(mail.getType()) & 0x00FFFFFF) | 0x55000000);
        graphics.text(font, typeIcon(mail.getType()) + " " + typeLabel, typeRect.x() + 6, typeRect.y() + 5,
                typeColor(mail.getType()), false);
        y += 27;

        graphics.text(font, MailUi.ellipsize(font, mail.getTitle(), innerWidth), x, y,
                MailUi.TEXT_PRIMARY, false);
        y += 18;
        String sentAt = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(mail.getCreatedTime()));
        graphics.text(font, "发件人: " + safe(mail.getSender(), "系统") + "   时间: " + sentAt,
                x, y, MailUi.TEXT_SECONDARY, false);
        y += 13;
        if (mail.isExpired()) {
            graphics.text(font, "已过期", x, y, MailUi.RED, false);
        } else if (mail.getExpireTime() == null) {
            graphics.text(font, "永久有效", x, y, MailUi.GREEN, false);
        } else {
            graphics.text(font, "剩余 " + formatRemaining(mail.getExpireTime() - System.currentTimeMillis()),
                    x, y, MailUi.GREEN, false);
        }
        y += 17;
        graphics.fill(x, y, x + innerWidth, y + 1, MailUi.DIVIDER);
        y += 12;
        graphics.text(font, "正文", x, y, MailUi.TEXT_PRIMARY, false);
        y += 14;

        String body = safe(mail.getBody(), "（无正文）");
        List<FormattedCharSequence> lines = font.split(Component.literal(body), innerWidth);
        int actionsTop = detailRect.bottom() - 54;
        int maxBodyBottom = Math.max(y + font.lineHeight, actionsTop - 47);
        for (FormattedCharSequence line : lines) {
            if (y + font.lineHeight > maxBodyBottom) {
                graphics.text(font, "...", x, y, MailUi.TEXT_SECONDARY, false);
                y += font.lineHeight;
                break;
            }
            graphics.text(font, line, x, y, 0xFFE0E0E0, false);
            y += font.lineHeight + 2;
        }

        List<MailAttachment> attachments = mail.getAttachments();
        if (attachments != null && !attachments.isEmpty()) {
            int attachmentY = Math.max(y + 8, actionsTop - 35);
            graphics.text(font, "附件 (" + attachments.size() + ")", x, attachmentY,
                    MailUi.TEXT_PRIMARY, false);
            int chipX = x;
            int chipY = attachmentY + 13;
            for (MailAttachment attachment : attachments) {
                if (attachment.type() == AttachmentType.ITEM) {
                    // ITEM 类型渲染物品贴图
                    ItemStack stack = decodeAttachmentItem(attachment);
                    if (!stack.isEmpty()) {
                        graphics.item(stack, chipX, chipY, 0);
                        graphics.itemDecorations(font, stack, chipX, chipY);
                        chipX += 18;
                    } else {
                        // 解码失败时回退文字
                        String label = "物品 ×" + Math.max(1, attachment.amount());
                        chipX = renderAttachmentChip(graphics, chipX, chipY, x, innerWidth, label);
                        if (chipX < 0) break;
                    }
                } else {
                    String label = attachmentLabel(attachment);
                    chipX = renderAttachmentChip(graphics, chipX, chipY, x, innerWidth, label);
                    if (chipX < 0) break;
                }
            }
        }

        int buttonY = detailRect.bottom() - 35;
        boolean canClaim = mail.getType() == MailType.REWARD && !mail.isExpired() && !ref.isClaimed();
        claimRect = new MailUi.Rect(x, buttonY, 78, 24);
        starRect = new MailUi.Rect(x + 88, buttonY, 82, 24);
        deleteRect = new MailUi.Rect(x + 180, buttonY, 52, 24);
        MailUi.button(graphics, font, claimRect, ref.isClaimed() ? "已领取" : "领取奖励",
                0xFF4DA346, 0xFFB9FFB9, claimRect.contains(mouseX, mouseY), canClaim);
        MailUi.button(graphics, font, starRect, ref.isStarred() ? "取消星标" : "收藏星标",
                0xFF8A7540, MailUi.YELLOW, starRect.contains(mouseX, mouseY), true);
        MailUi.button(graphics, font, deleteRect, "删除",
                0xFF8A4A4A, 0xFFFF7777, deleteRect.contains(mouseX, mouseY), true);
    }

    private void renderNoFilteredMail(GuiGraphicsExtractor graphics) {
        MailUi.roundedRect(graphics, detailRect.x(), detailRect.y(), detailRect.width(), detailRect.height(), 5,
                MailUi.PANEL_BACKGROUND);
        MailUi.centeredText(graphics, font, Component.literal("当前分类暂无邮件"), detailRect, MailUi.TEXT_MUTED);
        claimRect = starRect = deleteRect = new MailUi.Rect(0, 0, 0, 0);
    }

    /** 右侧详情区未选中任何邮件时的占位提示。 */
    private void renderNoSelectionHint(GuiGraphicsExtractor graphics) {
        MailUi.roundedRect(graphics, detailRect.x(), detailRect.y(), detailRect.width(), detailRect.height(), 5,
                MailUi.PANEL_BACKGROUND);
        MailUi.centeredText(graphics, font, Component.literal("← 请选择一封邮件查看详情"), detailRect, MailUi.TEXT_MUTED);
        claimRect = starRect = deleteRect = new MailUi.Rect(0, 0, 0, 0);
    }

    private void renderBatchActions(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                    List<MailStreamCodecs.MailRefAndMail> inbox) {
        int y = pageRect.bottom() - 46;
        deleteReadRect = new MailUi.Rect(pageRect.x() + 34, y, 88, 23);
        purgeExpiredRect = new MailUi.Rect(deleteReadRect.right() + 8, y, 96, 23);
        boolean hasRead = inbox.stream().anyMatch(pair -> canDeleteByReadFilter(pair));
        boolean hasExpired = inbox.stream().anyMatch(pair -> pair.mail().isExpired() && !pair.ref().isStarred());
        MailUi.button(graphics, font, deleteReadRect, "删除全部已读", 0xFF8A8A8A, 0xFF111111,
                deleteReadRect.contains(mouseX, mouseY), hasRead);
        MailUi.button(graphics, font, purgeExpiredRect, "清理过期邮件", 0xFF8A8A8A, 0xFF111111,
                purgeExpiredRect.contains(mouseX, mouseY), hasExpired);
        graphics.text(font, "清理前请确认，已收藏的过期邮件将保留", purgeExpiredRect.right() + 10, y + 7,
                MailUi.TEXT_MUTED, false);
    }

    private void renderEmptyState(GuiGraphicsExtractor graphics) {
        int centerX = pageRect.x() + pageRect.width() / 2;
        int centerY = pageRect.y() + pageRect.height() / 2 - 12;
        graphics.pose().pushMatrix();
        graphics.pose().scale(3.2f, 3.2f);
        String icon = "✉";
        graphics.text(font, icon, (int) ((centerX - font.width(icon) * 1.6f) / 3.2f),
                (int) ((centerY - 38) / 3.2f), 0xFF666666, false);
        graphics.pose().popMatrix();
        String heading = "邮箱空空如也";
        graphics.text(font, heading, centerX - font.width(heading) / 2, centerY + 15,
                MailUi.TEXT_PRIMARY, false);
        String description = "服务器发送的公告、通知与奖励都会显示在这里";
        graphics.text(font, description, centerX - font.width(description) / 2, centerY + 42,
                MailUi.TEXT_MUTED, false);
        String hint = "新邮件到达时将显示红色未读角标";
        graphics.text(font, hint, centerX - font.width(hint) / 2, centerY + 75,
                0xFF666666, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        double mouseX = viewport.toDesignX(event.x());
        double mouseY = viewport.toDesignY(event.y());

        if (backRect.contains(mouseX, mouseY)) {
            backToMenu();
            return true;
        }
        if (closeRect.contains(mouseX, mouseY)) {
            closeToGame();
            return true;
        }
        if (MailClientState.canSend && composeRect.contains(mouseX, mouseY)) {
            ClientPlayNetworking.send(new MailComposeOpenPayload());
            DebugLogger.info(MODULE, "请求打开发布邮件页面");
            return true;
        }
        if (MailClientState.canSend && sentRect.contains(mouseX, mouseY)) {
            ClientPlayNetworking.send(new MailSentListRequestPayload());
            DebugLogger.info(MODULE, "请求打开已发送邮件页面");
            return true;
        }

        List<MailStreamCodecs.MailRefAndMail> inbox = visibleInbox();
        if (!inbox.isEmpty()) {
            for (int i = 0; i < tabRects.length; i++) {
                if (tabRects[i] != null && tabRects[i].contains(mouseX, mouseY)) {
                    activeFilter = Filter.values()[i];
                    scrollOffset = 0;
                    selectedMailId = null;
                    return true;
                }
            }

            List<MailStreamCodecs.MailRefAndMail> filtered = filteredInbox(inbox);
            int bodyTop = listRect.y() + 30;
            if (listRect.contains(mouseX, mouseY) && mouseY >= bodyTop) {
                int index = scrollOffset + (int) ((mouseY - bodyTop) / ROW_HEIGHT);
                if (index >= 0 && index < filtered.size()) {
                    MailStreamCodecs.MailRefAndMail pair = filtered.get(index);
                    if (event.button() == 1) {
                        toggleStar(pair);
                    } else {
                        selectEntry(pair.mail(), pair.ref());
                    }
                    return true;
                }
            }

            MailStreamCodecs.MailRefAndMail selected = resolveSelection(filtered);
            if (selected != null) {
                if (claimRect.contains(mouseX, mouseY)) {
                    claim(selected);
                    return true;
                }
                if (starRect.contains(mouseX, mouseY)) {
                    toggleStar(selected);
                    return true;
                }
                if (deleteRect.contains(mouseX, mouseY)) {
                    deleteMail(selected.mail().getId());
                    return true;
                }
            }

            if (deleteReadRect.contains(mouseX, mouseY)) {
                List<UUID> ids = inbox.stream()
                        .filter(pair -> canDeleteByReadFilter(pair))
                        .map(pair -> pair.mail().getId()).toList();
                deleteMails(ids, "已删除全部已读邮件");
                return true;
            }
            if (purgeExpiredRect.contains(mouseX, mouseY)) {
                List<UUID> ids = inbox.stream()
                        .filter(pair -> pair.mail().isExpired() && !pair.ref().isStarred())
                        .map(pair -> pair.mail().getId()).toList();
                deleteMails(ids, "已清理当前信箱的过期邮件");
                return true;
            }
        }
        return super.mouseClicked(event, isActuallyClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double designX = viewport.toDesignX(mouseX);
        double designY = viewport.toDesignY(mouseY);
        if (!listRect.contains(designX, designY)) {
            return false;
        }
        List<MailStreamCodecs.MailRefAndMail> filtered = filteredInbox(visibleInbox());
        int visibleRows = Math.max(1, (listRect.height() - 30) / ROW_HEIGHT);
        int maxOffset = Math.max(0, filtered.size() - visibleRows);
        scrollOffset = scrollY > 0 ? Math.max(0, scrollOffset - 1) : Math.min(maxOffset, scrollOffset + 1);
        return true;
    }

    /** 选择邮件并在首次展示时标记为已读。 */
    public void selectEntry(Mail mail, MailRef ref) {
        selectedMailId = mail.getId();
        markRead(ref);
        DebugLogger.info(MODULE, "已选择邮件: mailId=%s", mail.getId());
    }

    /**
     * 标记为已读并同步未读数。
     * <p>
     * 幂等：仅在 {@code ref} 尚未读时发包，且立即本地置位，
     * 因此即便由每帧调用的 {@code resolveSelection} 触发也只会发送一次。
     * </p>
     */
    private void markRead(MailRef ref) {
        if (ref == null || ref.isRead()) {
            return;
        }
        ref.setRead(true);
        ClientPlayNetworking.send(new MailActionPayload(ref.getMailId(), MailActionPayload.ACTION_READ));
        MailClientState.recalculateUnreadCount();
    }

    /** 领取奖励：先本地乐观置位让按钮立刻变「已领取」，服务端随后回推权威状态。 */
    private void claim(MailStreamCodecs.MailRefAndMail pair) {
        if (pair.mail().getType() != MailType.REWARD || pair.mail().isExpired() || pair.ref().isClaimed()) {
            return;
        }
        ClientPlayNetworking.send(new MailActionPayload(pair.mail().getId(), MailActionPayload.ACTION_CLAIM));
        pair.ref().setClaimed(true);
        markRead(pair.ref());
        DebugLogger.info(MODULE, "请求领取邮件奖励: mailId=%s", pair.mail().getId());
    }

    private void toggleStar(MailStreamCodecs.MailRefAndMail pair) {
        boolean starred = !pair.ref().isStarred();
        pair.ref().setStarred(starred);
        ClientPlayNetworking.send(new MailActionPayload(pair.mail().getId(),
                starred ? MailActionPayload.ACTION_STAR : MailActionPayload.ACTION_UNSTAR));
        DebugLogger.info(MODULE, "邮件星标状态已切换: mailId=%s, starred=%s", pair.mail().getId(), starred);
    }

    private void deleteMail(UUID mailId) {
        deleteMails(List.of(mailId), "已删除邮件");
    }

    private void deleteMails(List<UUID> ids, String logMessage) {
        if (ids.isEmpty()) {
            return;
        }
        for (UUID id : ids) {
            ClientPlayNetworking.send(new MailActionPayload(id, MailActionPayload.ACTION_DELETE));
        }
        MailClientState.currentInbox.removeIf(pair -> ids.contains(pair.mail().getId()));
        MailClientState.recalculateUnreadCount();
        if (selectedMailId != null && ids.contains(selectedMailId)) {
            selectedMailId = null;
        }
        MailToast.show(logMessage + " ×" + ids.size(), true);
        DebugLogger.info(MODULE, "%s: count=%d", logMessage, ids.size());
    }

    private List<MailStreamCodecs.MailRefAndMail> visibleInbox() {
        return MailClientState.currentInbox.stream()
                .filter(pair -> pair.mail() != null && !pair.mail().isHidden())
                .sorted(Comparator.comparingLong((MailStreamCodecs.MailRefAndMail pair) ->
                        pair.mail().getCreatedTime()).reversed())
                .toList();
    }

    private List<MailStreamCodecs.MailRefAndMail> filteredInbox(List<MailStreamCodecs.MailRefAndMail> inbox) {
        List<MailStreamCodecs.MailRefAndMail> filtered = new ArrayList<>();
        for (MailStreamCodecs.MailRefAndMail pair : inbox) {
            if (activeFilter == Filter.UNREAD && pair.ref().isRead()) {
                continue;
            }
            if (activeFilter == Filter.STARRED && !pair.ref().isStarred()) {
                continue;
            }
            filtered.add(pair);
        }
        return filtered;
    }

    /**
     * 解析当前选中的邮件。
     * <p>
     * 仅在玩家主动点击邮件时设置 {@code selectedMailId}，右侧详情区在未选中时显示为空。
     * 邮件被标记为已读的时机由 {@link #selectEntry} 控制，与自动选中解耦。
     * </p>
     */
    private MailStreamCodecs.MailRefAndMail resolveSelection(List<MailStreamCodecs.MailRefAndMail> filtered) {
        if (filtered.isEmpty()) {
            selectedMailId = null;
            return null;
        }
        if (selectedMailId != null) {
            for (MailStreamCodecs.MailRefAndMail pair : filtered) {
                if (pair.mail().getId().equals(selectedMailId)) {
                    return pair;
                }
            }
        }
        // 不自动选中：保持 selectedMailId 为 null，右侧显示为空
        return null;
    }

    /**
     * 判断一封已读邮件是否可被「删除全部已读」操作删除。
     * <ul>
     *   <li>未读邮件不可删除。</li>
     *   <li>有未领取奖励且未过期的邮件按未读处理，不可删除。</li>
     *   <li>已过期且有星标的邮件受保护，不可删除。</li>
     * </ul>
     */
    private static boolean canDeleteByReadFilter(MailStreamCodecs.MailRefAndMail pair) {
        if (!pair.ref().isRead()) {
            return false;
        }
        // 未领取的奖励邮件，未过期时按未读处理
        if (pair.mail().getType() == MailType.REWARD && !pair.ref().isClaimed() && !pair.mail().isExpired()) {
            return false;
        }
        // 过期且星标的邮件受保护
        if (pair.mail().isExpired() && pair.ref().isStarred()) {
            return false;
        }
        return true;
    }

    private static String typeIcon(MailType type) {
        return switch (type) {
            case ANNOUNCEMENT -> "▣";
            case NOTICE -> "♪";
            case REWARD -> "◆";
        };
    }

    private static String typeLabel(MailType type) {
        return switch (type) {
            case ANNOUNCEMENT -> "公告";
            case NOTICE -> "通知";
            case REWARD -> "奖励";
        };
    }

    private static int typeColor(MailType type) {
        return switch (type) {
            case ANNOUNCEMENT, REWARD -> MailUi.GREEN;
            case NOTICE -> MailUi.YELLOW;
        };
    }

    private static String attachmentLabel(MailAttachment attachment) {
        return switch (attachment.type()) {
            case ITEM -> "物品 ×" + Math.max(1, attachment.amount());
            case COMMAND -> "指令";
            case VANILLA_EXP -> "原版经验 ×" + attachment.amount();
            case VANILLA_LEVEL -> "原版等级 ×" + attachment.amount();
            case ADVENTURE_EXP -> "冒险经验 ×" + attachment.amount();
            case ADVENTURE_LEVEL -> "冒险等级 ×" + attachment.amount();
        };
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String formatRemaining(long milliseconds) {
        if (milliseconds <= 0) {
            return "已过期";
        }
        long days = milliseconds / 86_400_000L;
        long hours = milliseconds % 86_400_000L / 3_600_000L;
        if (days > 0) {
            return days + "天 " + hours + "小时";
        }
        long minutes = milliseconds % 3_600_000L / 60_000L;
        return hours + "小时 " + minutes + "分钟";
    }

    /**
     * 渲染单个文字型附件标签，返回新的 x 坐标；超出宽度返回 -1。
     */
    private int renderAttachmentChip(GuiGraphicsExtractor graphics, int chipX, int chipY,
                                     int boundX, int innerWidth, String label) {
        int chipWidth = font.width(label) + 14;
        if (chipX + chipWidth > boundX + innerWidth) {
            return -1;
        }
        MailUi.roundedRect(graphics, chipX, chipY, chipWidth, 18, 4, 0xFF707070);
        graphics.text(font, label, chipX + 7, chipY + 5, 0xFFE8E8E8, false);
        return chipX + chipWidth + 6;
    }

    /**
     * 将 ITEM 类型附件的 NBT 字符串解码为 ItemStack。
     */
    private static ItemStack decodeAttachmentItem(MailAttachment attachment) {
        if (attachment.itemNbt() == null || attachment.itemNbt().isBlank()
                || Minecraft.getInstance().level == null) {
            return ItemStack.EMPTY;
        }
        try {
            var tag = TagParser.parseCompoundFully(attachment.itemNbt());
            var lookup = Minecraft.getInstance().level.registryAccess();
            ItemStack stack = ItemStack.CODEC.parse(
                    lookup.createSerializationContext(NbtOps.INSTANCE), tag)
                    .result().orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                stack.setCount(Math.min(Math.max(1, attachment.amount()), stack.getMaxStackSize()));
            }
            return stack;
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "decodeAttachmentItem", e);
            return ItemStack.EMPTY;
        }
    }

    private enum Filter {
        ALL,
        UNREAD,
        STARRED
    }
}
