package top.csituka.youzaiworldcore.client.config;

/**
 * 可独立调整位置的 YZHUD 组件。
 */
public enum YzHudComponent {
    INVENTORY("inventory"),
    ARMOR("armor"),
    EFFECTS("effects"),
    SCOREBOARD("scoreboard");

    private final String configPrefix;

    YzHudComponent(String configPrefix) {
        this.configPrefix = configPrefix;
    }

    /** @return 配置键和语言键使用的组件前缀 */
    public String configPrefix() {
        return configPrefix;
    }
}
