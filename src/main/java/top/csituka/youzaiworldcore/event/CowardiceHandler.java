package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 怯懦 (Cowardice) 附魔: 护腿附魔，满血时获得额外移动速度。
 * 等级 1: +5% 移动速度；等级 2: +10% 移动速度。
 * <p>
 * 每 10 tick 检查：玩家护腿带怯懦附魔且当前生命值已满时，施加移动速度修饰符；否则移除。
 * 之所以用 Handler 而非数据驱动，是因为 enchantment predicate 无法表达"生命值比例满"这一条件。
 */
public class CowardiceHandler {

    private static final String MODULE = "CowardiceHandler";
    private static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "cowardice_speed");
    private static final int INTERVAL = 10;
    private static int tickCounter = 0;
    private static Holder<Enchantment> cached;

    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < INTERVAL) return;
            tickCounter = 0;

            if (cached == null) {
                cached = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(ModEnchantments.COWARDICE_KEY);
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int level = player.getItemBySlot(EquipmentSlot.LEGS).getEnchantments().getLevel(cached);
                AttributeInstance inst = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (inst == null) continue;

                boolean fullHealth = player.getHealth() >= player.getMaxHealth();
                if (level > 0 && fullHealth) {
                    double amount = 0.05 + (level - 1) * 0.05;
                    AttributeModifier current = inst.getModifier(MODIFIER_ID);
                    if (current == null || Double.compare(current.amount(), amount) != 0
                            || current.operation() != AttributeModifier.Operation.ADD_VALUE) {
                        if (current != null) {
                            inst.removeModifier(MODIFIER_ID);
                        }
                        inst.addPermanentModifier(new AttributeModifier(
                                MODIFIER_ID, amount, AttributeModifier.Operation.ADD_VALUE));
                    }
                } else if (inst.hasModifier(MODIFIER_ID)) {
                    inst.removeModifier(MODIFIER_ID);
                }
            }
        });

        DebugLogger.info(MODULE, "怯懦附魔 Tick 事件处理器已注册 (interval=%d)", INTERVAL);
        DebugLogger.exiting(MODULE, "register");
    }
}
