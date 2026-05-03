package top.csituka.youzaiworldcore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.item.ModCreativeModeTabs;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.item.tool.YzChainMiningTool;

public class YouzaiworldCore implements ModInitializer {

    public static final String MOD_ID = "youzaiworldcore";

    public static final ResourceKey<PlacedFeature> YZ_ORE_PLACED_KEY = ResourceKey.create(
            Registries.PLACED_FEATURE,
            Identifier.fromNamespaceAndPath(MOD_ID, "ore_yz")
    );

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModCreativeModeTabs.initialize();
        YzChainMiningTool.registerChainMiningEvent();

        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                YZ_ORE_PLACED_KEY
        );
    }
}
