package top.csituka.youzaiworldcore.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.item.tool.*;

public class ModItems {

    public static final TagKey<Item> REPAIRS_YZ_TOOL = TagKey.create(
            Registries.ITEM,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "repairs_yz_tool")
    );

    public static final ToolMaterial YZ_TOOL_MATERIAL = new ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1800,
            8.0F,
            1.5F,
            10,
            REPAIRS_YZ_TOOL
    );

    public static final Item RAW_YZ = register(
            "raw_yz",
            new Item.Properties()
    );

    public static final Item YZ_INGOT = register(
            "yz_ingot",
            new Item.Properties()
    );

    public static final Item YZ_NUGGET = register(
            "yz_nugget",
            new Item.Properties()
    );

    public static final Item YZ_SHOVEL = registerShovel(
            "yz_shovel",
            YZ_TOOL_MATERIAL, 1.5F, -3.0F
    );

    public static final Item YZ_PICKAXE = registerPickaxe(
            "yz_pickaxe",
            YZ_TOOL_MATERIAL, 1.0F, -2.8F
    );

    public static final Item YZ_HOE = registerHoe(
            "yz_hoe",
            YZ_TOOL_MATERIAL, 0.0F, -3.0F
    );

    public static final Item YZ_SWORD = registerSword(
            "yz_sword",
            YZ_TOOL_MATERIAL, 7.5F, -2.4F
    );

    public static final Item YZ_AXE = registerAxe(
            "yz_axe",
            YZ_TOOL_MATERIAL, 10.5F, -3.0F
    );

    public static final Item HEART_OF_GUARDIANSHIP = registerHeartOfGuardianship(
            "heart_of_guardianship",
            new Item.Properties().rarity(Rarity.RARE)
    );

    public static final Item LOGO = register(
            "logo",
            new Item.Properties()
    );

    public static final Item FLY_CORE = registerFlyCore(
            "fly_core",
            new Item.Properties()
    );

    private static Item register(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new Item(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerHeartOfGuardianship(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new HeartOfGuardianshipItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerShovel(String name, ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new YzShovelItem(material, attackDamageBaseline, attackSpeedBaseline, new Item.Properties().setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerPickaxe(String name, ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new YzPickaxeItem(material, attackDamageBaseline, attackSpeedBaseline, new Item.Properties().setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerHoe(String name, ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new YzHoeItem(material, attackDamageBaseline, attackSpeedBaseline, new Item.Properties().setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerSword(String name, ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new YzSwordItem(material, attackDamageBaseline, attackSpeedBaseline, new Item.Properties().setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerAxe(String name, ToolMaterial material, float attackDamageBaseline, float attackSpeedBaseline) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new YzAxeItem(material, attackDamageBaseline, attackSpeedBaseline, new Item.Properties().setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerFlyCore(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new FlyCoreItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    public static void initialize() {
    }
}