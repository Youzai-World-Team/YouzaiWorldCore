package top.csituka.youzaiworldcore.mixin.client.inventory;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.client.config.InventoryItemGroupsConfig;
import top.csituka.youzaiworldcore.client.inventory.CreativeItemGroupScreen;
import top.csituka.youzaiworldcore.client.inventory.CreativeItemGroups;

import java.util.Collection;

/**
 * Minecraft 26.2 原版创造物品分组入口。
 * <p>已核验 selectTab、refreshCurrentTabContents、slotClicked 及 ItemPickerMenu 的滚动算法。
 * 点击组头在鼠标入口直接消费；键盘取物也被拦截，不借用光标物品或网络包触发折叠。</p>
 */
@SuppressWarnings("null")
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin
        extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu>
        implements CreativeItemGroupScreen {

    @Shadow private static CreativeModeTab selectedTab;
    @Shadow @Final private static SimpleContainer CONTAINER;
    @Shadow private float scrollOffs;

    @Unique private final CreativeItemGroups youzaiworldcore$groups = new CreativeItemGroups();
    @Unique private CreativeModeTab youzaiworldcore$groupTab;
    @Unique private int youzaiworldcore$consumedButton = -1;

    protected CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu,
                                                Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "selectTab", at = @At("HEAD"))
    private void youzaiworldcore$selectGroupTab(CreativeModeTab tab, CallbackInfo ci) {
        if (youzaiworldcore$groupTab != tab) {
            youzaiworldcore$groups.clear();
        }
        youzaiworldcore$groupTab = tab;
    }

    @WrapOperation(method = "selectTab", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/CreativeModeTab;getDisplayItems()Ljava/util/Collection;"))
    private Collection<ItemStack> youzaiworldcore$groupTabItems(CreativeModeTab tab,
                                                               Operation<Collection<ItemStack>> original) {
        youzaiworldcore$groups.rebuild(tab, original.call(tab));
        return youzaiworldcore$groups.items();
    }

    @ModifyVariable(method = "refreshCurrentTabContents", at = @At("HEAD"), argsOnly = true)
    private Collection<ItemStack> youzaiworldcore$refreshGroups(Collection<ItemStack> original) {
        youzaiworldcore$groupTab = selectedTab;
        if (selectedTab.getType() != CreativeModeTab.Type.CATEGORY) {
            youzaiworldcore$groups.clear();
            return original;
        }
        youzaiworldcore$groups.rebuild(selectedTab, original);
        return youzaiworldcore$groups.items();
    }

    @Override
    public CreativeItemGroups.Entry youzaiworldcore$getGroupEntry(Slot slot) {
        if (!InventoryItemGroupsConfig.isEnabled() || slot == null || slot.container != CONTAINER
                || slot.index < 0 || slot.index >= 45 || youzaiworldcore$groupTab != selectedTab
                || selectedTab.getType() != CreativeModeTab.Type.CATEGORY) {
            return null;
        }
        int index = youzaiworldcore$topRow() * 9 + slot.index;
        CreativeItemGroups.Entry entry = youzaiworldcore$groups.entry(index);
        // 其他模组如果替换了展示列表，不对无法确认归属的格子进行分组交互。
        return entry != null && index < menu.items.size() && menu.items.get(index) == entry.stack() ? entry : null;
    }

    @Unique
    private int youzaiworldcore$topRow() {
        int rows = youzaiworldcore$scrollRows();
        return Float.isFinite(scrollOffs) ? Math.clamp(Math.round(scrollOffs * rows), 0, rows) : 0;
    }

    @Unique
    private int youzaiworldcore$scrollRows() {
        return Math.max(0, (menu.items.size() + 8) / 9 - 5);
    }

    @Unique
    private void youzaiworldcore$toggleGroup(Slot slot) {
        int row = youzaiworldcore$topRow();
        if (youzaiworldcore$groups.toggle(row * 9 + slot.index)) {
            youzaiworldcore$syncGroupItems(row);
        }
    }

    @Unique
    private void youzaiworldcore$syncGroupItems(int row) {
        menu.items.clear();
        menu.items.addAll(youzaiworldcore$groups.items());
        int rows = youzaiworldcore$scrollRows();
        scrollOffs = rows == 0 ? 0 : (float) Math.min(row, rows) / rows;
        menu.scrollTo(scrollOffs);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void youzaiworldcore$advanceGroupAnimations(GuiGraphicsExtractor graphics, int x, int y, float tick,
                                                        CallbackInfo ci) {
        if (selectedTab != null && youzaiworldcore$groupTab == selectedTab
                && selectedTab.getType() == CreativeModeTab.Type.CATEGORY) {
            int row = youzaiworldcore$topRow();
            if (youzaiworldcore$groups.advanceAnimations()) {
                youzaiworldcore$syncGroupItems(row);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$clickGroup(MouseButtonEvent event, boolean doubleClick,
                                          CallbackInfoReturnable<Boolean> cir) {
        for (Slot slot : menu.slots) {
            CreativeItemGroups.Entry entry = youzaiworldcore$getGroupEntry(slot);
            if (entry != null && !entry.canTake() && isHovering(slot.x, slot.y, 16, 16, event.x(), event.y())) {
                if (entry.header() && (event.button() == 0 || event.button() == 1)) {
                    youzaiworldcore$toggleGroup(slot);
                }
                youzaiworldcore$consumedButton = event.button();
                cir.setReturnValue(true);
                return;
            }
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$releaseGroup(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == youzaiworldcore$consumedButton) {
            youzaiworldcore$consumedButton = -1;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$dragGroup(MouseButtonEvent event, double dx, double dy,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == youzaiworldcore$consumedButton) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$preventGroupPickup(Slot slot, int slotId, int button, ContainerInput input,
                                                    CallbackInfo ci) {
        CreativeItemGroups.Entry entry = youzaiworldcore$getGroupEntry(slot);
        if (entry != null && !entry.canTake()) {
            ci.cancel();
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$keyboardToggle(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        CreativeItemGroups.Entry entry = youzaiworldcore$getGroupEntry(hoveredSlot);
        if (entry != null && entry.header() && (event.key() == 257 || event.key() == 335 || event.key() == 32)) {
            youzaiworldcore$toggleGroup(hoveredSlot);
            cir.setReturnValue(true);
        }
    }
}
