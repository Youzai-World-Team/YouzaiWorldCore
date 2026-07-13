package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 维度切换 → +10 / 首次下界 +200 / 首次末地 +300
 */
@Mixin(ServerPlayer.class)
public class DimensionExpMixin {

    @Inject(method = "changeDimension", at = @At("RETURN"))
    private void onChangeDimension(CallbackInfoReturnable<ServerPlayer> cir) {
        ServerPlayer newPlayer = cir.getReturnValue();
        if (newPlayer == null) return;

        ResourceKey<Level> dim = newPlayer.level().dimension();

        // 每次都给的维度切换经验
        AdventureLevelManager.grantExp(newPlayer, AdventureLevelManager.EXP_DIMENSION_CHANGE);

        // 首次抵达检查
        if (dim == Level.NETHER) {
            AdventureLevelManager.checkFirstNether(newPlayer);
        } else if (dim == Level.END) {
            AdventureLevelManager.checkFirstEnd(newPlayer);
        }
    }
}
