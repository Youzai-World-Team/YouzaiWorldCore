package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.network.chat.Component;

/** Core 设置的共用分类顺序与名称，供设置中心和 ModMenu 独立页面使用。 */
public enum CoreSettingsSection {
    APPEARANCE(0, "appearance"),
    HUD(4, "hud"),
    MAP(5, "map"),
    CONFIG_IO(1, "config_io"),
    ABOUT(2, "about"),
    DEVELOPER(3, "developer");

    private final int id;
    private final String translationSuffix;

    CoreSettingsSection(int id, String translationSuffix) {
        this.id = id;
        this.translationSuffix = translationSuffix;
    }

    /** 保留已有四个分类的标识，导航顺序由枚举顺序决定。 */
    public int id() { return id; }

    /** 在当前游戏语言下读取分类名称，不缓存翻译结果。 */
    public Component title() {
        return Component.translatable("screen.youzaiworldcore.settings.sidebar_" + translationSuffix);
    }

    /** 将页面路由使用的分类标识还原为分类，未知标识回到界面外观。 */
    public static CoreSettingsSection fromId(int id) {
        for (CoreSettingsSection section : values()) {
            if (section.id == id) return section;
        }
        return APPEARANCE;
    }
}
