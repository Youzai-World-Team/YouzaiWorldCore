package top.csituka.youzaiworldcore.client.screen.brewing;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

/**
 * Minecraft 26.2 原版药水酿造表。
 * <p>
 * 配方签名已依据 {@code PotionBrewing.addVanillaMixes} 核验；这里只保存客户端指南所需的
 * “基底药水 + 材料 -> 产物药水”关系，不参与服务端酿造判定。
 */
public final class BrewingGuideRecipes {

    private static final List<BrewingGuideRecipe> RECIPES = List.of(
            recipe(Potions.WATER, Items.NETHER_WART, Potions.AWKWARD),
            recipe(Potions.WATER, Items.REDSTONE, Potions.MUNDANE),
            recipe(Potions.WATER, Items.GLOWSTONE_DUST, Potions.THICK),

            recipe(Potions.AWKWARD, Items.GOLDEN_CARROT, Potions.NIGHT_VISION),
            recipe(Potions.NIGHT_VISION, Items.REDSTONE, Potions.LONG_NIGHT_VISION),
            recipe(Potions.NIGHT_VISION, Items.FERMENTED_SPIDER_EYE, Potions.INVISIBILITY),
            recipe(Potions.LONG_NIGHT_VISION, Items.FERMENTED_SPIDER_EYE, Potions.LONG_INVISIBILITY),
            recipe(Potions.INVISIBILITY, Items.REDSTONE, Potions.LONG_INVISIBILITY),

            recipe(Potions.AWKWARD, Items.RABBIT_FOOT, Potions.LEAPING),
            recipe(Potions.LEAPING, Items.REDSTONE, Potions.LONG_LEAPING),
            recipe(Potions.LEAPING, Items.GLOWSTONE_DUST, Potions.STRONG_LEAPING),
            recipe(Potions.LEAPING, Items.FERMENTED_SPIDER_EYE, Potions.SLOWNESS),
            recipe(Potions.LONG_LEAPING, Items.FERMENTED_SPIDER_EYE, Potions.LONG_SLOWNESS),

            recipe(Potions.AWKWARD, Items.MAGMA_CREAM, Potions.FIRE_RESISTANCE),
            recipe(Potions.FIRE_RESISTANCE, Items.REDSTONE, Potions.LONG_FIRE_RESISTANCE),

            recipe(Potions.AWKWARD, Items.TURTLE_HELMET, Potions.TURTLE_MASTER),
            recipe(Potions.TURTLE_MASTER, Items.REDSTONE, Potions.LONG_TURTLE_MASTER),
            recipe(Potions.TURTLE_MASTER, Items.GLOWSTONE_DUST, Potions.STRONG_TURTLE_MASTER),

            recipe(Potions.AWKWARD, Items.SUGAR, Potions.SWIFTNESS),
            recipe(Potions.SWIFTNESS, Items.REDSTONE, Potions.LONG_SWIFTNESS),
            recipe(Potions.SWIFTNESS, Items.GLOWSTONE_DUST, Potions.STRONG_SWIFTNESS),
            recipe(Potions.SWIFTNESS, Items.FERMENTED_SPIDER_EYE, Potions.SLOWNESS),
            recipe(Potions.LONG_SWIFTNESS, Items.FERMENTED_SPIDER_EYE, Potions.LONG_SLOWNESS),
            recipe(Potions.SLOWNESS, Items.REDSTONE, Potions.LONG_SLOWNESS),
            recipe(Potions.SLOWNESS, Items.GLOWSTONE_DUST, Potions.STRONG_SLOWNESS),

            recipe(Potions.AWKWARD, Items.PUFFERFISH, Potions.WATER_BREATHING),
            recipe(Potions.WATER_BREATHING, Items.REDSTONE, Potions.LONG_WATER_BREATHING),

            recipe(Potions.AWKWARD, Items.GLISTERING_MELON_SLICE, Potions.HEALING),
            recipe(Potions.HEALING, Items.GLOWSTONE_DUST, Potions.STRONG_HEALING),
            recipe(Potions.HEALING, Items.FERMENTED_SPIDER_EYE, Potions.HARMING),
            recipe(Potions.STRONG_HEALING, Items.FERMENTED_SPIDER_EYE, Potions.STRONG_HARMING),
            recipe(Potions.HARMING, Items.GLOWSTONE_DUST, Potions.STRONG_HARMING),

            recipe(Potions.AWKWARD, Items.SPIDER_EYE, Potions.POISON),
            recipe(Potions.POISON, Items.REDSTONE, Potions.LONG_POISON),
            recipe(Potions.POISON, Items.GLOWSTONE_DUST, Potions.STRONG_POISON),
            recipe(Potions.POISON, Items.FERMENTED_SPIDER_EYE, Potions.HARMING),
            recipe(Potions.LONG_POISON, Items.FERMENTED_SPIDER_EYE, Potions.HARMING),
            recipe(Potions.STRONG_POISON, Items.FERMENTED_SPIDER_EYE, Potions.STRONG_HARMING),

            recipe(Potions.AWKWARD, Items.GHAST_TEAR, Potions.REGENERATION),
            recipe(Potions.REGENERATION, Items.REDSTONE, Potions.LONG_REGENERATION),
            recipe(Potions.REGENERATION, Items.GLOWSTONE_DUST, Potions.STRONG_REGENERATION),

            recipe(Potions.AWKWARD, Items.BLAZE_POWDER, Potions.STRENGTH),
            recipe(Potions.STRENGTH, Items.REDSTONE, Potions.LONG_STRENGTH),
            recipe(Potions.STRENGTH, Items.GLOWSTONE_DUST, Potions.STRONG_STRENGTH),

            recipe(Potions.WATER, Items.FERMENTED_SPIDER_EYE, Potions.WEAKNESS),
            recipe(Potions.WEAKNESS, Items.REDSTONE, Potions.LONG_WEAKNESS),

            recipe(Potions.AWKWARD, Items.PHANTOM_MEMBRANE, Potions.SLOW_FALLING),
            recipe(Potions.SLOW_FALLING, Items.REDSTONE, Potions.LONG_SLOW_FALLING),

            recipe(Potions.AWKWARD, Items.BREEZE_ROD, Potions.WIND_CHARGED),
            recipe(Potions.AWKWARD, Items.COBWEB, Potions.WEAVING),
            recipe(Potions.AWKWARD, Items.SLIME_BLOCK, Potions.OOZING),
            recipe(Potions.AWKWARD, Items.STONE, Potions.INFESTED),

            BrewingGuideRecipe.containerMix(Items.POTION, Items.GUNPOWDER, Items.SPLASH_POTION),
            BrewingGuideRecipe.containerMix(Items.SPLASH_POTION, Items.DRAGON_BREATH, Items.LINGERING_POTION)
    );

    private BrewingGuideRecipes() {
    }

    /** 返回按酿造路径排序的原版药水配方。 */
    public static List<BrewingGuideRecipe> all() {
        return RECIPES;
    }

    private static BrewingGuideRecipe recipe(
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> input,
            net.minecraft.world.item.Item ingredient,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> output) {
        return BrewingGuideRecipe.potionMix(input, ingredient, output);
    }
}
