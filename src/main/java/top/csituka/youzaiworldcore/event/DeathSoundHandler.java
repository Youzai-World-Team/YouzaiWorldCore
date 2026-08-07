package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * 死亡音效事件处理器。
 * <p>
 * 玩家死亡时在死亡位置播放随机全局音效（从 10 种变体中随机选择）。
 * 与原版死亡音叠加，提供更多样的听觉反馈。
 * </p>
 */
@SuppressWarnings("null")
public class DeathSoundHandler {

    private static final DeathSoundHandler INSTANCE = new DeathSoundHandler();
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/DeathSound");
    private static final Random RANDOM = new Random();

    private DeathSoundHandler() {
    }

    private void onEntityDeath(LivingEntity entity, DamageSource damageSource) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (!(entity instanceof Player)) {
            return;
        }

        ServerLevel level = (ServerLevel) entity.level();

        LOGGER.info("玩家死亡触发: {}", entity.getName().getString());

        playRandomDeathSound(level, entity.getX(), entity.getY(), entity.getZ());

        LOGGER.info("死亡音效已播放");
    }

    /**
     * 随机选择一种死亡音效变体并在死亡位置播放。
     */
    private static void playRandomDeathSound(ServerLevel level, double x, double y, double z) {
        SoundEvent sound = switch (RANDOM.nextInt(10)) {
            case 0 -> SoundEvents.PLAYER_DEATH;
            case 1 -> SoundEvents.GENERIC_DEATH;
            case 2 -> SoundEvents.ENDER_DRAGON_DEATH;
            case 3 -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case 4 -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case 5 -> SoundEvents.BELL_BLOCK;
            case 6 -> SoundEvents.BELL_RESONATE;
            case 7 -> SoundEvents.WARDEN_DEATH;
            case 8 -> SoundEvents.END_PORTAL_SPAWN;
            default -> SoundEvents.PORTAL_TRIGGER;
        };

        level.playSound(null, x, y, z, sound, SoundSource.PLAYERS, 1.0f, 1.0f);

        LOGGER.debug("死亡音效: {}", sound.location());
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register(INSTANCE::onEntityDeath);
        LOGGER.info("死亡音效事件处理器已注册");
    }
}
