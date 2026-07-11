package top.csituka.youzaiworldcore.invisibility;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 隐身功能管理器。
 * <p>
 * 负责：</p>
 * <ul>
 *   <li>管理玩家的隐身状态</li>
 *   <li>创建/销毁白色 Boss 栏（标题「隐身中」）</li>
 *   <li>向其他玩家发送伪装退服/进服消息</li>
 *   <li>从 Tab 列表移除/恢复玩家</li>
 *   <li>从其他玩家视野中隐藏/恢复玩家实体</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class InvisibilityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/InvisibilityManager");

    /** 隐身在线的玩家 UUID 集合 */
    private static final Set<UUID> INVISIBLE_PLAYERS = new HashSet<>();

    /** 每个隐身玩家对应的 Boss 栏 */
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();

    /**
     * 被隐身玩家交互的容器位置集合。
     * 当隐身玩家打开容器时，记录该容器位置，用于抑制动画和声音的广播。
     */
    private static final Set<BlockPos> INVISIBLE_CONTAINER_INTERACTIONS = ConcurrentHashMap.newKeySet();

    /** 权限节点 */
    public static final String PERMISSION_INVISIBILITY = "youzaiworldcore.command.function.invisibility";

    // ==================== 公开 API ====================

    /**
     * 判断玩家是否处于隐身状态。
     */
    public static boolean isInvisible(ServerPlayer player) {
        DebugLogger.entering("InvisibilityManager", "isInvisible", "player=" + player.getName().getString());
        boolean result = INVISIBLE_PLAYERS.contains(player.getUUID());
        DebugLogger.exiting("InvisibilityManager", "isInvisible", String.valueOf(result));
        return result;
    }

    /**
     * 判断玩家是否有权使用隐身功能。
     * <p>只有 OP（原版 4 级）或拥有权限节点的玩家才能使用。</p>
     */
    public static boolean hasPermission(ServerPlayer player) {
        DebugLogger.entering("InvisibilityManager", "hasPermission", "player=" + player.getName().getString());
        // OP 检查（4 级）
        if (Commands.LEVEL_ADMINS.check(player.permissions())) {
            DebugLogger.branch("InvisibilityManager", "OP 4级权限检查通过", true);
            DebugLogger.exiting("InvisibilityManager", "hasPermission", "true (OP)");
            return true;
        }
        DebugLogger.branch("InvisibilityManager", "OP 4级权限检查未通过，检查 LuckPerms", false);
        // LuckPerms 权限节点检查
        boolean hasLuckPerms = top.csituka.youzaiworldcore.luckperms.LuckPermsHelper.checkLuckPermsOnly(
                player.getUUID(), PERMISSION_INVISIBILITY
        );
        DebugLogger.branch("InvisibilityManager", "LuckPerms 权限检查", hasLuckPerms);
        DebugLogger.exiting("InvisibilityManager", "hasPermission", String.valueOf(hasLuckPerms));
        return hasLuckPerms;
    }

    /**
     * 开启隐身。
     *
     * @param player 目标玩家
     */
    public static void enable(ServerPlayer player) {
        String playerName = player.getName().getString();
        DebugLogger.entering("InvisibilityManager", "enable", "player=" + playerName);
        UUID uuid = player.getUUID();
        if (INVISIBLE_PLAYERS.contains(uuid)) {
            DebugLogger.branch("InvisibilityManager", "玩家已处于隐身状态", true, playerName);
            player.sendSystemMessage(Component.literal("§c你已经处于隐身状态了"));
            DebugLogger.exiting("InvisibilityManager", "enable");
            return;
        }
        DebugLogger.branch("InvisibilityManager", "玩家未隐身，开始执行启用流程", false);

        MinecraftServer server = player.level().getServer();
        PlayerList playerList = server.getPlayerList();
        List<ServerPlayer> allPlayers = playerList.getPlayers();

        // 1. 添加隐身状态效果（使本体及装备不可见）
        DebugLogger.branch("InvisibilityManager", "步骤1: 添加隐身状态效果", true);
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                MobEffectInstance.INFINITE_DURATION,
                0,
                false,
                false,
                false
        ));

        // 2. 仅从其他玩家的 Tab 列表中移除（自己保留，方便观察）
        DebugLogger.branch("InvisibilityManager", "步骤2: 从其他玩家 Tab 列表中移除", true);
        ClientboundPlayerInfoRemovePacket removePacket =
                new ClientboundPlayerInfoRemovePacket(List.of(uuid));
        for (ServerPlayer other : allPlayers) {
            if (other != player) {
                other.connection.send(removePacket);
            }
        }

        // 3. 仅从其他玩家的视野中移除实体（自己不移除）
        DebugLogger.branch("InvisibilityManager", "步骤3: 从其他玩家视野中移除实体", true);
        ClientboundRemoveEntitiesPacket removeEntityPacket =
                new ClientboundRemoveEntitiesPacket(player.getId());
        for (ServerPlayer other : allPlayers) {
            if (other != player) {
                other.connection.send(removeEntityPacket);
            }
        }

        // 4. 仅给其他玩家发送伪装退服消息
        DebugLogger.branch("InvisibilityManager", "步骤4: 发送伪装退服消息给其他玩家", true);
        Component leaveMessage = Component.translatable(
                "multiplayer.player.left", player.getDisplayName()
        );
        for (ServerPlayer other : allPlayers) {
            if (other != player) {
                other.sendSystemMessage(leaveMessage);
            }
        }

        // 5. 给自己发送提示
        player.sendSystemMessage(Component.literal("§7你已进入隐身状态"));

        // 6. 创建 Boss 栏
        ServerBossEvent bossBar = new ServerBossEvent(
                UUID.randomUUID(),
                Component.literal("§f隐身中"),
                BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.PROGRESS
        );
        bossBar.setProgress(1.0f);
        bossBar.setVisible(true);
        bossBar.addPlayer(player);
        BOSS_BARS.put(uuid, bossBar);

        // 7. 记录状态
        INVISIBLE_PLAYERS.add(uuid);
        DebugLogger.stateChange("InvisibilityManager", String.valueOf(uuid), "INVISIBLE_PLAYERS", "added");
        DebugLogger.stateChange("InvisibilityManager", String.valueOf(uuid), "BOSS_BARS", "created");
        DebugLogger.info("InvisibilityManager", "玩家 %s 已开启隐身", playerName);
        LOGGER.info("玩家 {} 已开启隐身", player.getName().getString());
        DebugLogger.exiting("InvisibilityManager", "enable");
    }

    /**
     * 关闭隐身。
     *
     * @param player 目标玩家
     */
    public static void disable(ServerPlayer player) {
        String playerName = player.getName().getString();
        DebugLogger.entering("InvisibilityManager", "disable", "player=" + playerName);
        UUID uuid = player.getUUID();
        if (!INVISIBLE_PLAYERS.contains(uuid)) {
            DebugLogger.branch("InvisibilityManager", "玩家未处于隐身状态", true, playerName);
            player.sendSystemMessage(Component.literal("§c你并未处于隐身状态"));
            DebugLogger.exiting("InvisibilityManager", "disable");
            return;
        }
        DebugLogger.branch("InvisibilityManager", "玩家隐身中，开始执行关闭流程", false);

        MinecraftServer server = player.level().getServer();
        PlayerList playerList = server.getPlayerList();
        List<ServerPlayer> allPlayers = playerList.getPlayers();

        // 1. 移除隐身状态效果
        DebugLogger.branch("InvisibilityManager", "步骤1: 移除隐身状态效果", true);
        player.removeEffect(MobEffects.INVISIBILITY);

        // 2. 仅向其他玩家重新发送玩家信息（恢复 Tab 列表）
        DebugLogger.branch("InvisibilityManager", "步骤2: 恢复其他玩家 Tab 列表", true);
        ClientboundPlayerInfoUpdatePacket addToTabPacket =
                new ClientboundPlayerInfoUpdatePacket(
                        EnumSet.allOf(ClientboundPlayerInfoUpdatePacket.Action.class),
                        List.of(player)
                );
        for (ServerPlayer other : allPlayers) {
            if (other != player) {
                other.connection.send(addToTabPacket);
            }
        }

        // 3. 仅向其他玩家重新发送玩家实体包（恢复可见）
        DebugLogger.branch("InvisibilityManager", "步骤3: 恢复其他玩家实体可见", true);
        List<ServerPlayer> others = allPlayers.stream()
                .filter(p -> p != player)
                .toList();
        resendPlayerEntity(player, others);

        // 4. 发送伪装进服消息给其他玩家
        DebugLogger.branch("InvisibilityManager", "步骤4: 发送伪装进服消息给其他玩家", true);
        Component joinMessage = Component.translatable(
                "multiplayer.player.joined", player.getDisplayName()
        );
        for (ServerPlayer other : allPlayers) {
            if (other != player) {
                other.sendSystemMessage(joinMessage);
            }
        }

        // 5. 给自己发送提示
        player.sendSystemMessage(Component.literal("§7你已退出隐身状态"));

        // 6. 移除 Boss 栏
        ServerBossEvent bossBar = BOSS_BARS.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }

        // 7. 清除状态
        INVISIBLE_PLAYERS.remove(uuid);
        DebugLogger.stateChange("InvisibilityManager", String.valueOf(uuid), "INVISIBLE_PLAYERS", "removed");
        DebugLogger.stateChange("InvisibilityManager", String.valueOf(uuid), "BOSS_BARS", "removed");
        LOGGER.info("玩家 {} 已关闭隐身", player.getName().getString());
        DebugLogger.exiting("InvisibilityManager", "disable");
    }

    /**
     * 强制关闭指定玩家的隐身（不发送加入/离开提示）。
     * 用于自动关闭场景（如切换生存模式、玩家登出等）。
     */
    public static void forceDisable(ServerPlayer player) {
        String playerName = player.getName().getString();
        DebugLogger.entering("InvisibilityManager", "forceDisable", "player=" + playerName);
        UUID uuid = player.getUUID();
        if (!INVISIBLE_PLAYERS.contains(uuid)) {
            DebugLogger.branch("InvisibilityManager", "玩家未隐身，无需强制关闭", true, playerName);
            DebugLogger.exiting("InvisibilityManager", "forceDisable");
            return;
        }
        DebugLogger.branch("InvisibilityManager", "玩家隐身中，开始执行强制关闭流程", false);

        MinecraftServer server = player.level().getServer();
        PlayerList playerList = server.getPlayerList();
        List<ServerPlayer> allPlayers = playerList.getPlayers();

        // 1. 移除效果
        DebugLogger.branch("InvisibilityManager", "步骤1: 移除隐身效果", true);
        player.removeEffect(MobEffects.INVISIBILITY);

        // 2. 仅向其他玩家恢复 Tab 列表
        DebugLogger.branch("InvisibilityManager", "步骤2: 恢复其他玩家 Tab 列表", true);
        ClientboundPlayerInfoUpdatePacket addToTabPacket =
                new ClientboundPlayerInfoUpdatePacket(
                        EnumSet.allOf(ClientboundPlayerInfoUpdatePacket.Action.class),
                        List.of(player)
                );
        for (ServerPlayer other : allPlayers) {
            if (other != player) {
                other.connection.send(addToTabPacket);
            }
        }

        // 3. 仅恢复其他玩家的实体可见
        DebugLogger.branch("InvisibilityManager", "步骤3: 恢复其他玩家实体可见", true);
        List<ServerPlayer> others = allPlayers.stream()
                .filter(p -> p != player)
                .toList();
        resendPlayerEntity(player, others);

        // 4. 移除 Boss 栏
        ServerBossEvent bossBar = BOSS_BARS.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }

        // 5. 清除状态
        INVISIBLE_PLAYERS.remove(uuid);
        DebugLogger.stateChange("InvisibilityManager", String.valueOf(uuid), "INVISIBLE_PLAYERS", "removed");
        DebugLogger.stateChange("InvisibilityManager", String.valueOf(uuid), "BOSS_BARS", "removed");
        LOGGER.info("玩家 {} 隐身已被强制关闭", player.getName().getString());
        DebugLogger.exiting("InvisibilityManager", "forceDisable");
    }

    /**
     * 当玩家登出时，清理其隐身状态（不向外广播）。
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        String playerName = player.getName().getString();
        DebugLogger.entering("InvisibilityManager", "onPlayerDisconnect", "player=" + playerName);
        UUID uuid = player.getUUID();
        if (!INVISIBLE_PLAYERS.contains(uuid)) {
            DebugLogger.branch("InvisibilityManager", "玩家未隐身，无需清理", true, playerName);
            DebugLogger.exiting("InvisibilityManager", "onPlayerDisconnect");
            return;
        }
        DebugLogger.branch("InvisibilityManager", "玩家隐身中，清理隐身状态", false);

        // 移除 Boss 栏
        ServerBossEvent bossBar = BOSS_BARS.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }

        INVISIBLE_PLAYERS.remove(uuid);
        DebugLogger.stateChange("InvisibilityManager", String.valueOf(uuid), "INVISIBLE_PLAYERS", "removed (disconnect)");
        DebugLogger.stateChange("InvisibilityManager", String.valueOf(uuid), "BOSS_BARS", "removed (disconnect)");
        LOGGER.info("玩家 {} 登出，已清理隐身状态", player.getName().getString());
        DebugLogger.exiting("InvisibilityManager", "onPlayerDisconnect");
    }

    /**
     * 获取当前所有隐身中的玩家 UUID 集合（只读视图）。
     */
    public static Set<UUID> getInvisiblePlayers() {
        DebugLogger.entering("InvisibilityManager", "getInvisiblePlayers");
        Set<UUID> result = Collections.unmodifiableSet(INVISIBLE_PLAYERS);
        DebugLogger.exiting("InvisibilityManager", "getInvisiblePlayers", "size=" + result.size());
        return result;
    }

    // ==================== 容器交互跟踪 ====================

    /**
     * 标记一个容器位置作为"被隐身玩家交互中"。
     * 该标记会抑制该容器的动画和声音广播给其他玩家。
     *
     * @param pos 容器位置
     */
    public static void markContainerInteraction(BlockPos pos) {
        INVISIBLE_CONTAINER_INTERACTIONS.add(pos);
    }

    /**
     * 检查指定位置是否被隐身玩家交互（需要抑制动画/声音）。
     *
     * @param pos 容器位置
     * @return 如果该位置被隐身玩家交互则返回 true
     */
    public static boolean isContainerInteractionBlocked(BlockPos pos) {
        return INVISIBLE_CONTAINER_INTERACTIONS.contains(pos);
    }

    /**
     * 清除指定位置的隐身容器交互标记。
     *
     * @param pos 容器位置
     */
    public static void clearContainerInteraction(BlockPos pos) {
        INVISIBLE_CONTAINER_INTERACTIONS.remove(pos);
    }

    // ==================== 内部方法 ====================

    /**
     * 向指定玩家列表重新发送隐身玩家的实体包，使其恢复可见。
     * <p>
     * 由于之前通过 {@link ClientboundRemoveEntitiesPacket} 移除了实体，
     * 服务器不会自动重新发送，需要手动发送添加实体包、同步数据和效果。
     * </p>
     */
    @SuppressWarnings({"null"})
    private static void resendPlayerEntity(ServerPlayer player, Collection<ServerPlayer> targets) {
        String playerName = player.getName().getString();
        DebugLogger.entering("InvisibilityManager", "resendPlayerEntity",
                "player=" + playerName + ", targetCount=" + targets.size());
        int entityId = player.getId();
        UUID uuid = player.getUUID();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float yHeadRot = player.getYHeadRot();
        Vec3 velocity = player.getDeltaMovement();

        // 构造添加实体包
        ClientboundAddEntityPacket addEntityPacket = new ClientboundAddEntityPacket(
                entityId,
                uuid,
                x, y, z,
                xRot, yRot,
                EntityTypes.PLAYER,
                0,                  // data（玩家无额外数据）
                velocity,
                yHeadRot
        );

        // 获取实体的非默认同步数据
        List<SynchedEntityData.DataValue<?>> packedData =
                player.getEntityData().getNonDefaultValues();

        for (ServerPlayer target : targets) {
            String targetName = target.getName().getString();
            // 1. 发送添加实体包
            target.connection.send(addEntityPacket);
            DebugLogger.trace("InvisibilityManager", "发送添加实体包到 %s (entityId=%d)", targetName, entityId);

            // 2. 发送实体同步数据（包括装备、状态等）
            if (packedData != null && !packedData.isEmpty()) {
                target.connection.send(new ClientboundSetEntityDataPacket(entityId, packedData));
                DebugLogger.trace("InvisibilityManager", "发送实体同步数据到 %s (entityId=%d)", targetName, entityId);
            }

            // 3. 发送位置同步包（确保位置精确）
            target.connection.send(ClientboundTeleportEntityPacket.teleport(
                    entityId,
                    PositionMoveRotation.of(player),
                    Collections.emptySet(),
                    player.onGround()
            ));
            DebugLogger.trace("InvisibilityManager", "发送位置同步包到 %s (entityId=%d)", targetName, entityId);

            // 4. 发送所有活跃状态效果
            for (MobEffectInstance effect : player.getActiveEffects()) {
                target.connection.send(new ClientboundUpdateMobEffectPacket(
                        entityId, effect, false
                ));
                DebugLogger.trace("InvisibilityManager", "发送状态效果到 %s (entityId=%d, effect=%s)",
                        targetName, entityId, effect.getEffect().getRegisteredName());
            }
        }
        DebugLogger.exiting("InvisibilityManager", "resendPlayerEntity");
    }
}
