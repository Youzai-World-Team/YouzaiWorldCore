package top.csituka.youzaiworldcore.client.screen.map;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.map.MapPersonalData;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.map.MapWaypointGroup;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 所有路径点编辑入口共用分页分组选择页，取消不会改变草稿。 */
final class MapGroupSelectScreen extends MapScreen {
    private final String selected;
    private final Consumer<MapWaypointGroup> choose;
    private final String world = MapPersonalData.worldId();
    private List<MapWaypointGroup> groups = List.of();
    private int page, capacity;

    MapGroupSelectScreen(Screen parent, MapWaypointGroup selected, Consumer<MapWaypointGroup> choose) {
        super(parent, "choose_group"); this.selected = selected.id(); this.choose = choose;
    }
    @Override protected void init() {
        super.init(); groups = MapPersonalData.assignableGroups();
        capacity = Math.max(1, (panelHeight - 112) / 28);
        page = Math.clamp(page, 0, maxPage());
        int x = panelX + 12, y = panelY + 42, w = panelWidth - 24;
        for (int i = page * capacity; i < Math.min(groups.size(), (page + 1) * capacity); i++) {
            var group = groups.get(i);
            addRenderableWidget(new MapGroupButton(x, y + (i - page * capacity) * 28, w, group,
                    () -> selected.equals(group.id()), () -> {
                        if (!world.equals(MapPersonalData.worldId())) { error = MapTexts.text("invalid_group"); return; }
                        choose.accept(group);
                        DebugLogger.debug("MapWaypoints", "选择路径点分组：%s", group.id()); onClose();
                    }));
        }
        int footer = panelY + panelHeight - 62;
        button(x, footer, 30, Component.literal("‹"), () -> { page--; init(); }).active = page > 0;
        button(x + w - 30, footer, 30, Component.literal("›"), () -> { page++; init(); }).active = page < maxPage();
        button(x, footer + 28, w, MapTexts.text("cancel"), this::onClose);
    }
    private int maxPage() { return Math.max(0, (groups.size() - 1) / capacity); }
    @Override protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        label(g, MapTexts.text("page", page + 1, maxPage() + 1), panelX + 52, panelY + panelHeight - 55, panelWidth - 104);
    }
}
