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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.item.preset.PresetItems;
import top.csituka.youzaiworldcore.util.DebugLogger;

@SuppressWarnings("null")
public class ModCreativeModeTabs {

        // ── 悠哉世界 - 方块 ──
        public static final ResourceKey<CreativeModeTab> YOUZAI_BLOCKS_KEY = ResourceKey.create(
                        Registries.CREATIVE_MODE_TAB,
                        Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "youzai_blocks"));

        public static final CreativeModeTab YOUZAI_BLOCKS = FabricCreativeModeTab.builder()
                        .icon(() -> new ItemStack(ModBlocks.YZ_BLOCK))
                        .title(Component.translatable("itemGroup.youzaiworldcore.youzai_blocks"))
                        .displayItems((params, output) -> {
                                output.accept(ModBlocks.YZ_ORE);
                                output.accept(ModBlocks.DEEPSLATE_YZ_ORE);
                                output.accept(ModBlocks.RAW_YZ_BLOCK);
                                output.accept(ModBlocks.YZ_BLOCK);
                                output.accept(ModBlocks.DECOMPOSITION_TABLE);
                                output.accept(ModBlocks.FLY_BEACON);
                                output.accept(ModBlocks.TP_ANCHOR);
                        })
                        .build();

        // ── 悠哉世界 - 工具&武器 ──
        public static final ResourceKey<CreativeModeTab> YOUZAI_TOOLS_WEAPONS_KEY = ResourceKey.create(
                        Registries.CREATIVE_MODE_TAB,
                        Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "youzai_tools_weapons"));

        public static final CreativeModeTab YOUZAI_TOOLS_WEAPONS = FabricCreativeModeTab.builder()
                        .icon(() -> new ItemStack(ModItems.YZ_SWORD))
                        .title(Component.translatable("itemGroup.youzaiworldcore.youzai_tools_weapons"))
                        .displayItems((params, output) -> {
                                output.accept(ModItems.YZ_SHOVEL);
                                output.accept(ModItems.YZ_PICKAXE);
                                output.accept(ModItems.YZ_HOE);
                                output.accept(ModItems.YZ_SWORD);
                                output.accept(ModItems.YZ_AXE);
                                output.accept(ModItems.VOID_STAFF);
                                output.accept(ModItems.FLAME_STAFF);
                                output.accept(ModItems.SKY_STAR_STAFF);
                        })
                        .build();

        // ── 悠哉世界 - 原材料 ──
        public static final ResourceKey<CreativeModeTab> YOUZAI_MATERIALS_KEY = ResourceKey.create(
                        Registries.CREATIVE_MODE_TAB,
                        Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "youzai_materials"));

        public static final CreativeModeTab YOUZAI_MATERIALS = FabricCreativeModeTab.builder()
                        .icon(() -> new ItemStack(ModItems.RAW_YZ))
                        .title(Component.translatable("itemGroup.youzaiworldcore.youzai_materials"))
                        .displayItems((params, output) -> {
                                output.accept(ModItems.RAW_YZ);
                                output.accept(ModItems.YZ_INGOT);
                                output.accept(ModItems.YZ_NUGGET);
                        })
                        .build();

        // ── 悠哉世界 - 实用物品 ──
        public static final ResourceKey<CreativeModeTab> YOUZAI_UTILITIES_KEY = ResourceKey.create(
                        Registries.CREATIVE_MODE_TAB,
                        Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "youzai_utilities"));

        public static final CreativeModeTab YOUZAI_UTILITIES = FabricCreativeModeTab.builder()
                        .icon(() -> new ItemStack(ModItems.HEART_OF_GUARDIANSHIP))
                        .title(Component.translatable("itemGroup.youzaiworldcore.youzai_utilities"))
                        .displayItems((params, output) -> {
                                output.accept(ModItems.HEART_OF_GUARDIANSHIP);
                                output.accept(ModItems.INVISIBLE_ITEM_FRAME);
                                output.accept(ModItems.INVISIBLE_GLOW_ITEM_FRAME);
                                output.accept(ModItems.TELEPORT_STONE);
                        })
                        .build();

        // ── 悠哉世界 - 工具包 ──
        public static final ResourceKey<CreativeModeTab> YOUZAI_KITS_KEY = ResourceKey.create(
                        Registries.CREATIVE_MODE_TAB,
                        Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "youzai_kits"));

        public static final CreativeModeTab YOUZAI_KITS = FabricCreativeModeTab.builder()
                        .icon(() -> new ItemStack(Items.SHULKER_BOX))
                        .title(Component.translatable("itemGroup.youzaiworldcore.youzai_kits"))
                        .displayItems((params, output) -> {
                                var holders = params.holders();
                                output.accept(PresetItems.createPreset01(holders));
                                output.accept(PresetItems.createPreset02(holders));
                                output.accept(PresetItems.createPreset03(holders));
                                output.accept(PresetItems.createPreset04(holders));
                                output.accept(PresetItems.createPreset05(holders));
                                output.accept(PresetItems.createPreset06(holders));
                                output.accept(PresetItems.createPreset07(holders));
                                output.accept(PresetItems.createPreset08(holders));
                                output.accept(PresetItems.createPreset09(holders));
                        })
                        .build();

        // ── 悠哉世界 - 附魔 ──
        public static final ResourceKey<CreativeModeTab> YOUZAI_ENCHANTMENTS_KEY = ResourceKey.create(
                        Registries.CREATIVE_MODE_TAB,
                        Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "youzai_enchantments"));

        public static final CreativeModeTab YOUZAI_ENCHANTMENTS = FabricCreativeModeTab.builder()
                        .icon(() -> new ItemStack(Items.ENCHANTED_BOOK))
                        .title(Component.translatable("itemGroup.youzaiworldcore.youzai_enchantments"))
                        .displayItems((params, output) -> {
                                var enchantmentRegistry = params.holders().lookupOrThrow(Registries.ENCHANTMENT);
                                int total = 0;
                                for (var key : ModEnchantments.ALL) {
                                        var holderOpt = enchantmentRegistry.get(key);
                                        if (holderOpt.isPresent()) {
                                                var holder = holderOpt.get();
                                                int maxLevel = holder.value().getMaxLevel();
                                                for (int level = 1; level <= maxLevel; level++) {
                                                        output.accept(EnchantmentHelper.createBook(new EnchantmentInstance(holder, level)));
                                                        total++;
                                                }
                                                DebugLogger.debug("ModCreativeModeTabs",
                                                                "YOUZAI_ENCHANTMENTS: added enchantment %s (max level %d)",
                                                                key.identifier(), maxLevel);
                                        } else {
                                                DebugLogger.warn("ModCreativeModeTabs",
                                                                "YOUZAI_ENCHANTMENTS: enchantment %s not found in registry, skipped",
                                                                key.identifier());
                                        }
                                }
                                DebugLogger.info("ModCreativeModeTabs",
                                                "YOUZAI_ENCHANTMENTS tab populated with %d enchanted book(s)", total);
                        })
                        .build();

        public static void initialize() {
                Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_BLOCKS_KEY, YOUZAI_BLOCKS);
                Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_TOOLS_WEAPONS_KEY, YOUZAI_TOOLS_WEAPONS);
                Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_MATERIALS_KEY, YOUZAI_MATERIALS);
                Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_UTILITIES_KEY, YOUZAI_UTILITIES);
                Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_KITS_KEY, YOUZAI_KITS);
                Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_ENCHANTMENTS_KEY, YOUZAI_ENCHANTMENTS);
                DebugLogger.info("ModCreativeModeTabs", "Registered YOUZAI_ENCHANTMENTS creative tab (%s)",
                                YOUZAI_ENCHANTMENTS_KEY.identifier());
        }
}
