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
import net.minecraft.world.phys.AABB;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;

/**
 * 熔炼 (Smelting) 附魔: 挖掘矿物时自动将掉落物烧炼为成品。
 * 等级 1: 基础熔炼；等级 2: 额外掉落经验。
 * <p>
 * 烧炼分两条路径：
 * <ul>
 *   <li>物品映射：扫描破坏后掉落的物品实体，按 {@link #ITEM_SMELTING_MAP} 转换
 *       （铁/金/铜矿石掉落的粗矿、粘土球、沙子、远古残骸等）。</li>
 *   <li>方块映射：原矿块（粗铁/铜/金块）精准采集时掉落物不在物品映射中，
 *       由 {@link #SMELTING_MAP} 直接生成产物（并清除原掉落物避免重复）。</li>
 * </ul>
 */
public class SmeltingHandler {

    private static final String MODULE = "SmeltingHandler";

    /** 方块 → 烧炼产物 Item（仅处理掉落物不在 {@link #ITEM_SMELTING_MAP} 的方块，如原矿块） */
    @SuppressWarnings("null")
    private static final Map<Block, Item> SMELTING_MAP = Map.ofEntries(
            Map.entry(Blocks.RAW_COPPER_BLOCK, Items.COPPER_INGOT),
            Map.entry(Blocks.RAW_IRON_BLOCK, Items.IRON_INGOT),
            Map.entry(Blocks.RAW_GOLD_BLOCK, Items.GOLD_INGOT));

    /** 掉落物品 → 烧炼产物 Item（覆盖绝大多数矿石与可烧炼方块） */
    @SuppressWarnings("null")
    private static final Map<Item, Item> ITEM_SMELTING_MAP = Map.ofEntries(
            Map.entry(Items.CLAY_BALL, Items.BRICK),
            Map.entry(Items.RAW_COPPER, Items.COPPER_INGOT),
            Map.entry(Items.RAW_IRON, Items.IRON_INGOT),
            Map.entry(Items.RAW_GOLD, Items.GOLD_INGOT),
            Map.entry(Items.SAND, Items.GLASS),
            Map.entry(Items.RED_SAND, Items.GLASS),
            Map.entry(Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP));

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide())
                return;

            var reg = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> holder = reg.getOrThrow(ModEnchantments.SMELTING_KEY);

            int enchantLevel = player.getMainHandItem().getEnchantments().getLevel(holder);
            if (enchantLevel <= 0)
                return;

            var items = world.getEntitiesOfClass(ItemEntity.class,
                    new AABB(pos).inflate(2.0));

            // 1. 独立扫描掉落物，按物品映射烧炼
            //    （核心修复：铁/金/铜矿石掉落的粗矿在此转换，不再被方块映射判空挡住）
            boolean replaced = false;
            for (ItemEntity itemEntity : items) {
                Item smeltedItem = ITEM_SMELTING_MAP.get(itemEntity.getItem().getItem());
                if (smeltedItem != null) {
                    int count = itemEntity.getItem().getCount();
                    itemEntity.setItem(new ItemStack(smeltedItem, count));
                    replaced = true;
                    DebugLogger.info(MODULE, "Smelted %d x %s at %s", count, smeltedItem, pos);
                }
            }

            // 2. 方块直接映射（原矿块：掉落物不在物品映射中，直接生成产物）
            Item resultItem = SMELTING_MAP.get(state.getBlock());
            if (resultItem != null && !replaced) {
                // 移除原方块掉落物，避免与生成产物重复
                Item blockItem = state.getBlock().asItem();
                for (ItemEntity ie : items) {
                    if (!ie.getItem().isEmpty() && ie.getItem().getItem() == blockItem) {
                        ie.discard();
                    }
                }
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
        });
    }
}
