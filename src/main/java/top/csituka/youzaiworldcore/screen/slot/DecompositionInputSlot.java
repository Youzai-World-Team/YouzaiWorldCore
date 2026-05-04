package top.csituka.youzaiworldcore.screen.slot;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;

public class DecompositionInputSlot extends Slot {

    private final DecompositionTableMenu menu;

    public DecompositionInputSlot(Container container, int slot, int x, int y, DecompositionTableMenu menu) {
        super(container, slot, x, y);
        this.menu = menu;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        menu.onInputChanged();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 64;
    }
}
