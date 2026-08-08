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
import top.csituka.youzaiworldcore.util.DebugLogger;

@SuppressWarnings("null")
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

    public static final Item LOGO = registerLogo(
            "logo",
            new Item.Properties()
    );

    public static final Item VOID_STAFF = registerVoidStaff(
            "void_staff",
            new Item.Properties()
    );

    public static final Item FLAME_STAFF = registerFlameStaff(
            "flame_staff",
            new Item.Properties()
    );

    public static final Item SKY_STAR_STAFF = registerSkyStarStaff(
            "sky_star_staff",
            new Item.Properties()
    );

    public static final Item INVISIBLE_ITEM_FRAME = registerInvisibleItemFrame(
            "invisible_item_frame",
            new Item.Properties()
    );

    public static final Item INVISIBLE_GLOW_ITEM_FRAME = registerInvisibleGlowItemFrame(
            "invisible_glow_item_frame",
            new Item.Properties()
    );

    public static final Item TELEPORT_STONE = registerTeleportStone(
            "teleport_stone",
            new Item.Properties()
    );

    public static final Item WARP_SCROLL = registerWarpScroll(
            "warp_scroll",
            new Item.Properties()
    );

    public static final Item RETURN_SCROLL = registerReturnScroll(
            "return_scroll",
            new Item.Properties()
    );

    /**
     * 《云·原神》音乐唱片。
     * <p>
     * 26.2 起，原版 {@code MusicDiscItem} 子类被移除，唱片统一通过
     * {@code Item.Properties.jukeboxPlayable(ResourceKey&lt;JukeboxSong&gt;)} 注入。
     * {@code JukeboxSong} 本体由 datapack 提供（见
     * {@code data/youzaiworldcore/jukebox_song/cloud_genshin.json}），而其内部持有的
     * {@code Holder&lt;SoundEvent&gt;} 又依赖 {@code BuiltInRegistries.SOUND_EVENT}
     * 中已注册的 {@link top.csituka.youzaiworldcore.sound.ModSoundEvents#MUSIC_DISC_CLOUD_GENSHIN}。
     * </p>
     * <p>
     * 稀有度设为 {@link Rarity#EPIC}，按照《我的世界》原版唱片的标准为不可堆叠（{@code stacksTo(1)}）。
     * 物品名称读取 {@code item.youzaiworldcore.music_disc_cloud_genshin}，
     * hover 描述则由 JukeboxSong 的 description 提供，会读取
     * {@code item.youzaiworldcore.music_disc_cloud_genshin.desc}（在 datapack JSON 中显式覆盖）。
     * </p>
     */
    public static final Item MUSIC_DISC_CLOUD_GENSHIN = registerMusicDiscCloudGenshin(
            "music_disc_cloud_genshin",
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
                    .jukeboxPlayable(
                            net.minecraft.resources.ResourceKey.create(
                                    net.minecraft.core.registries.Registries.JUKEBOX_SONG,
                                    Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "cloud_genshin")
                            )
                    )
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

    private static Item registerVoidStaff(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new VoidStaffItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerLogo(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new LogoItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerFlameStaff(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new FlameStaffItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerSkyStarStaff(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new SkyStarStaffItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerInvisibleItemFrame(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new InvisibleItemFrameItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerInvisibleGlowItemFrame(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new InvisibleGlowItemFrameItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerTeleportStone(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new TeleportStoneItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerWarpScroll(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new WarpScrollItem(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerReturnScroll(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new ReturnScrollItem(settings.setId(itemKey));
        DebugLogger.info("ModItems", "注册物品 %s (ReturnScroll)".formatted(itemKey.identifier()));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    /**
     * 《云·原神》唱片的注册器——保持与原版唱片一致的「Items#registerItem」路径，
     * 使用最朴素的 {@link Item} 构造以让 {@code Item.Properties.jukeboxPlayable(...)}
     * 已经注入的 {@code JUKEBOX_PLAYABLE} 数据组件生效。
     */
    private static Item registerMusicDiscCloudGenshin(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new Item(settings.setId(itemKey));
        DebugLogger.info("ModItems",
                "注册物品 %s (MusicDisc<CloudGenshin>, stacksTo=1, rarity=EPIC)",
                itemKey.identifier());
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    public static void initialize() {
    }
}