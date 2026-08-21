package top.csituka.youzaiworldcore.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

import top.csituka.youzaiworldcore.YouzaiworldCore;

@SuppressWarnings("null")
public class ModBlocks {

    public static final Block YZ_ORE = register(
            "yz_ore",
            props -> new DropExperienceBlock(UniformInt.of(2, 5), props),
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 3.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final Block DEEPSLATE_YZ_ORE = register(
            "deepslate_yz_ore",
            props -> new DropExperienceBlock(UniformInt.of(2, 5), props),
            BlockBehaviour.Properties.of()
                    .strength(4.5f, 3.0f)
                    .sound(SoundType.DEEPSLATE)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final Block RAW_YZ_BLOCK = register(
            "raw_yz_block",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final Block YZ_BLOCK = register(
            "yz_block",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final DecompositionTableBlock DECOMPOSITION_TABLE = register(
            "decomposition_table",
            DecompositionTableBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 3.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops(),
            true
    );

    public static final FlyBeaconBlock FLY_BEACON = register(
            "fly_beacon",
            FlyBeaconBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 3.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(FlyBeaconBlock.ACTIVE) ? 12 : 0),
            true
    );

    public static final TeleportAnchorBlock TP_ANCHOR = register(
            "tp_anchor",
            TeleportAnchorBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(5.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 15),
            true
    );

    /**
     * 魔力台：装饰性方块，当前无任何交互（未来可能用于魔力合成等）。
     * <p>
     * 物理特性完全对齐原版附魔台：
     * <ul>
     *   <li>硬度 5.0</li>
     *   <li>爆炸抗性 1200.0</li>
     *   <li>需稿子才能掉落（{@code requiresCorrectToolForDrops}），因此「用稿子挖速度较快」</li>
     *   <li>默认石头声音类型（与附魔台一致）</li>
     * </ul>
     * 自定义特性：固定发光 2 级（贴图本身带荧光效果，故不开玩家可感知光照）。
     * <p>
     * 用普通 {@link Block} 而非 {@code BaseEntityBlock}：当前无 BlockEntity、无 GUI、无红石信号。
     */
    public static final Block MAGIC_TABLE = register(
            "magic_table",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 2),
            true
    );

    /**
     * 生物感压板：仅非玩家的 {@link net.minecraft.world.entity.LivingEntity}
     * （动物、怪物、Boss 等）能够触发，向相邻方块输出强度 15 的红石信号。
     * <p>
     * 玩家踩踏不触发；掉落物 / 经验球 / 投射物等不是 {@code LivingEntity}，
     * 自动不会触发。
     *
     * @see MobPressurePlateBlock
     */
    public static final MobPressurePlateBlock MOB_PRESSURE_PLATE = register(
            "mob_pressure_plate",
            MobPressurePlateBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(0.5f, 0.5f) // 与原版压力板一致：低硬度易碎但不可爆炸
                    .sound(SoundType.STONE)
                    .noCollision(), // 压力板不能用普通碰撞（26.2 修正了原版 noCollission 拼写）
            true
    );

    /**
     * 复制方块：创造模式工具方块。
     * <p>
     * 放置后记录放置者 UUID；正上方有目标方块时切换为激活态，
     * 同玩家在 16 格内放置另一个复制方块会以最近激活方块为起点填充矩形区域。
     */
    public static final DuplicateBlock DUPLICATE_BLOCK = register(
            "duplicate_block",
            DuplicateBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            true
    );

    /**
     * 非门红石中继器（NOT Gate Redstone Repeater）：
     * 一个纯垂直（输入下、输出上）的红石逻辑元件。
     * <p>
     * 物理特性沿用原版红石中继器族：硬度 3.0、爆炸抗性 6.0、石头音效，
     * 需要用稿子才能无损拆除。
     * <p>
     * ⚠️ {@code requiresCorrectToolForDrops()} 必须与 {@code mineable/pickaxe} 标签配对：
     * 只写前者而方块不在任何工具标签里，等于「没有任何工具是正确工具」，
     * 结果是<b>怎么挖都不掉落</b>。本方块曾漏登记该标签，现已补上。
     * <p>
     * 逻辑由 {@link NotGateRedstoneRepeaterBlock} 自身的覆写处理，无 BlockEntity、无 GUI。
     *
     * @see NotGateRedstoneRepeaterBlock
     */
    public static final NotGateRedstoneRepeaterBlock NOT_GATE_REDSTONE_REPEATER = register(
            "not_gate_redstone_repeater",
            NotGateRedstoneRepeaterBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops(),
            true
    );

    /**
     * 无线红石发射器：四个侧边任一有红石信号进入时激活，
     * 驱动 32 格内所有<b>同频道</b>的无线红石接收器。右键设置频道。
     * <p>
     * 硬度与爆炸抗性沿用本模组的非门红石中继器（3.0 / 6.0、石头音效），已登记进
     * {@code mineable/pickaxe}，用稿子挖得快。
     * <p>
     * 但<b>不加 {@code requiresCorrectToolForDrops()}</b>：红石元件在搭电路时会被反复
     * 拆装，空手误拆就消失太伤人，原版红石中继器 / 比较器 / 侦测器同样都不要求工具。
     * <p>
     * ⚠️ 若日后要给本模组的方块加 {@code requiresCorrectToolForDrops()}，
     * <b>必须同时把它写进 {@code mineable/pickaxe} 标签</b>，
     * 否则会变成「怎么挖都不掉落」——{@code tp_anchor} 与 {@code magic_table} 目前就缺
     * 战利品表，属于同一类问题。
     *
     * @see WirelessRedstoneTransmitterBlock
     */
    public static final WirelessRedstoneTransmitterBlock WIRELESS_REDSTONE_TRANSMITTER = register(
            "wireless_redstone_transmitter",
            WirelessRedstoneTransmitterBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.STONE),
            true
    );

    /**
     * 无线红石接收器：范围内存在同频道的激活发射器时激活，
     * 向自己的四个侧边输出强度 15 的红石信号。右键设置频道。
     * <p>
     * 物理特性与 {@link #WIRELESS_REDSTONE_TRANSMITTER} 完全一致。
     *
     * @see WirelessRedstoneReceiverBlock
     */
    public static final WirelessRedstoneReceiverBlock WIRELESS_REDSTONE_RECEIVER = register(
            "wireless_redstone_receiver",
            WirelessRedstoneReceiverBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.STONE),
            true
    );

    // ===== 大字牌系列 =====
    //
    // 与原版墙上告示牌同为 2 像素厚的薄板，但铺满整个侧面（16×16×2），
    // 右键可编辑一个大字，支持染料染色 / 荧光墨囊发光 / 蜜脾涂蜡。
    // 全系列共用一个 LargeSignBlock 类与一个方块实体类型
    // （见 ModBlockEntities.LARGE_SIGN），彼此只有材质与音效不同。
    //
    // 新增变体时：在此加一行 registerWoodLargeSign / registerMineralLargeSign，
    // 再补齐 blockstates / models(block+item) / items / loot_table / recipe 各一个 JSON，
    // 以及 10 个语言文件里的 block.youzaiworldcore.<id> 键与对应的挖掘工具 tag。

    // ── 木质：12 种木板（音效沿用各自木材在原版里的 SoundType）──
    public static final LargeSignBlock OAK_PLANKS_LARGE_SIGN = registerWoodLargeSign("oak_planks_large_sign", SoundType.WOOD, true);
    public static final LargeSignBlock SPRUCE_PLANKS_LARGE_SIGN = registerWoodLargeSign("spruce_planks_large_sign", SoundType.WOOD, true);
    public static final LargeSignBlock BIRCH_PLANKS_LARGE_SIGN = registerWoodLargeSign("birch_planks_large_sign", SoundType.WOOD, true);
    public static final LargeSignBlock JUNGLE_PLANKS_LARGE_SIGN = registerWoodLargeSign("jungle_planks_large_sign", SoundType.WOOD, true);
    public static final LargeSignBlock ACACIA_PLANKS_LARGE_SIGN = registerWoodLargeSign("acacia_planks_large_sign", SoundType.WOOD, true);
    public static final LargeSignBlock DARK_OAK_PLANKS_LARGE_SIGN = registerWoodLargeSign("dark_oak_planks_large_sign", SoundType.WOOD, true);
    public static final LargeSignBlock MANGROVE_PLANKS_LARGE_SIGN = registerWoodLargeSign("mangrove_planks_large_sign", SoundType.WOOD, true);
    public static final LargeSignBlock PALE_OAK_PLANKS_LARGE_SIGN = registerWoodLargeSign("pale_oak_planks_large_sign", SoundType.WOOD, true);
    public static final LargeSignBlock CHERRY_PLANKS_LARGE_SIGN = registerWoodLargeSign("cherry_planks_large_sign", SoundType.CHERRY_WOOD, true);
    public static final LargeSignBlock BAMBOO_PLANKS_LARGE_SIGN = registerWoodLargeSign("bamboo_planks_large_sign", SoundType.BAMBOO_WOOD, true);
    /** 绯红大字牌：下界木不可燃，故 ignitedByLava 传 false。 */
    public static final LargeSignBlock CRIMSON_PLANKS_LARGE_SIGN = registerWoodLargeSign("crimson_planks_large_sign", SoundType.NETHER_WOOD, false);
    /** 诡异大字牌：下界木不可燃，故 ignitedByLava 传 false。 */
    public static final LargeSignBlock WARPED_PLANKS_LARGE_SIGN = registerWoodLargeSign("warped_planks_large_sign", SoundType.NETHER_WOOD, false);

    // ── 矿物：7 种金属 / 宝石方块 ──
    public static final LargeSignBlock COPPER_BLOCK_LARGE_SIGN = registerMineralLargeSign("copper_block_large_sign");
    public static final LargeSignBlock IRON_BLOCK_LARGE_SIGN = registerMineralLargeSign("iron_block_large_sign");
    public static final LargeSignBlock GOLD_BLOCK_LARGE_SIGN = registerMineralLargeSign("gold_block_large_sign");
    public static final LargeSignBlock DIAMOND_BLOCK_LARGE_SIGN = registerMineralLargeSign("diamond_block_large_sign");
    public static final LargeSignBlock EMERALD_BLOCK_LARGE_SIGN = registerMineralLargeSign("emerald_block_large_sign");
    public static final LargeSignBlock NETHERITE_BLOCK_LARGE_SIGN = registerMineralLargeSign("netherite_block_large_sign");
    public static final LargeSignBlock YZ_BLOCK_LARGE_SIGN = registerMineralLargeSign("yz_block_large_sign");

    /**
     * 全部大字牌，按创造物品栏展示顺序排列。
     * <p>
     * 供 {@code ModBlockEntities} 一次性绑定方块实体类型、
     * 以及 {@code ModCreativeModeTabs} 批量加入物品栏使用；
     * 新增变体后只需把它补进这个数组。
     */
    public static final LargeSignBlock[] LARGE_SIGNS = {
            OAK_PLANKS_LARGE_SIGN,
            SPRUCE_PLANKS_LARGE_SIGN,
            BIRCH_PLANKS_LARGE_SIGN,
            JUNGLE_PLANKS_LARGE_SIGN,
            ACACIA_PLANKS_LARGE_SIGN,
            DARK_OAK_PLANKS_LARGE_SIGN,
            MANGROVE_PLANKS_LARGE_SIGN,
            PALE_OAK_PLANKS_LARGE_SIGN,
            CHERRY_PLANKS_LARGE_SIGN,
            BAMBOO_PLANKS_LARGE_SIGN,
            CRIMSON_PLANKS_LARGE_SIGN,
            WARPED_PLANKS_LARGE_SIGN,
            COPPER_BLOCK_LARGE_SIGN,
            IRON_BLOCK_LARGE_SIGN,
            GOLD_BLOCK_LARGE_SIGN,
            DIAMOND_BLOCK_LARGE_SIGN,
            EMERALD_BLOCK_LARGE_SIGN,
            NETHERITE_BLOCK_LARGE_SIGN,
            YZ_BLOCK_LARGE_SIGN,
    };

    /**
     * 注册一块木质大字牌。
     * <p>
     * {@code noCollision} 与原版告示牌一致（薄板不阻挡移动），
     * 硬度沿用原版告示牌的 1.0，方便快速拆改。
     *
     * @param name          方块 ID（同时也是材质名，如 {@code oak_planks_large_sign}）
     * @param soundType     该木材在原版里的音效类型
     * @param ignitedByLava 是否可被岩浆点燃（下界木材传 false）
     * @return 已注册的方块
     */
    private static LargeSignBlock registerWoodLargeSign(String name, SoundType soundType, boolean ignitedByLava) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .strength(1.0f)
                .sound(soundType)
                .noCollision();
        if (ignitedByLava) {
            properties = properties.ignitedByLava();
        }
        return register(name, LargeSignBlock::new, properties, true);
    }

    /**
     * 注册一块矿物大字牌（铜 / 铁 / 金 / 钻石 / 绿宝石 / 下界合金 / 悠哉）。
     * <p>
     * 需要正确工具（稿子）才掉落，硬度与爆炸抗性略高于木质版本。
     *
     * @param name 方块 ID（同时也是材质名，如 {@code iron_block_large_sign}）
     * @return 已注册的方块
     */
    private static LargeSignBlock registerMineralLargeSign(String name) {
        return register(
                name,
                LargeSignBlock::new,
                BlockBehaviour.Properties.of()
                        .strength(3.0f, 6.0f)
                        .sound(SoundType.METAL)
                        .noCollision()
                        .requiresCorrectToolForDrops(),
                true
        );
    }

    private static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> blockFactory, BlockBehaviour.Properties settings, boolean shouldRegisterItem) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        T block = blockFactory.apply(settings.setId(blockKey));

        if (shouldRegisterItem) {
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
            BlockItem blockItem = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
            Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);
        }

        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
    }

    public static void initialize() {
    }
}
