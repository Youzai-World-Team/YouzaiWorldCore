package top.csituka.youzaiworldcore.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.item.tool.YzHoeItem;
import top.csituka.youzaiworldcore.item.tool.YzPickaxeItem;
import top.csituka.youzaiworldcore.item.tool.YzShovelItem;

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

    public static final Item HEART_OF_GUARDIANSHIP = register(
            "heart_of_guardianship",
            new Item.Properties()
    );

    private static Item register(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new Item(settings.setId(itemKey));
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

    public static void initialize() {
    }
}
