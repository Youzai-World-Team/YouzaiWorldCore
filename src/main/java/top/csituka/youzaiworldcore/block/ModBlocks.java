package top.csituka.youzaiworldcore.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

import top.csituka.youzaiworldcore.YouzaiworldCore;

public class ModBlocks {

    public static final Block YZ_ORE = register(
            "yz_ore",
            props -> new DropExperienceBlock(UniformInt.of(2, 5), props),
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 3.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final Block DEEPSLATE_YZ_ORE = register(
            "deepslate_yz_ore",
            props -> new DropExperienceBlock(UniformInt.of(2, 5), props),
            BlockBehaviour.Properties.of()
                    .strength(4.5f, 3.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final Block RAW_YZ_BLOCK = register(
            "raw_yz_block",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final Block YZ_BLOCK = register(
            "yz_block",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final DecompositionTableBlock DECOMPOSITION_TABLE = register(
            "decomposition_table",
            DecompositionTableBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.METAL),
            true
    );

    public static final FlyBeaconBlock FLY_BEACON = register(
            "fly_beacon",
            FlyBeaconBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 3.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops(),
            true
    );

    @SuppressWarnings("unchecked")
    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> blockFactory, BlockBehaviour.Properties settings, boolean shouldRegisterItem) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        T block = blockFactory.apply(settings.setId(blockKey));

        if (shouldRegisterItem) {
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
            BlockItem blockItem = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
            Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
        }

        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
    }

    public static void initialize() {
    }
}
