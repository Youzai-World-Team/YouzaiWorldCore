package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.client.cosmetic.CosmeticClientManager;

/** 将客户端缓存的自定义皮肤与披风合并到玩家渲染皮肤。 */
@SuppressWarnings("null")
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoSkinMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void youzaiworldcore$applyCosmeticSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerInfo self = (PlayerInfo) (Object) this;
        cir.setReturnValue(CosmeticClientManager.apply(self.getProfile().id(), cir.getReturnValue()));
    }
}
