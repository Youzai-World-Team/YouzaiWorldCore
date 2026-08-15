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
