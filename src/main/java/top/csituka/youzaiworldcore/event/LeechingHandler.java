package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 吸血 (Leeching) 附魔: 击杀生物时回复目标最大生命值的一定比例。
 * 等级 1: 回复 25%；等级 2: 回复 40%。
 */
public class LeechingHandler {

    private static final String MODULE = "LeechingHandler";

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.level().isClientSide())
                return;
            var attacker = source.getEntity();
            if (!(attacker instanceof Player player))
                return;

            ServerLevel level = (ServerLevel) entity.level();
            var reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> holder = reg.getOrThrow(ModEnchantments.LEECHING_KEY);

            int enchantLevel = getEnchantmentLevel(player, holder);
            if (enchantLevel <= 0)
                return;

            float maxHealth = entity.getMaxHealth();
            float healPercent = enchantLevel == 1 ? 0.25f : 0.40f;
            float healAmount = maxHealth * healPercent;

            player.heal(healAmount);
            DebugLogger.info(MODULE, "Leeching healed %s for %.1f HP (level=%d, victim=%s)",
                    player.getName().getString(), healAmount, enchantLevel, entity.getName().getString());
        });
    }

    private static int getEnchantmentLevel(Player player, Holder<Enchantment> holder) {
        int maxLevel = 0;
        // 检查主手
        maxLevel = Math.max(maxLevel, player.getMainHandItem().getEnchantments().getLevel(holder));
        // 检查物品栏中其他可能持握的物品
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                maxLevel = Math.max(maxLevel, stack.getEnchantments().getLevel(holder));
            }
        }
        return maxLevel;
    }
}
