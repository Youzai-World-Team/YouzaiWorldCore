package top.csituka.youzaiworldcore.worldgen;

import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.util.DebugLogger;

@SuppressWarnings("null")
public final class StructureLoot {
    private static final String MODULE = "StructureLoot";
    private static final Identifier GENSHIN_RUINS_POT = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "chests/genshin_ruins_pot");

    // 宝库的子表会在主奖励表内反复抽取，只在主奖励表发放额外战利品。
    private static final Set<ResourceKey<LootTable>> EXCLUDED_TABLES = Set.of(
            BuiltInLootTables.SPAWN_BONUS_CHEST,
            BuiltInLootTables.JUNGLE_TEMPLE_DISPENSER,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_COMMON,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_UNIQUE,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_COMMON,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_RARE,
            BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE);

    private static final LootPool BONUS_LOOT = LootPool.lootPool()
            .add(EmptyLootItem.emptyItem().setWeight(70))
            .add(LootItem.lootTableItem(ModItems.RETURN_SCROLL).setWeight(9))
            .add(LootItem.lootTableItem(ModItems.WARP_SCROLL).setWeight(6))
            .add(LootItem.lootTableItem(ModItems.YZ_NUGGET).setWeight(7)
                    .apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 3))))
            .add(LootItem.lootTableItem(ModItems.FLASHING_INK_SAC).setWeight(4))
            .add(LootItem.lootTableItem(ModItems.YZ_INGOT).setWeight(3))
            .add(LootItem.lootTableItem(ModItems.HEART_OF_GUARDIANSHIP).setWeight(1))
            .build();

    private StructureLoot() {
    }

    public static void register() {
        var logger = DebugLogger.bootstrap();
        logger.entering(MODULE, "register");
        LootTableEvents.MODIFY_DROPS.register(StructureLoot::addRewards);
        logger.debug(MODULE, "已注册遗迹战利品注入");
        logger.exiting(MODULE, "register");
    }

    private static void addRewards(Holder<LootTable> holder, LootContext context, List<ItemStack> drops) {
        ResourceKey<LootTable> key = holder.unwrapKey().orElse(null);
        if (key == null || EXCLUDED_TABLES.contains(key)) {
            return;
        }

        String path = key.identifier().getPath();
        if (path.startsWith("dispensers/") || path.startsWith("spawners/")) {
            return;
        }

        ContextKeySet type = holder.value().getParamSet();
        boolean archaeology = type == LootContextParamSets.ARCHAEOLOGY
                || (type == LootTable.DEFAULT_PARAM_SET && path.startsWith("archaeology/"));
        boolean container = type == LootContextParamSets.CHEST || type == LootContextParamSets.VAULT
                || (type == LootTable.DEFAULT_PARAM_SET && (path.startsWith("chests/")
                        || path.startsWith("barrels/") || path.startsWith("pots/") || path.startsWith("vaults/")));
        if (!archaeology && !container) {
            return;
        }

        if (archaeology || isDecoratedPot(key.identifier(), context)) {
            // 考古和陶罐只能容纳一堆物品，最多以 10% 的概率抽中模组物品。
            if (context.getRandom().nextInt(3) == 0) {
                BONUS_LOOT.addRandomItems(stack -> {
                    drops.clear();
                    drops.add(stack);
                }, context);
            }
        } else {
            BONUS_LOOT.addRandomItems(drops::add, context);
        }
    }

    private static boolean isDecoratedPot(Identifier id, LootContext context) {
        if (id.equals(GENSHIN_RUINS_POT) || id.getPath().startsWith("pots/")) {
            return true;
        }
        Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
        return origin != null
                && context.getLevel().getBlockEntity(BlockPos.containing(origin)) instanceof DecoratedPotBlockEntity;
    }
}
