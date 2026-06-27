package top.csituka.youzaiworldcore.item.preset;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * 四大预设潜影盒生成工具类。
 * 移植自 godlygearbox (毕业套装) 模组，整合到悠哉世界核心模组中。
 *
 * <p>直接在创造模式标签页中调用这些方法即可生成带有丰富附魔和物品的预设潜影盒，
 * 无需注册额外物品 ID。</p>
 */
public class PresetItems {

    // ========================================================================
    // Preset 01 — §c毕业套装 (红色潜影盒)
    // ========================================================================

    /**
     * 创建「毕业套装」潜影盒 — 全套满配战斗/工具/防具/消耗品
     */
    public static ItemStack createPreset01(HolderLookup.Provider lookup) {
        List<ItemStack> items = new ArrayList<>();

        // ── 武器 ──
        items.add(ench(Items.NETHERITE_SWORD, lookup,
                Enchantments.SHARPNESS, 5, Enchantments.SMITE, 5, Enchantments.BANE_OF_ARTHROPODS, 5,
                Enchantments.FIRE_ASPECT, 2, Enchantments.LOOTING, 3, Enchantments.KNOCKBACK, 2,
                Enchantments.SWEEPING_EDGE, 3, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.BOW, lookup,
                Enchantments.POWER, 5, Enchantments.FLAME, 1, Enchantments.PUNCH, 2,
                Enchantments.INFINITY, 1, Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.TRIDENT, lookup,
                Enchantments.IMPALING, 5, Enchantments.LOYALTY, 3, Enchantments.CHANNELING, 1,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.TRIDENT, lookup,
                Enchantments.IMPALING, 5, Enchantments.RIPTIDE, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.MACE, lookup,
                Enchantments.DENSITY, 5, Enchantments.BREACH, 4, Enchantments.WIND_BURST, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        // ── 工具 ──
        items.add(ench(Items.NETHERITE_PICKAXE, lookup,
                Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.NETHERITE_PICKAXE, lookup,
                Enchantments.EFFICIENCY, 5, Enchantments.SILK_TOUCH, 1,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.NETHERITE_AXE, lookup,
                Enchantments.EFFICIENCY, 5, Enchantments.SHARPNESS, 5, Enchantments.SMITE, 5,
                Enchantments.BANE_OF_ARTHROPODS, 5, Enchantments.LOOTING, 3, Enchantments.SILK_TOUCH, 1,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.NETHERITE_SHOVEL, lookup,
                Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.NETHERITE_HOE, lookup,
                Enchantments.EFFICIENCY, 5, Enchantments.FORTUNE, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        // ── 防具 ──
        items.add(ench(Items.NETHERITE_HELMET, lookup,
                Enchantments.PROTECTION, 4, Enchantments.BLAST_PROTECTION, 4, Enchantments.FIRE_PROTECTION, 4,
                Enchantments.PROJECTILE_PROTECTION, 4, Enchantments.RESPIRATION, 3, Enchantments.AQUA_AFFINITY, 1,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.NETHERITE_CHESTPLATE, lookup,
                Enchantments.PROTECTION, 4, Enchantments.BLAST_PROTECTION, 4, Enchantments.FIRE_PROTECTION, 4,
                Enchantments.PROJECTILE_PROTECTION, 4,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.NETHERITE_LEGGINGS, lookup,
                Enchantments.PROTECTION, 4, Enchantments.BLAST_PROTECTION, 4, Enchantments.FIRE_PROTECTION, 4,
                Enchantments.PROJECTILE_PROTECTION, 4, Enchantments.SWIFT_SNEAK, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.NETHERITE_BOOTS, lookup,
                Enchantments.PROTECTION, 4, Enchantments.BLAST_PROTECTION, 4, Enchantments.FIRE_PROTECTION, 4,
                Enchantments.PROJECTILE_PROTECTION, 4, Enchantments.FEATHER_FALLING, 4, Enchantments.DEPTH_STRIDER, 3,
                Enchantments.SOUL_SPEED, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.ELYTRA, lookup,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.NETHERITE_SPEAR, lookup,
                Enchantments.SHARPNESS, 5, Enchantments.SMITE, 5,
                Enchantments.BANE_OF_ARTHROPODS, 5, Enchantments.FIRE_ASPECT, 2, Enchantments.LOOTING, 3,
                Enchantments.KNOCKBACK, 2, Enchantments.LUNGE, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        // ── 消耗品 / 杂物 ──
        items.add(new ItemStack(Items.TOTEM_OF_UNDYING));
        items.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64));
        items.add(new ItemStack(Items.TOTEM_OF_UNDYING));
        items.add(new ItemStack(Items.WATER_BUCKET));
        items.add(new ItemStack(Items.ENDER_PEARL, 16));
        items.add(new ItemStack(Items.WIND_CHARGE, 64));
        items.add(new ItemStack(Items.ARROW, 64));
        items.add(new ItemStack(Items.COOKED_BEEF, 64));

        // 烟花火箭 (三种飞行时长)
        ItemStack r1 = new ItemStack(Items.FIREWORK_ROCKET, 64);
        r1.set(DataComponents.FIREWORKS, new Fireworks(1, List.of()));
        items.add(r1);

        ItemStack r2 = new ItemStack(Items.FIREWORK_ROCKET, 64);
        r2.set(DataComponents.FIREWORKS, new Fireworks(2, List.of()));
        items.add(r2);

        ItemStack r3 = new ItemStack(Items.FIREWORK_ROCKET, 64);
        r3.set(DataComponents.FIREWORKS, new Fireworks(3, List.of()));
        items.add(r3);

        // ── 打包进红色潜影盒 ──
        ItemStack container = new ItemStack(Items.DYED_SHULKER_BOX.pick(DyeColor.RED));
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        container.set(DataComponents.CUSTOM_NAME, Component.translatable("youzaiworldcore.message.preset.graduation_set"));
        return container;
    }

    // ========================================================================
    // Preset 02 — 毕业套补充 (橙色潜影盒)
    // ========================================================================

    /**
     * 创建「毕业套补充」潜影盒 — 实用工具、材料、额外装备
     */
    public static ItemStack createPreset02(HolderLookup.Provider lookup) {
        List<ItemStack> items = new ArrayList<>();

        // ── 远程 / 工具 ──
        items.add(ench(Items.CROSSBOW, lookup,
                Enchantments.QUICK_CHARGE, 3, Enchantments.MULTISHOT, 1,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.CROSSBOW, lookup,
                Enchantments.QUICK_CHARGE, 3, Enchantments.PIERCING, 4,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.FISHING_ROD, lookup,
                Enchantments.LUCK_OF_THE_SEA, 3, Enchantments.LURE, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.SHEARS, lookup,
                Enchantments.EFFICIENCY, 5,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.FLINT_AND_STEEL, lookup,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.SHIELD, lookup,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        // ── 物品 ──
        items.add(new ItemStack(Items.LAVA_BUCKET));
        items.add(new ItemStack(Items.POWDER_SNOW_BUCKET));
        items.add(new ItemStack(Items.GOLDEN_APPLE, 64));
        items.add(new ItemStack(Items.GOLDEN_CARROT, 64));
        items.add(new ItemStack(Items.SCAFFOLDING, 64));
        items.add(new ItemStack(Items.SPYGLASS));
        items.add(Items.BED.pick(DyeColor.PINK).getDefaultInstance());
        items.add(new ItemStack(Items.OAK_BOAT));
        items.add(new ItemStack(Items.OBSIDIAN, 64));
        items.add(new ItemStack(Items.ENDER_CHEST, 64));
        items.add(new ItemStack(Items.END_CRYSTAL, 64));
        items.add(new ItemStack(Items.TNT, 64));
        items.add(new ItemStack(Items.COOKED_PORKCHOP, 64));
        items.add(new ItemStack(Items.CHORUS_FRUIT, 64));
        items.add(new ItemStack(Items.HAY_BLOCK, 64));
        items.add(new ItemStack(Items.COAL_BLOCK, 64));
        items.add(new ItemStack(Items.BIRCH_LOG, 64));
        items.add(new ItemStack(Items.SPECTRAL_ARROW, 64));
        items.add(new ItemStack(Items.RESPAWN_ANCHOR, 64));

        // ── 额外防具 ──
        items.add(ench(Items.TURTLE_HELMET, lookup,
                Enchantments.PROTECTION, 4, Enchantments.BLAST_PROTECTION, 4, Enchantments.FIRE_PROTECTION, 4,
                Enchantments.PROJECTILE_PROTECTION, 4, Enchantments.RESPIRATION, 3, Enchantments.AQUA_AFFINITY, 1,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        items.add(ench(Items.LEATHER_BOOTS, lookup,
                Enchantments.PROTECTION, 4, Enchantments.BLAST_PROTECTION, 4, Enchantments.FIRE_PROTECTION, 4,
                Enchantments.PROJECTILE_PROTECTION, 4, Enchantments.FEATHER_FALLING, 4, Enchantments.FROST_WALKER, 2,
                Enchantments.SOUL_SPEED, 3,
                Enchantments.UNBREAKING, 3, Enchantments.MENDING, 1));

        // ── 打包进橙色潜影盒 ──
        ItemStack container = new ItemStack(Items.DYED_SHULKER_BOX.pick(DyeColor.ORANGE));
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        container.set(DataComponents.CUSTOM_NAME, Component.translatable("youzaiworldcore.message.preset.graduation_supplement"));
        return container;
    }

    // ========================================================================
    // Preset 03 — 不死图腾 (黄色潜影盒)
    // ========================================================================

    /**
     * 创建「不死图腾」潜影盒 — 27 个不死图腾（填满整盒）
     */
    public static ItemStack createPreset03(HolderLookup.Provider lookup) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            items.add(new ItemStack(Items.TOTEM_OF_UNDYING));
        }

        ItemStack container = new ItemStack(Items.DYED_SHULKER_BOX.pick(DyeColor.YELLOW));
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        container.set(DataComponents.CUSTOM_NAME, Component.translatable("youzaiworldcore.message.preset.totem_box"));
        return container;
    }

    // ========================================================================
    // Preset 04 — §8炸药包 (灰色潜影盒)
    // ========================================================================

    /**
     * 创建「炸药包」潜影盒 — 27 组 × 64 个 TNT
     */
    public static ItemStack createPreset04(HolderLookup.Provider lookup) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            items.add(new ItemStack(Items.TNT, 64));
        }

        ItemStack container = new ItemStack(Items.DYED_SHULKER_BOX.pick(DyeColor.GRAY));
        container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        container.set(DataComponents.CUSTOM_NAME, Component.translatable("youzaiworldcore.message.preset.explosive_pack"));
        return container;
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    /**
     * 对指定物品批量附魔。
     *
     * @param item         目标物品
     * @param lookup       注册表查询器（从 {@code parameters.holders()} 获取）
     * @param enchantments 交替的附魔 key 和等级：{key1, level1, key2, level2, ...}
     * @return 附魔后的物品栈
     */
    @SuppressWarnings("unchecked")
    private static ItemStack ench(Item item, HolderLookup.Provider lookup, Object... enchantments) {
        ItemStack stack = new ItemStack(item);
        HolderLookup.RegistryLookup<Enchantment> reg = lookup.lookupOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i < enchantments.length; i += 2) {
            ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) enchantments[i];
            int level = (Integer) enchantments[i + 1];
            reg.get(key).ifPresent(holder -> stack.enchant((Holder<Enchantment>) holder, level));
        }
        return stack;
    }
}
