package top.csituka.youzaiworldcore.client.screen.map;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.map.MapGroupIcons;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.map.MapWaypointIcon;

/** 创建及编辑私人分组，提供内置图标预览与二次删除确认，死亡点分组不开放此入口。 */
final class MapGroupEditScreen extends MapScreen {
    private final MapWaypointGroup original;
    private final Consumer<MapWaypointGroup> changed;
    private final String world = MapPersonalData.worldId();
    private String name;
    private MapWaypointIcon icon;
    private boolean deleting;
    private int iconY;

    MapGroupEditScreen(Screen parent, MapWaypointGroup group, Consumer<MapWaypointGroup> changed) {
        super(parent, group == null ? "new_group" : "edit_group");
        if (group != null && !group.editable()) throw new IllegalArgumentException("内置分组不可编辑");
        original = group; this.changed = changed;
        name = group == null ? "" : group.name(); icon = group == null ? MapWaypointIcon.STARRED : group.icon();
    }
    @Override protected void init() {
        super.init();
        int x = panelX + 12, w = panelWidth - 24, footer = panelY + panelHeight - 34;
        if (deleting) {
            button(x, footer, (w - 6) / 2, MapTexts.text("confirm_delete"), () -> {
                if (!sameWorld() || !MapPersonalData.deleteGroup(original.id())) { error = MapTexts.text("invalid_group"); return; }
                changed.accept(MapWaypointGroup.UNGROUPED); onClose();
            }).setStyle(YzuiTheme.ButtonStyle.FILLED);
            button(x + (w + 6) / 2, footer, (w - 6) / 2, MapTexts.text("cancel"), () -> { deleting = false; init(); });
            return;
        }
        var input = field(x, panelY + 60, w, "group_name", name, 32, value -> name = value);
        setFocused(input); iconY = panelY + 112;
        int iconHeight = MapGroupButton.iconGrid(x, iconY, w, () -> icon, value -> icon = value, this::addRenderableWidget);
        if (original != null) button(x, iconY + iconHeight + 6, w, MapTexts.text("delete_group"), () -> { deleting = true; error = net.minecraft.network.chat.Component.empty(); init(); });
        button(x, footer, (w - 6) / 2, MapTexts.text("save"), this::save).setStyle(YzuiTheme.ButtonStyle.FILLED);
        button(x + (w + 6) / 2, footer, (w - 6) / 2, MapTexts.text("cancel"), this::onClose);
    }
    private boolean sameWorld() { return world.equals(MapPersonalData.worldId()); }
    private void save() {
        if (!sameWorld()) { error = MapTexts.text("invalid_group"); return; }
        MapWaypointGroup result;
        if (original == null) result = MapPersonalData.createGroup(name, icon);
        else result = MapPersonalData.editGroup(original.id(), name, icon) ? MapPersonalData.resolveGroup(original.id()) : null;
        if (result == null) { error = MapTexts.text("invalid_group"); return; }
        changed.accept(result); onClose();
    }
    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int x = panelX + 12, w = panelWidth - 24;
        if (deleting) {
            int y = panelY + 60;
            for (var text : font.split(MapTexts.text("delete_group_hint"), w)) {
                var plain = new StringBuilder();
                text.accept((index, style, codepoint) -> { plain.appendCodePoint(codepoint); return true; });
                label(g, net.minecraft.network.chat.Component.literal(plain.toString()), x, y, w); y += 12;
            }
            return;
        }
        label(g, MapTexts.text("group_name"), x, panelY + 44, w);
        label(g, MapTexts.text("group_icon"), x, panelY + 94, w - 32);
        MapGroupIcons.render(g, icon, x + w - 24, panelY + 88, 24, YzuiTheme.text());
    }
}
