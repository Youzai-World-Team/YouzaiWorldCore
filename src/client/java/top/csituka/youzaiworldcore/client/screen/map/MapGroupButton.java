package top.csituka.youzaiworldcore.client.screen.map;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.NonNull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.map.MapGroupIcons;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.map.MapWaypointIcon;

/** 分组和图标库共用主题按钮，支持名称、提示、键盘操作及选中边框。 */
final class MapGroupButton extends TransparentButton {
    private final MapWaypointIcon icon;
    private final BooleanSupplier selected;
    private final boolean caption;
    private final YzuiHover hover = new YzuiHover();

    @SuppressWarnings("null")
    MapGroupButton(int x, int y, int width, MapWaypointGroup group, BooleanSupplier selected, Runnable action) {
        this(x, y, width, MapGroupIcons.label(group), group.icon(), selected, action, true);
    }

    private MapGroupButton(int x, int y, int width, @NonNull Component label, MapWaypointIcon icon,
            BooleanSupplier selected, Runnable action, boolean caption) {
        super(x, y, width, 24, label, action);
        this.icon = icon;
        this.selected = selected;
        this.caption = caption;
        setTooltip(Tooltip.create(label));
    }

    /** 编辑入口显示当前分组，打开分页选择页，避免大量自建分组挤出坐标和保存按钮。 */
    static int selector(int x, int y, int width, Supplier<MapWaypointGroup> selected,
            Consumer<MapWaypointGroup> choose, Consumer<MapGroupButton> add, boolean active) {
        var button = new MapGroupButton(x, y, width, selected.get(), () -> false, () -> {
            var client = Minecraft.getInstance();
            client.gui.setScreen(new MapGroupSelectScreen(client.gui.screen(), selected.get(), choose));
        });
        button.active = active;
        add.accept(button);
        return 28;
    }

    /** 内置图标库独立于分组，以紧凑网格列出所有图标，空间不足时换行。 */
    @SuppressWarnings("null")
    static int iconGrid(int x, int y, int width, Supplier<MapWaypointIcon> selected,
            Consumer<MapWaypointIcon> choose, Consumer<MapGroupButton> add) {
        int columns = Math.min(MapWaypointIcon.ALL.size(), Math.max(1, (width + 4) / 28));
        int cell = (width - (columns - 1) * 4) / columns;
        for (int i = 0; i < MapWaypointIcon.ALL.size(); i++) {
            var icon = MapWaypointIcon.ALL.get(i);
            add.accept(new MapGroupButton(x + i % columns * (cell + 4), y + i / columns * 28, cell,
                    MapTexts.text("icon." + icon.id()), icon, () -> selected.get() == icon,
                    () -> choose.accept(icon), false));
        }
        return (MapWaypointIcon.ALL.size() + columns - 1) / columns * 28;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        boolean chosen = selected.getAsBoolean();
        var style = chosen ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL;
        float opacity = externalAlpha() * getAlpha();
        isHovered = isMouseOver(mx, my);
        YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), hover.sample(active && isHovered),
                isFocused(), active, opacity, style);
        if (chosen)
            YzuiTheme.border(g, getX(), getY(), getWidth(), getHeight(), getHeight() / 2,
                    YzuiTheme.alpha(YzuiTheme.primary(), opacity));
        int foreground = YzuiTheme.alpha(YzuiTheme.buttonText(style, active), opacity * (active ? 1f : 0.68f));
        int iconX = caption && getWidth() >= 60 ? getX() + 8 : getX() + (getWidth() - 16) / 2;
        MapGroupIcons.render(g, icon, iconX, getY() + 4, 16, foreground);
        if (caption && getWidth() >= 60)
            YzuiTheme.label(g, Minecraft.getInstance().font, getMessage(),
                    getX() + 30, getY() + 8, getWidth() - 38, foreground, false);
    }
}
