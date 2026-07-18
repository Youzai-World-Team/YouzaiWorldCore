package top.csituka.youzaiworldcore.mixin.doubledoors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.event.DoubleDoorsHandler;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 门（DoorBlock）的双开 Mixin。
 * <ul>
 *   <li>注入 {@code useWithoutItem} 中的 {@code playSound} 调用点，
 *       捕获玩家点击门时的交互（仅徒手可开的门）</li>
 *   <li>注入 {@code setOpen} 方法 RETURN，
 *       捕获红石信号或村民 AI 导致的门状态变化</li>
 * </ul>
 */
@Mixin(value = DoorBlock.class, priority = 1001)
public class DoorBlockMixin {

    private static final String MODULE = "DoorBlockMixin";

    /**
     * 玩家点击门时触发。
     * 注入点在 {@code playSound} 调用前（此时门状态已被切换），
     * 与 Serilum 原版 Double Doors 一致。
     */
    @Inject(method = "useWithoutItem", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/DoorBlock;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Z)V"))
    private void youzaiworldcore$onUseWithoutItem(BlockState blockState, Level level, BlockPos blockPos,
                                                   Player player, BlockHitResult blockHitResult,
                                                   CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide()) {
            return;
        }
        DebugLogger.debug(MODULE, "useWithoutItem@playSound: pos=%s, player=%s",
                blockPos, player.getName().getString());
        DoubleDoorsHandler.onDoorClick(level, player, blockPos, blockHitResult);
    }

    /**
     * 门因红石或村民 AI 发生状态变化时触发。
     * 注入在 {@code setOpen} 方法 RETURN 处（门状态已更新）。
     */
    @Inject(method = "setOpen", at = @At("RETURN"))
    private void youzaiworldcore$onSetOpen(Entity entity, Level level,
                                            BlockState state, BlockPos pos, boolean open,
                                            CallbackInfo ci) {
        if (level.isClientSide()) {
            return;
        }
        BlockState currentState = level.getBlockState(pos);
        DebugLogger.debug(MODULE, "setOpen@RETURN: pos=%s, open=%s", pos, open);
        DoubleDoorsHandler.onSetOpen(level, pos, currentState);
    }
}
