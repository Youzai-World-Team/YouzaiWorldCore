package top.csituka.youzaiworldcore.mixin.craftsound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 合成音效 Mixin。
 * <p>
 * 在玩家从合成结果槽取出物品时，播放与物品类型对应的特色音效。
 * 注入 {@link ResultSlot#onTake(Player, ItemStack)}。
 * </p>
 */
@SuppressWarnings("null")
@Mixin(ResultSlot.class)
public abstract class ResultSlotOnTakeMixin {

    @Inject(method = "onTake", at = @At("TAIL"))
    private void playCraftSound(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide()) {
            return;
        }
        if (stack.isEmpty()) {
            return;
        }

        var sound = getCraftSound(stack);
        if (sound != null && player instanceof ServerPlayer) {
            player.level().playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    sound, SoundSource.PLAYERS, 0.7f, 1.0f);
            DebugLogger.debug("CraftSound", "合成音效: %s -> %s",
                    stack.getDisplayName().getString(), sound.location());
        }
    }

    private static net.minecraft.sounds.SoundEvent getCraftSound(ItemStack stack) {
        var item = stack.getItem();

        // 下界合金物品
        if (item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP
                || item == Items.NETHERITE_BLOCK
                || item == Items.NETHERITE_SWORD || item == Items.NETHERITE_PICKAXE
                || item == Items.NETHERITE_AXE || item == Items.NETHERITE_SHOVEL
                || item == Items.NETHERITE_HOE
                || item == Items.NETHERITE_HELMET || item == Items.NETHERITE_CHESTPLATE
                || item == Items.NETHERITE_LEGGINGS || item == Items.NETHERITE_BOOTS) {
            return SoundEvents.ANCIENT_DEBRIS_BREAK;
        }

        // 钻石物品
        if (item == Items.DIAMOND || item == Items.DIAMOND_BLOCK
                || item == Items.DIAMOND_SWORD || item == Items.DIAMOND_PICKAXE
                || item == Items.DIAMOND_AXE || item == Items.DIAMOND_SHOVEL
                || item == Items.DIAMOND_HOE
                || item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
                || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS) {
            return SoundEvents.EXPERIENCE_ORB_PICKUP;
        }

        // 金物品
        if (item == Items.GOLD_INGOT || item == Items.GOLD_BLOCK
                || item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
            return SoundEvents.BELL_BLOCK;
        }

        // 附魔物品
        if (item == Items.ENCHANTING_TABLE || item == Items.ENCHANTED_BOOK) {
            return SoundEvents.ENCHANTMENT_TABLE_USE;
        }

        // 铁砧
        if (item == Items.ANVIL || item == Items.CHIPPED_ANVIL
                || item == Items.DAMAGED_ANVIL) {
            return SoundEvents.ANVIL_USE;
        }

        // 信标
        if (item == Items.BEACON) {
            return SoundEvents.BEACON_ACTIVATE;
        }

        // 普通铁质物品
        if (item == Items.IRON_INGOT || item == Items.IRON_BLOCK
                || item == Items.IRON_SWORD || item == Items.IRON_PICKAXE
                || item == Items.IRON_AXE || item == Items.IRON_SHOVEL
                || item == Items.IRON_HOE
                || item == Items.IRON_HELMET || item == Items.IRON_CHESTPLATE
                || item == Items.IRON_LEGGINGS || item == Items.IRON_BOOTS) {
            return SoundEvents.ANVIL_LAND;
        }

        // 没有特殊音效的物品
        return null;
    }
}
