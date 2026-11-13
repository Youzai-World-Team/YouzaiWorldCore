package top.csituka.youzaiworldcore.mixin.client.compat;

import eu.pb4.trinkets.api.TrinketInventory;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.lang.reflect.Constructor;
import java.util.Optional;

/** 补齐精妙背包在原版饰品槽上悬停并按打开快捷键时的库存识别。 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.client.KeybindHandler", remap = false)
public abstract class SophisticatedBackpackKeybindMixin {
    @Unique private static Constructor<?> youzaiworldcore$inventoryReturn;

    @Inject(method = "isTrinket", at = @At("HEAD"), cancellable = true, require = 0)
    private static void youzaiworldcore$recognizeTrinket(Container container, CallbackInfoReturnable<Boolean> cir) {
        if (container instanceof TrinketInventory) cir.setReturnValue(true);
    }

    @Inject(method = "getPlayerInventory", at = @At("HEAD"), cancellable = true, require = 0)
    private static void youzaiworldcore$trinketInventory(Slot slot, CallbackInfoReturnable<Optional<?>> cir) {
        if (!(slot.container instanceof TrinketInventory inventory)) return;
        try {
            if (youzaiworldcore$inventoryReturn == null) {
                Class<?> result = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.client.KeybindHandler$PlayerInventoryReturn");
                youzaiworldcore$inventoryReturn = result.getDeclaredConstructor(String.class, String.class);
                youzaiworldcore$inventoryReturn.setAccessible(true);
            }
            cir.setReturnValue(Optional.of(youzaiworldcore$inventoryReturn.newInstance(inventory.slotType().getId(), "trinkets")));
        } catch (ReflectiveOperationException | LinkageError error) {
            // 不能把饰品槽编号当作主物品栏编号，避免错误打开另一只背包。
            cir.setReturnValue(Optional.empty());
            DebugLogger.exception("SophisticatedBackpackKeybindMixin", "trinketInventory", error);
        }
    }
}
