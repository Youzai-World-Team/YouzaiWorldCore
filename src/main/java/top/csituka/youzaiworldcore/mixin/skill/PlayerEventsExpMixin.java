package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 玩家事件 → 冒险经验。
 * 当前仅保留：原版等级升级检测。
 * 进食/睡觉/buff 因 MC 1.21.5 API 变更暂移除。
 */
@Mixin(ServerPlayer.class)
public class PlayerEventsExpMixin {

    @Unique
    private int yzwc$prevExperienceLevel = -1;

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickCheckLevelUp(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        int current = self.experienceLevel;
        if (yzwc$prevExperienceLevel < 0) {
            yzwc$prevExperienceLevel = current;
            return;
        }
        if (current > yzwc$prevExperienceLevel) {
            int gained = current - yzwc$prevExperienceLevel;
            for (int i = 0; i < gained; i++) {
                AdventureLevelManager.grantExp(self, AdventureLevelManager.EXP_VANILLA_LEVEL_UP);
            }
        }
        yzwc$prevExperienceLevel = current;
    }
}
