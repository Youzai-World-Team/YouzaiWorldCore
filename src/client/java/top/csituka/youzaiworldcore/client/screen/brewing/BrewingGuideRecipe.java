package top.csituka.youzaiworldcore.client.screen.brewing;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

/**
 * 酿造指南中的一条原版药水配方。
 *
 * @param input           基底药水
 * @param inputContainer  基底容器
 * @param ingredient      酿造材料
 * @param output          产物药水
 * @param outputContainer 产物容器
 * @param containerMix    是否为“任意药水更换容器”配方
 */
public record BrewingGuideRecipe(
        Holder<Potion> input,
        Item inputContainer,
        Item ingredient,
        Holder<Potion> output,
        Item outputContainer,
        boolean containerMix) {

    /** 创建普通药水效果转换配方。 */
    public static BrewingGuideRecipe potionMix(
            Holder<Potion> input, Item ingredient, Holder<Potion> output) {
        return new BrewingGuideRecipe(input, Items.POTION, ingredient, output, Items.POTION, false);
    }

    /** 创建任意药水的容器转换配方。 */
    public static BrewingGuideRecipe containerMix(
            Item inputContainer, Item ingredient, Item outputContainer) {
        return new BrewingGuideRecipe(
                Potions.WATER, inputContainer, ingredient, Potions.WATER, outputContainer, true);
    }

    /** 创建用于指南渲染的基底药水物品。 */
    @SuppressWarnings("null")
    public ItemStack inputStack() {
        return PotionContents.createItemStack(this.inputContainer, this.input);
    }

    /** 创建用于指南渲染的材料物品。 */
    @SuppressWarnings("null")
    public ItemStack ingredientStack() {
        return new ItemStack(this.ingredient);
    }

    /** 创建用于指南渲染的产物药水物品。 */
    @SuppressWarnings("null")
    public ItemStack outputStack() {
        return PotionContents.createItemStack(this.outputContainer, this.output);
    }

    /** 返回产物药水提供的状态效果。 */
    public List<MobEffectInstance> effects() {
        return this.output.value().getEffects();
    }

    /** 返回指南中显示的基底名称。 */
    public Component inputName() {
        if (!this.containerMix) {
            return inputStack().getHoverName();
        }
        return this.inputContainer == Items.SPLASH_POTION
                ? Component.translatable("screen.youzaiworldcore.brewing.any_splash_potion")
                : Component.translatable("screen.youzaiworldcore.brewing.any_potion");
    }

    /** 返回指南中显示的产物名称。 */
    public Component outputName() {
        if (!this.containerMix) {
            return outputStack().getHoverName();
        }
        return this.outputContainer == Items.LINGERING_POTION
                ? Component.translatable("screen.youzaiworldcore.brewing.any_lingering_potion")
                : Component.translatable("screen.youzaiworldcore.brewing.any_splash_potion");
    }
}
