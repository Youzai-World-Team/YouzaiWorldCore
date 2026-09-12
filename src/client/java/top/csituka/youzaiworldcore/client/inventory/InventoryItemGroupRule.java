package top.csituka.youzaiworldcore.client.inventory;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * 创造物品分组规则；字段保存在客户端全局配置的 {@code inventory_item_groups_module.groups} 中。
 * <p>参考 Inventory Item Groups 的精确 ID、包含片段和排除片段配置，排除条件优先。</p>
 */
@SuppressWarnings("null")
public record InventoryItemGroupRule(String groupName, String tabId, List<String> equivalentItems,
                                     List<String> containedItems, List<String> nonContainedItems) {

    public InventoryItemGroupRule {
        equivalentItems = List.copyOf(equivalentItems);
        containedItems = List.copyOf(containedItems);
        nonContainedItems = List.copyOf(nonContainedItems);
    }

    /** 根据完整注册 ID 匹配；多个包含条件取或，任意排除条件命中即排除。 */
    public boolean matches(String itemId) {
        return nonContainedItems.stream().noneMatch(itemId::contains)
                && (equivalentItems.contains(itemId) || containedItems.stream().anyMatch(itemId::contains));
    }

    /** 优先读取资源包中的组名翻译，没有翻译时显示用户填写的名称。 */
    public Component displayName() {
        String key = "group.youzaiworldcore.inventory_item_groups." + groupName;
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(groupName);
    }

    /**
     * 返回第一个非法字段名；null 表示有效。未知模组的合法 ID 允许提前配置。
     * <p>界面据此显示本地化错误，配置加载据此交给 ConfigSection 隔离坏文件。</p>
     */
    public String invalidField() {
        if (groupName.isBlank()) {
            return "group_name";
        }
        if (!validId(tabId)) {
            return "tab_id";
        }
        if (equivalentItems.stream().anyMatch(id -> !validId(id))) {
            return "equivalent_items";
        }
        if ((equivalentItems.isEmpty() && containedItems.isEmpty())
                || containedItems.stream().anyMatch(String::isBlank)) {
            return "contained_items";
        }
        if (nonContainedItems.stream().anyMatch(String::isBlank)) {
            return "non_contained_items";
        }
        return null;
    }

    private static boolean validId(String value) {
        int separator = value.indexOf(':');
        return separator > 0 && separator < value.length() - 1 && Identifier.tryParse(value) != null;
    }
}
