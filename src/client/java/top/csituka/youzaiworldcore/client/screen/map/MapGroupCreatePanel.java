package top.csituka.youzaiworldcore.client.screen.map;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.map.MapGroupIcons;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.map.MapWaypointIcon;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 路径点管理器上方的添加分组弹窗；名称和图标保存到当前存档的客户端地图 SQLite。 */
@SuppressWarnings("null")
final class MapGroupCreatePanel extends MapOverlayPanel {
    private final Consumer<MapWaypointGroup> created;
    private final String world = MapPersonalData.worldId();
    private String name = "";
    private MapWaypointIcon icon = MapWaypointIcon.STARRED;

    MapGroupCreatePanel(YzWorldMapScreen owner, Consumer<MapWaypointGroup> created) {
        super(owner, "new_group");
        this.created = created;
    }

    @Override
    protected void init() {
        super.init();
        panelWidth = Math.min(360, width - 16);
        panelX = (width - panelWidth) / 2;
        int x = panelX + 12, w = panelWidth - 24;
        int columns = Math.min(MapWaypointIcon.ALL.size(), Math.max(1, (w + 4) / 28));
        int iconHeight = (MapWaypointIcon.ALL.size() + columns - 1) / columns * 28;
        panelHeight = Math.min(height - 16, 172 + iconHeight);
        panelY = (height - panelHeight) / 2;
        button(panelX + panelWidth - 32, panelY + 7, 22, Component.literal("×"), this::onClose);
        var input = field(x, panelY + 60, w, "group_name", name, 32, value -> name = value);
        setFocused(input);
        MapGroupButton.iconGrid(x, panelY + 112, w, () -> icon, value -> icon = value, this::addRenderableWidget);
        int footer = panelY + panelHeight - 34, half = (w - 6) / 2;
        button(x, footer, half, MapTexts.text("save"), this::save).setStyle(YzuiTheme.ButtonStyle.FILLED);
        button(x + half + 6, footer, half, MapTexts.text("cancel"), this::onClose);
    }

    private void save() {
        if (!world.equals(MapPersonalData.worldId())) {
            error = MapTexts.text("invalid_group");
            return;
        }
        var group = MapPersonalData.createGroup(name, icon);
        if (group == null) {
            error = MapTexts.text("invalid_group");
            return;
        }
        created.accept(group);
        DebugLogger.debug("MapWaypoints", "添加分组弹窗保存成功：%s", group.id());
        onClose();
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = panelX + 12, w = panelWidth - 24;
        label(g, MapTexts.text("group_name"), x, panelY + 44, w);
        label(g, MapTexts.text("group_icon"), x, panelY + 94, w - 32);
        MapGroupIcons.render(g, icon, x + w - 24, panelY + 88, 24, YzuiTheme.text());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.nextStratum();
        g.fill(0, 0, width, height, YzuiTheme.scrim());
        super.extractRenderState(g, mx, my, delta);
    }
}
