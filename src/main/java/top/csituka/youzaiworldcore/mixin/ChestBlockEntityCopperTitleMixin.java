package top.csituka.youzaiworldcore.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CopperChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 修复 26.2 铜箱子容器标题。
 * <p>
 * 原版 {@link ChestBlockEntity#getDefaultName()} 对所有箱子硬编码返回
 * {@code container.chest}（"箱子"），而 26.2 的铜箱子（CopperChestBlock 及其
 * 5 种锈蚀/涂蜡变体）复用 {@link ChestBlockEntity} 作为方块实体，因此打开铜箱子时
 * 服务端下发的容器标题错误显示为"箱子"，而非"铜箱子"/"锈蚀的铜箱子"等正确名称。
 * <p>
 * 修复方式：当方块为 {@link CopperChestBlock} 时，返回方块自身的本地化名称
 * （{@code block.minecraft.copper_chest} 等）。自定义命名箱子不受影响
 * （getName() 优先返回自定义名，不会走到 getDefaultName）。
 * 仅针对铜箱子注入，普通箱子/陷阱箱标题保持原版行为不变。
 * <p>
 * 注意：客户端 YZUI 容器屏（YzuContainerScreen.Kind.resolve）依赖此修正后的标题
 * 翻译键（block.minecraft.*copper_chest）来区分铜箱子样式与图标。
 */
@Mixin(ChestBlockEntity.class)
@SuppressWarnings({ "null", "unused" })
public abstract class ChestBlockEntityCopperTitleMixin {

    @Inject(method = "getDefaultName", at = @At("HEAD"), cancellable = true)
    private void yzwc$fixCopperChestDefaultName(CallbackInfoReturnable<Component> cir) {
        ChestBlockEntity self = (ChestBlockEntity) (Object) this;
        Block block = self.getBlockState().getBlock();
        if (block instanceof CopperChestBlock) {
            DebugLogger.info("CopperChestTitle", "铜箱子容器标题修正: %s → %s",
                    "container.chest", block.getName().getString());
            cir.setReturnValue(block.getName());
        }
    }
}
