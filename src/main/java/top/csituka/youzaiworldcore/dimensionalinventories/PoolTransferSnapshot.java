package top.csituka.youzaiworldcore.dimensionalinventories;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 维度池切换的回滚快照。物品直接复制，回滚时无需读取或解码存档。 */
final class PoolTransferSnapshot {
    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("DimensionPoolManager");

    private final ServerLevel level;
    private final Vec3 position;
    private final Vec3 movement;
    private final float yaw, pitch, headYaw;
    private final double fallDistance;
    private final GameType gameMode;
    private final boolean flying;
    private final List<ItemStack> inventory, enderChest;
    private final int selectedSlot;
    private final List<MobEffectInstance> effects;
    private final float health, absorption, saturation, exhaustion, experienceProgress;
    private final int foodLevel, experienceLevel, totalExperience, score;

    @SuppressWarnings("null")
    PoolTransferSnapshot(ServerPlayer player) {
        level = player.level();
        position = player.position();
        movement = player.getDeltaMovement();
        yaw = player.getYRot();
        pitch = player.getXRot();
        headYaw = player.getYHeadRot();
        fallDistance = player.fallDistance;
        gameMode = player.gameMode();
        flying = player.getAbilities().flying;
        inventory = copyItems(player.getInventory());
        enderChest = copyItems(player.getEnderChestInventory());
        selectedSlot = player.getInventory().getSelectedSlot();
        effects = player.getActiveEffects().stream().map(MobEffectInstance::new).toList();
        health = player.getHealth();
        absorption = player.getAbsorptionAmount();
        var food = player.getFoodData();
        foodLevel = food.getFoodLevel();
        saturation = food.getSaturationLevel();
        exhaustion = PlayerStateData.getExhaustion(player);
        experienceLevel = player.experienceLevel;
        experienceProgress = player.experienceProgress;
        totalExperience = player.totalExperience;
        score = player.getScore();
    }

    /** 独立尝试恢复位置、模式和装备，某一步异常时仍恢复其余状态。 */
    @SuppressWarnings("null")
    boolean restore(ServerPlayer player) {
        if (!atOrigin(player)) {
            try {
                player.teleportTo(level, position.x, position.y, position.z, Set.of(), yaw, pitch, true);
            } catch (RuntimeException error) {
                LOGGER.error("返回玩家 {} 的源位置时出错", player.getScoreboardName(), error);
            }
        }
        if (!atOrigin(player)) {
            try {
                player.teleport(new TeleportTransition(level, position, movement, yaw, pitch,
                        TeleportTransition.DO_NOTHING));
            } catch (RuntimeException error) {
                LOGGER.error("重试返回玩家 {} 的源位置时出错", player.getScoreboardName(), error);
            }
        }

        try {
            if (player.gameMode() != gameMode) {
                player.setGameMode(gameMode);
            }
        } catch (RuntimeException error) {
            LOGGER.error("恢复玩家 {} 的游戏模式时出错", player.getScoreboardName(), error);
        }

        try {
            restoreItems(player.getInventory(), inventory);
            restoreItems(player.getEnderChestInventory(), enderChest);
            player.getInventory().setSelectedSlot(selectedSlot);
        } catch (RuntimeException error) {
            LOGGER.error("恢复玩家 {} 的物品时出错", player.getScoreboardName(), error);
            return false;
        }

        try {
            player.getAbilities().flying = flying;
            player.setDeltaMovement(movement);
            player.setYHeadRot(headYaw);
            player.fallDistance = fallDistance;
            player.removeAllEffects();
            effects.forEach(effect -> player.addEffect(new MobEffectInstance(effect)));
            player.setHealth(health);
            player.setAbsorptionAmount(absorption);
            var food = player.getFoodData();
            food.setFoodLevel(foodLevel);
            food.setSaturation(saturation);
            PlayerStateData.setExhaustion(player, exhaustion);
            player.experienceLevel = experienceLevel;
            player.experienceProgress = experienceProgress;
            player.totalExperience = totalExperience;
            player.setScore(score);
            if (!player.hasDisconnected()) {
                player.onUpdateAbilities();
                player.containerMenu.broadcastChanges();
            }
        } catch (RuntimeException error) {
            LOGGER.error("恢复玩家 {} 的状态时出错", player.getScoreboardName(), error);
            return false;
        }
        return atOrigin(player) && player.gameMode() == gameMode;
    }

    @SuppressWarnings("null")
    private boolean atOrigin(ServerPlayer player) {
        return player.level() == level && player.position().equals(position)
                && player.getYRot() == yaw && player.getXRot() == pitch;
    }

    private static List<ItemStack> copyItems(Container container) {
        var items = new ArrayList<ItemStack>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            items.add(container.getItem(slot).copy());
        }
        return items;
    }

    private static void restoreItems(Container container, List<ItemStack> items) {
        for (int slot = 0; slot < items.size(); slot++) {
            container.setItem(slot, items.get(slot).copy());
        }
        container.setChanged();
    }
}
