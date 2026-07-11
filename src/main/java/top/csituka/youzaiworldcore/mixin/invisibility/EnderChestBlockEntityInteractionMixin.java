package top.csituka.youzaiworldcore.mixin.invisibility;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.invisibility.InvisibilityManager;

/**
 * 隐身玩家末影箱交互跟踪 Mixin。
 * <p>
 * 末影箱的 {@code onOpen} / {@code onClose} / {@code openerCountChanged} 实现在
 * 匿名内部类 {@code EnderChestBlockEntity$1} 中，无法直接 Mixin。
 * 因此在本 Mixin 的 {@code startOpen} 中将位置标记到 {@link InvisibilityManager}，
 * 再由 {@link ServerLevelInteractionMixin} 统一拦截 {@code Level.playSound} 和
 * {@code ServerLevel.blockEvent} 进行抑制。
 * </p>
 */
@Mixin(EnderChestBlockEntity.class)
public abstract class EnderChestBlockEntityInteractionMixin {

    /**
     * 在 {@code startOpen} 中记录隐身玩家与末影箱的交互关系。
     */
    @Inject(method = "startOpen", at = @At("HEAD"))
    private void youzaiworldcore$onStartOpen(ContainerUser user, CallbackInfo ci) {
        EnderChestBlockEntity self = (EnderChestBlockEntity) (Object) this;
        BlockPos pos = self.getBlockPos();

        if (user instanceof ServerPlayer player) {
            if (InvisibilityManager.isInvisible(player)) {
                InvisibilityManager.markContainerInteraction(pos);
            } else {
                InvisibilityManager.clearContainerInteraction(pos);
            }
        }
    }
}
