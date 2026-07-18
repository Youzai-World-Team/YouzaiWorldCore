package top.csituka.youzaiworldcore.highlightitem;

/**
 * 选项枚举通用接口 —— 同时提供有序 id 与本地化键，供 {@link ItemComparator} 与
 * {@link Configurator.NotificationPreference} 复用。
 */
public interface OptionEnum {
    int getId();

    String getKey();
}
