package top.csituka.youzaiworldcore.invisibility;

import net.minecraft.commands.Commands;
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

import java.util.*;

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
public final class InvisibilityManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("InvisibilityManager");

    /** 隐身在线的玩家 UUID 集合 */
    private static final Set<UUID> INVISIBLE_PLAYERS = new HashSet<>();

    /** 每个隐身玩家对应的 Boss 栏 */
    private static final Map<UUID, ServerBossEvent> BOSS_BARS = new HashMap<>();

    /** 权限节点 */
    public static final String PERMISSION_INVISIBILITY = "youzaiworldcore.command.function.invisibility";

    // ==================== 公开 API ====================

    /**
     * 判断玩家是否处于隐身状态。
     */
    public static boolean isInvisible(ServerPlayer player) {
        return INVISIBLE_PLAYERS.contains(player.getUUID());
    }

    /**
     * 判断玩家是否有权使用隐身功能。
     * <p>只有 OP（原版 4 级）或拥有权限节点的玩家才能使用。</p>
     */
    public static boolean hasPermission(ServerPlayer player) {
        // OP 检查（4 级）
        if (Commands.LEVEL_ADMINS.check(player.permissions())) {
            return true;
        }
        // LuckPerms 权限节点检查
        return top.csituka.youzaiworldcore.luckperms.LuckPermsHelper.checkLuckPermsOnly(
                player.getUUID(), PERMISSION_INVISIBILITY
        );
    }

    /**
     * 开启隐身。
     *
     * @param player 目标玩家
     */
    public static void enable(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (INVISIBLE_PLAYERS.contains(uuid)) {
            player.sendSystemMessage(Component.literal("§c你已经处于隐身状态了"));
            return;
        }

        MinecraftServer server = player.level().getServer();
        PlayerList playerList = server.getPlayerList();
        List<ServerPlayer> allPlayers = playerList.getPlayers();

        // 1. 添加隐身状态效果（使本体及装备不可见）
        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                MobEffectInstance.INFINITE_DURATION,
                0,
                false,
                false,
                false
        ));

        // 2. 从其他玩家的 Tab 列表中移除
        ClientboundPlayerInfoRemovePacket removePacket =
                new ClientboundPlayerInfoRemovePacket(List.of(uuid));
        for (ServerPlayer other : allPlayers) {
            if (other != player) {
                other.connection.send(removePacket);
            }
        }

        // 3. 从其他玩家的视野中移除实体
        ClientboundRemoveEntitiesPacket removeEntityPacket =
                new ClientboundRemoveEntitiesPacket(player.getId());
        for (ServerPlayer other : allPlayers) {
            if (other != player) {
                other.connection.send(removeEntityPacket);
            }
        }

        // 4. 发送伪装退服消息给其他玩家
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
        LOGGER.info("玩家 {} 已开启隐身", player.getName().getString());
    }

    /**
     * 关闭隐身。
     *
     * @param player 目标玩家
     */
    public static void disable(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!INVISIBLE_PLAYERS.contains(uuid)) {
            player.sendSystemMessage(Component.literal("§c你并未处于隐身状态"));
            return;
        }

        MinecraftServer server = player.level().getServer();
        PlayerList playerList = server.getPlayerList();
        List<ServerPlayer> allPlayers = playerList.getPlayers();

        // 1. 移除隐身状态效果
        player.removeEffect(MobEffects.INVISIBILITY);

        // 2. 重新发送玩家信息给所有其他玩家（恢复 Tab 列表）
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

        // 3. 向其他玩家重新发送玩家实体包（恢复可见）
        resendPlayerEntity(player, allPlayers);

        // 4. 发送伪装进服消息给其他玩家
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
        LOGGER.info("玩家 {} 已关闭隐身", player.getName().getString());
    }

    /**
     * 强制关闭指定玩家的隐身（不发送加入/离开提示）。
     * 用于自动关闭场景（如切换生存模式、玩家登出等）。
     */
    public static void forceDisable(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!INVISIBLE_PLAYERS.contains(uuid)) return;

        MinecraftServer server = player.level().getServer();
        PlayerList playerList = server.getPlayerList();
        List<ServerPlayer> allPlayers = playerList.getPlayers();

        // 1. 移除效果
        player.removeEffect(MobEffects.INVISIBILITY);

        // 2. 恢复 Tab 和实体可见
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

        // 3. 恢复实体可见
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
        LOGGER.info("玩家 {} 隐身已被强制关闭", player.getName().getString());
    }

    /**
     * 当玩家登出时，清理其隐身状态（不向外广播）。
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!INVISIBLE_PLAYERS.contains(uuid)) return;

        // 移除 Boss 栏
        ServerBossEvent bossBar = BOSS_BARS.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAllPlayers();
        }

        INVISIBLE_PLAYERS.remove(uuid);
        LOGGER.info("玩家 {} 登出，已清理隐身状态", player.getName().getString());
    }

    /**
     * 获取当前所有隐身中的玩家 UUID 集合（只读视图）。
     */
    public static Set<UUID> getInvisiblePlayers() {
        return Collections.unmodifiableSet(INVISIBLE_PLAYERS);
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
            // 1. 发送添加实体包
            target.connection.send(addEntityPacket);

            // 2. 发送实体同步数据（包括装备、状态等）
            if (packedData != null && !packedData.isEmpty()) {
                target.connection.send(new ClientboundSetEntityDataPacket(entityId, packedData));
            }

            // 3. 发送位置同步包（确保位置精确）
            target.connection.send(ClientboundTeleportEntityPacket.teleport(
                    entityId,
                    PositionMoveRotation.of(player),
                    Collections.emptySet(),
                    player.onGround()
            ));

            // 4. 发送所有活跃状态效果
            for (MobEffectInstance effect : player.getActiveEffects()) {
                target.connection.send(new ClientboundUpdateMobEffectPacket(
                        entityId, effect, false
                ));
            }
        }
    }
}
