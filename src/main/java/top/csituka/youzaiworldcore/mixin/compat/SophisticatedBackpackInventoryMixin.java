package top.csituka.youzaiworldcore.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.compat.SophisticatedBackpacksCompat;

import java.util.Optional;

/** 可选模组库存桥接：未安装精妙背包时由 Pseudo 跳过，不引用第三方类。 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider", remap = false)
public abstract class SophisticatedBackpackInventoryMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void youzaiworldcore$registerTrinkets(CallbackInfo ci) {
        SophisticatedBackpacksCompat.registerProvider(this);
    }

    @WrapMethod(method = "getBackpackFromRendered")
    private Optional<?> youzaiworldcore$respectVisibility(Player player, Operation<Optional<?>> original) {
        return SophisticatedBackpacksCompat.withRenderVisibility(() -> original.call(player));
    }
}
