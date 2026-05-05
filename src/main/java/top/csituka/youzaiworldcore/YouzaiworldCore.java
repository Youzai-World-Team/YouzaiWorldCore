package top.csituka.youzaiworldcore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.event.FlyCoreTickHandler;
import top.csituka.youzaiworldcore.item.ModCreativeModeTabs;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.YzChainMiningTool;
import top.csituka.youzaiworldcore.network.ModNetworking;
import top.csituka.youzaiworldcore.screen.ModMenuTypes;

public class YouzaiworldCore implements ModInitializer {

    public static final String MOD_ID = "youzaiworldcore";

    public static final ResourceKey<PlacedFeature> YZ_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(MOD_ID, "ore_yz")
    );

    public static final ResourceKey<PlacedFeature> RAW_YZ_BLOCK_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(MOD_ID, "ore_raw_yz_block")
    );

    @Override
    public void onInitialize() {
        ModDataComponents.initialize();
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();
        ModCreativeModeTabs.initialize();
        ModMenuTypes.initialize();
        ModNetworking.initialize();
        YzChainMiningTool.registerChainMiningEvent();
        FlyCoreTickHandler.register();

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                YZ_ORE_PLACED_KEY
        );

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                RAW_YZ_BLOCK_PLACED_KEY
        );
    }
}
