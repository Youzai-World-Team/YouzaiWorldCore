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
 * 进度 / 成就 / 挑战 → 冒险经验。
 * 通过 AdvancementHolder.id() 路径中的关键词区分：
 * challenge → +250 / goal → +100 / 其他 → +50
 */
@Mixin(PlayerAdvancements.class)
public class AdvancementExpMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = "award", at = @At("RETURN"))
    private void onAwardAdvancement(AdvancementHolder holder, String string,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() == null || !cir.getReturnValue()) return;

        // 通过 display() 检测帧类型
        var display = holder.value().display();
        if (display.isPresent()) {
            var d = display.get();
            // AdvancementDisplay.type() 返回 AdvancementType 枚举
            var displayType = d.getClass().getSimpleName();
            var typeStr = d.toString().toLowerCase();

            if (typeStr.contains("challenge")) {
                AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_ADVANCEMENT_CHALLENGE);
                return;
            }
            if (typeStr.contains("goal")) {
                AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_ADVANCEMENT_GOAL);
                return;
            }
        }
        AdventureLevelManager.grantExp(player, AdventureLevelManager.EXP_ADVANCEMENT);
    }
}
