package top.csituka.youzaiworldcore.mixin.client.inventory;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.inventory.CreativeItemGroupRenderer;
import top.csituka.youzaiworldcore.client.inventory.CreativeItemGroupScreen;
import top.csituka.youzaiworldcore.client.inventory.CreativeItemGroups;

import java.util.Optional;

/** 仅为实现分组接口的原版创造屏幕绘制组标记，其他容器继续原有渲染。 */
@SuppressWarnings("null")
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {

    @Shadow protected Slot hoveredSlot;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private CreativeItemGroups.Entry youzaiworldcore$entry(Slot slot) {
        return (Object) this instanceof CreativeItemGroupScreen screen ? screen.youzaiworldcore$getGroupEntry(slot) : null;
    }

    @WrapMethod(method = "extractSlot")
    private void youzaiworldcore$animateGroupSlot(GuiGraphicsExtractor graphics, Slot slot, int x, int y,
                                                 Operation<Void> original) {
        CreativeItemGroups.Entry entry = youzaiworldcore$entry(slot);
        if (entry == null || entry.group() == null) {
            original.call(graphics, slot, x, y);
            return;
        }
        if (!CreativeItemGroupRenderer.beginSlot(graphics, entry, slot.x, slot.y)) {
            return;
        }
        try {
            CreativeItemGroupRenderer.background(graphics, entry, slot.x, slot.y, false);
            original.call(graphics, slot, x, y);
            CreativeItemGroupRenderer.badge(graphics, entry, slot.x, slot.y, false);
        } finally {
            CreativeItemGroupRenderer.endSlot(graphics);
        }
    }

    @WrapOperation(method = "extractSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0))
    private ItemStack youzaiworldcore$previewGroup(Slot slot, Operation<ItemStack> original) {
        CreativeItemGroups.Entry entry = youzaiworldcore$entry(slot);
        return entry != null && entry.header() ? CreativeItemGroupRenderer.displayStack(entry) : original.call(slot);
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$groupTooltip(GuiGraphicsExtractor graphics, int x, int y, CallbackInfo ci) {
        CreativeItemGroups.Entry entry = youzaiworldcore$entry(hoveredSlot);
        if (entry != null && !entry.canTake()) {
            if (entry.header()) {
                graphics.setTooltipForNextFrame(font, CreativeItemGroupRenderer.tooltip(entry), Optional.empty(), x, y, null);
            }
            ci.cancel();
        }
    }
}
