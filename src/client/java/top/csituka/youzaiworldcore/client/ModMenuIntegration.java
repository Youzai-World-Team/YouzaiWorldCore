package top.csituka.youzaiworldcore.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen;

/**
 * ModMenu 集成：注册模组设置页面工厂。
 * <p>
 * 当用户在 ModMenu 模组列表中点击 YouzaiWorldCore 的「设置」按钮时，
 * 会通过此工厂创建 {@link YouzaiWorldCoreSettingsScreen} 实例。
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return YouzaiWorldCoreSettingsScreen::new;
    }
}
