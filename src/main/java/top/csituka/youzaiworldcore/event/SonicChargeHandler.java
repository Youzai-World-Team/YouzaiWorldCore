package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.enchantment.ModEnchantments;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 音速充能 (Sonic Charge) 附魔: 弩附魔，射出弩箭时给使用者反向击退（后坐力）。
 * 等级 1: 基础后坐力；等级 2: 更强后坐力。
 * <p>
 * 额外伤害部分由数据驱动 effects(minecraft:damage) 实现，本处理器仅负责"击退使用者"。
 * 监听 {@link ServerEntityEvents#ENTITY_LOAD}：弩射出的弹射物生成时给射手反向推力。
 * 多重射击（同一 tick 多支箭）只触发一次后坐力。
 */
public class SonicChargeHandler {

    private static final String MODULE = "SonicChargeHandler";
    /** 记录每位玩家上次触发后坐力的游戏时间，用于多重射击去重 */
    private static final Map<UUID, Long> lastRecoilGameTime = new HashMap<>();

    public static void register() {
        DebugLogger.entering(MODULE, "register");

        ServerEntityEvents.ENTITY_LOAD.register((Entity entity, ServerLevel world) -> {
            if (!(entity instanceof Projectile proj))
                return;
            if (!(proj.getOwner() instanceof ServerPlayer player))
                return;

            // 仅处理主手为弩且带音速充能的情况
            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.is(Items.CROSSBOW))
                return;

            var reg = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Holder<Enchantment> holder = reg.getOrThrow(ModEnchantments.SONIC_CHARGE_KEY);
            int level = mainHand.getEnchantments().getLevel(holder);
            if (level <= 0)
                return;

            // 去重：同一游戏 tick 只触发一次（多重射击一次射出多支箭）
            long currentGameTime = world.getGameTime();
            Long last = lastRecoilGameTime.get(player.getUUID());
            if (last != null && last == currentGameTime)
                return;
            lastRecoilGameTime.put(player.getUUID(), currentGameTime);

            // 反向击退：与玩家看向方向相反，水平为主 + 少量向上
            Vec3 look = player.getLookAngle();
            double strength = (level == 1) ? 0.4 : 0.7;
            Vec3 recoil = new Vec3(-look.x * strength, 0.15, -look.z * strength);
            player.setDeltaMovement(player.getDeltaMovement().add(recoil));
            player.hurtMarked = true;

            DebugLogger.info(MODULE, "Sonic Charge Lv%d 后坐力: player=%s, strength=%.2f",
                    level, player.getName().getString(), strength);
        });

        DebugLogger.info(MODULE, "音速充能附魔事件处理器已注册 (ServerEntityEvents.ENTITY_LOAD)");
        DebugLogger.exiting(MODULE, "register");
    }
}
