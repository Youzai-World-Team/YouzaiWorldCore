package top.csituka.youzaiworldcore.client.inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版创造标签页的默认可编辑规则，以及可选模组的内置兼容分组。
 * <p>规则参考 Inventory Item Groups（Copyright (c) 2026 Bizarre Cube，MIT），
 * 完整授权见 META-INF/licenses/inventory-item-groups.txt。</p>
 */
public final class InventoryItemGroupDefaults {

    private static final String BACKPACKS_TAB = "sophisticatedbackpacks:main";
    private static final List<InventoryItemGroupRule> BACKPACKS_GROUPS = List.of(
            new InventoryItemGroupRule("backpacks", BACKPACKS_TAB,
                    List.of("sophisticatedbackpacks:backpack"), List.of(), List.of()),
            new InventoryItemGroupRule("upgraded_backpacks", BACKPACKS_TAB,
                    List.of(), List.of("_backpack"), List.of()),
            new InventoryItemGroupRule("stack_upgrades", BACKPACKS_TAB,
                    List.of("sophisticatedbackpacks:stack_upgrade_omega_tier",
                            "sophisticatedbackpacks:stack_upgrade_starter_tier"),
                    List.of("sophisticatedbackpacks:stack_upgrade_tier_"), List.of("_conversion")),
            new InventoryItemGroupRule("stack_upgrade_conversions", BACKPACKS_TAB,
                    List.of(), List.of("_conversion"), List.of()));

    private InventoryItemGroupDefaults() {
    }

    /** 创建原版分类默认规则；悠哉世界的内置分类由标签页生成，独立于用户规则保存。 */
    public static List<InventoryItemGroupRule> create() {
        List<InventoryItemGroupRule> defaults = new ArrayList<>();

        String tabId = "minecraft:building_blocks";
        addGroup(defaults, tabId, "logs", List.of("log", "stem", "bamboo_block"), List.of("stripped"));
        addGroup(defaults, tabId, "woods", List.of("wood", "hyphae"), List.of("stripped"));
        addGroup(defaults, tabId, "stripped_logs", List.of("log", "stem", "bamboo_block"));
        addGroup(defaults, tabId, "stripped_woods", List.of("wood", "hyphae"));
        addGroup(defaults, tabId, "stairs", List.of("stair"));
        addGroup(defaults, tabId, "slabs", List.of("slab"));
        addGroup(defaults, tabId, "planks", List.of("planks", "mosaic"));
        addGroup(defaults, tabId, "fence_gates", List.of("fence_gate"));
        addGroup(defaults, tabId, "fences", List.of("fence"));
        addGroup(defaults, tabId, "trapdoors", List.of("trapdoor"));
        addGroup(defaults, tabId, "doors", List.of("door"));
        addGroup(defaults, tabId, "pressure_plates", List.of("pressure_plate"));
        addGroup(defaults, tabId, "buttons", List.of("button"));
        addGroup(defaults, tabId, "bars", List.of("bar"), List.of("cinnabar"));
        addGroup(defaults, tabId, "chains", List.of("chain"));
        addGroup(defaults, tabId, "copper", List.of("copper"));
        addGroup(defaults, tabId, "walls", List.of("wall"));
        addGroup(defaults, tabId, "decorative_stone", List.of("bricks", "chiseled", "tiles", "polished"));
        addGroup(defaults, tabId, "sandstone", List.of("sandstone"));

        tabId = "minecraft:colored_blocks";
        addGroup(defaults, tabId, "wool", List.of("wool"));
        addGroup(defaults, tabId, "carpets", List.of("carpet"));
        addGroup(defaults, tabId, "glazed_terracotta", List.of("glazed_terracotta"));
        addGroup(defaults, tabId, "terracotta", List.of("terracotta"));
        addGroup(defaults, tabId, "concrete_powder", List.of("concrete_powder"));
        addGroup(defaults, tabId, "concrete", List.of("concrete"));
        addGroup(defaults, tabId, "glass_panes", List.of("glass_pane"));
        addGroup(defaults, tabId, "glass", List.of("glass"));
        addGroup(defaults, tabId, "shulker_boxes", List.of("shulker_box"));
        addGroup(defaults, tabId, "candles", List.of("candle"));
        addGroup(defaults, tabId, "banners", List.of("banner"));
        addGroup(defaults, tabId, "beds", List.of("bed"));

        tabId = "minecraft:natural_blocks";
        addGroup(defaults, tabId, "ores", List.of("_ore", "debris", "raw_"));
        addGroup(defaults, tabId, "mushrooms", List.of("mushroom", "fungus"));
        addGroup(defaults, tabId, "saplings", List.of("sapling", "propagule"));
        addGroup(defaults, tabId, "ground_cover", List.of("fern", "_grass", "bush", "_sprouts", "hanging_moss", "_vines"), List.of("_bush"));
        addGroup(defaults, tabId, "seeds", List.of("seeds", "_pod"));
        addGroup(defaults, tabId, "flowers", List.of("dandelion", "poppy", "orchid", "allium", "tulip", "daisy", "cornflower", "torchflower", "azure_bluet", "valley", "cactus_flower", "eyeblossom", "rose", "petals", "wildflower", "crimson_roots", "warped_roots", "sunflower", "peony", "lilac", "pitcher_plant"));
        addGroup(defaults, tabId, "leaves", List.of("leaves"));
        addGroup(defaults, tabId, "coral_blocks", List.of("coral_block"));
        addGroup(defaults, tabId, "coral_decorations", List.of("coral"));
        addGroup(defaults, tabId, "stone", List.of(":stone", "diorite", "andesite", "granite", "tuff", "basalt", "blackstone", "deepslate"));
        addGroup(defaults, tabId, "logs", List.of("log", "stem"));

        tabId = "minecraft:functional_blocks";
        addGroup(defaults, tabId, "lanterns", List.of("lantern"), List.of("sea"));
        addGroup(defaults, tabId, "chains", List.of("chain"));
        addGroup(defaults, tabId, "bulbs", List.of("bulb"));
        addGroup(defaults, tabId, "anvils", List.of("anvil"));
        addGroup(defaults, tabId, "lightning_rods", List.of("lightning_rod"));
        addGroup(defaults, tabId, "shelves", List.of("_shelf"));
        addGroup(defaults, tabId, "hanging_signs", List.of("hanging_sign"));
        addGroup(defaults, tabId, "signs", List.of("sign"));
        addGroup(defaults, tabId, "chests", List.of("chest"));
        addGroup(defaults, tabId, "shulker_boxes", List.of("shulker_box"));
        addGroup(defaults, tabId, "beds", List.of("_bed"));
        addGroup(defaults, tabId, "candles", List.of("candle"));
        addGroup(defaults, tabId, "banners", List.of("banner"));
        addGroup(defaults, tabId, "skulls", List.of("head", "skull"));
        addGroup(defaults, tabId, "golem_statues", List.of("golem_statue"));
        addGroup(defaults, tabId, "infested_stone", List.of("infested"));
        addGroup(defaults, tabId, "paintings", List.of("painting"));

        tabId = "minecraft:redstone_blocks";
        addGroup(defaults, tabId, "bulbs", List.of("bulb"));
        addGroup(defaults, tabId, "pressure_plates", List.of("pressure_plate"));
        addGroup(defaults, tabId, "transport", List.of("minecart", "boat", "_raft"));
        addGroup(defaults, tabId, "chests", List.of("chest"));
        addGroup(defaults, tabId, "rails", List.of("rail"));

        tabId = "minecraft:tools_and_utilities";
        addGroup(defaults, tabId, "shovels", List.of("shovel"));
        addGroup(defaults, tabId, "pickaxes", List.of("pickaxe"));
        addGroup(defaults, tabId, "axes", List.of("axe"));
        addGroup(defaults, tabId, "hoes", List.of("hoe"));
        addGroup(defaults, tabId, "bundles", List.of("bundle"));
        addGroup(defaults, tabId, "firework_rockets", List.of("firework_rocket"));
        addGroup(defaults, tabId, "harnesses", List.of("harness"));
        addGroup(defaults, tabId, "chest_boats", List.of("chest_boat", "chest_raft"));
        addGroup(defaults, tabId, "boats", List.of("boat", "_raft"));
        addGroup(defaults, tabId, "rails", List.of("rail"));
        addGroup(defaults, tabId, "minecarts", List.of("minecart"));
        addGroup(defaults, tabId, "discs", List.of("disc"));
        addGroup(defaults, tabId, "goat_horns", List.of("goat_horn"));
        addGroup(defaults, tabId, "creature_buckets", List.of("cod_bucket", "salmon_bucket", "tropical_fish_bucket", "pufferfish_bucket", "axolotl_bucket", "tadpole_bucket", "sulfur_cube_bucket"));

        tabId = "minecraft:combat";
        addGroup(defaults, tabId, "swords", List.of("sword"));
        addGroup(defaults, tabId, "spears", List.of("spear"));
        addGroup(defaults, tabId, "axes", List.of("axe"));
        addGroup(defaults, tabId, "helmets", List.of("helmet"));
        addGroup(defaults, tabId, "chestplates", List.of("chestplate"));
        addGroup(defaults, tabId, "leggings", List.of("leggings"));
        addGroup(defaults, tabId, "boots", List.of("boots"));
        addGroup(defaults, tabId, "horse_armor", List.of("horse_armor"));
        addGroup(defaults, tabId, "nautilus_armor", List.of("nautilus_armor"));
        addGroup(defaults, tabId, "eggs", List.of("egg"));
        addGroup(defaults, tabId, "tipped_arrows", List.of("tipped_arrow"));
        addGroup(defaults, tabId, "firework_rockets", List.of("firework_rocket"));

        tabId = "minecraft:food_and_drinks";
        addGroup(defaults, tabId, "suspicious_stews", List.of("suspicious_stew"));
        addGroup(defaults, tabId, "ominous_bottles", List.of("ominous_bottle"));
        addGroup(defaults, tabId, "splash_potions", List.of("splash_potion"));
        addGroup(defaults, tabId, "lingering_potions", List.of("lingering_potion"));
        addGroup(defaults, tabId, "potions", List.of("potion"));
        addGroup(defaults, tabId, "cooked_food", List.of("cooked"));
        addGroup(defaults, tabId, "raw_food", List.of("beef", "porkchop", "mutton", "chicken", "rabbit", ":cod", "salmon"), List.of("rabbit_"));

        tabId = "minecraft:ingredients";
        addGroup(defaults, tabId, "dyes", List.of("dye"));
        addGroup(defaults, tabId, "banner_patterns", List.of("banner_pattern"));
        addGroup(defaults, tabId, "pottery_sherds", List.of("pottery_sherd"));
        addGroup(defaults, tabId, "smithing_templates", List.of("smithing_template"));
        addGroup(defaults, tabId, "enchanted_books", List.of("enchanted_book"));
        return List.copyOf(defaults);
    }

    /**
     * 指定创造标签页的内置兼容规则，按声明顺序在玩家自定义规则之后匹配。
     * <p>只使用注册 ID，不依赖第三方模组类，也不写入或覆盖玩家现有配置。</p>
     */
    public static List<InventoryItemGroupRule> getBuiltInRules(String tabId) {
        return BACKPACKS_TAB.equals(tabId) ? BACKPACKS_GROUPS : List.of();
    }

    private static void addGroup(List<InventoryItemGroupRule> rules, String tabId, String name,
                                 List<String> included) {
        addGroup(rules, tabId, name, included, List.of());
    }

    private static void addGroup(List<InventoryItemGroupRule> rules, String tabId, String name,
                                 List<String> included, List<String> excluded) {
        rules.add(new InventoryItemGroupRule(name, tabId, List.of(), included, excluded));
    }
}
