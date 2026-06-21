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
 * 玩家登录状态与状态快照。
 * <p>
 * 职责：
 * <ul>
 *   <li>记录当前玩家是否已登录 ({@link #loggedIn})</li>
 *   <li>在登录/注册流程前快照并保存玩家位置、血量、饱食度、背包、状态效果</li>
 *   <li>提供 {@link #restorePosition(ServerPlayer)} 在登录成功后恢复位置</li>
 *   <li>记录连续登录失败次数</li>
 * </ul>
 * </p>
 *
 * <p>
 * 每个在线玩家在 {@link AccountManager} 中都有一个对应的 LoginState 实例，
 * 玩家完全断开连接后会被清理。
 * </p>
 *
 * @see AccountManager#getLoginState(ServerPlayer)
 */
public class LoginState {

    /** 玩家 UUID */
    private final UUID playerUuid;

    /** 是否已通过登录校验 */
    private boolean loggedIn = false;

    // ===== 位置快照 =====
    // 用于登录后恢复玩家到下线前的位置（维度 + 坐标 + 朝向）

    /** 上一次所在维度（登录/断线时记录） */
    private ResourceKey<Level> lastDimension;

    /** 上一次所在 X 坐标 */
    private double lastX;

    /** 上一次所在 Y 坐标 */
    private double lastY;

    /** 上一次所在 Z 坐标 */
    private double lastZ;

    /** 上一次的水平旋转角（偏航角） */
    private float lastYRot;

    /** 上一次的垂直旋转角（俯仰角） */
    private float lastXRot;

    // ===== 状态快照（暂不序列化，保留以备后续扩展） =====

    /** 登出前的背包物品快照（共 36 格主背包 + 装备栏） */
    private final List<ItemStack> lastInventory = new ArrayList<>();

    /** 登出前的活跃状态效果快照 */
    private final List<MobEffectInstance> lastEffects = new ArrayList<>();

    /** 登出前的生命值 */
    private float lastHealth;

    /** 登出前的饱食度 */
    private int lastFoodLevel;

    /** 连续登录失败次数 */
    private int consecutiveLoginFailures = 0;

    /**
     * 是否已通过 {@link #snapshotFullState(ServerPlayer)} 保存了完整状态快照。
     * 用于区分"新玩家无快照"和"断线重连玩家有快照"两种场景。
     */
    private boolean hasSnapshot = false;

    /**
     * 构造一个登录状态对象，首次创建时立即快照当前位置。
     *
     * @param player 服务端玩家对象
     */
    public LoginState(ServerPlayer player) {
        this.playerUuid = player.getUUID();
        snapshotPosition(player);
    }

    /**
     * 快照玩家的当前位置和朝向。
     * 不覆盖其他状态字段（血量、背包等）。
     *
     * @param player 服务端玩家对象
     */
    public void snapshotPosition(ServerPlayer player) {
        this.lastDimension = player.level().dimension();
        this.lastX = player.getX();
        this.lastY = player.getY();
        this.lastZ = player.getZ();
        this.lastYRot = player.getYRot();
        this.lastXRot = player.getXRot();
    }

    /**
     * 快照玩家的完整状态：位置 + 血量 + 饱食度 + 背包 + 状态效果。
     * 调用后 {@link #hasSnapshot} 返回 true。
     *
     * @param player 服务端玩家对象
     */
    public void snapshotFullState(ServerPlayer player) {
        snapshotPosition(player);
        this.lastHealth = player.getHealth();
        this.lastFoodLevel = player.getFoodData().getFoodLevel();

        // 备份背包物品（全尺寸遍历）
        this.lastInventory.clear();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            this.lastInventory.add(player.getInventory().getItem(i).copy());
        }

        // 备份状态效果
        this.lastEffects.clear();
        this.lastEffects.addAll(player.getActiveEffects());

        this.hasSnapshot = true;
    }

    /**
     * 将玩家恢复到快照时保存的维度和位置。
     * 如果保存的维度不可用，降级到主世界。
     *
     * @param player 服务端玩家对象
     */
    public void restorePosition(ServerPlayer player) {
        ServerLevel targetLevel = player.level().getServer().getLevel(lastDimension);
        if (targetLevel == null) targetLevel = player.level().getServer().getLevel(Level.OVERWORLD);
        if (targetLevel == null) return;
        player.teleportTo(targetLevel, lastX, lastY, lastZ, Set.of(), lastYRot, lastXRot, true);
    }

    /**
     * 是否有可用的完整状态快照。
     *
     * @return true 如果 {@link #snapshotFullState(ServerPlayer)} 之前被调用过
     */
    public boolean hasSnapshot() {
        return hasSnapshot;
    }

    /**
     * 清除完整状态快照标记。
     * 在位置恢复完成后调用，防止重复恢复。
     */
    public void clearSnapshot() {
        this.hasSnapshot = false;
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
