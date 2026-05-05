package top.csituka.youzaiworldcore.screen.slot;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.screen.DecompositionTableMenu;

public class DecompositionOutputSlot extends Slot {

    private final DecompositionTableMenu menu;

    public DecompositionOutputSlot(Container container, int slot, int x, int y, DecompositionTableMenu menu) {
        super(container, slot, x, y);
        this.menu = menu;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        super.onTake(player, stack);
        menu.onOutputTaken(this.index);
    }

    @Override
    public ItemStack remove(int amount) {
        ItemStack result = super.remove(amount);
        if (!result.isEmpty()) {
            menu.onOutputTaken(this.index);
        }
        return result;
    }
}
