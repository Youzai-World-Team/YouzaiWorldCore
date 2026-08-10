package top.csituka.youzaiworldcore.highlightitem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.function.BiPredicate;

/**
 * 物品相似度比较器（参考 HighLightItem，适配 26.2 组件 API）。
 * <p>
 * 提供 7 种比较模式，决定“与悬停物品相似”的判定规则。
 */
public class ItemComparator {

    public static boolean test(Comparators comparator, ItemStack stack, ItemStack stack2) {
        // 先判等级再取参数：本方法在容器界面里是「每槽位、每帧」调用
        // （创造物品栏一屏 100+ 槽位），而两次 getDescriptionId() 与 varargs
        // 数组即使日志关闭也会照常求值。
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace("HighlightItem", "比较物品: mode=%s, a=%s, b=%s",
                    comparator.name(), stack.getItem().getDescriptionId(), stack2.getItem().getDescriptionId());
        }
        return comparator.predicate.test(stack, stack2);
    }

    public enum Comparators implements OptionEnum {
        ITEM_ONLY((stack, stack2) -> stack.getItem().equals(stack2.getItem())),
        ITEM_AND_AMOUNT(((BiPredicate<ItemStack, ItemStack>) (stack, stack2) -> stack.getCount() == stack2.getCount()).and(ITEM_ONLY.predicate)),
        ITEM_AND_NBT(ITEM_ONLY.predicate.and((stack, stack2) ->
                (stack.getComponents() == null && stack2.getComponents() == null)
                        || (stack.getComponents() != null && stack2.getComponents() != null
                        && stack.getComponents().equals(stack2.getComponents())))),
        ITEM_AND_NBT_AND_AMOUNT(ITEM_AND_AMOUNT.predicate.and((stack, stack2) ->
                (stack.getComponents() == null && stack2.getComponents() == null)
                        || (stack.getComponents() != null && stack2.getComponents() != null
                        && stack.getComponents().equals(stack2.getComponents())))),
        NAME_ONLY((stack, stack2) -> stack.getHoverName().equals(stack2.getHoverName())),
        NAME_AND_AMOUNT(((BiPredicate<ItemStack, ItemStack>) (stack, stack2) -> stack.getCount() == stack2.getCount()).and(NAME_ONLY.predicate)),
        NAMESPACE((stack, stack2) -> {
            var key1 = BuiltInRegistries.ITEM.getKey(stack.getItem());
            var key2 = BuiltInRegistries.ITEM.getKey(stack2.getItem());
            return key1 != null && key2 != null
                    && key1.getNamespace().equalsIgnoreCase(key2.getNamespace());
        });

        final BiPredicate<ItemStack, ItemStack> predicate;

        Comparators(BiPredicate<ItemStack, ItemStack> predicate) {
            this.predicate = predicate;
        }

        public String translationKey() {
            return "youzaiworldcore.highlight.comparator." + this.name().toLowerCase();
        }

        @Override
        public int getId() {
            return this.ordinal();
        }

        @Override
        public String getKey() {
            return translationKey();
        }
    }
}
