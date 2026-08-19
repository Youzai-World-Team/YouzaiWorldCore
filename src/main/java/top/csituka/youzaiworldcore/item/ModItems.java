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
import net.minecraft.world.food.FoodProperties;

import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.item.tool.*;
import top.csituka.youzaiworldcore.util.DebugLogger;

import net.minecraft.world.entity.decoration.painting.PaintingVariant;

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

    /** 闪烁墨染：使告示牌与大字牌文字按 20 tick 周期闪烁。 */
    public static final Item FLASHING_INK_SAC = registerFlashingInkSac(
            "glow_ink_sac",
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

    // ── Meme 画作（10 张）──

    public static final Item MEME_PAINTING_01 = registerMemePainting("meme_01");
    public static final Item MEME_PAINTING_02 = registerMemePainting("meme_02");
    public static final Item MEME_PAINTING_03 = registerMemePainting("meme_03");
    public static final Item MEME_PAINTING_04 = registerMemePainting("meme_04");
    public static final Item MEME_PAINTING_05 = registerMemePainting("meme_05");
    public static final Item MEME_PAINTING_06 = registerMemePainting("meme_06");
    public static final Item MEME_PAINTING_07 = registerMemePainting("meme_07");
    public static final Item MEME_PAINTING_08 = registerMemePainting("meme_08");
    public static final Item MEME_PAINTING_09 = registerMemePainting("meme_09");
    public static final Item MEME_PAINTING_10 = registerMemePainting("meme_10");
    public static final Item MEME_PAINTING_11 = registerMemePainting("meme_11");
    public static final Item MEME_PAINTING_12 = registerMemePainting("meme_12");

    // ── Genshin 主题物品 ──

    /**
     * 「原石」原材料——悠哉世界货币体系的基础资源之一，Epic 紫色稀有度，
     * 普通玩家无法在创造模式以外直接获得（仅创造模式 + 模组发放）。
     */
    public static final Item PRIMOGEM = registerPrimogem("primogem");

    /**
     * 「甜甜玛德琳」食物——效果等同原版熟鸡肉：
     * nutrition=6（恢复 6 点饥饿值）、saturation=0.6。
     */
    public static final Item SWEET_MADAME = registerSweetMadame("sweet_madame");

    private static Item register(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new Item(settings.setId(itemKey));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    private static Item registerFlashingInkSac(String name, Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new FlashingInkSacItem(settings.setId(itemKey));
        DebugLogger.info("ModItems", "注册物品 %s (FlashingInkSac)".formatted(itemKey.identifier()));
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

    // ===== Meme 画作注册帮助器 =====

    /**
     * 注册一张自定义 meme 画物品。
     * <p>
     * {@link MemePaintingItem} 构造器会自动将变体→物品映射存入
     * {@link MemePaintingItem#VARIANT_ITEM_MAP}，供 Mixin 使用。
     * </p>
     *
     * @param id 画作 ID（不含命名空间，如 {@code meme_01}）
     */
    private static Item registerMemePainting(String id) {
        ResourceKey<PaintingVariant> variantKey = ResourceKey.create(
                Registries.PAINTING_VARIANT,
                Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, id));
        ResourceKey<Item> itemKey = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, id));
        Item item = new MemePaintingItem(variantKey, new Item.Properties().setId(itemKey).rarity(Rarity.UNCOMMON));
        DebugLogger.info("ModItems", "注册物品 %s (MemePainting)".formatted(itemKey.identifier()));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    /**
     * 「原石」注册：纯原材料物品，Epic 紫色稀有度，无功能属性。
     * <p>
     * 纹理文件应放在 {@code assets/youzaiworldcore/textures/item/primogem.png}，
     * 模型 JSON 已由 {@code items/primogem.json} + {@code models/item/primogem.json} 提供。
     * </p>
     */
    private static Item registerPrimogem(String name) {
        ResourceKey<Item> itemKey = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        Item item = new Item(new Item.Properties().setId(itemKey).rarity(Rarity.EPIC));
        DebugLogger.info("ModItems", "注册物品 %s (Primogem raw material, rarity=EPIC)".formatted(itemKey.identifier()));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    /**
     * 「甜甜玛德琳」注册：食物物品，营养与饱食度等同原版熟鸡肉
     * （nutrition=6, saturation=0.6）。
     * <p>
     * 通过 {@code Item.Properties.food(FoodProperties)} 注入 {@link FoodProperties}
     * 数据组件，无需自定义 Item 类。
     * </p>
     */
    private static Item registerSweetMadame(String name) {
        ResourceKey<Item> itemKey = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        FoodProperties food = new FoodProperties.Builder()
                .nutrition(6)
                .saturationModifier(0.6f)
                .build();
        Item item = new SweetMadameItem(new Item.Properties().setId(itemKey).rarity(Rarity.EPIC).food(food));
        DebugLogger.info("ModItems", "注册物品 %s (SweetMadame food: nutrition=6, sat=0.6, rarity=EPIC)".formatted(itemKey.identifier()));
        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }
}
