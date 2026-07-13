package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 完成进度（成就）→ +50 冒险经验。
 */
@Mixin(PlayerAdvancements.class)
public class AdvancementExpMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void onAwardAdvancement(AdvancementHolder holder, String string,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue()) {
            AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_ADVANCEMENT);
        }
    }
}
