package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 吸血 (Leeching) 附魔: 击杀生物时回复固定生命值。
 * 等级 1: 回复 2 HP；等级 2: 回复 4 HP。
 * <p>
 * 仅检查主手武器（与附魔 slots:[mainhand] 定义一致）；回复量固定，
 * 避免按目标最大血量比例回复导致击杀 BOSS 时超模。
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

            // 仅查主手武器（与 slots:[mainhand] 定义一致）
            int enchantLevel = player.getMainHandItem().getEnchantments().getLevel(holder);
            if (enchantLevel <= 0)
                return;

            // 固定值回复，避免按目标最大血量比例导致 BOSS 超模
            float healAmount = (enchantLevel == 1) ? 2.0f : 4.0f;
            player.heal(healAmount);

            DebugLogger.info(MODULE, "Leeching healed %s for %.1f HP (level=%d, victim=%s)",
                    player.getName().getString(), healAmount, enchantLevel, entity.getName().getString());
        });
    }
}
