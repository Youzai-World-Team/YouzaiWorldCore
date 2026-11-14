package top.csituka.youzaiworldcore.client.screen.options;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 地图分类使用设置中心的滚动、搜索和直接选择控件，返回原来的地图画布。 */
public class YzuiMapSettingsScreen extends Screen {
    private final Screen parent;
    private final SettingsDetails.MapGroup group;
    private final SettingsViewState viewState = new SettingsViewState();
    private SettingsFrame frame;
    private List<AbstractWidget> controls = List.of();

    public YzuiMapSettingsScreen(Screen parent, String group) {
        super(SettingsDetails.mapGroupTitle(SettingsDetails.MapGroup.valueOf(group)));
        this.parent = parent;
        this.group = SettingsDetails.MapGroup.valueOf(group);
        DebugLogger.debug("MapSettings", "打开直接选择地图设置：%s", group);
    }

    @SuppressWarnings("null")
    @Override
    protected void init() {
        String query = frame == null ? "" : frame.search.getValue();
        double scroll = frame == null ? 0 : frame.content.scrollAmount();
        if (frame != null)
            frame.content.rememberView();
        SettingsDetails.commitNumbers(controls);
        clearFocus();
        clearWidgets();
        List<SettingsList.Row> rows = new ArrayList<>();
        SettingsDetails.map(rows, this, group);
        controls = rows.stream().flatMap(row -> row.widgets.stream()).toList();
        frame = new SettingsFrame(this, rows, List.of(new TransparentButton(0, 0, 160, 24,
                Component.translatable("gui.done"), this::onClose)), this::addRenderableWidget, query, scroll, true,
                null, viewState);
    }

    @SuppressWarnings("null")
    @Override
    public void extractRenderState(@SuppressWarnings("null") GuiGraphicsExtractor g, int x, int y, float tick) {
        frame.render(g);
        super.extractRenderState(g, x, y, tick);
        frame.content.renderPopups(g, x, y, tick);
    }

    @SuppressWarnings("null")
    @Override
    public boolean mouseClicked(@SuppressWarnings("null") MouseButtonEvent event, boolean actual) {
        return frame.content.popupClicked(event, actual) || super.mouseClicked(event, actual);
    }

    @Override
    public boolean keyPressed(@SuppressWarnings("null") KeyEvent event) {
        if (frame.content.popupKeyPressed(event))
            return true;
        if (event.isEscape() && !frame.search.getValue().isEmpty()) {
            frame.search.setValue("");
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void repositionElements() {
        init();
    }

    @Override
    public void onClose() {
        SettingsDetails.commitNumbers(controls);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (frame != null)
            frame.content.closePopups();
        SettingsDetails.commitNumbers(controls);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
