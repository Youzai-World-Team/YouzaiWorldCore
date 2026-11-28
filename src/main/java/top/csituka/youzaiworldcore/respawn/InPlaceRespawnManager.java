package top.csituka.youzaiworldcore.respawn;

import top.csituka.youzaiworldcore.util.LogValues;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolRespawnCounts;
import top.csituka.youzaiworldcore.network.InPlaceRespawnInfoPayload;
import top.csituka.youzaiworldcore.network.InPlaceRespawnResultPayload;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.OptionalInt;

/**
 * 原地重生的服务端权威管理器。
 * <p>
 * 负责计费、请求校验、复活后传送、池内计数与 10 秒抗性 V。
 */
@SuppressWarnings("null")
public final class InPlaceRespawnManager {

    public static final String RESULT_APPROVED = "approved";
    public static final String RESULT_NOT_ENOUGH_LEVELS = "not_enough_levels";
    public static final String RESULT_UNAVAILABLE = "unavailable";

    private static final int RESISTANCE_DURATION_TICKS = 20 * 10;
    private static final int RESISTANCE_AMPLIFIER = 4;

    private InPlaceRespawnManager() {
    }

    /**
     * 计算玩家下一次原地重生的等级费用。
     * 第一次的本次累计次数为 1，因此费用为 floor(log2(1 + 1)) + 5 = 6。
     */
    public static OptionalInt getRequiredLevel(ServerPlayer player) {
        OptionalInt count = PoolRespawnCounts.get(player);
        if (count.isEmpty()) {
            DebugLogger.SERVER.warn("InPlaceRespawn", "Api 账户状态不可用，拒绝为玩家 {} 计算原地重生费用", player.getName().getString());
            return OptionalInt.empty();
        }
        long currentUseNumber = (long) count.getAsInt() + 1L;
        int logarithm = 63 - Long.numberOfLeadingZeros(currentUseNumber + 1L);
        return OptionalInt.of(logarithm + 5);
    }

    /** 在死亡包之前同步本次按钮是否可用以及费用。 */
    public static void syncDeathInfo(ServerPlayer player, boolean enabled, int requiredLevel) {
        ServerPlayNetworking.send(player, new InPlaceRespawnInfoPayload(enabled, requiredLevel));
    }

    /** 校验客户端的原地重生申请，通过后允许客户端发送原版重生指令。 */
    public static void handleRequest(ServerPlayer player) {
        DebugLogger.SERVER.entering("InPlaceRespawn", "handleRequest", "player=" + player.getName().getString());
        if (player.isAlive() || !(player instanceof InPlaceRespawnPlayerAccess access)
                || !access.youzaiworldcore$isInPlaceRespawnEnabled()
                || !InPlaceRespawnConfig.isEnabled(player)) {
            reject(player, RESULT_UNAVAILABLE, 0);
            DebugLogger.SERVER.exiting("InPlaceRespawn", "handleRequest", "unavailable");
            return;
        }

        OptionalInt requiredLevelResult = getRequiredLevel(player);
        if (requiredLevelResult.isEmpty()) {
            reject(player, RESULT_UNAVAILABLE, 0);
            DebugLogger.SERVER.exiting("InPlaceRespawn", "handleRequest", "account_unavailable");
            return;
        }
        int requiredLevel = requiredLevelResult.getAsInt();
        if (player.experienceLevel < requiredLevel) {
            reject(player, RESULT_NOT_ENOUGH_LEVELS, requiredLevel);
            DebugLogger.SERVER.branch("InPlaceRespawn", "玩家等级是否足够", false,
                    "required=" + requiredLevel + ", current=" + player.experienceLevel);
            DebugLogger.SERVER.exiting("InPlaceRespawn", "handleRequest", "not_enough_levels");
            return;
        }

        access.youzaiworldcore$selectInPlaceRespawn(requiredLevel);
        ServerPlayNetworking.send(player, new InPlaceRespawnResultPayload(
                true, RESULT_APPROVED, requiredLevel, player.experienceLevel));
        DebugLogger.SERVER.branch("InPlaceRespawn", "玩家等级是否足够", true,
                "required=" + requiredLevel + ", current=" + player.experienceLevel);
        DebugLogger.SERVER.info("InPlaceRespawn", "已批准玩家 {} 的原地重生请求", player.getName().getString());
        DebugLogger.SERVER.exiting("InPlaceRespawn", "handleRequest", "approved");
    }

    private static void reject(ServerPlayer player, String reason, int requiredLevel) {
        ServerPlayNetworking.send(player, new InPlaceRespawnResultPayload(
                false, reason, requiredLevel, player.experienceLevel));
    }

    public static void finishRespawn(InPlaceRespawnPlayerAccess access, ServerPlayer newPlayer) {
        DebugLogger.SERVER.entering("InPlaceRespawn", "finishRespawn",
                "player=" + newPlayer.getName().getString());
        if (PoolRespawnCounts.get(newPlayer).isEmpty()) {
            newPlayer.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.respawn.in_place.unavailable"));
            DebugLogger.SERVER.warn("InPlaceRespawn", "Api 账户状态不可用，取消玩家 {} 的原地重生结算", newPlayer.getName().getString());
            DebugLogger.SERVER.exiting("InPlaceRespawn", "finishRespawn", "account_unavailable");
            return;
        }
        ServerLevel deathLevel = newPlayer.level().getServer()
                .getLevel(access.youzaiworldcore$getDeathDimension());
        if (deathLevel == null) {
            newPlayer.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.respawn.in_place.unavailable"));
            DebugLogger.SERVER.warn("InPlaceRespawn", "死亡维度未加载，无法将玩家 {} 送回原地", newPlayer.getName().getString());
            DebugLogger.SERVER.exiting("InPlaceRespawn", "finishRespawn", "death_level_missing");
            return;
        }

        var deathPosition = access.youzaiworldcore$getDeathPosition();
        boolean teleported = DimensionPoolManager.withTeleportGuard(newPlayer.getUUID(), () ->
                newPlayer.teleportTo(deathLevel,
                        deathPosition.x, deathPosition.y, deathPosition.z,
                        java.util.Set.of(), access.youzaiworldcore$getDeathYaw(),
                        access.youzaiworldcore$getDeathPitch(), true));
        if (!teleported) {
            newPlayer.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.respawn.in_place.unavailable"));
            DebugLogger.SERVER.warn("InPlaceRespawn", "玩家 {} 原地重生传送失败", newPlayer.getName().getString());
            DebugLogger.SERVER.exiting("InPlaceRespawn", "finishRespawn", "teleport_failed");
            return;
        }

        int requiredLevel = access.youzaiworldcore$getInPlaceRespawnCost();
        int oldLevel = newPlayer.experienceLevel;
        newPlayer.giveExperienceLevels(-requiredLevel);
        DebugLogger.SERVER.stateChange("InPlaceRespawn", newPlayer.getName().getString(),
                "experienceLevel", oldLevel, newPlayer.experienceLevel);

        int oldCount = PoolRespawnCounts.get(newPlayer).orElseThrow();
        int newCount = PoolRespawnCounts.increment(newPlayer);
        DebugLogger.SERVER.stateChange("InPlaceRespawn", newPlayer.getName().getString(),
                "inPlaceRespawnCount", oldCount, newCount);

        newPlayer.addEffect(new MobEffectInstance(
                MobEffects.RESISTANCE, RESISTANCE_DURATION_TICKS,
                RESISTANCE_AMPLIFIER, false, true, true));
        newPlayer.sendSystemMessage(Component.translatable(
                "youzaiworldcore.respawn.in_place.success", requiredLevel));

        try {
            AdventureLevelManager.grantExpSilent(
                    newPlayer.getUUID(), newPlayer.getName().getString(),
                    AdventureLevelManager.EXP_DEATH);
        } catch (Exception e) {
            DebugLogger.SERVER.exception("InPlaceRespawn", "grantDeathAdventureExp", e);
        }

        DebugLogger.SERVER.info("InPlaceRespawn", "玩家 {} 已在 {} 的 ({}, {}, {}) 原地重生，消耗 {} 级", newPlayer.getName().getString(), deathLevel.dimension().identifier(), LogValues.decimal(deathPosition.x, 2), LogValues.decimal(deathPosition.y, 2), LogValues.decimal(deathPosition.z, 2), requiredLevel);
        DebugLogger.SERVER.exiting("InPlaceRespawn", "finishRespawn", "success");
    }
}
