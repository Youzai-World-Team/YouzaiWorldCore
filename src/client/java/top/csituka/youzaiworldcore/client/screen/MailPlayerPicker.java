package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.animation.YzuiPopupAnimation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 发布页「选取玩家」弹窗。
 * <p>
 * 列出账户系统中全部已注册玩家代号（含离线玩家，数据由
 * {@code MailPlayerListPayload} 写入 {@link MailClientState#registeredPlayers}），
 * 每行一个复选框，顶部提供搜索框。与 {@code MailComposeScreen} 的物品选取弹窗一样，
 * 采用「自绘 + 自行处理输入」的模态实现，不进原版组件树，避免与表单输入框抢焦点。
 * </p>
 * <p>坐标全部为设计空间坐标，由所属界面在缩放矩阵内调用。</p>
 */
@SuppressWarnings("null")
final class MailPlayerPicker {

    private static final String MODULE = "MailPlayerPicker";

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 252;
    private static final int ROW_HEIGHT = 17;

    private final YzuiPopupAnimation animation = new YzuiPopupAnimation();
    private final YzuiHover searchHover = new YzuiHover();
    private String query = "";
    private int scrollOffset;
    private final Set<String> selected = new LinkedHashSet<>();
    private Consumer<List<String>> onConfirm;

    private MailUi.Rect panelRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect searchRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect listRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect invertRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect clearRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect cancelRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect confirmRect = new MailUi.Rect(0, 0, 0, 0);

    boolean isOpen() {
        return animation.isVisible();
    }

    boolean acceptsInput() { return animation.acceptsInput(); }

    /**
     * 打开弹窗。
     *
     * @param preselected 已选中的玩家代号（回填复选框）
     * @param onConfirm   点击「确定」后的回调，参数为最终选中的玩家代号列表
     */
    void open(Collection<String> preselected, Consumer<List<String>> onConfirm) {
        animation.show(MailViewport.DESIGN_WIDTH, MailViewport.DESIGN_HEIGHT);
        this.query = "";
        this.scrollOffset = 0;
        this.onConfirm = onConfirm;
        this.selected.clear();
        if (preselected != null) {
            this.selected.addAll(preselected);
        }
        DebugLogger.info(MODULE, "打开选取玩家弹窗: 预选 %d 人, 名单 %d 人",
                this.selected.size(), MailClientState.registeredPlayers.size());
    }
    void close() {
        animation.hide();
        onConfirm = null;
    }

    // ===== 渲染 =====

    /**
     * 绘制弹窗（含遮罩）。
     *
     * @param designWidth  设计空间宽
     * @param designHeight 设计空间高
     */
    void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
                int designWidth, int designHeight) {
        if (!animation.update(designWidth, designHeight)) return;
        animation.scrim(graphics);
        int localX = (int) Math.floor(animation.toLocalX(mouseX));
        int localY = (int) Math.floor(animation.toLocalY(mouseY));
        var previous = animation.begin(graphics);
        try {
            renderPanel(graphics, font, acceptsInput() ? localX : -10000,
                    acceptsInput() ? localY : -10000, designWidth, designHeight);
        } finally {
            animation.end(graphics, previous);
        }
    }

    private void renderPanel(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
                             int designWidth, int designHeight) {
        panelRect = new MailUi.Rect((designWidth - PANEL_WIDTH) / 2, (designHeight - PANEL_HEIGHT) / 2,
                PANEL_WIDTH, PANEL_HEIGHT);
        YzuiTheme.card(graphics, panelRect.x(), panelRect.y(), panelRect.width(), panelRect.height());

        int x = panelRect.x() + 12;
        graphics.text(font, "选取玩家", x, panelRect.y() + 11, MailUi.textPrimary(), false);
        String counter = "已选 " + selected.size() + " 人";
        graphics.text(font, counter, panelRect.right() - font.width(counter) - 12, panelRect.y() + 11,
                MailUi.textSecondary(), false);

        // ===== 搜索框 =====
        searchRect = new MailUi.Rect(x, panelRect.y() + 28, panelRect.width() - 24, 18);
        YzuiTheme.field(graphics, searchRect.x(), searchRect.y(), searchRect.width(), searchRect.height(),
                searchHover.sample(searchRect.contains(mouseX, mouseY)), acceptsInput(), true, 1f);
        graphics.text(font, "🔍", searchRect.x() + 5, searchRect.y() + 5, MailUi.textMuted(), false);
        String shown = query.isEmpty() ? "搜索玩家代号..." : query;
        int queryColor = query.isEmpty() ? MailUi.textMuted() : YzuiTheme.text();
        graphics.text(font, MailUi.ellipsize(font, shown, searchRect.width() - 34), searchRect.x() + 18,
                searchRect.y() + 5, queryColor, false);
        // 光标闪烁：约 1.3Hz，始终显示以指示输入焦点
        if (System.currentTimeMillis() / 400 % 2 == 0) {
            int cursorX = searchRect.x() + 18
                    + (query.isEmpty() ? 0 : Math.min(font.width(query), searchRect.width() - 34));
            graphics.fill(cursorX + 1, searchRect.y() + 4, cursorX + 2, searchRect.y() + 14, YzuiTheme.primary());
        }

        // ===== 名单列表 =====
        listRect = new MailUi.Rect(x, searchRect.bottom() + 8, panelRect.width() - 24, 140);
        MailUi.roundedRect(graphics, listRect.x(), listRect.y(), listRect.width(), listRect.height(), 4,
                YzuiTheme.surfaceLow());

        List<String> filtered = filteredNames();
        int visibleRows = Math.max(1, listRect.height() / ROW_HEIGHT);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, filtered.size() - visibleRows)));

        if (MailClientState.registeredPlayers.isEmpty()) {
            MailUi.centeredText(graphics, font, net.minecraft.network.chat.Component.literal("正在加载玩家名单..."),
                    listRect, MailUi.textMuted());
        } else if (filtered.isEmpty()) {
            MailUi.centeredText(graphics, font, net.minecraft.network.chat.Component.literal("没有匹配的玩家代号"),
                    listRect, MailUi.textMuted());
        } else {
            int end = Math.min(filtered.size(), scrollOffset + visibleRows);
            for (int i = scrollOffset; i < end; i++) {
                String name = filtered.get(i);
                int rowY = listRect.y() + (i - scrollOffset) * ROW_HEIGHT;
                MailUi.Rect rowRect = new MailUi.Rect(listRect.x(), rowY, listRect.width(), ROW_HEIGHT);
                boolean hovered = rowRect.contains(mouseX, mouseY);
                if (hovered) {
                    graphics.fill(rowRect.x(), rowRect.y(), rowRect.right(), rowRect.bottom(), YzuiTheme.surfaceHigh());
                }
                boolean checked = selected.contains(name);
                int boxX = rowRect.x() + 8;
                int boxY = rowRect.y() + 4;
                graphics.fill(boxX, boxY, boxX + 10, boxY + 10, checked ? YzuiTheme.primary() : YzuiTheme.outline());
                graphics.fill(boxX + 1, boxY + 1, boxX + 9, boxY + 9, checked ? YzuiTheme.primary() : YzuiTheme.surface());
                if (checked) {
                    graphics.text(font, "✓", boxX + 1, boxY - 1, YzuiTheme.onPrimary(), false);
                }
                graphics.text(font, MailUi.ellipsize(font, name, rowRect.width() - 36), rowRect.x() + 24,
                        rowRect.y() + 5, checked ? MailUi.textPrimary() : YzuiTheme.textMuted(), false);
            }

            if (filtered.size() > visibleRows) {
                int trackHeight = listRect.height() - 6;
                int trackY = listRect.y() + 3;
                int thumbHeight = Math.max(16, trackHeight * visibleRows / filtered.size());
                int maxOffset = filtered.size() - visibleRows;
                int thumbY = trackY + (trackHeight - thumbHeight) * scrollOffset / Math.max(1, maxOffset);
                graphics.fill(listRect.right() - 5, trackY, listRect.right() - 3, trackY + trackHeight, YzuiTheme.surfaceHigh());
                graphics.fill(listRect.right() - 5, thumbY, listRect.right() - 3, thumbY + thumbHeight, YzuiTheme.outline());
            }
        }

        // ===== 底部按钮 =====
        int buttonY = panelRect.bottom() - 34;
        invertRect = new MailUi.Rect(x, buttonY, 50, 22);
        clearRect = new MailUi.Rect(invertRect.right() + 8, buttonY, 50, 22);
        confirmRect = new MailUi.Rect(panelRect.right() - 62, buttonY, 50, 22);
        cancelRect = new MailUi.Rect(confirmRect.x() - 58, buttonY, 50, 22);
        MailUi.button(graphics, font, invertRect, "反选", YzuiTheme.primaryContainer(), YzuiTheme.onPrimaryContainer(),
                invertRect.contains(mouseX, mouseY), !filtered.isEmpty());
        MailUi.button(graphics, font, clearRect, "清空", YzuiTheme.primaryContainer(), YzuiTheme.onPrimaryContainer(),
                clearRect.contains(mouseX, mouseY), !selected.isEmpty());
        MailUi.button(graphics, font, cancelRect, "取消", YzuiTheme.primaryContainer(), YzuiTheme.onPrimaryContainer(),
                cancelRect.contains(mouseX, mouseY), true);
        MailUi.button(graphics, font, confirmRect, "确定", YzuiTheme.primary(), YzuiTheme.onPrimary(),
                confirmRect.contains(mouseX, mouseY), true);
    }

    // ===== 输入 =====

    /** 处理点击；弹窗打开时始终吞掉事件（模态）。 */
    boolean mouseClicked(double mouseX, double mouseY) {
        if (!isOpen()) {
            return false;
        }
        if (!acceptsInput()) return true;
        mouseX = animation.toLocalX(mouseX);
        mouseY = animation.toLocalY(mouseY);
        if (confirmRect.contains(mouseX, mouseY)) {
            List<String> result = new ArrayList<>(selected);
            Consumer<List<String>> callback = onConfirm;
            close();
            if (callback != null) {
                callback.accept(result);
            }
            DebugLogger.info(MODULE, "确认选取玩家: %d 人", result.size());
            return true;
        }
        if (cancelRect.contains(mouseX, mouseY)) {
            close();
            return true;
        }
        if (clearRect.contains(mouseX, mouseY)) {
            selected.clear();
            return true;
        }
        if (invertRect.contains(mouseX, mouseY)) {
            List<String> filtered = filteredNames();
            for (String name : filtered) {
                if (!selected.remove(name)) {
                    selected.add(name);
                }
            }
            return true;
        }
        if (listRect.contains(mouseX, mouseY)) {
            List<String> filtered = filteredNames();
            int index = scrollOffset + (int) ((mouseY - listRect.y()) / ROW_HEIGHT);
            if (index >= 0 && index < filtered.size()) {
                String name = filtered.get(index);
                if (!selected.remove(name)) {
                    selected.add(name);
                }
            }
            return true;
        }
        if (!panelRect.contains(mouseX, mouseY)) {
            // 点击面板外视为取消
            close();
        }
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isOpen()) {
            return false;
        }
        if (!acceptsInput()) return true;
        mouseX = animation.toLocalX(mouseX);
        mouseY = animation.toLocalY(mouseY);
        if (listRect.contains(mouseX, mouseY)) {
            int visibleRows = Math.max(1, listRect.height() / ROW_HEIGHT);
            int maxOffset = Math.max(0, filteredNames().size() - visibleRows);
            scrollOffset = delta > 0 ? Math.max(0, scrollOffset - 1) : Math.min(maxOffset, scrollOffset + 1);
        }
        return true;
    }

    /** 处理按键：ESC 关闭、回车确认；其余按键交由调用方转发给搜索输入框。 */
    boolean keyPressed(int key) {
        if (!isOpen()) {
            return false;
        }
        if (!acceptsInput()) return true;
        switch (key) {
            case 256 -> close();                                   // GLFW_KEY_ESCAPE
            case 257, 335 -> {                                     // GLFW_KEY_ENTER / KP_ENTER
                List<String> result = new ArrayList<>(selected);
                Consumer<List<String>> callback = onConfirm;
                close();
                if (callback != null) {
                    callback.accept(result);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * 更新搜索词。
     * <p>文本编辑交给发布页的键盘与字符事件，本弹窗负责过滤与展示。</p>
     */
    void setQuery(String value) {
        String next = value == null ? "" : value;
        if (!next.equals(query)) {
            query = next;
            scrollOffset = 0;
        }
    }

    /** 获取当前搜索词。 */
    String getQuery() {
        return query;
    }

    // ===== 工具 =====

    /** 按搜索词过滤后的玩家代号列表。 */
    private List<String> filteredNames() {
        List<String> all = MailClientState.registeredPlayers;
        if (query.isBlank()) {
            return all;
        }
        String lower = query.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String name : all) {
            if (name.toLowerCase().contains(lower)) {
                result.add(name);
            }
        }
        return result;
    }
}
