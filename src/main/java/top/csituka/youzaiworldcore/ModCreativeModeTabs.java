package top.csituka.youzaiworldcore;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

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
            })
            .build();

    public static void initialize() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_TAB_KEY, YOUZAI_TAB);
    }
}
