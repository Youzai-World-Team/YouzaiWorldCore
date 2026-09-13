package top.csituka.youzaiworldcore.client.screen.options;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.util.DebugLogger;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** MD3 设置列表；使用原版列表的滚动、键盘焦点和旁白机制，行内控件随布局同步移动。 */
final class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {
    private final List<Row> allRows = new ArrayList<>();
    private Row revealedSection;

    SettingsList(int x, int y, int width, int height) {
        super(Minecraft.getInstance(), width, height, y, 40);
        setX(x);
        centerListVertically = false;
    }

    void setRows(List<Row> rows, String query) {
        allRows.clear();
        allRows.addAll(rows);
        filter(query);
    }

    void filter(String query) {
        closePopups();
        setFocused(null);
        setDragging(false);
        clearEntries();
        Row section = null;
        String group = "";
        for (Row row : allRows) {
            if (row.navigation) {
                section = row;
                group = "";
            } else if (row.heading) group = row.label.getString();
            row.section = section;
            String sectionText = section == null ? "" : section.label.getString();
            if (query.isBlank() || !row.heading && matches(query, sectionText + " " + group + " " + row.searchText())) {
                addEntry(row, row.heightFor(getRowWidth()));
            }
        }
        setScrollAmount(0);
    }

    static boolean matches(String query, String text) {
        String normalized = normalize(text);
        for (String word : normalize(query).split("\\s+")) {
            if (!normalized.contains(word)) return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).strip();
    }

    /** 按当前布局的实际行高把分组标题对齐顶部，原版 scrollToEntry 只保证标题可见。 */
    void reveal(Row row) {
        int offset = 0;
        for (Row entry : children()) {
            if (entry == row) {
                closePopups();
                setScrollAmount(offset);
                // 末尾分组不足一屏时会被原版滚动上限截断，仍选中用户实际点击的可见分组。
                if (scrollAmount() < offset) revealedSection = row.section;
                DebugLogger.debug("YzuiSettings", "定位设置分组：%s，滚动位置 %.0f", row.label.getString(), scrollAmount());
                return;
            }
            offset += entry.getHeight();
        }
    }

    /** 侧栏跟随内容高亮时只做最小滚动，不改变右侧或抢走键盘焦点。 */
    void ensureVisible(Row row) { if (children().contains(row)) scrollToEntry(row); }

    /** 搜索隐藏标题后仍按结果所属分组高亮；滚动到底部时选中最后一个分组。 */
    Row activeSection() {
        if (children().isEmpty()) return null;
        if (revealedSection != null) return revealedSection;
        if (scrollAmount() > 0 && scrollAmount() >= maxScrollAmount()) return children().getLast().section;
        for (Row row : children()) {
            if (row.getY() + row.getHeight() > getY() + 2) return row.section;
        }
        return children().getLast().section;
    }

    @Override
    public void setScrollAmount(double amount) {
        revealedSection = null;
        super.setScrollAmount(amount);
    }

    private List<DropdownButton> dropdowns() {
        return children().stream().flatMap(row -> row.children().stream())
                .filter(DropdownButton.class::isInstance).map(DropdownButton.class::cast).toList();
    }

    void closePopups() { dropdowns().forEach(DropdownButton::closePopup); }

    /** 下拉菜单在列表裁剪之后绘制，并优先处理覆盖到其他行的点击。 */
    void renderPopups(GuiGraphicsExtractor g, int x, int y, float tick) {
        for (DropdownButton dropdown : dropdowns()) {
            if (dropdown.getY() >= getY() && dropdown.getBottom() <= getBottom()) {
                dropdown.renderPopup(g, x, y, tick, getY(), getBottom(), 0);
            } else dropdown.closePopup();
        }
    }

    boolean popupClicked(MouseButtonEvent event, boolean doubleClick) {
        for (DropdownButton dropdown : dropdowns()) {
            if (!dropdown.isOpen()) continue;
            if (dropdown.isMouseOver(event.x(), event.y())) return dropdown.mouseClicked(event, doubleClick);
            dropdown.closePopup();
        }
        return false;
    }

    boolean popupKeyPressed(KeyEvent event) {
        for (DropdownButton dropdown : dropdowns()) {
            if (dropdown.isOpen() && dropdown.keyPressed(event)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        for (DropdownButton dropdown : dropdowns()) {
            if (dropdown.isOpen() && dropdown.mouseScrolled(x, y, dx, dy)) return true;
        }
        closePopups();
        return super.mouseScrolled(x, y, dx, dy);
    }

    /** 在列表子类中访问原版受保护的 Entry 类型，语言选择仍走其公开 setSelected 方法。 */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void selectNativeEntry(AbstractSelectionList list, Object entry) {
        list.setSelected((AbstractSelectionList.Entry) entry);
    }

    @Override public int getRowWidth() { return Math.max(1, getWidth() - 12); }
    @Override public int getRowLeft() { return getX() + 2; }
    @Override protected int scrollBarX() { return getRight() - 5; }
    @Override protected void extractListBackground(GuiGraphicsExtractor g) { }
    @Override protected void extractListSeparators(GuiGraphicsExtractor g) { }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        Row row = getHovered() != null ? getHovered() : getFocused();
        if (row != null) {
            if (!row.label.getString().isEmpty()) output.add(NarratedElementType.TITLE, row.label);
            if (!row.detail.getString().isEmpty()) output.add(NarratedElementType.HINT, row.detail);
        }
        super.updateWidgetNarration(output);
    }

    /** 一行可容纳设置控件、带说明的操作、分组标题或只读信息。 */
    static final class Row extends ContainerObjectSelectionList.Entry<Row> {
        final Component label;
        final Component detail;
        final List<AbstractWidget> widgets;
        final boolean heading;
        boolean navigation;
        /** 在对应侧栏按钮上方显示的纯文本分组标题。 */
        Component navigationGroup;
        private Row section;
        private final Identifier icon;
        private String keywords;

        private Row(Component label, Component detail, List<AbstractWidget> widgets,
                    boolean heading, Identifier icon, String keywords) {
            this.label = label;
            this.detail = detail;
            this.widgets = widgets.stream().map(SettingsChoiceControl::adapt).toList();
            this.heading = heading;
            this.navigation = heading;
            this.icon = icon;
            this.keywords = keywords;
        }

        static Row widget(AbstractWidget widget) {
            return new Row(Component.empty(), Component.empty(), List.of(widget), false, null, "");
        }

        static Row controls(Component label, Component detail, AbstractWidget... widgets) {
            return new Row(label, detail, List.of(widgets), false, null, "");
        }

        static Row text(Component label, Component detail) {
            return new Row(label, detail, List.of(), false, null, "");
        }

        static Row heading(Component label) {
            return new Row(label, Component.empty(), List.of(), true, null, "");
        }

        static Row subheading(Component label) {
            Row row = heading(label);
            row.navigation = false;
            return row;
        }

        void addSearchTerms(String terms) { keywords += " " + terms; }

        static Row pack(Component label, Component detail, Identifier icon, String id, AbstractWidget... widgets) {
            return new Row(label, detail, List.of(widgets), false, icon, id);
        }

        private String searchText() {
            StringBuilder text = new StringBuilder(label.getString()).append(' ').append(detail.getString())
                    .append(' ').append(keywords);
            for (AbstractWidget widget : widgets) text.append(' ').append(SettingsChoiceControl.searchText(widget));
            return text.toString();
        }

        int heightFor(int width) {
            if (heading) return 28;
            if (widgets.size() == 1 && widgets.getFirst() instanceof MultiLineTextWidget text) {
                text.setMaxWidth(Math.max(1, width - 24)).setMaxRows(Integer.MAX_VALUE);
                return Math.max(38, text.getHeight() + 20);
            }
            int inner = Math.max(20, width - 24 - (icon == null ? 0 : 40));
            int textLines = label.getString().isEmpty() ? 0 : Minecraft.getInstance().font.split(label, inner).size();
            int detailLines = detail.getString().isEmpty() ? 0 : Minecraft.getInstance().font.split(detail, inner).size();
            int contentHeight = Math.max(icon == null ? 0 : 32, (textLines + detailLines) * 12);
            return Math.max(38, 16 + contentHeight + (widgets.isEmpty() ? 0 : controlHeight(width) + 4));
        }

        private int controlHeight(int width) {
            if (widgets.size() == 1 && widgets.getFirst() instanceof CheckboxButton checkbox) {
                return Math.max(24, Minecraft.getInstance().font.split(checkbox.getMessage(), Math.max(1, width - 50)).size() * 12 + 6);
            }
            return 24;
        }

        private void positionWidgets() {
            if (widgets.isEmpty()) return;
            if (widgets.size() == 1 && widgets.getFirst() instanceof MultiLineTextWidget text) {
                // 保留原生富文本的链接点击与键盘操作，长说明可以完整滚动阅读。
                text.setMaxWidth(Math.max(1, getWidth() - 24));
                text.setX(getX() + 10);
                text.setY(getY() + 9);
                int color = YzuiTheme.text() & 0xFFFFFF;
                var currentColor = text.getMessage().getStyle().getColor();
                if (currentColor == null || currentColor.getValue() != color) {
                    text.setMessage(text.getMessage().copy().withColor(color));
                }
                return;
            }
            int left = getX() + 10;
            int available = Math.max(1, getWidth() - 20);
            int gap = 6;
            int controlWidth = Math.max(1, (available - (widgets.size() - 1) * gap) / widgets.size());
            int height = controlHeight(getWidth());
            int y = getY() + getHeight() - height - 8;
            for (int i = 0; i < widgets.size(); i++) {
                AbstractWidget widget = widgets.get(i);
                widget.setRectangle(controlWidth, height, left + i * (controlWidth + gap), y);
            }
        }

        @Override public void setX(int value) { super.setX(value); positionWidgets(); }
        @Override public void setY(int value) { super.setY(value); positionWidgets(); }
        @Override public void setWidth(int value) { super.setWidth(value); positionWidgets(); }
        @Override public void setHeight(int value) { super.setHeight(value); positionWidgets(); }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float tick) {
            widgets.forEach(SettingsChoiceControl::synchronize);
            positionWidgets();
            var font = Minecraft.getInstance().font;
            int x = getX(), y = getY(), width = getWidth();
            if (heading) {
                YzuiTheme.label(g, font, label, x + 10, y + 10, width - 20, YzuiTheme.primary(), false);
                return;
            }
            RoundedRect.fill(g, x, y + 1, width, getHeight() - 5, 10,
                    hovered ? YzuiTheme.surfaceHigh() : YzuiTheme.surfaceLow());
            int textX = x + 10, textWidth = width - 20;
            if (icon != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, icon, textX, y + 9, 0, 0, 32, 32, 32, 32);
                textX += 40;
                textWidth -= 40;
            }
            int textY = y + 9;
            if (!label.getString().isEmpty()) {
                for (var line : font.split(label, Math.max(1, textWidth))) {
                    g.text(font, line, textX, textY, YzuiTheme.text(), false);
                    textY += 12;
                }
            }
            if (!detail.getString().isEmpty()) {
                for (var line : font.split(detail, Math.max(1, textWidth))) {
                    g.text(font, line, textX, textY, YzuiTheme.textMuted(), false);
                    textY += 12;
                }
            }
            for (AbstractWidget widget : widgets) widget.extractRenderState(g, mouseX, mouseY, tick);
        }

        @Override public List<? extends GuiEventListener> children() {
            widgets.forEach(SettingsChoiceControl::synchronize);
            return widgets;
        }
        @Override public List<? extends NarratableEntry> narratables() { return widgets; }

    }
}
