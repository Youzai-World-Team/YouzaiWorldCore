package top.csituka.youzaiworldcore.mixin.compat;

import eu.pb4.trinkets.api.TrinketInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.compat.SophisticatedBackpacksCompat;

import java.util.Set;

/** 保留精妙背包原生注册和 tick，仅统一它的饰品来源与槽位读取；旧版缺少目标时由 Pseudo 跳过。 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.compat.trinkets.TrinketsCompat", remap = false)
public abstract class SophisticatedBackpackTrinketsMixin {
    @Inject(method = "getTrinketInventory", at = @At("HEAD"), cancellable = true)
    private static void youzaiworldcore$safeInventory(Player player, String slotId,
            CallbackInfoReturnable<TrinketInventory> cir) {
        cir.setReturnValue(SophisticatedBackpacksCompat.getTrinketInventory(player, slotId));
    }

    @Inject(method = "getBackpackTrinketSlots", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$playerInventories(Player player, long gameTime,
            CallbackInfoReturnable<Set<String>> cir) {
        cir.setReturnValue(SophisticatedBackpacksCompat.getTrinketInventoryIds(player));
    }

    // 按调用点匹配，不依赖编译器生成的 lambda$init$2 名称。
    @Redirect(method = "*", at = @At(value = "INVOKE",
            target = "Leu/pb4/trinkets/api/TrinketInventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack youzaiworldcore$readTrinketStack(TrinketInventory inventory, int slot) {
        return SophisticatedBackpacksCompat.getTrinketStack(inventory, slot);
    }
}
