package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

/**
 * 隐身玩家潜影盒交互跟踪 Mixin。
 * <p>
 * 潜影盒的 {@code startOpen} / {@code stopOpen} 直接调用
 * {@code level.blockEvent} 和 {@code level.playSound}。
 * 在本 Mixin 中将交互位置标记到 {@link InvisibilityManager}，
 * 再由 {@link ServerLevelInteractionMixin} 统一拦截。
 * </p>
 */
@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxBlockEntityInteractionMixin {

    /**
     * 在 {@code startOpen} 中记录隐身玩家与潜影盒的交互关系。
     */
    @Inject(method = "startOpen", at = @At("HEAD"))
    private void youzaiworldcore$onStartOpen(ContainerUser user, CallbackInfo ci) {
        ShulkerBoxBlockEntity self = (ShulkerBoxBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        if (user instanceof ServerPlayer player) {
            if (InvisibilityManager.isInvisible(player)) {
                InvisibilityManager.markContainerInteraction(pos);
            } else {
                InvisibilityManager.clearContainerInteraction(pos);
            }
        }
    }

    /**
     * 在 {@code stopOpen} 中，当关闭者是隐身玩家时清除跟踪标记。
     */
    @Inject(method = "stopOpen", at = @At("HEAD"))
    private void youzaiworldcore$onStopOpen(ContainerUser user, CallbackInfo ci) {
        ShulkerBoxBlockEntity self = (ShulkerBoxBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        // 潜影盒直接管理 openCount，close 时清理标记
        InvisibilityManager.clearContainerInteraction(pos);
    }
}
