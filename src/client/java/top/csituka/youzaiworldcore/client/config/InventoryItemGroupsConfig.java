package top.csituka.youzaiworldcore.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import top.csituka.youzaiworldcore.client.inventory.InventoryItemGroupDefaults;
import top.csituka.youzaiworldcore.client.inventory.InventoryItemGroupRule;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * 创造物品分组的纯客户端配置。
 * <p>文件为 {@code yzwc/client/global_settings.json}，分节为 {@code inventory_item_groups_module}；
 * 不涉及服务端权限、玩家背包数据或额外配置库。</p>
 */
@SuppressWarnings("null")
public final class InventoryItemGroupsConfig {

    private static final String MODULE = "InventoryItemGroupsConfig";
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_SHOW_ITEMS_IN_GROUP = false;
    public static final Sort DEFAULT_SORT = Sort.DEFAULT;

    /** 组内排列方式；同 ID 的不同组件变体始终保持原有先后次序。 */
    public enum Sort {
        DEFAULT, ALPHABETICAL
    }

    private static boolean enabled = DEFAULT_ENABLED;
    private static boolean showItemsInGroup = DEFAULT_SHOW_ITEMS_IN_GROUP;
    private static Sort sort = DEFAULT_SORT;
    private static List<InventoryItemGroupRule> groups = InventoryItemGroupDefaults.create();

    private InventoryItemGroupsConfig() {
    }

    /** 是否启用创造物品分组，独立于 YZUI 总开关。 */
    public static boolean isEnabled() {
        return enabled;
    }

    /** 是否每秒轮换分组图标所显示的物品。 */
    public static boolean showItemsInGroup() {
        return showItemsInGroup;
    }

    /** 当前组内排序方式。 */
    public static Sort getSort() {
        return sort;
    }

    /** 按优先级排列的不可变规则列表；前面的有效分组优先认领物品。 */
    public static List<InventoryItemGroupRule> getGroups() {
        return groups;
    }

    /** 从统一客户端配置读取；非法规则沿用隔离、重建默认值并退出的流程。 */
    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.INVENTORY_ITEM_GROUPS_MODULE);
        if (section.isEmpty()) {
            writeDefaults();
        } else {
            enabled = section.getBoolean("enabled", DEFAULT_ENABLED);
            showItemsInGroup = section.getBoolean("show_items_in_group", DEFAULT_SHOW_ITEMS_IN_GROUP);
            sort = section.getEnum("sort", DEFAULT_SORT, Sort.class);
            List<ConfigSection> entries = section.getObjectList("groups");
            List<InventoryItemGroupRule> loaded = new ArrayList<>();
            if (entries == null) {
                loaded.addAll(InventoryItemGroupDefaults.create());
            } else {
                for (ConfigSection entry : entries) {
                    InventoryItemGroupRule rule = new InventoryItemGroupRule(
                            entry.getString("group_name", ""), entry.getString("tab_id", ""),
                            entry.getStringList("equivalent_items", List.of()),
                            entry.getStringList("contained_items", List.of()),
                            entry.getStringList("non_contained_items", List.of()));
                    String invalid = rule.invalidField();
                    if (invalid != null) {
                        entry.fail(invalid, switch (invalid) {
                            case "group_name" -> "分组名称不能为空";
                            case "tab_id" -> "标签页 ID 必须使用合法的命名空间:路径格式";
                            case "equivalent_items" -> "精确物品 ID 必须使用合法的命名空间:路径格式";
                            case "contained_items" -> "必须至少提供一个精确物品 ID 或包含片段，片段不能为空";
                            default -> "排除片段不能为空字符串；不排除物品时请使用空数组";
                        });
                    }
                    loaded.add(rule);
                }
            }
            groups = List.copyOf(loaded);
        }
        DebugLogger.info(MODULE, "已加载创造物品分组：启用=%s，规则=%d，排序=%s，轮播=%s",
                enabled, groups.size(), sort, showItemsInGroup);
        DebugLogger.exiting(MODULE, "load");
    }

    /** 确认设置页面的草稿，校验完成后一次性保存。 */
    public static void apply(boolean newEnabled, Sort newSort, boolean newShowItems,
                             List<InventoryItemGroupRule> newGroups) {
        List<InventoryItemGroupRule> checked = List.copyOf(newGroups);
        for (InventoryItemGroupRule rule : checked) {
            if (rule.invalidField() != null) {
                throw new IllegalArgumentException("非法物品分组：" + rule.groupName() + "." + rule.invalidField());
            }
        }
        enabled = newEnabled;
        sort = java.util.Objects.requireNonNull(newSort);
        showItemsInGroup = newShowItems;
        groups = checked;
        save();
        DebugLogger.info(MODULE, "已保存创造物品分组设置，共 %d 条规则", groups.size());
    }

    /** 重置全部字段为默认值，供首次启动和坏配置恢复使用。 */
    public static void writeDefaults() {
        enabled = DEFAULT_ENABLED;
        sort = DEFAULT_SORT;
        showItemsInGroup = DEFAULT_SHOW_ITEMS_IN_GROUP;
        groups = InventoryItemGroupDefaults.create();
        save();
    }

    /** 保存到统一客户端配置，不创建额外的配置文件。 */
    public static void save() {
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.INVENTORY_ITEM_GROUPS_MODULE);
        section.set("enabled", enabled);
        section.set("sort", sort);
        section.set("show_items_in_group", showItemsInGroup);
        JsonArray array = new JsonArray();
        for (InventoryItemGroupRule rule : groups) {
            JsonObject object = new JsonObject();
            object.addProperty("group_name", rule.groupName());
            object.addProperty("tab_id", rule.tabId());
            object.add("equivalent_items", strings(rule.equivalentItems()));
            object.add("contained_items", strings(rule.containedItems()));
            object.add("non_contained_items", strings(rule.nonContainedItems()));
            array.add(object);
        }
        section.set("groups", array);
        ClientGlobalSettings.save();
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
