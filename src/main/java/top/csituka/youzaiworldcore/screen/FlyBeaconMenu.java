package top.csituka.youzaiworldcore.screen;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;

public class FlyBeaconMenu extends AbstractContainerMenu {

    private static final int FUEL_SLOT = 0;
    private static final int PLAYER_INV_START = 1;
    private static final int PLAYER_INV_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final Container container;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public FlyBeaconMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(1), new ContainerData() {
            private final int[] data = new int[2];
            @Override
            public int get(int index) {
                return data[index];
            }
            @Override
            public void set(int index, int value) {
                data[index] = value;
            }
            @Override
            public int getCount() {
                return 2;
            }
        }, ContainerLevelAccess.NULL);
    }

    public FlyBeaconMenu(int containerId, Inventory playerInventory, FlyBeaconBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity, blockEntity.getDataAccess(), ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()));
    }

    public FlyBeaconMenu(int containerId, Inventory playerInventory, Container container, ContainerData data, ContainerLevelAccess access) {
        super(ModMenuTypes.FLY_BEACON, containerId);
        checkContainerSize(container, 1);
        checkContainerDataCount(data, 2);
        this.container = container;
        this.data = data;
        this.access = access;

        this.addSlot(new Slot(container, FUEL_SLOT, 80, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI);
            }
        });

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    public int getEnergy() {
        return this.data.get(0);
    }

    public int getMaxEnergy() {
        return this.data.get(1);
    }

    public float getEnergyRatio() {
        int energy = this.data.get(0);
        int maxEnergy = this.data.get(1);
        if (maxEnergy == 0) {
            return 0.0f;
        }
        return (float) energy / maxEnergy;
    }

    public Container getContainer() {
        return container;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemStack = slotStack.copy();

            if (index == FUEL_SLOT) {
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (slotStack.is(Items.LAPIS_LAZULI)) {
                    if (!this.moveItemStackTo(slotStack, FUEL_SLOT, FUEL_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= PLAYER_INV_START && index < PLAYER_INV_END) {
                    if (!this.moveItemStackTo(slotStack, HOTBAR_START, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index >= HOTBAR_START && index < HOTBAR_END) {
                    if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.FLY_BEACON);
    }
}
