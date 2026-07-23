package top.csituka.youzaiworldcore.anviluses;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.List;

/**
 * 铁砧使用次数显示功能（客户端）。
 * <p>
 * 在物品悬浮提示中显示该物品经过铁砧加工的次数，依据 {@code DataComponents.REPAIR_COST}
 * 的递推关系 {@code cost(n) = cost(n-1) * 2 + 1} 反推：{@code uses = log2(repairCost + 1)}。
 * <p>
 * 验证依据：反编译 26.2 原版 {@code AnvilMenu.calculateIncreasedRepairCost(I)I}
 * 为该递推公式的原版实现，证明该算法对单链加工场景严格正确。
 * <p>
 * 独立重写，参考：Anvil Uses by Z1proW（MIT 许可）
 * https://github.com/Z1proW/Anvil-Uses
 */
public final class AnvilUsesClient {

    public static final String MODULE = "AnvilUses";

    private static volatile boolean initialized = false;

    private AnvilUsesClient() {
    }

    /**
     * 初始化：注册物品悬浮提示回调。
     * <p>
     * 该回调仅在客户端渲染提示时触发，无需服务端参与。
     */
    public static void initialize() {
        if (initialized) {
            return;
        }
        DebugLogger.entering(MODULE, "initialize");

        ItemTooltipCallback.EVENT.register(AnvilUsesClient::onItemTooltip);

        DebugLogger.info(MODULE, "铁砧使用次数显示功能已注册");

        initialized = true;
        DebugLogger.exiting(MODULE, "initialize");
    }

    /**
     * 工具提示回调：读取 {@code REPAIR_COST} 并追加使用次数显示。
     */
    private static void onItemTooltip(ItemStack item, Item.TooltipContext context, TooltipFlag flag, List<Component> tooltip) {
        DebugLogger.trace(MODULE, "onItemTooltip: item={}", item.getItem());

        int repairCost = item.getOrDefault(DataComponents.REPAIR_COST, 0);

        if (repairCost == 0) {
            DebugLogger.trace(MODULE, "repairCost==0，跳过");
            return;
        }

        // repairCost+1 可能溢出为负数（当 repairCost == Integer.MAX_VALUE 时）
        // log2 处理：Integer.numberOfLeadingZeros(负数) = 0，结果为 31
        int uses = floorLog2(repairCost + 1);

        DebugLogger.debug(MODULE, "repairCost=%d -> uses=%d (item=%s)", repairCost, uses, item.getItem());

        tooltip.add(Component.translatable("youzaiworldcore.anvil_uses", uses));
    }

    /**
     * 计算 {@code floor(log2(n))} 的位运算实现。
     * <p>
     * 对正整数 {@code n} 返回 {@code floor(log2(n))}；对零或负数返回 31（与
     * {@code 31 - Integer.numberOfLeadingZeros(n)} 等价）。
     */
    private static int floorLog2(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }
}
