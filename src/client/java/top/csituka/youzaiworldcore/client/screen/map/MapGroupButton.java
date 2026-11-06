package top.csituka.youzaiworldcore.client.screen.map;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.map.MapGroupIcons;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 纯图标分组按钮，保留悬停提示、键盘操作、主题状态色及选中边框。 */
final class MapGroupButton extends TransparentButton {
    private final MapWaypointGroup group;
    private final BooleanSupplier selected;
    private final YzuiHover hover = new YzuiHover();

    MapGroupButton(int x, int y, int width, MapWaypointGroup group, BooleanSupplier selected, Runnable action) {
        super(x, y, width, 24, MapGroupIcons.label(group), action);
        this.group = group; this.selected = selected;
        setTooltip(Tooltip.create(MapGroupIcons.label(group)));
    }

    /** 所有编辑入口按相同顺序列出全部图标；空间不足时换行，返回含间距的总高度。 */
    static int grid(int x, int y, int width, Supplier<MapWaypointGroup> selected,
                    Consumer<MapWaypointGroup> choose, Consumer<MapGroupButton> add, boolean active) {
        int columns = Math.min(MapWaypointGroup.ALL.size(), Math.max(1, (width + 4) / 28));
        int cell = (width - (columns - 1) * 4) / columns;
        for (int i = 0; i < MapWaypointGroup.ALL.size(); i++) {
            var group = MapWaypointGroup.ALL.get(i);
            var button = new MapGroupButton(x + i % columns * (cell + 4), y + i / columns * 28, cell,
                    group, () -> selected.get() == group, () -> {
                        DebugLogger.debug("MapWaypoints", "选择路径点图标分组：%s", group.id());
                        choose.accept(group);
                    });
            button.active = active; add.accept(button);
        }
        return (MapWaypointGroup.ALL.size() + columns - 1) / columns * 28;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean chosen = selected.getAsBoolean();
        var style = chosen ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL;
        float opacity = externalAlpha() * getAlpha();
        isHovered = isMouseOver(mx, my);
        YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), hover.sample(active && isHovered),
                isFocused(), active, opacity, style);
        if (chosen) YzuiTheme.border(g, getX(), getY(), getWidth(), getHeight(), getHeight() / 2,
                YzuiTheme.alpha(YzuiTheme.primary(), opacity));
        int foreground = YzuiTheme.alpha(YzuiTheme.buttonText(style, active), opacity * (active ? 1f : 0.68f));
        MapGroupIcons.render(g, group, getX() + (getWidth() - 16) / 2, getY() + 4, 16, foreground);
    }
}
