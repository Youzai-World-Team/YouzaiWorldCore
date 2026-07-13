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
 * 混合注入 {@link PlayerAdvancements#award}，
 * 当玩家完成进度（成就）时发放冒险经验。
 */
@Mixin(PlayerAdvancements.class)
public class AdvancementExpMixin {

    @Shadow
    private ServerPlayer player;

    /**
     * 在 award 方法成功返回 true（进度被授予）时发放经验。
     */
    @Inject(method = "award", at = @At("RETURN"))
    private void onAwardAdvancement(AdvancementHolder advancementHolder, String string,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue()) {
            AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_ADVANCEMENT);
        }
    }
}
