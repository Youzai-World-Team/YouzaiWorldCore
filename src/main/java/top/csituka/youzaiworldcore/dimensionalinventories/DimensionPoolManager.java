package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 维度池传送管理器 — 核心调度器。
 * <p>
 * 负责：
 * <ul>
 *   <li>玩家在维度池之间的状态保存/加载</li>
 *   <li>背包清空与恢复</li>
 *   <li>游戏模式的强制设置</li>
 *   <li>坐标的保存与传送</li>
 * </ul>
 */
public final class DimensionPoolManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/DimensionPoolManager");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * 防止重复处理的守卫集合。
     * <p>
     * 当 {@link #teleportToPool(ServerPlayer, String)} 主动调用
     * {@code player.teleportTo()} 时，Minecraft 内部会触发
     * {@code AFTER_PLAYER_CHANGE_LEVEL} 事件，进而调用
     * {@link #onPlayerChangeDimension(ServerPlayer, ServerLevel, ServerLevel)}。
     * 该守卫用于跳过由此触发的重复处理，防止状态被二次清空/覆盖。
     */
    private static final Set<UUID> TELEPORT_IN_PROGRESS = new HashSet<>();

    private DimensionPoolManager() {}

    // ===== 路径辅助 =====

    /**
     * 将含有非法文件名字符（如 {@code :}）的池 ID 转换为安全的目录名。
     * <p>
     * Minecraft 资源标识符含冒号 {@code :}，但 Windows 文件系统不允许冒号出现在路径中。
     * 转换为 {@code _} 以兼容各操作系统。
     */
    private static String sanitizePoolId(String poolId) {
        return poolId.replace(':', '_');
    }

    /** 玩家状态数据根目录：<world>/youzaiworldcore/dimensional_inventories/data/ */
    private static Path getDataRoot(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("youzaiworldcore")
                .resolve("dimensional_inventories")
                .resolve("data");
    }

    /** 获取指定池中指定玩家的状态文件路径 */
    private static Path getPlayerDataFile(MinecraftServer server, String poolId, String playerUuid) {
        return getDataRoot(server).resolve(sanitizePoolId(poolId)).resolve(playerUuid + ".json");
    }

    /** 从玩家对象获取 MinecraftServer */
    private static MinecraftServer getServer(ServerPlayer player) {
        return player.level().getServer();
    }

    // ===== 核心传送方法 =====

    /**
     * 将玩家传送到指定的目标维度池。
     * <p>
     * 流程：
     * <ol>
     *   <li>检查目标池是否存在</li>
     *   <li>如果目标池为空（无维度），提示管理员设置并取消传送</li>
     *   <li>保存玩家当前状态到源池</li>
     *   <li>清空玩家背包</li>
     *   <li>加载目标池的玩家历史状态</li>
     *   <li>将玩家传送到目标池保存的维度与坐标</li>
     *   <li>强制设置目标池的游戏模式</li>
     * </ol>
     */
    public static boolean teleportToPool(ServerPlayer player, String targetPoolId) {
        MinecraftServer server = getServer(player);

        // 1. 查找目标池
        DimensionPool targetPool = DimensionPoolSettings.getPool(targetPoolId);
        if (targetPool == null) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.pool_not_found", targetPoolId));
            return false;
        }

        // 2. 检查目标池是否为空
        if (targetPool.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.pool_empty", targetPool.displayName()));
            return false;
        }

        // 3. 确定源池 & 已在目标池中则跳过
        String currentDimensionId = player.level().dimension().identifier().toString();
        DimensionPool sourcePool = DimensionPoolSettings.getPoolByDimension(currentDimensionId).orElse(null);

        if (sourcePool != null && sourcePool.id().equals(targetPoolId)) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.already_in_pool", targetPool.displayName()));
            return true;
        }

        // ★★★ 4. 先确定传送到哪个维度，验证可用性，再修改玩家状态 ★★★
        ResourceKey<Level> targetDimensionKey = determineTeleportTarget(player, server, targetPool);
        if (targetDimensionKey == null) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.no_valid_dimension",
                    targetPool.displayName()));
            return false;
        }

        ServerLevel targetLevel = server.getLevel(targetDimensionKey);
        if (targetLevel == null) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.dimension_not_found",
                    targetDimensionKey.identifier().toString()));
            return false;
        }

        // 计算传送坐标
        double teleportX, teleportY, teleportZ;
        float teleportYRot, teleportXRot;
        boolean hasSavedState = hasSavedPlayerData(player, server, targetPool.id());
        if (hasSavedState) {
            PlayerStateData loadedData = getLastLoadedData(player, server);
            if (loadedData != null) {
                teleportX = loadedData.getX();
                teleportY = loadedData.getY();
                teleportZ = loadedData.getZ();
                teleportYRot = loadedData.getYRot();
                teleportXRot = loadedData.getXRot();
            } else {
                teleportX = 0.5; teleportY = 100; teleportZ = 0.5;
                teleportYRot = 90; teleportXRot = 0;
            }
        } else {
            teleportX = 0.5; teleportY = 100; teleportZ = 0.5;
            teleportYRot = 90; teleportXRot = 0;
        }

        // ★★★ 5. 验证通过，现在才修改玩家状态 ★★★
        // 保存当前状态到源池
        if (sourcePool != null) {
            savePlayerState(player, server, sourcePool.id());
        }

        // 清空背包 + 移除效果
        PlayerStateData.clearPlayerInventory(player);
        player.removeAllEffects();

        // 加载目标池的历史状态
        loadPlayerState(player, server, targetPool.id());

        // 6. 执行传送
        TELEPORT_IN_PROGRESS.add(player.getUUID());
        try {
            player.teleportTo(targetLevel, teleportX, teleportY, teleportZ,
                    java.util.Set.of(), teleportYRot, teleportXRot, true);
        } finally {
            TELEPORT_IN_PROGRESS.remove(player.getUUID());
        }

        // 7. 强制设置游戏模式
        player.setGameMode(targetPool.gameMode());

        // 8. 成功消息
        player.sendSystemMessage(Component.translatable(
                "youzaiworldcore.message.diminv.teleport_success",
                targetPool.displayName()));

        LOGGER.info("玩家 {} 已传送到维度池 {}",
                player.getName().getString(), targetPoolId);
        return true;
    }

    /**
     * 确定传送目标维度键。
     * <p>
     * 优先使用保存的历史数据中的维度；无历史数据时使用池配置的首个可用维度。
     * 逐一验证池中的每个维度，返回第一个在服务端已加载的维度。
     *
     * @return 可用的目标维度 ResourceKey，如果池中所有维度均不可用则返回 null
     */
    private static ResourceKey<Level> determineTeleportTarget(
            ServerPlayer player, MinecraftServer server, DimensionPool targetPool) {

        // 优先使用已保存数据中的维度（但必须属于目标池）
        if (hasSavedPlayerData(player, server, targetPool.id())) {
            PlayerStateData loadedData = getLastLoadedData(player, server);
            if (loadedData != null) {
                String dimStr = loadedData.getDimension();
                if (dimStr != null && !dimStr.isEmpty()
                        && targetPool.containsDimension(dimStr)) {  // ★ 验证维度属于此池
                    ResourceKey<Level> key = ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            Identifier.parse(dimStr));
                    if (server.getLevel(key) != null) {
                        return key;
                    }
                    LOGGER.warn("保存的维度 {} 不可用，尝试池中的其他维度", dimStr);
                } else {
                    LOGGER.debug("忽略已保存的维度 {}（不属于池 {}）", dimStr, targetPool.id());
                }
            }
        }

        // 逐一遍历池中的维度，返回第一个可用的
        for (String dimId : targetPool.dimensions()) {
            if (dimId == null || dimId.isEmpty()) continue;
            ResourceKey<Level> key = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(dimId));
            if (server.getLevel(key) != null) {
                return key;
            }
            LOGGER.warn("池 {} 中的维度 {} 不可用，尝试下一个",
                    targetPool.displayName(), dimId);
        }

        return null;
    }

    /** 检查指定池是否存在玩家的历史状态数据文件 */
    private static boolean hasSavedPlayerData(ServerPlayer player, MinecraftServer server, String poolId) {
        try {
            Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
            return Files.exists(file);
        } catch (Exception e) {
            return false;
        }
    }

    // ===== 状态保存/加载 =====

    private static void savePlayerState(ServerPlayer player, MinecraftServer server, String poolId) {
        try {
            PlayerStateData data = PlayerStateData.fromPlayer(player);
            Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
            Files.createDirectories(file.getParent());
            String json = GSON.toJson(data);
            Files.writeString(file, json);
            LOGGER.debug("玩家 {} 的状态已保存到池 {}", player.getName().getString(), poolId);
        } catch (Exception e) {
            LOGGER.error("保存玩家 {} 状态到池 {} 失败: {}",
                    player.getName().getString(), poolId, e.getMessage());
            player.sendSystemMessage(Component.literal("§c⚠ 数据保存失败，请联系管理员。"));
        }
    }

    private static boolean loadPlayerState(ServerPlayer player, MinecraftServer server, String poolId) {
        try {
            Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
            if (!Files.exists(file)) {
                return false;
            }
            String json = Files.readString(file);
            PlayerStateData data = GSON.fromJson(json, PlayerStateData.class);
            if (data == null) return false;

            data.applyToPlayer(player);
            LOGGER.debug("玩家 {} 的状态已从池 {} 加载", player.getName().getString(), poolId);
            return true;
        } catch (Exception e) {
            LOGGER.error("加载玩家 {} 状态从池 {} 失败: {}",
                    player.getName().getString(), poolId, e.getMessage());
            return false;
        }
    }

    private static PlayerStateData getLastLoadedData(ServerPlayer player, MinecraftServer server) {
        try {
            String currentDim = player.level().dimension().identifier().toString();
            var poolOpt = DimensionPoolSettings.getPoolByDimension(currentDim);
            if (poolOpt.isEmpty()) return null;

            Path file = getPlayerDataFile(server, poolOpt.get().id(), player.getUUID().toString());
            if (!Files.exists(file)) return null;

            String json = Files.readString(file);
            return GSON.fromJson(json, PlayerStateData.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ===== 事件处理 =====

    public static void onPlayerChangeDimension(ServerPlayer player,
                                                ServerLevel origin,
                                                ServerLevel destination) {
        // 守卫检查：如果该玩家的传送是由 teleportToPool() 主动触发的，
        // 则跳过重复处理（teleportToPool() 已完成了状态保存/加载）。
        if (TELEPORT_IN_PROGRESS.contains(player.getUUID())) {
            LOGGER.debug("跳过玩家 {} 的维度变化事件（由 teleportToPool 主动触发）",
                    player.getName().getString());
            return;
        }

        String originDim = origin.dimension().identifier().toString();
        String destDim = destination.dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(originDim, destDim);
        if (samePool) return;

        DimensionPool sourcePool = DimensionPoolSettings.getPoolByDimension(originDim).orElse(null);
        DimensionPool destPool = DimensionPoolSettings.getPoolByDimension(destDim).orElse(null);

        if (destPool == null) return;

        MinecraftServer server = getServer(player);

        if (sourcePool != null && !sourcePool.id().equals(destPool.id())) {
            savePlayerState(player, server, sourcePool.id());
            PlayerStateData.clearPlayerInventory(player);
            player.removeAllEffects();
        }

        loadPlayerState(player, server, destPool.id());
        GameType targetGameMode = destPool.gameMode();
        if (targetGameMode != null) {
            player.setGameMode(targetGameMode);
        }
    }

    /**
     * 处理玩家复活事件。
     * <p>
     * 玩家死亡时，Minecraft 原生机制已处理了物品掉落和状态重置。
     * 此方法仅做两件事：
     * <ol>
     *   <li>记录日志用于调试</li>
     *   <li>根据复活后的维度设置正确的游戏模式</li>
     * </ol>
     * <p>
     * <b>注意：</b>不应在此处保存 oldPlayer 状态到源池。
     * 死亡状态（空背包、死亡坐标）会覆盖该池之前保存的正确状态，
     * 导致后续传送回该池时玩家获得空背包和错误坐标，
     * 进而引发 {@code server.getLevel()} 找不到维度而使传送失效。
     */
    public static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        if (TELEPORT_IN_PROGRESS.contains(newPlayer.getUUID())) {
            LOGGER.debug("跳过玩家 {} 的复活事件（teleport 进行中）", newPlayer.getName().getString());
            return;
        }

        String oldDim = oldPlayer.level().dimension().identifier().toString();
        String newDim = newPlayer.level().dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(oldDim, newDim);
        LOGGER.info("玩家 {} 复活：{} → {} {}",
                newPlayer.getName().getString(), oldDim, newDim,
                samePool ? "（同池，不改状态）" : "（跨池，仅设游戏模式）");

        // 仅设置游戏模式，不做状态保存/加载
        setPoolGameMode(newPlayer, newDim);
    }

    /** 根据当前维度设置玩家的游戏模式 */
    private static void setPoolGameMode(ServerPlayer player, String dimensionId) {
        DimensionPoolSettings.getPoolByDimension(dimensionId).ifPresent(pool -> {
            GameType targetGameMode = pool.gameMode();
            if (targetGameMode != null) {
                player.setGameMode(targetGameMode);
            }
        });
    }

    /**
     * 清理指定玩家的传送守卫标记。
     * <p>
     * 在玩家断开连接时调用，防止因断线导致 {@link #TELEPORT_IN_PROGRESS} 留下脏数据，
     * 进而影响该玩家重新加入后的维度切换处理。
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (TELEPORT_IN_PROGRESS.remove(uuid)) {
            LOGGER.warn("清理玩家 {} 的残留传送守卫标记", player.getName().getString());
        }
    }

    public static void onNonPlayerEntityChangeDimension(net.minecraft.world.entity.Entity originalEntity,
                                                         net.minecraft.world.entity.Entity newEntity,
                                                         ServerLevel origin,
                                                         ServerLevel destination) {
        String originDim = origin.dimension().identifier().toString();
        String destDim = destination.dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(originDim, destDim);
        if (samePool) return;

        if (originalEntity instanceof net.minecraft.world.entity.item.ItemEntity
                || originalEntity instanceof net.minecraft.world.entity.projectile.Projectile
                || originalEntity instanceof net.minecraft.world.entity.monster.Monster) {
            originalEntity.discard();
        }
    }
}
