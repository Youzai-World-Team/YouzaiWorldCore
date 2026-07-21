package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.network.MailRecallPayload;
import top.csituka.youzaiworldcore.network.MailFetchPayload;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 已发送邮件管理列表。
 */
@SuppressWarnings("null")
public class MailSentScreen extends Screen {

    private static final int LIST_X = 10;
    private static final int ITEM_H = 22;
    private int scrollOff = 0;
    @SuppressWarnings("unused")
    private int listW;

    public MailSentScreen() {
        super(Component.translatable("youzaiworldcore.message.gui.mail.sent.title"));
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(Component.literal("↩ 返回"), btn -> onClose())
                .bounds(10, 10, 60, 20).build());

        addRenderableWidget(Button.builder(Component.literal("🔄 刷新"), btn -> {
            ClientPlayNetworking.send(new top.csituka.youzaiworldcore.network.MailSentListRequestPayload());
        }).bounds(80, 10, 60, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        extractBackground(g, mx, my, pt);
        super.extractRenderState(g, mx, my, pt);

        g.text(font, title, 10, 35, 0xFFFFFF);

        var entries = MailClientState.currentSentList;
        int y = 50;

        // 列头
        g.text(font, "主题", LIST_X + 5, y, 0xAAAAAA);
        g.text(font, "接收范围", LIST_X + 150, y, 0xAAAAAA);
        g.text(font, "发送时间", LIST_X + 260, y, 0xAAAAAA);
        g.text(font, "到期", LIST_X + 340, y, 0xAAAAAA);
        g.text(font, "操作", LIST_X + 390, y, 0xAAAAAA);
        y += 14;

        for (int i = scrollOff; i < entries.size() && i < scrollOff + 20; i++) {
            var sum = entries.get(i);
            int ey = y;

            boolean hover = mx >= LIST_X && mx <= LIST_X + width - 20 && my >= ey && my <= ey + ITEM_H;
            if (hover)
                g.fill(LIST_X, ey, LIST_X + width - 20, ey + ITEM_H, 0x33FFFFFF);

            g.text(font,
                    sum.title() != null && sum.title().length() > 12 ? sum.title().substring(0, 12) + ".."
                            : (sum.title() != null ? sum.title() : "-"),
                    LIST_X + 5, ey + 4, 0xFFFFFF);
            g.text(font,
                    sum.scopeSummary() != null && sum.scopeSummary().length() > 12
                            ? sum.scopeSummary().substring(0, 12) + ".."
                            : (sum.scopeSummary() != null ? sum.scopeSummary() : ""),
                    LIST_X + 150, ey + 4, 0xCCCCCC);

            String timeStr = new SimpleDateFormat("MM/dd HH:mm").format(new Date(sum.sentTime()));
            g.text(font, timeStr, LIST_X + 260, ey + 4, 0x888888);

            if (sum.expireTime() != null) {
                long remain = sum.expireTime() - System.currentTimeMillis();
                if (remain <= 0)
                    g.text(font, "§c已过期", LIST_X + 340, ey + 4, 0xFF5555);
                else
                    g.text(font, (remain / 86400000L) + "d", LIST_X + 340, ey + 4, 0x55FF55);
            } else {
                g.text(font, "永久", LIST_X + 340, ey + 4, 0x55FF55);
            }

            // 操作按钮（用文字代替按钮以减少控件复杂性）
            boolean editHover = mx >= LIST_X + 390 && mx <= LIST_X + 430 && my >= ey && my <= ey + 20;
            boolean recallHover = mx >= LIST_X + 435 && mx <= LIST_X + 475 && my >= ey && my <= ey + 20;

            g.text(font, (editHover ? "§e" : "§7") + "编辑", LIST_X + 390, ey + 4, 0xFFFFAA);
            g.text(font, (recallHover ? "§e" : "§7") + "撤回", LIST_X + 435, ey + 4, 0xFFAA55);

            if (hover) {
                g.text(font, "ID: " + sum.mailId(), LIST_X, ey + 22, 0x666666);
            }
            y += ITEM_H + 4;
        }

        if (entries.isEmpty()) {
            g.text(font, "暂未发送邮件", width / 2 - 40, height / 2, 0x888888);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean bl) {
        if (super.mouseClicked(ev, bl))
            return true;
        double mx = ev.x(), my = ev.y();

        var entries = MailClientState.currentSentList;
        for (int i = scrollOff; i < entries.size() && i < scrollOff + 20; i++) {
            var sum = entries.get(i);
            int ey = 50 + 14 + (i - scrollOff) * (ITEM_H + 4);

            if (mx >= LIST_X + 390 && mx <= LIST_X + 430 && my >= ey && my <= ey + 20) {
                // 编辑
                ClientPlayNetworking.send(new MailFetchPayload(sum.mailId()));
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§7正在加载邮件数据..."));
                return true;
            }
            if (mx >= LIST_X + 435 && mx <= LIST_X + 475 && my >= ey && my <= ey + 20) {
                // 撤回
                ClientPlayNetworking.send(new MailRecallPayload(sum.mailId()));
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
            scrollOff = Math.min(Math.max(0, MailClientState.currentSentList.size() - 1), scrollOff + 1);
        return true;
    }

    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
