package top.csituka.youzaiworldcore.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.network.MailFetchPayload;
import top.csituka.youzaiworldcore.network.MailRecallPayload;
import top.csituka.youzaiworldcore.network.MailSentListRequestPayload;
import top.csituka.youzaiworldcore.network.MailStreamCodecs;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 管理员已发送邮件列表，支持刷新、编辑与撤回。
 */
@SuppressWarnings("null")
public class MailSentScreen extends Screen {

    private static final String MODULE = "MailSentScreen";
    private static final int ROW_HEIGHT = 46;

    private int scrollOffset;
    private MailUi.Rect pageRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect tableRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect refreshRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect backRect = new MailUi.Rect(0, 0, 0, 0);

    public MailSentScreen() {
        super(Component.translatable("youzaiworldcore.message.gui.mail.sent.title"));
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;
        DebugLogger.info(MODULE, "已打开已发送邮件页面");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        pageRect = MailUi.centeredPage(width, height, 824, 464);
        MailUi.drawPage(graphics, pageRect);
        renderHeader(graphics, mouseX, mouseY);
        renderTable(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int titleX = pageRect.x() + 34;
        int titleY = pageRect.y() + 34;
        graphics.text(font, "➤", titleX, titleY + 3, MailUi.TEXT_PRIMARY, false);
        graphics.pose().pushMatrix();
        graphics.pose().scale(1.45f, 1.45f);
        graphics.text(font, "已发送邮件", (int) ((titleX + 28) / 1.45f), (int) (titleY / 1.45f),
                MailUi.TEXT_PRIMARY, false);
        graphics.pose().popMatrix();

        int buttonY = pageRect.y() + 34;
        backRect = new MailUi.Rect(pageRect.right() - 72, buttonY, 42, 22);
        refreshRect = new MailUi.Rect(backRect.x() - 56, buttonY, 48, 22);
        MailUi.button(graphics, font, refreshRect, "刷新", 0xFF9A9A9A, 0xFF111111,
                refreshRect.contains(mouseX, mouseY), true);
        MailUi.button(graphics, font, backRect, "返回", 0xFF9A9A9A, 0xFF111111,
                backRect.contains(mouseX, mouseY), true);
    }

    private void renderTable(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        tableRect = new MailUi.Rect(pageRect.x() + 34, pageRect.y() + 76,
                pageRect.width() - 68, pageRect.height() - 104);
        MailUi.roundedRect(graphics, tableRect.x(), tableRect.y(), tableRect.width(), tableRect.height(), 5,
                MailUi.PANEL_BACKGROUND);
        graphics.fill(tableRect.x(), tableRect.y(), tableRect.right(), tableRect.y() + 30, MailUi.PANEL_HEADER);

        int subjectX = tableRect.x() + 14;
        int scopeX = tableRect.x() + tableRect.width() * 29 / 100;
        int sentTimeX = tableRect.x() + tableRect.width() * 50 / 100;
        int expireX = tableRect.x() + tableRect.width() * 68 / 100;
        int actionX = tableRect.x() + tableRect.width() * 80 / 100;
        int headerY = tableRect.y() + 10;
        graphics.text(font, "主题", subjectX, headerY, MailUi.TEXT_SECONDARY, false);
        graphics.text(font, "接收范围", scopeX, headerY, MailUi.TEXT_SECONDARY, false);
        graphics.text(font, "发送时间", sentTimeX, headerY, MailUi.TEXT_SECONDARY, false);
        graphics.text(font, "到期", expireX, headerY, MailUi.TEXT_SECONDARY, false);
        graphics.text(font, "操作", actionX, headerY, MailUi.TEXT_SECONDARY, false);

        List<MailStreamCodecs.MailSummary> entries = sortedEntries();
        if (entries.isEmpty()) {
            MailUi.centeredText(graphics, font, Component.literal("暂未发送邮件"),
                    new MailUi.Rect(tableRect.x(), tableRect.y() + 30, tableRect.width(), tableRect.height() - 30),
                    MailUi.TEXT_MUTED);
            return;
        }

        int visibleRows = visibleRows();
        scrollOffset = Math.min(scrollOffset, Math.max(0, entries.size() - visibleRows));
        int end = Math.min(entries.size(), scrollOffset + visibleRows);
        for (int i = scrollOffset; i < end; i++) {
            MailStreamCodecs.MailSummary summary = entries.get(i);
            int rowY = tableRect.y() + 30 + (i - scrollOffset) * ROW_HEIGHT;
            MailUi.Rect rowRect = new MailUi.Rect(tableRect.x(), rowY, tableRect.width(), ROW_HEIGHT);
            boolean hovered = rowRect.contains(mouseX, mouseY);
            int background = hovered ? MailUi.ROW_HOVERED
                    : (i % 2 == 0 ? MailUi.PANEL_BACKGROUND : MailUi.ROW_ALTERNATE);
            graphics.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), background);

            int textY = rowY + 18;
            graphics.text(font, MailUi.ellipsize(font, summary.title(), scopeX - subjectX - 18),
                    subjectX, textY, MailUi.TEXT_PRIMARY, false);
            graphics.text(font, MailUi.ellipsize(font, summary.scopeSummary(), sentTimeX - scopeX - 18),
                    scopeX, textY, 0xFFE0E0E0, false);
            String sentAt = new SimpleDateFormat("MM/dd HH:mm").format(new Date(summary.sentTime()));
            graphics.text(font, sentAt, sentTimeX, textY, MailUi.TEXT_SECONDARY, false);

            if (summary.expireTime() == null) {
                graphics.text(font, "永久", expireX, textY, MailUi.GREEN, false);
            } else {
                long remain = summary.expireTime() - System.currentTimeMillis();
                if (remain <= 0) {
                    graphics.text(font, "已过期", expireX, textY, MailUi.RED, false);
                } else {
                    long days = Math.max(1, (remain + 86_399_999L) / 86_400_000L);
                    graphics.text(font, "剩余 " + days + "d", expireX, textY, MailUi.GREEN, false);
                }
            }

            MailUi.Rect edit = editRect(rowY, actionX);
            MailUi.Rect recall = recallRect(rowY, actionX);
            MailUi.button(graphics, font, edit, "编辑", 0xFF8A7540, MailUi.YELLOW,
                    edit.contains(mouseX, mouseY), true);
            MailUi.button(graphics, font, recall, "撤回", 0xFF8A4A4A, 0xFFFF7777,
                    recall.contains(mouseX, mouseY), true);
        }

        if (entries.size() > visibleRows) {
            int trackY = tableRect.y() + 34;
            int trackHeight = tableRect.height() - 40;
            int thumbHeight = Math.max(18, trackHeight * visibleRows / entries.size());
            int maxOffset = entries.size() - visibleRows;
            int thumbY = trackY + (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxOffset);
            graphics.fill(tableRect.right() - 4, trackY, tableRect.right() - 2, trackY + trackHeight, 0x66404040);
            graphics.fill(tableRect.right() - 4, thumbY, tableRect.right() - 2, thumbY + thumbHeight, 0xFFD0D0D0);
        }

        graphics.text(font, "已领取奖励附件的邮件不可编辑；过期邮件仍可修改文本但不可领取",
                tableRect.x() + 14, tableRect.bottom() - 18, MailUi.TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (refreshRect.contains(mouseX, mouseY)) {
            ClientPlayNetworking.send(new MailSentListRequestPayload());
            DebugLogger.info(MODULE, "已请求刷新已发送邮件");
            return true;
        }
        if (backRect.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }

        List<MailStreamCodecs.MailSummary> entries = sortedEntries();
        int actionX = tableRect.x() + tableRect.width() * 80 / 100;
        int visibleRows = visibleRows();
        int end = Math.min(entries.size(), scrollOffset + visibleRows);
        for (int i = scrollOffset; i < end; i++) {
            MailStreamCodecs.MailSummary summary = entries.get(i);
            int rowY = tableRect.y() + 30 + (i - scrollOffset) * ROW_HEIGHT;
            if (editRect(rowY, actionX).contains(mouseX, mouseY)) {
                ClientPlayNetworking.send(new MailFetchPayload(summary.mailId()));
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("§7正在加载邮件数据..."));
                }
                DebugLogger.info(MODULE, "已请求编辑邮件: mailId=%s", summary.mailId());
                return true;
            }
            if (recallRect(rowY, actionX).contains(mouseX, mouseY)) {
                ClientPlayNetworking.send(new MailRecallPayload(summary.mailId()));
                MailClientState.currentSentList.removeIf(entry -> entry.mailId().equals(summary.mailId()));
                DebugLogger.info(MODULE, "已请求撤回邮件: mailId=%s", summary.mailId());
                return true;
            }
        }
        return super.mouseClicked(event, isActuallyClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta, double unused) {
        if (!tableRect.contains(mouseX, mouseY)) {
            return false;
        }
        int visibleRows = visibleRows();
        int maxOffset = Math.max(0, sortedEntries().size() - visibleRows);
        scrollOffset = delta > 0 ? Math.max(0, scrollOffset - 1) : Math.min(maxOffset, scrollOffset + 1);
        return true;
    }

    private MailUi.Rect editRect(int rowY, int actionX) {
        return new MailUi.Rect(actionX, rowY + 11, 38, 24);
    }

    private MailUi.Rect recallRect(int rowY, int actionX) {
        return new MailUi.Rect(actionX + 46, rowY + 11, 38, 24);
    }

    private int visibleRows() {
        return Math.max(1, (tableRect.height() - 55) / ROW_HEIGHT);
    }

    private List<MailStreamCodecs.MailSummary> sortedEntries() {
        return MailClientState.currentSentList.stream()
                .sorted(Comparator.comparingLong(MailStreamCodecs.MailSummary::sentTime).reversed())
                .toList();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(new MailScreen());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MailUi.drawBackdrop(graphics, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
