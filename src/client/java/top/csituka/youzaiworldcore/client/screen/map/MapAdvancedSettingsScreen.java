package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapTexts;

/** 高级设置仅提供地形诊断、导航参数与恢复默认，不重复罗列日常开关。 */
public final class MapAdvancedSettingsScreen extends MapScreen {
    public MapAdvancedSettingsScreen(Screen parent) { super(parent, "more_settings"); }
    @Override protected void init() {
        super.init(); int x = panelX + 12, width = panelWidth - 24;
        button(x, panelY + 42, width, MapTexts.text("map_terrain"), () -> open(MapSettingsCategoryScreen.Category.TERRAIN));
        button(x, panelY + 70, width, MapTexts.text("map_navigation"), () -> open(MapSettingsCategoryScreen.Category.NAVIGATION));
        int half = (width - 6) / 2, footer = panelY + panelHeight - 34;
        button(x, footer, half, MapTexts.text("reset_display"), () -> { MapSettings.writeDefaults(); init(); });
        button(x + half + 6, footer, half, MapTexts.text("done"), this::onClose);
    }
    private void open(MapSettingsCategoryScreen.Category category) {
        Minecraft.getInstance().gui.setScreen(new MapSettingsCategoryScreen(this, category));
    }
}
