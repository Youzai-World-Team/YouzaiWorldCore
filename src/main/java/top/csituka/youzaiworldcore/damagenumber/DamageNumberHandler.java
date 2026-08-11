package top.csituka.youzaiworldcore.damagenumber;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import top.csituka.youzaiworldcore.config.FunctionToggleManager;
import top.csituka.youzaiworldcore.network.DamageNumberPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashSet;
import java.util.Set;

/**
 * 服务端伤害跳字处理器。
 * <p>
 * 将实体实际损失的生命值与吸收生命值同步给正在追踪该实体的客户端；
 * 隐身实体只会向受伤玩家本人和直接伤害来源玩家显示，避免旁观者借跳字定位。
 * </p>
 */
public final class DamageNumberHandler {

    private static final String MODULE = "DamageNumber";

    private DamageNumberHandler() {
    }

    /** 初始化伤害跳字服务端处理器。 */
    public static void initialize() {
        DebugLogger.info(MODULE, "伤害跳字服务端处理器已初始化");
    }

    /**
     * 向可见且正在追踪目标实体的玩家广播一次伤害跳字。
     *
     * @param target 受伤实体
     * @param source 伤害来源
     * @param damage 实际损失的生命值与吸收生命值总量
     */
    public static void broadcast(LivingEntity target, DamageSource source, float damage) {
        if (damage <= 0.0F || !Float.isFinite(damage)) {
            return;
        }

        Set<ServerPlayer> recipients = new HashSet<>(PlayerLookup.tracking(target));
        ServerPlayer damagedPlayer = target instanceof ServerPlayer player ? player : null;
        if (damagedPlayer != null) {
            recipients.add(damagedPlayer);
        }

        ServerPlayer attackingPlayer = source.getEntity() instanceof ServerPlayer player ? player : null;
        if (attackingPlayer != null && attackingPlayer.level() == target.level()) {
            recipients.add(attackingPlayer);
        }

        DamageNumberPayload payload = new DamageNumberPayload(
                target.getX(), target.getY(), target.getZ(), target.getBbHeight(), damage);
        int sentCount = 0;
        for (ServerPlayer recipient : recipients) {
            if (!FunctionToggleManager.isEnabled(
                    recipient.getUUID(), FunctionToggleManager.KEY_DAMAGE_NUMBERS)) {
                continue;
            }
            boolean maySeeInvisibleTarget = recipient == damagedPlayer
                    || recipient == attackingPlayer;
            if (target.isInvisibleTo(recipient) && !maySeeInvisibleTarget) {
                continue;
            }
            if (!ServerPlayNetworking.canSend(recipient, DamageNumberPayload.ID)) {
                continue;
            }
            ServerPlayNetworking.send(recipient, payload);
            sentCount++;
        }

        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace(MODULE, "已广播伤害跳字: target=%s, damage=%.2f, recipients=%d",
                    target.getName().getString(), damage, sentCount);
        }
    }

}
