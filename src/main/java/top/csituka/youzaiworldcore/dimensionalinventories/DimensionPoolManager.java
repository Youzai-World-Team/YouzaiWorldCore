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

        // 3. 确定源池
        String currentDimensionId = player.level().dimension().identifier().toString();
        DimensionPool sourcePool = DimensionPoolSettings.getPoolByDimension(currentDimensionId).orElse(null);

        // 4. 已在目标池中则跳过
        if (sourcePool != null && sourcePool.id().equals(targetPoolId)) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.already_in_pool", targetPool.displayName()));
            return true;
        }

        // 5. 保存当前状态到源池
        if (sourcePool != null) {
            savePlayerState(player, server, sourcePool.id());
        }

        // 6. 清空背包 + 移除效果
        PlayerStateData.clearPlayerInventory(player);
        player.removeAllEffects();

        // 7. 加载目标池的历史状态
        boolean hasSavedState = loadPlayerState(player, server, targetPool.id());

        // 8. 确定传送目标
        ResourceKey<Level> targetDimensionKey;
        double teleportX, teleportY, teleportZ;
        float teleportYRot, teleportXRot;

        if (hasSavedState) {
            PlayerStateData loadedData = getLastLoadedData(player, server);
            if (loadedData != null) {
                targetDimensionKey = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        Identifier.parse(loadedData.getDimension()));
                teleportX = loadedData.getX();
                teleportY = loadedData.getY();
                teleportZ = loadedData.getZ();
                teleportYRot = loadedData.getYRot();
                teleportXRot = loadedData.getXRot();
            } else {
                String firstDim = targetPool.dimensions().first();
                targetDimensionKey = ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        Identifier.parse(firstDim));
                teleportX = 0.5; teleportY = 100; teleportZ = 0.5;
                teleportYRot = 90; teleportXRot = 0;
            }
        } else {
            String firstDim = targetPool.dimensions().first();
            targetDimensionKey = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(firstDim));
            teleportX = 0.5; teleportY = 100; teleportZ = 0.5;
            teleportYRot = 90; teleportXRot = 0;
        }

        // 9. 获取目标 ServerLevel
        ServerLevel targetLevel = server.getLevel(targetDimensionKey);
        if (targetLevel == null) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.dimension_not_found",
                    targetDimensionKey.identifier().toString()));
            return false;
        }

        // 10. 设置守卫标记，防止 AFTER_PLAYER_CHANGE_LEVEL 事件重复处理
        TELEPORT_IN_PROGRESS.add(player.getUUID());
        try {
            player.teleportTo(targetLevel, teleportX, teleportY, teleportZ,
                    java.util.Set.of(), teleportYRot, teleportXRot, true);
        } finally {
            TELEPORT_IN_PROGRESS.remove(player.getUUID());
        }

        // 11. 强制设置游戏模式
        player.setGameMode(targetPool.gameMode());

        // 12. 成功消息
        player.sendSystemMessage(Component.translatable(
                "youzaiworldcore.message.diminv.teleport_success",
                targetPool.displayName()));

        LOGGER.info("玩家 {} 已传送到维度池 {}",
                player.getName().getString(), targetPoolId);
        return true;
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

    public static void onPlayerRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        String currentDim = newPlayer.level().dimension().identifier().toString();
        DimensionPoolSettings.getPoolByDimension(currentDim).ifPresent(pool -> {
            GameType targetGameMode = pool.gameMode();
            if (targetGameMode != null) {
                newPlayer.setGameMode(targetGameMode);
            }
        });
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
