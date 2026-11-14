package top.csituka.youzaiworldcore.client.screen.map;

import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.MapSettings;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.screen.options.YzuiMapSettingsScreen;

/** 保留地图内部入口与返回链，分类内容复用设置中心的直接选择控件。 */
final class MapSettingsCategoryScreen extends YzuiMapSettingsScreen {
    enum Category { APPEARANCE, INFORMATION, MOBS, TERRAIN, NAVIGATION }

    MapSettingsCategoryScreen(Screen parent, Category category) { super(parent, category.name()); }

    static Component markerDisplayLabel() {
        return MapTexts.text("marker_display." + MapSettings.markerDisplay().name().toLowerCase(Locale.ROOT));
    }
}
