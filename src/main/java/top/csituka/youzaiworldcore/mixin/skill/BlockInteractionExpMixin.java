package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 方块交互 → 冒险经验。
 * 当前仅保留：信标激活检测。
 * 附魔台/铁砧/锻造台/酿造台因 MC 1.21.5 菜单 API 变更暂移除。
 */
public class BlockInteractionExpMixin {

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
