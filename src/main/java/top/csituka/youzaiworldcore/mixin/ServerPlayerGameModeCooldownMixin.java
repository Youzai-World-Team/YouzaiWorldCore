package top.csituka.youzaiworldcore.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.item.tool.TeleportStoneItem;

/**
 * 混合注入：传送石处于冷却中被右键时，向玩家发送剩余冷却时间的动作栏提示。
 * <p>
 * {@link ServerPlayerGameMode#useItem} 在最开头就会因为物品冷却直接返回
 * {@code PASS}，{@code Item#use} 根本不会被调用，因此提示文本没办法写在物品本身里。
 * 这里在方法 HEAD 处旁路观察一次：只对传送石且确实在冷却中的情况发提示，不改变任何原版逻辑。
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeCooldownMixin {

    @Inject(method = "useItem", at = @At("HEAD"))
    private void youzaiworldcore$notifyTeleportStoneCooldown(ServerPlayer player, Level level,
                                                             ItemStack stack, InteractionHand hand,
                                                             CallbackInfoReturnable<InteractionResult> cir) {
        if (!(stack.getItem() instanceof TeleportStoneItem)) {
            return;
        }
        if (!player.getCooldowns().isOnCooldown(stack)) {
            return;
        }
        TeleportStoneItem.sendCooldownMessage(player, stack);
    }
}
