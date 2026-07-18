package top.csituka.youzaiworldcore.mixin.client.highlightitem;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.highlightitem.Configurator;
import top.csituka.youzaiworldcore.highlightitem.ItemComparator;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 在容器/物品栏界面的渲染状态提取阶段，为与悬停物品“相似”的槽位绘制重着色高亮。
 * <p>
 * 直接于每个 {@code extractSlot(...)} 返回后，把着色矩形绘入 {@link GuiGraphicsExtractor}
 * （与原版高亮同一管线），无需拦截 {@code blitSprite} 或引入信号量字段，规避了 26.2 中已变更的精灵绘制细节。
 * 这里把 {@code slot} 作为原方法形参直接注入到处理器，避免依赖 {@code @Local} 注解。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "extractSlot",
            at = @At("RETURN"))
    private void afterExtractSlot(GuiGraphicsExtractor gui, Slot slot, int x, int y, CallbackInfo ci) {
        if (!Configurator.TOGGLE || this.hoveredSlot == null) {
            return;
        }

        // 悬停物品本身：仅当开启“悬停着色”时才着色，否则保留原版白色高亮
        if (slot == this.hoveredSlot && !Configurator.COLOR_HOVERED) {
            return;
        }

        if (slot.isActive()
                && ItemComparator.test(Configurator.COMPARATOR, this.hoveredSlot.getItem(), slot.getItem())) {
            DebugLogger.trace("HighlightItem", "绘制高亮槽位: %s (mode=%s)",
                    slot.getItem().getItem().getDescriptionId(), Configurator.COMPARATOR.name());
            gui.fillGradient(slot.x, slot.y, slot.x + 16, slot.y + 16, Configurator.COLOR, Configurator.COLOR);
        }
    }
}
