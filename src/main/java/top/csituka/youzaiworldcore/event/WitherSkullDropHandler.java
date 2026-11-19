package top.csituka.youzaiworldcore.event;

import top.csituka.youzaiworldcore.util.DebugLogger;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;


import top.csituka.youzaiworldcore.config.EventSettings;

/**
 * 凋零头颅必定掉落事件处理器。
 * <p>
 * 凋零死亡时必定掉落一个凋零骷髅头颅。
 * 监听 {@link ServerLivingEntityEvents#AFTER_DEATH}。
 * </p>
 */
@SuppressWarnings("null")
public class WitherSkullDropHandler {

    private static final WitherSkullDropHandler INSTANCE = new WitherSkullDropHandler();
    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("WitherSkull");

    private WitherSkullDropHandler() {
    }

    private void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (!(entity instanceof WitherBoss)) {
            return;
        }
        if (!EventSettings.isWitherSkullDropEnabled()) return;

        Level level = entity.level();
        LOGGER.debug("凋零被击杀，掉落凋零骷髅头颅");

        ItemStack skullStack = new ItemStack(Items.WITHER_SKELETON_SKULL, 1);
        ItemEntity skullEntity = new ItemEntity(level,
                entity.getX(), entity.getY() + 0.5, entity.getZ(),
                skullStack);
        skullEntity.setDefaultPickUpDelay();
        level.addFreshEntity(skullEntity);

        LOGGER.debug("凋零骷髅头颅已掉落于 [{}, {}, {}]",
                entity.getX(), entity.getY() + 0.5, entity.getZ());
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(INSTANCE::onEntityDeath);
        LOGGER.debug("凋零头颅必定掉落事件处理器已注册");
    }
}
