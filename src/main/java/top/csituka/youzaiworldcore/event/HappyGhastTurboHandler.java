package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;

/**
 * 乐魂涡轮加速器附魔的 tick 处理器。
 * 每 20 tick 检查所有 HappyGhast，若其挽具带有此附魔则提升飞行速度。
 * 每级 +20% 飞行速度（FLYING_SPEED）。
 */
@SuppressWarnings("null")
public class HappyGhastTurboHandler {

    private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "spirit_turbo_speed");
    private static final int INTERVAL = 20;
    private static int tickCounter = 0;
    private static Holder<Enchantment> cachedEnchantment;

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < INTERVAL) return;
            tickCounter = 0;

            if (cachedEnchantment == null) {
                cachedEnchantment = server.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(ModEnchantments.SPIRIT_TURBO_KEY);
            }

            for (ServerLevel level : server.getAllLevels()) {
                for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                    if (entity instanceof HappyGhast ghast) {
                        applySpeedModifier(ghast);
                    }
                }
            }
        });
    }

    private static void applySpeedModifier(HappyGhast ghast) {
        ItemStack harness = ghast.getItemBySlot(EquipmentSlot.BODY);
        int level = harness.isEmpty() ? 0
                : harness.getEnchantments().getLevel(cachedEnchantment);

        var instance = ghast.getAttribute(Attributes.FLYING_SPEED);
        if (instance == null) return;

        // 先移除旧修饰符
        instance.removeModifier(MODIFIER_ID);

        if (level > 0) {
            double bonus = level * 0.20; // 每级 +20%
            instance.addPermanentModifier(new AttributeModifier(
                    MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
