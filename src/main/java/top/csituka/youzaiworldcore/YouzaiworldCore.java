package top.csituka.youzaiworldcore;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.entity.ModBlockEntities;
import top.csituka.youzaiworldcore.component.ModDataComponents;
import top.csituka.youzaiworldcore.event.FlyBeaconTickHandler;
import top.csituka.youzaiworldcore.event.VoidStaffTickHandler;
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
        VoidStaffTickHandler.register();
        FlyBeaconTickHandler.register();

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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                dispatcher.register(Commands.literal("yzwc")
                .executes(context -> {
                        // 在 mojmap 中，使用 sendSuccess 方法并传入一个 lambda 表达式来提供文本组件。
                        // 第二个参数 'false' 表示消息仅对命令执行者可见，不会广播给其他管理员。
                        context.getSource().sendSuccess(() -> Component.literal("Hello World!"), false);
                        return 1;
                        })
                );
        });
    }
}
