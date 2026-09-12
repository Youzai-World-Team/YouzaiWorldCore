package top.csituka.youzaiworldcore.client.inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.config.InventoryItemGroupsConfig;
import top.csituka.youzaiworldcore.item.ModCreativeModeTabs;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 原版与 YZUI 共用的创造物品分组模型，每个屏幕持有独立实例。
 * <p>功能参考 Inventory Item Groups（Bizarre Cube，MIT），运行逻辑原生重写。
 * 用源列表位置识别物品变体，以显式显示条目区分组头和可取物品；不修改物品组件或注册表内容。</p>
 */
@SuppressWarnings("null")
public final class CreativeItemGroups {

    private static final String MODULE = "CreativeItemGroups";
    private static final int MIN_GROUP_SIZE = 3;
    private static final long ANIMATION_NANOS = 200_000_000L;

    private CreativeModeTab tab;
    private List<ItemStack> source = List.of();
    private Group[] owners = new Group[0];
    private final List<Group> groups = new ArrayList<>();
    private List<Entry> entries = List.of();
    private List<ItemStack> items = List.of();

    /** 一个显示格子；header 为 true 时仅为展开/收起按钮，不能取物。 */
    public record Entry(ItemStack stack, Group group, boolean header) {
        /** 组头和正在收起的成员不能取物，避免过渡画面触发复制或丢弃。 */
        public boolean canTake() {
            return !header && (group == null || group.expanded);
        }
    }

    /** 当前分类中的一个分组，保留全部 ItemStack 组件变体及其展开状态。 */
    public static final class Group {
        private final InventoryItemGroupRule rule;
        private final List<ItemStack> members;
        private final int firstIndex;
        private boolean expanded;
        private boolean membersVisible;
        private float animationFrom;
        private long animationStartedAt;
        private long animationDuration;

        private Group(InventoryItemGroupRule rule, List<ItemStack> members, int firstIndex, Group previous) {
            this.rule = rule;
            this.members = List.copyOf(members);
            this.firstIndex = firstIndex;
            if (previous != null) {
                expanded = previous.expanded;
                membersVisible = previous.membersVisible;
                animationFrom = previous.animationFrom;
                animationStartedAt = previous.animationStartedAt;
                animationDuration = previous.animationDuration;
            }
        }

        /** 组名，支持资源包翻译。 */
        public Component name() {
            return rule.displayName();
        }

        /** 不可变的组内物品列表，调用方取物时仍应复制 ItemStack。 */
        public List<ItemStack> members() {
            return members;
        }

        /** 当前目标是否为展开状态。 */
        public boolean expanded() {
            return expanded;
        }

        /** 当前展开比例，使用单调时钟推进；刷新率不会改变动画时长。 */
        public float openness() {
            return openness(System.nanoTime());
        }

        private float openness(long now) {
            float target = expanded ? 1.0F : 0.0F;
            if (animationDuration == 0) {
                return target;
            }
            float time = Math.clamp((float) (now - animationStartedAt) / animationDuration, 0.0F, 1.0F);
            float remaining = 1.0F - time;
            float eased = 1.0F - remaining * remaining * remaining;
            return animationFrom + (target - animationFrom) * eased;
        }

        private void toggle(long now) {
            // 从当前画面反向过渡，快速连点不会跳回完全展开或完全收起的状态。
            animationFrom = openness(now);
            expanded = !expanded;
            animationStartedAt = now;
            animationDuration = (long) (ANIMATION_NANOS * Math.abs((expanded ? 1.0F : 0.0F) - animationFrom));
            membersVisible = expanded || animationDuration > 0;
        }
    }

    /** 清除屏幕状态；搜索、库存和快捷栏预设页不保留分组命中信息。 */
    public void clear() {
        tab = null;
        source = List.of();
        owners = new Group[0];
        groups.clear();
        entries = List.of();
        items = List.of();
    }

    /**
     * 从分类的原始展示列表构建分组，同一分类刷新时保留已有展开状态与动画进度。
     * <p>只有至少匹配三个未认领物品的规则才形成分组；无效的小组不会抢占后续规则的物品。</p>
     * <p>悠哉世界和兼容模组在自定义规则之后应用内置分类，不改写玩家保存的规则列表。</p>
     */
    public void rebuild(CreativeModeTab selectedTab, Collection<ItemStack> original) {
        Map<InventoryItemGroupRule, Group> previousGroups = new HashMap<>();
        if (tab == selectedTab) {
            for (Group group : groups) {
                previousGroups.put(group.rule, group);
            }
        }
        clear();
        tab = selectedTab;
        source = List.copyOf(original);
        owners = new Group[source.size()];
        if (InventoryItemGroupsConfig.isEnabled() && tab != null
                && tab.getType() == CreativeModeTab.Type.CATEGORY) {
            String tabId = String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
            List<String> itemIds = source.stream()
                    .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).toList();
            List<InventoryItemGroupRule> rules = new ArrayList<>(InventoryItemGroupsConfig.getGroups());
            rules.addAll(InventoryItemGroupDefaults.getBuiltInRules(tabId));
            if (tab == ModCreativeModeTabs.YOUZAI_WORLD) {
                // 与标签页共用一份物品分类；已有配置也能直接显示新增的七类分组。
                for (ModCreativeModeTabs.ItemGroup group : ModCreativeModeTabs.getItemGroups()) {
                    rules.add(new InventoryItemGroupRule(group.groupName(), tabId,
                            group.itemIds(), List.of(), List.of()));
                }
            }
            for (InventoryItemGroupRule rule : rules) {
                if (!rule.tabId().equals(tabId)) {
                    continue;
                }
                List<Integer> matches = new ArrayList<>();
                for (int i = 0; i < source.size(); i++) {
                    if (owners[i] == null && !source.get(i).isEmpty() && rule.matches(itemIds.get(i))) {
                        matches.add(i);
                    }
                }
                if (matches.size() < MIN_GROUP_SIZE) {
                    continue;
                }
                List<ItemStack> members = new ArrayList<>(matches.size());
                for (int index : matches) {
                    members.add(source.get(index));
                }
                if (InventoryItemGroupsConfig.getSort() == InventoryItemGroupsConfig.Sort.ALPHABETICAL) {
                    members.sort(Comparator.comparing(stack ->
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
                }
                Group group = new Group(rule, members, matches.getFirst(), previousGroups.get(rule));
                groups.add(group);
                for (int index : matches) {
                    owners[index] = group;
                }
            }
            DebugLogger.debug(MODULE, "分类 %s：%d 个物品变体，%d 个分组", tabId, source.size(), groups.size());
        }
        rebuildEntries();
    }

    /** 当前格子的类型和归属；越界返回 null，不反查物品 ID 或对象相等性。 */
    public Entry entry(int index) {
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    /** 当前展示列表，与 entry(index) 一一对应；组头只是代表物品的只读引用。 */
    public List<ItemStack> items() {
        return items;
    }

    /**
     * 在绘制前结束到期的过渡；只有收起完成时才移除成员并返回 true，通知界面同步列表与滚动。
     * <p>收起期间保留显示位置，避免尚未消失的图标与后续格子的取物结果错位。</p>
     */
    public boolean advanceAnimations() {
        long now = System.nanoTime();
        boolean changed = false;
        for (Group group : groups) {
            if (group.animationDuration > 0 && now - group.animationStartedAt >= group.animationDuration) {
                group.animationDuration = 0;
                if (!group.expanded && group.membersVisible) {
                    group.membersVisible = false;
                    changed = true;
                }
            }
        }
        if (changed) {
            rebuildEntries();
            DebugLogger.debug(MODULE, "收起动画完成，创造列表更新为 %d 个格子", entries.size());
        }
        return changed;
    }

    /** 切换指定组头并重建显示列表；不会读取或修改玩家光标、背包或服务端状态。 */
    public boolean toggle(int index) {
        Entry entry = entry(index);
        if (entry == null || !entry.header()) {
            return false;
        }
        Group group = entry.group();
        boolean old = group.expanded;
        group.toggle(System.nanoTime());
        rebuildEntries();
        DebugLogger.stateChange(MODULE, group.rule.groupName(), "expanded", old, group.expanded);
        return true;
    }

    private void rebuildEntries() {
        List<Entry> rebuilt = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            Group group = owners[i];
            if (group == null) {
                rebuilt.add(new Entry(source.get(i), null, false));
            } else if (i == group.firstIndex) {
                rebuilt.add(new Entry(group.members.getFirst(), group, true));
                if (group.membersVisible) {
                    for (ItemStack member : group.members) {
                        rebuilt.add(new Entry(member, group, false));
                    }
                }
            }
        }
        entries = List.copyOf(rebuilt);
        items = entries.stream().map(Entry::stack).toList();
    }
}
