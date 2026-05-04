package top.csituka.youzaiworldcore.item;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.block.ModBlocks;

public class ModCreativeModeTabs {

    public static final ResourceKey<CreativeModeTab> YOUZAI_TAB_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "youzai_tab")
    );

    public static final CreativeModeTab YOUZAI_TAB = FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(ModItems.RAW_YZ))
            .title(Component.translatable("itemGroup.youzaiworldcore.youzai_tab"))
            .displayItems((params, output) -> {
                output.accept(ModBlocks.YZ_ORE.asItem());
                output.accept(ModBlocks.DEEPSLATE_YZ_ORE.asItem());
                output.accept(ModItems.RAW_YZ);
                output.accept(ModBlocks.RAW_YZ_BLOCK.asItem());
                output.accept(ModItems.YZ_INGOT);
                output.accept(ModItems.YZ_NUGGET);
                output.accept(ModBlocks.YZ_BLOCK.asItem());
                output.accept(ModItems.YZ_SHOVEL);
                output.accept(ModItems.YZ_PICKAXE);
                output.accept(ModItems.YZ_HOE);
                output.accept(ModItems.YZ_SWORD);
                output.accept(ModItems.YZ_AXE);
                output.accept(ModItems.HEART_OF_GUARDIANSHIP);
            })
            .build();

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_TAB_KEY, YOUZAI_TAB);
    }
}
