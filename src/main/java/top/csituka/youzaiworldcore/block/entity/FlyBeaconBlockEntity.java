package top.csituka.youzaiworldcore.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import top.csituka.youzaiworldcore.screen.FlyBeaconMenu;

public class FlyBeaconBlockEntity extends BlockEntity implements Container, MenuProvider {

    public static final int MAX_ENERGY = 10000;
    public static final int ENERGY_PER_LAPIS = 1000;
    private static final int FUEL_CONSUME_INTERVAL = 20;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int energy = 0;
    private int fuelTickCounter = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> energy;
                case 1 -> MAX_ENERGY;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> energy = value;
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public FlyBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLY_BEACON, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FlyBeaconBlockEntity blockEntity) {
        if (level.isClientSide()) {
            return;
        }

        if (blockEntity.energy < MAX_ENERGY) {
            ItemStack fuelStack = blockEntity.items.get(0);
            if (fuelStack.is(Items.LAPIS_LAZULI)) {
                blockEntity.fuelTickCounter++;
                if (blockEntity.fuelTickCounter >= FUEL_CONSUME_INTERVAL) {
                    blockEntity.fuelTickCounter = 0;
                    fuelStack.shrink(1);
                    blockEntity.energy = Math.min(MAX_ENERGY, blockEntity.energy + ENERGY_PER_LAPIS);
                    blockEntity.setChanged();
                }
            } else {
                blockEntity.fuelTickCounter = 0;
            }
        } else {
            blockEntity.fuelTickCounter = 0;
        }
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null) {
            return false;
        }
        if (this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(Items.LAPIS_LAZULI);
    }

    public int getEnergy() {
        return energy;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("Energy", energy);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, items);
        energy = input.getIntOr("Energy", 0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.youzaiworldcore.fly_beacon");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FlyBeaconMenu(containerId, playerInventory, this);
    }
}
