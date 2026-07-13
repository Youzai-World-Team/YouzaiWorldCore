package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 混合注入 {@link ServerPlayerGameMode#useItemOn}，
 * 追踪玩家成功放置方块，累计到放置计数器。
 */
@Mixin(ServerPlayerGameMode.class)
public class BlockPlaceExpMixin {

    /**
     * 在 useItemOn 返回 SUCCESS 或 CONSUME 时（即方块放置成功），
     * 增加该玩家的放置计数器。
     */
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void onUseItemOn(ServerPlayer serverPlayer, Level level, ItemStack itemStack,
                             InteractionHand interactionHand, BlockHitResult blockHitResult,
                             CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        if (result == InteractionResult.SUCCESS || result == InteractionResult.CONSUME) {
            if (!serverPlayer.isCreative()) {
                AdventureLevelManager.incrementPlaceCounter(serverPlayer);
            }
        }
    }
}
