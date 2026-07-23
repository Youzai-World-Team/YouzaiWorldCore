package top.csituka.youzaiworldcore.mixin.client.itemborder;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;

/**
 * 为 {@link AbstractContainerScreen} 的每个物品槽位绘制稀有度边框。
 * <p>
 * 注入点：{@code extractSlot(GuiGraphicsExtractor, Slot, int, int)} 的 RETURN，
 * 此时物品图标、数量、耐久条均已绘制完毕，边框自然叠于其上。
 * </p>
 *
 * <p>参照 {@link top.csituka.youzaiworldcore.mixin.client.highlightitem.AbstractContainerScreenMixin}
 * 的注入模式，均使用 26.2 的 Extractor 管线。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    /**
     * 在每个槽位的 extractSlot 返回后绘制边框。
     *
     * @param gui  GuiGraphicsExtractor 实例
     * @param slot 当前正在绘制的槽位
     * @param x    槽位 X 坐标（与 slot.x 相同，加此形参便于直接使用）
     * @param y    槽位 Y 坐标
     * @param ci   回调信息
     */
    @Inject(method = "extractSlot",
            at = @At("RETURN"))
    private void afterExtractSlot(GuiGraphicsExtractor gui, Slot slot, int x, int y, CallbackInfo ci) {
        ItemBorderRenderer.renderBorder(gui, slot);
    }
}
