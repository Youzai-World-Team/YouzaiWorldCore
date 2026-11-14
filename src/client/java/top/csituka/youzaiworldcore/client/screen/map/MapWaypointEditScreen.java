package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.map.MapClient;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.map.MapCoordinates;
import top.csituka.youzaiworldcore.client.map.MapWaypointColors;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapWaypoint;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;

import java.util.LinkedHashMap;
import java.util.Map;

/** 本人私人路径点编辑，保存到本机地图 SQLite。 */
public final class MapWaypointEditScreen extends MapScreen {
    private final MapWaypoint original;
    private final Map<String, String> values = new LinkedHashMap<>();
    private int rowHeight, groupHeight, groupLabelWidth;
    private MapWaypointGroup group;

    public MapWaypointEditScreen(Screen parent, MapWaypoint point, boolean existing) {
        super(parent, existing ? "edit_point" : "new_point");
        original = point;
        group = MapPersonalData.groupOf(point);
        values.put("name", point.name());
        values.put("dimension", point.dimension());
        values.put("x", Integer.toString(point.x()));
        values.put("y", Integer.toString(point.y()));
        values.put("z", Integer.toString(point.z()));
        values.put("color", MapWaypointColors.hex(point.color()));
    }

    @SuppressWarnings("null")
    @Override
    protected void init() {
        super.init();
        rowHeight = Math.max(25, Math.min(38, (panelHeight - 120) / 5));
        int x = panelX + 18, y = panelY + 42, w = panelWidth - 36;
        edit("name", x, y, w, 64);
        groupLabelWidth = w >= 294 ? 74 : 0;
        groupHeight = Math.max(rowHeight,
                MapGroupButton.selector(x + groupLabelWidth, y + rowHeight, w - groupLabelWidth,
                        () -> group, value -> group = value, this::addRenderableWidget,
                        !MapClient.isAnchor(original) && original.kind() != MapWaypoint.Kind.DEATH));
        int dimensionY = y + rowHeight + groupHeight;
        edit("dimension", x, dimensionY, w, 128);
        int cw = (w - 12) / 3;
        edit("x", x, dimensionY + rowHeight, cw, 10);
        edit("y", x + cw + 6, dimensionY + rowHeight, cw, 10);
        edit("z", x + (cw + 6) * 2, dimensionY + rowHeight, cw, 10);
        button(x + 74, dimensionY + rowHeight * 2, w - 74,
                net.minecraft.network.chat.Component.literal("#" + values.get("color") + "  ✎"),
                () -> Minecraft.getInstance().gui.setScreen(new MapWaypointColorScreen(this,
                        MapWaypointColors.parse(values.get("color")), selected ->
                                values.put("color", MapWaypointColors.hex(selected))))).active = editable();
        int bw = (w - 12) / 3, footer = panelY + panelHeight - 34;
        button(x, footer, bw, MapTexts.text("save"), this::savePoint)
                .setStyle(YzuiTheme.ButtonStyle.FILLED).active = editable();
        button(x + bw + 6, footer, bw, MapTexts.text("copy"), () -> {
            try {
                Minecraft.getInstance().keyboardHandler.setClipboard(MapCoordinates.coordinate(readPoint()));
                error = MapTexts.text("copied");
            } catch (IllegalArgumentException ignored) {
                error = MapTexts.text("invalid_position");
            }
        });
        button(x + (bw + 6) * 2, footer, bw, MapTexts.text("cancel"), this::onClose);
    }

    private void edit(String key, int x, int y, int width, int limit) {
        int label = key.length() == 1 ? 15 : Math.min(74, width / 3);
        var field = field(x + label, y, width - label, key, values.get(key), limit, value -> values.put(key, value));
        field.setEditable(editable());
    }

    private boolean editable() {
        return !MapClient.isAnchor(original) && !original.shared();
    }

    private MapWaypoint readPoint() {
        int color = MapWaypointColors.parse(values.get("color"));
        return new MapWaypoint(original.id(), original.owner(), values.get("name"),
                original.kind() == MapWaypoint.Kind.DEATH ? MapWaypointGroup.DEATH.id() : group.id(),
                values.get("dimension"),
                Integer.parseInt(values.get("x")), Integer.parseInt(values.get("y")), Integer.parseInt(values.get("z")),
                color,
                original.enabled(), false, original.kind(), original.createdAt());
    }

    private void savePoint() {
        if (!editable()) return;
        try {
            if (!MapPersonalData.put(readPoint())) {
                error = MapTexts.text("limit");
                return;
            }
            onClose();
        } catch (IllegalArgumentException error) {
            this.error = MapTexts.text("invalid_point");
        }
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = panelX + 18, y = panelY + 49, w = panelWidth - 36;
        label(g, MapTexts.text("name"), x, y, 70);
        if (groupLabelWidth > 0)
            label(g, MapTexts.text("group"), x, y + rowHeight, groupLabelWidth - 4);
        int dimensionY = y + rowHeight + groupHeight;
        label(g, MapTexts.text("dimension"), x, dimensionY, 70);
        label(g, MapTexts.text("color"), x, dimensionY + rowHeight * 2, 48);
        MapWaypointColors.render(g, MapWaypointColors.parse(values.get("color")),
                x + 60, dimensionY + rowHeight * 2 + 3, 1.0F);
        int cw = (w - 12) / 3;
        label(g, net.minecraft.network.chat.Component.literal("X"), x, dimensionY + rowHeight, 13);
        label(g, net.minecraft.network.chat.Component.literal("Y"), x + cw + 6, dimensionY + rowHeight, 13);
        label(g, net.minecraft.network.chat.Component.literal("Z"), x + (cw + 6) * 2, dimensionY + rowHeight, 13);
        if (!editable())
            label(g, MapTexts.text("anchor_teleport_hint"), x, panelY + panelHeight - 64, w);
    }
}
