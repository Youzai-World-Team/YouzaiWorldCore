package top.csituka.youzaiworldcore.screen;

import net.minecraft.core.Holder;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.block.entity.DecompositionTableBlockEntity;
import top.csituka.youzaiworldcore.screen.slot.DecompositionInputSlot;
import top.csituka.youzaiworldcore.screen.slot.DecompositionOutputSlot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DecompositionTableMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT_START = 1;
    private static final int OUTPUT_SLOT_END = 9;
    private static final int PLAYER_INV_START = 10;
    private static final int PLAYER_INV_END = 46;

    private final Container container;
    private final ContainerLevelAccess access;
    private final Level level;
    private List<ItemStack> currentOutputs = new ArrayList<>();
    private int currentRecipeIndex = 0;
    private List<RecipeHolder<CraftingRecipe>> currentRecipes = new ArrayList<>();

    public DecompositionTableMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(10), ContainerLevelAccess.NULL);
    }

    public DecompositionTableMenu(int containerId, Inventory playerInventory, DecompositionTableBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity, ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()));
    }

    public DecompositionTableMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access) {
        super(ModMenuTypes.DECOMPOSITION_TABLE, containerId);
        checkContainerSize(container, 10);
        this.container = container;
        this.access = access;
        this.level = playerInventory.player.level();

        this.addSlot(new DecompositionInputSlot(container, INPUT_SLOT, 49, 35, this));

        int outputSlotX = 107;
        int outputSlotY = 17;
        for (int i = 0; i < 9; i++) {
            int row = i / 3;
            int col = i % 3;
            this.addSlot(new DecompositionOutputSlot(container, OUTPUT_SLOT_START + i, outputSlotX + col * 18, outputSlotY + row * 18, this));
        }

        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public boolean canDecompose() {
        if (level.isClientSide()) {
            return false;
        }
        
        ItemStack inputStack = container.getItem(INPUT_SLOT);
        if (inputStack.isEmpty()) {
            return false;
        }
        
        for (int i = OUTPUT_SLOT_START; i <= OUTPUT_SLOT_END; i++) {
            if (!container.getItem(i).isEmpty()) {
                return false;
            }
        }
        
        return true;
    }

    public void performDecomposition() {
        if (!canDecompose()) {
            return;
        }
        
        ItemStack inputStack = container.getItem(INPUT_SLOT);
        if (inputStack.isEmpty()) {
            return;
        }

        currentRecipes.clear();
        currentRecipeIndex = 0;

        findRecipesForInput(inputStack);
        
        if (!currentRecipes.isEmpty()) {
            applyCurrentRecipe();
        }
    }

    private void findRecipesForInput(ItemStack inputStack) {
        if (level.isClientSide()) {
            return;
        }

        RecipeManager recipeManager = level.getServer().getRecipeManager();
        Item inputItem = inputStack.getItem();
        
        Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();
        
        for (RecipeHolder<?> holder : allRecipes) {
            CraftingRecipe recipe = null;
            
            if (holder.value() instanceof ShapedRecipe shapedRecipe) {
                recipe = shapedRecipe;
            } else if (holder.value() instanceof ShapelessRecipe shapelessRecipe) {
                recipe = shapelessRecipe;
            }
            
            if (recipe != null) {
                ItemStack result = getRecipeResult(recipe);
                
                if (!result.isEmpty() && result.getItem() == inputItem) {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<CraftingRecipe> craftingHolder = (RecipeHolder<CraftingRecipe>) holder;
                    currentRecipes.add(craftingHolder);
                }
            }
        }
    }

    private ItemStack getRecipeResult(CraftingRecipe recipe) {
        try {
            PlacementInfo placementInfo = recipe.placementInfo();
            List<Ingredient> ingredients = placementInfo.ingredients();
            
            if (ingredients.isEmpty()) {
                return ItemStack.EMPTY;
            }
            
            List<ItemStack> sampleItems = new ArrayList<>();
            for (Ingredient ingredient : ingredients) {
                if (!ingredient.isEmpty()) {
                    List<Holder<Item>> items = ingredient.items().toList();
                    if (!items.isEmpty()) {
                        sampleItems.add(new ItemStack(items.get(0).value()));
                    }
                }
            }
            
            if (sampleItems.isEmpty()) {
                return ItemStack.EMPTY;
            }
            
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 1;
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 1;
            
            while (sampleItems.size() < width * height) {
                sampleItems.add(ItemStack.EMPTY);
            }
            
            net.minecraft.world.item.crafting.CraftingInput craftingInput = 
                net.minecraft.world.item.crafting.CraftingInput.of(width, height, sampleItems);
            
            return recipe.assemble(craftingInput);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private void applyCurrentRecipe() {
        if (currentRecipes.isEmpty() || currentRecipeIndex >= currentRecipes.size()) {
            return;
        }

        RecipeHolder<CraftingRecipe> recipeHolder = currentRecipes.get(currentRecipeIndex);
        CraftingRecipe recipe = recipeHolder.value();
        
        ItemStack resultItem = getRecipeResult(recipe);
        int resultCount = resultItem.getCount();
        
        if (resultCount <= 0) {
            resultCount = 1;
        }
        
        ItemStack inputStack = container.getItem(INPUT_SLOT);
        if (inputStack.isEmpty() || inputStack.getCount() < resultCount) {
            return;
        }
        
        List<ItemStack> outputs = getIngredientsFromRecipe(recipe);
        
        currentOutputs = outputs;
        
        for (int i = 0; i < Math.min(outputs.size(), 9); i++) {
            container.setItem(OUTPUT_SLOT_START + i, outputs.get(i).copy());
        }
        
        inputStack.shrink(resultCount);
        container.setChanged();
    }

    private List<ItemStack> getIngredientsFromRecipe(CraftingRecipe recipe) {
        List<ItemStack> outputs = new ArrayList<>();
        
        PlacementInfo placementInfo = recipe.placementInfo();
        List<Ingredient> ingredients = placementInfo.ingredients();
        
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                List<Holder<Item>> items = ingredient.items().toList();
                if (!items.isEmpty()) {
                    outputs.add(new ItemStack(items.get(0).value()));
                } else {
                    outputs.add(ItemStack.EMPTY);
                }
            } else {
                outputs.add(ItemStack.EMPTY);
            }
        }
        
        while (outputs.size() < 9) {
            outputs.add(ItemStack.EMPTY);
        }
        
        return outputs;
    }

    public void cycleRecipe() {
        if (level.isClientSide()) {
            return;
        }
        
        if (currentRecipes.size() <= 1) {
            return;
        }
        
        currentRecipeIndex = (currentRecipeIndex + 1) % currentRecipes.size();
        applyCurrentRecipe();
    }

    public boolean hasMultipleRecipes() {
        return currentRecipes.size() > 1;
    }

    public int getCurrentRecipeIndex() {
        return currentRecipeIndex;
    }

    public int getTotalRecipes() {
        return currentRecipes.size();
    }

    public void onInputChanged() {
    }

    public void onOutputTaken(int slot) {
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        
        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemStack = slotStack.copy();
            
            if (index >= INPUT_SLOT && index <= OUTPUT_SLOT_END) {
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_INV_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
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
        return stillValid(this.access, player, top.csituka.youzaiworldcore.block.ModBlocks.DECOMPOSITION_TABLE);
    }

    public Container getContainer() {
        return container;
    }
}
