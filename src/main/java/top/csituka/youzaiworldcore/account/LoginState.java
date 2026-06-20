package top.csituka.youzaiworldcore.account;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 玩家登录状态
 * 记录玩家未登录时的各项信息，以便登录后恢复
 */
public class LoginState {

    private final UUID playerUuid;
    private boolean loggedIn = false;

    // 上一次位置（用于登出后恢复）
    private ResourceKey<Level> lastDimension;
    private double lastX;
    private double lastY;
    private double lastZ;
    private float lastYRot;
    private float lastXRot;

    // 登出前的装备/物品栏快照 — 暂不序列化，保留概念
    private final List<ItemStack> lastInventory = new ArrayList<>();
    private final List<MobEffectInstance> lastEffects = new ArrayList<>();
    private float lastHealth;
    private int lastFoodLevel;

    private int consecutiveLoginFailures = 0;

    public LoginState(ServerPlayer player) {
        this.playerUuid = player.getUUID();
        snapshotPosition(player);
    }

    /** 快照玩家当前位置 */
    public void snapshotPosition(ServerPlayer player) {
        this.lastDimension = player.level().dimension();
        this.lastX = player.getX();
        this.lastY = player.getY();
        this.lastZ = player.getZ();
        this.lastYRot = player.getYRot();
        this.lastXRot = player.getXRot();
    }

    /** 快照玩家完整状态 */
    public void snapshotFullState(ServerPlayer player) {
        snapshotPosition(player);
        this.lastHealth = player.getHealth();
        this.lastFoodLevel = player.getFoodData().getFoodLevel();
        this.lastInventory.clear();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            this.lastInventory.add(player.getInventory().getItem(i).copy());
        }
        this.lastEffects.clear();
        this.lastEffects.addAll(player.getActiveEffects());
    }

    /** 恢复玩家位置 */
    public void restorePosition(ServerPlayer player) {
        ServerLevel targetLevel = player.level().getServer().getLevel(lastDimension);
        if (targetLevel == null) targetLevel = player.level().getServer().getLevel(Level.OVERWORLD);
        if (targetLevel == null) return;
        player.teleportTo(targetLevel, lastX, lastY, lastZ, Set.of(), lastYRot, lastXRot, true);
    }

    // ===== Getters & Setters =====

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public int getConsecutiveLoginFailures() {
        return consecutiveLoginFailures;
    }

    public void incrementFailures() {
        this.consecutiveLoginFailures++;
    }

    public void resetFailures() {
        this.consecutiveLoginFailures = 0;
    }
}
