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
 * 铁砧使用次数与剩余可修次数显示功能（客户端）。
 * <p>
 * 在物品悬浮提示中显示：
 * <ol>
 *   <li>该物品经过铁砧加工的次数（依据 {@code REPAIR_COST} 递推反推）</li>
 *   <li>大约还可维修的次数（直至「过于昂贵」上限 40 级）</li>
 * </ol>
 * <p>
 * 剩余次数估算假设最小材料费 = 1 级经验，递推 {@code cost_{n+1} = cost_n * 2 + 1}，
 * 直至下次维修费用 ≥ 40。
 * <p>
 * 验证依据：反编译 26.2 原版 {@code AnvilMenu.calculateIncreasedRepairCost(I)I} 与
 * {@code COST_FAIL = 40}。
 * <p>
 * 独立重写，参考：Anvil Uses by Z1proW（MIT 许可）
 * https://github.com/Z1proW/Anvil-Uses
 */
public final class AnvilUsesClient {

    public static final String MODULE = "AnvilUses";

    /**
     * 原版铁砧「过于昂贵」上限（{@code COST_FAIL}），取自 26.2 的
     * {@code net.minecraft.world.inventory.AnvilMenu}。
     */
    private static final int COST_FAIL = 40;

    /**
     * 每次维修的最小材料费估算值（经验等级），用于剩余次数递推。
     */
    private static final int MIN_MATERIAL_COST = 1;

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

        DebugLogger.info(MODULE, "铁砧使用次数与剩余可修显示功能已注册 (COST_FAIL=%d)", COST_FAIL);

        initialized = true;
        DebugLogger.exiting(MODULE, "initialize");
    }

    /**
     * 工具提示回调：读取 {@code REPAIR_COST} 并追加使用次数与剩余可修行。
     */
    private static void onItemTooltip(ItemStack item, Item.TooltipContext context, TooltipFlag flag, List<Component> tooltip) {
        DebugLogger.trace(MODULE, "onItemTooltip: item={}", item.getItem());

        int repairCost = item.getOrDefault(DataComponents.REPAIR_COST, 0);

        if (repairCost > 0) {
            // — 铁砧使用次数 —
            // repairCost+1 可能溢出为负数（当 repairCost == Integer.MAX_VALUE 时）
            // log2 处理：Integer.numberOfLeadingZeros(负数) = 0，结果为 31
            int uses = floorLog2(repairCost + 1);
            DebugLogger.debug(MODULE, "repairCost=%d -> uses=%d", repairCost, uses);

            tooltip.add(Component.translatable("youzaiworldcore.anvil_uses", uses));

            // — 剩余可修次数 —
            int remaining = calculateRemainingRepairs(repairCost);
            DebugLogger.debug(MODULE, "repairCost=%d -> remainingRepairs=%d", repairCost, remaining);

            tooltip.add(Component.translatable("youzaiworldcore.anvil_remaining", remaining));
        } else {
            DebugLogger.trace(MODULE, "repairCost==0，跳过");
        }
    }

    /**
     * 估算从当前 {@code repairCost} 状态出发，还可继续维修的次数。
     * <p>
     * 模拟递推：每次维修假设材料费 = {@value #MIN_MATERIAL_COST} 级，
     * 维修后 {@code repairCost} 按原版 {@code calculateIncreasedRepairCost}
     * 规则增长，直到下次维修费用 ≥ {@value #COST_FAIL}（「过于昂贵」上限）。
     */
    static int calculateRemainingRepairs(int repairCost) {
        int c = repairCost;
        int remaining = 0;

        while (true) {
            // 下次维修费用 ≈ 材料费 + 当前惩罚值
            int nextCost = MIN_MATERIAL_COST + c;
            if (nextCost >= COST_FAIL) {
                break;
            }
            remaining++;
            // 维修后 repairCost 按 calculateIncreasedRepairCost 递推
            c = c * 2 + 1;
        }

        return remaining;
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
