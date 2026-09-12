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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.item.preset.PresetItems;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 注册唯一的「悠哉世界」创造标签页，按七类依次收录全部物品。
 * <p>填充标签页时同步生成内置分组索引，供客户端折叠展示复用；
 * 完整物品栈仍交给原版管理，搜索及关闭分组时均可直接取用全部变体。</p>
 */
@SuppressWarnings("null")
public final class ModCreativeModeTabs {

    private static final String MODULE = "ModCreativeModeTabs";

    public static final ResourceKey<CreativeModeTab> YOUZAI_WORLD_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "youzai_world"));

    public static final CreativeModeTab YOUZAI_WORLD = FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(ModBlocks.YZ_BLOCK))
            .title(Component.translatable("itemGroup.youzaiworldcore.youzai_world"))
            .displayItems(ModCreativeModeTabs::displayItems)
            .build();

    private static List<ItemGroup> itemGroups = List.of();

    private ModCreativeModeTabs() {
    }

    /** 内置分类的组名与物品 ID；只用于匹配，不替代包含组件的原始物品栈。 */
    public record ItemGroup(String groupName, List<String> itemIds) {
        public ItemGroup {
            itemIds = List.copyOf(itemIds);
        }
    }

    /** 返回最近一次标签页内容生成时的分类索引，保持与物品清单相同的顺序。 */
    public static List<ItemGroup> getItemGroups() {
        return itemGroups;
    }

    /** 注册一个创造标签页；七个分类由客户端共用的物品分组模型展示。 */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, YOUZAI_WORLD_KEY, YOUZAI_WORLD);
        DebugLogger.info(MODULE, "已注册悠哉世界创造标签页：%s", YOUZAI_WORLD_KEY.identifier());
        DebugLogger.exiting(MODULE, "initialize");
    }

    private static void displayItems(CreativeModeTab.ItemDisplayParameters params, CreativeModeTab.Output output) {
        List<ItemGroup> rebuilt = new ArrayList<>();

        // ===== 方块 =====
        addGroup(rebuilt, "youzai_blocks", output, items -> {
            items.accept(ModBlocks.YZ_ORE);
            items.accept(ModBlocks.DEEPSLATE_YZ_ORE);
            items.accept(ModBlocks.RAW_YZ_BLOCK);
            items.accept(ModBlocks.YZ_BLOCK);
            items.accept(ModBlocks.DECOMPOSITION_TABLE);
            items.accept(ModBlocks.FLY_BEACON);
            items.accept(ModBlocks.TP_ANCHOR);
            items.accept(ModBlocks.MAGIC_TABLE);
            items.accept(ModBlocks.MOB_PRESSURE_PLATE);
            items.accept(ModBlocks.DUPLICATE_BLOCK);
            items.accept(ModBlocks.NOT_GATE_REDSTONE_REPEATER);
            items.accept(ModBlocks.WIRELESS_REDSTONE_TRANSMITTER);
            items.accept(ModBlocks.WIRELESS_REDSTONE_RECEIVER);
            // 大字牌系列（12 木质 + 7 矿物），顺序见 ModBlocks.LARGE_SIGNS。
            for (var largeSign : ModBlocks.LARGE_SIGNS) {
                items.accept(largeSign);
            }
        });

        // ===== 工具与武器 =====
        addGroup(rebuilt, "youzai_tools_weapons", output, items -> {
            items.accept(ModItems.YZ_SHOVEL);
            items.accept(ModItems.YZ_PICKAXE);
            items.accept(ModItems.YZ_HOE);
            items.accept(ModItems.YZ_SWORD);
            items.accept(ModItems.YZ_AXE);
            items.accept(ModItems.VOID_STAFF);
            items.accept(ModItems.FLAME_STAFF);
            items.accept(ModItems.SKY_STAR_STAFF);
        });

        // ===== 材料 =====
        addGroup(rebuilt, "youzai_materials", output, items -> {
            items.accept(ModItems.RAW_YZ);
            items.accept(ModItems.YZ_INGOT);
            items.accept(ModItems.YZ_NUGGET);
            items.accept(ModItems.PRIMOGEM);
            items.accept(ModItems.SWEET_MADAME);
        });

        // ===== 实用物品 =====
        addGroup(rebuilt, "youzai_utilities", output, items -> {
            items.accept(ModItems.HEART_OF_GUARDIANSHIP);
            items.accept(ModItems.INVISIBLE_ITEM_FRAME);
            items.accept(ModItems.INVISIBLE_GLOW_ITEM_FRAME);
            items.accept(ModItems.TELEPORT_STONE);
            items.accept(ModItems.WARP_SCROLL);
            items.accept(ModItems.RETURN_SCROLL);
            items.accept(ModItems.FLASHING_INK_SAC);
            items.accept(ModItems.MUSIC_DISC_CLOUD_GENSHIN);
        });

        // ===== 画作 =====
        addGroup(rebuilt, "youzai_paintings", output, items -> {
            items.accept(ModItems.MEME_PAINTING_01);
            items.accept(ModItems.MEME_PAINTING_02);
            items.accept(ModItems.MEME_PAINTING_03);
            items.accept(ModItems.MEME_PAINTING_04);
            items.accept(ModItems.MEME_PAINTING_05);
            items.accept(ModItems.MEME_PAINTING_06);
            items.accept(ModItems.MEME_PAINTING_07);
            items.accept(ModItems.MEME_PAINTING_08);
            items.accept(ModItems.MEME_PAINTING_09);
            items.accept(ModItems.MEME_PAINTING_10);
            items.accept(ModItems.MEME_PAINTING_11);
            items.accept(ModItems.MEME_PAINTING_12);
        });

        // ===== 工具包 =====
        addGroup(rebuilt, "youzai_kits", output, items -> {
            var holders = params.holders();
            items.accept(PresetItems.createPreset01(holders));
            items.accept(PresetItems.createPreset02(holders));
            items.accept(PresetItems.createPreset03(holders));
            items.accept(PresetItems.createPreset04(holders));
            items.accept(PresetItems.createPreset05(holders));
            items.accept(PresetItems.createPreset06(holders));
            items.accept(PresetItems.createPreset07(holders));
            items.accept(PresetItems.createPreset08(holders));
            items.accept(PresetItems.createPreset09(holders));
        });

        // ===== 附魔书 =====
        addGroup(rebuilt, "youzai_enchantments", output, items -> {
            var enchantmentRegistry = params.holders().lookupOrThrow(Registries.ENCHANTMENT);
            int total = 0;
            for (var key : ModEnchantments.ALL) {
                var holderOpt = enchantmentRegistry.get(key);
                if (holderOpt.isPresent()) {
                    var holder = holderOpt.get();
                    int maxLevel = holder.value().getMaxLevel();
                    for (int level = 1; level <= maxLevel; level++) {
                        items.accept(EnchantmentHelper.createBook(new EnchantmentInstance(holder, level)));
                        total++;
                    }
                    DebugLogger.debug(MODULE, "已加入附魔 %s 的全部等级（最高 %d 级）", key.identifier(), maxLevel);
                } else {
                    DebugLogger.warn(MODULE, "注册表中未找到附魔 %s，跳过对应附魔书", key.identifier());
                }
            }
            DebugLogger.debug(MODULE, "附魔分类已加入 %d 本附魔书", total);
        });

        itemGroups = List.copyOf(rebuilt);
        DebugLogger.debug(MODULE, "已生成悠哉世界创造标签页及 %d 个内置分类", itemGroups.size());
    }

    private static void addGroup(List<ItemGroup> groups, String groupName, CreativeModeTab.Output output,
                                 Consumer<CreativeModeTab.Output> generator) {
        Set<String> itemIds = new LinkedHashSet<>();
        generator.accept((stack, visibility) -> {
            // 原样传递组件与可见范围，索引仅记录可在当前标签页展示的物品类型。
            output.accept(stack, visibility);
            if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY) {
                itemIds.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        });
        groups.add(new ItemGroup(groupName, List.copyOf(itemIds)));
    }
}
