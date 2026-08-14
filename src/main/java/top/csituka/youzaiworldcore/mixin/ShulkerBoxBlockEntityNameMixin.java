package top.csituka.youzaiworldcore.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 修正 26.2 潜影盒容器标题：原版 {@link ShulkerBoxBlockEntity#getDefaultName()}
 * 对所有颜色（16 色 + 未染色）硬编码返回 {@code container.shulkerBox}（"潜影盒"），
 * 标题不含颜色信息。
 * <p>
 * 修复方式：返回方块自身的本地化名称（{@code block.minecraft.purple_shulker_box}
 * 等），使标题显示为"紫色潜影盒"等正确名称；同时客户端 YZUI 潜影盒屏
 * （YzuShulkerBoxScreen）依赖该翻译键解析颜色，从而让标题图标与主题色跟随
 * 潜影盒实际颜色。自定义命名潜影盒不受影响（getName() 优先返回自定义名）。
 */
@Mixin(ShulkerBoxBlockEntity.class)
@SuppressWarnings({ "null", "unused" })
public abstract class ShulkerBoxBlockEntityNameMixin {

    @Inject(method = "getDefaultName", at = @At("HEAD"), cancellable = true)
    private void yzwc$useBlockName(CallbackInfoReturnable<Component> cir) {
        ShulkerBoxBlockEntity self = (ShulkerBoxBlockEntity) (Object) this;
        DebugLogger.info("ShulkerBoxTitle", "潜影盒容器标题修正: %s → %s",
                "container.shulkerBox", self.getBlockState().getBlock().getName().getString());
        cir.setReturnValue(self.getBlockState().getBlock().getName());
    }
}
