package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 方块交互 → 冒险经验。
 * 附魔台 / 铁砧 / 锻造台 / 酿造台 / 信标
 * 注：织布机/砂轮/制图台在 MC 1.21.5 中 API 不兼容，暂跳过。
 */
public class BlockInteractionExpMixin {

    // ─── 附魔台 ───
    @Mixin(EnchantmentMenu.class)
    public static class EnchantExpMixin {
        @Inject(method = "clickMenuButton", at = @At("RETURN"))
        private void onEnchant(Player player, int slot, CallbackInfoReturnable<Boolean> cir) {
            if (cir.getReturnValue() != null && cir.getReturnValue()
                    && player instanceof ServerPlayer sp) {
                AdventureLevelManager.grantExp(sp, AdventureLevelManager.EXP_ENCHANT);
            }
        }
    }

    // ─── 铁砧 ───
    @Mixin(AnvilMenu.class)
    public static class AnvilExpMixin {
        @Inject(method = "onTake", at = @At("TAIL"))
        private void onAnvilTake(Player player, ItemStack itemStack, CallbackInfo ci) {
            if (player instanceof ServerPlayer sp) {
                AdventureLevelManager.grantExp(sp, AdventureLevelManager.EXP_ANVIL);
            }
        }
    }

    // ─── 锻造台 ───
    @Mixin(SmithingMenu.class)
    public static class SmithingExpMixin {
        @Inject(method = "onTake", at = @At("TAIL"))
        private void onSmithingTake(Player player, ItemStack itemStack, CallbackInfo ci) {
            if (player instanceof ServerPlayer sp) {
                AdventureLevelManager.grantExp(sp, AdventureLevelManager.EXP_SMITHING);
            }
        }
    }

    // ─── 酿造台 ───
    @Mixin(BrewingStandMenu.class)
    public static class BrewingExpMixin {
        @Inject(method = "clickMenuButton", at = @At("RETURN"))
        private void onBrew(Player player, int slot, CallbackInfoReturnable<Boolean> cir) {
            if (cir.getReturnValue() != null && cir.getReturnValue()
                    && player instanceof ServerPlayer sp) {
                AdventureLevelManager.grantExp(sp, AdventureLevelManager.EXP_BREWING);
            }
        }
    }

    // ─── 信标激活（通过检测玩家获得信标 buff） ───
    @Mixin(ServerPlayer.class)
    public static class BeaconPlayerMixin {
        @Unique
        private boolean yzwc$beaconChecked = false;

        @Inject(method = "tick", at = @At("TAIL"))
        private void onTickBeaconCheck(CallbackInfo ci) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            boolean hasBeaconEffect = false;
            for (var effect : self.getActiveEffects()) {
                if (effect.getDuration() > 200 && effect.getAmplifier() >= 0
                        && effect.isVisible()) {
                    hasBeaconEffect = true;
                    break;
                }
            }
            if (hasBeaconEffect && !yzwc$beaconChecked) {
                yzwc$beaconChecked = true;
                AdventureLevelManager.checkFirstBeacon(self);
            }
            if (!hasBeaconEffect) {
                yzwc$beaconChecked = false;
            }
        }
    }
}
