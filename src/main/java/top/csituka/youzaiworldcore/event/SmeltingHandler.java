package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;

/**
 * 熔炼 (Smelting) 附魔: 挖掘矿物时自动将掉落物烧炼为成品。
 * 等级 1: 基础熔炼；等级 2: 额外掉落经验。
 * <p>
 * 支持烧炼映射：
 * 沙子/红沙 -> 玻璃，粘土 -> 砖，粗铜/铁/金 -> 锭，远古残骸 -> 下界合金碎片
 */
public class SmeltingHandler {

    private static final String MODULE = "SmeltingHandler";

    // 方块 → 烧炼产物 Item（延迟创建 ItemStack，避免 static init 时 Components not bound）
    private static final Map<Block, Item> SMELTING_MAP = Map.ofEntries(
            Map.entry(Blocks.SAND, Items.GLASS.asItem()),
            Map.entry(Blocks.RED_SAND, Items.GLASS.asItem()),
            Map.entry(Blocks.CLAY, Items.BRICK),
            Map.entry(Blocks.RAW_COPPER_BLOCK, Items.COPPER_INGOT),
            Map.entry(Blocks.RAW_IRON_BLOCK, Items.IRON_INGOT),
            Map.entry(Blocks.RAW_GOLD_BLOCK, Items.GOLD_INGOT),
            Map.entry(Blocks.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP)
    );

    // 掉落物品 → 烧炼产物 Item
    private static final Map<Item, Item> ITEM_SMELTING_MAP = Map.of(
            Items.CLAY_BALL, Items.BRICK,
            Items.RAW_COPPER, Items.COPPER_INGOT,
            Items.RAW_IRON, Items.IRON_INGOT,
            Items.RAW_GOLD, Items.GOLD_INGOT
    );

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) return;

            var reg = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> holder = reg.getOrThrow(ModEnchantments.SMELTING_KEY);

            int enchantLevel = player.getMainHandItem().getEnchantments().getLevel(holder);
            if (enchantLevel <= 0) return;

            // 方块映射
            Item resultItem = SMELTING_MAP.get(state.getBlock());
            if (resultItem != null) {
                // 检查是否有对应掉落物需要替换
                var items = world.getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(2.0));
                boolean replaced = false;
                for (ItemEntity itemEntity : items) {
                    Item smeltedItem = ITEM_SMELTING_MAP.get(itemEntity.getItem().getItem());
                    if (smeltedItem != null) {
                        int count = itemEntity.getItem().getCount();
                        ItemStack newStack = new ItemStack(smeltedItem, count);
                        itemEntity.setItem(newStack);
                        replaced = true;
                        DebugLogger.info(MODULE, "Smelted %d x %s at %s",
                                count, smeltedItem, pos);
                    }
                }
                // 如果附近没有掉落物实体（如精准采集），直接生成
                if (!replaced) {
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    ItemStack drop = resultItem.getDefaultInstance();
                    world.addFreshEntity(new ItemEntity(world,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop));
                    // 额外经验
                    for (int i = 0; i < enchantLevel; i++) {
                        world.addFreshEntity(new ExperienceOrb(world,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1));
                    }
                    DebugLogger.info(MODULE, "Smelting block drop: %s -> %s at %s",
                            state.getBlock(), resultItem, pos);
                }
            }
        });
    }
}
