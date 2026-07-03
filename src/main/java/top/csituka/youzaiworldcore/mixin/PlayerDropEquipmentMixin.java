package top.csituka.youzaiworldcore.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.item.ModItems;

/**
 * 混合注入：拦截 {@link Player#dropEquipment(ServerLevel)}，
 * 当玩家背包中有「守护之心」时阻止物品掉落，
 * 由 {@link ServerPlayerDeathMixin} 处理后续的心消耗与提示。
 *
 * <p>原本由数据包 {@code keepInventory} 游戏规则 + 守护之心标记逻辑实现，
 * 现改为 Mixin 方式精确控制。</p>
 */
@Mixin(Player.class)
public class PlayerDropEquipmentMixin {

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onDropEquipment(ServerLevel level, CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // 仅在服务端处理
        if (level.isClientSide()) {
            return;
        }

        // 检查玩家背包中是否有守护之心
        if (hasHeartInInventory(player)) {
            // 有守护之心 → 阻止物品掉落
            ci.cancel();
        }
        // 没有守护之心 → 允许正常掉落（由原版逻辑处理）
    }

    /**
     * 遍历玩家背包（含快捷栏、主背包、盔甲栏、副手），
     * 检查是否有至少一个 {@link HeartOfGuardianshipItem}。
     */
    private static boolean hasHeartInInventory(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                return true;
            }
        }
        return false;
    }
}
