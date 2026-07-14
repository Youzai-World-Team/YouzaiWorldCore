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
import top.csituka.youzaiworldcore.util.DebugLogger;

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
@SuppressWarnings({"null", "unused"})
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

    /**
     * 记录跨池死亡后需要回默认出生点的玩家。
     * <p>
     * 键格式：{@code <playerUuid>|<poolId>}。当玩家在池 B 死亡、复活到池 A、
     * 再传送回池 B 时，使用池 B 配置的 {@link DimensionPool.DefaultSpawn defaultSpawn}
     * 而非从状态文件中读取的死亡位置坐标。
     * 传送完成后自动移除对应条目。
     */
    private static final Set<String> DEATH_PENDING_SPAWN = new HashSet<>();

    private DimensionPoolManager() {}

    // ===== 路径辅助 =====

    /**
     * 将含有非法文件名字符（如 {@code :}）的池 ID 转换为安全的目录名。
     * <p>
     * Minecraft 资源标识符含冒号 {@code :}，但 Windows 文件系统不允许冒号出现在路径中。
     * 转换为 {@code _} 以兼容各操作系统。
     */
    private static String sanitizePoolId(String poolId) {
        DebugLogger.entering("DimensionPoolManager", "sanitizePoolId", "poolId=" + poolId);
        String result = poolId.replace(':', '_');
        DebugLogger.exiting("DimensionPoolManager", "sanitizePoolId", "result=" + result);
        return result;
    }

    /** 玩家状态数据根目录：<world>/youzaiworldcore/dimensional_inventories/data/ */
    private static Path getDataRoot(MinecraftServer server) {
        DebugLogger.entering("DimensionPoolManager", "getDataRoot", "server=" + server);
        Path result = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("youzaiworldcore")
                .resolve("dimensional_inventories")
                .resolve("data");
        DebugLogger.exiting("DimensionPoolManager", "getDataRoot", "result=" + result);
        return result;
    }

    /** 获取指定池中指定玩家的状态文件路径 */
    private static Path getPlayerDataFile(MinecraftServer server, String poolId, String playerUuid) {
        DebugLogger.entering("DimensionPoolManager", "getPlayerDataFile",
                "server=" + server + ", poolId=" + poolId + ", playerUuid=" + playerUuid);
        Path result = getDataRoot(server).resolve(sanitizePoolId(poolId)).resolve(playerUuid + ".json");
        DebugLogger.exiting("DimensionPoolManager", "getPlayerDataFile", "result=" + result);
        return result;
    }

    /** 从玩家对象获取 MinecraftServer */
    private static MinecraftServer getServer(ServerPlayer player) {
        DebugLogger.entering("DimensionPoolManager", "getServer", "player=" + player.getName().getString());
        MinecraftServer result = player.level().getServer();
        DebugLogger.exiting("DimensionPoolManager", "getServer", "result=" + result);
        return result;
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
        DebugLogger.entering("DimensionPoolManager", "teleportToPool",
                "player=" + player.getName().getString() + ", targetPoolId=" + targetPoolId);
        MinecraftServer server = getServer(player);

        // 1. 查找目标池
        DimensionPool targetPool = DimensionPoolSettings.getPool(targetPoolId);
        DebugLogger.branch("DimensionPoolManager", "目标池是否存在", targetPool == null, "poolId=" + targetPoolId);
        if (targetPool == null) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.pool_not_found", targetPoolId));
            DebugLogger.exiting("DimensionPoolManager", "teleportToPool", "result=false (pool_not_found)");
            return false;
        }

        // 2. 检查目标池是否为空
        DebugLogger.branch("DimensionPoolManager", "目标池是否为空", targetPool.isEmpty(), "pool=" + targetPool.displayName());
        if (targetPool.isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.pool_empty", targetPool.displayName()));
            DebugLogger.exiting("DimensionPoolManager", "teleportToPool", "result=false (pool_empty)");
            return false;
        }

        // 3. 确定源池 & 已在目标池中则跳过
        String currentDimensionId = player.level().dimension().identifier().toString();
        DimensionPool sourcePool = DimensionPoolSettings.getPoolByDimension(currentDimensionId).orElse(null);

        boolean alreadyInTarget = sourcePool != null && sourcePool.id().equals(targetPoolId);
        DebugLogger.branch("DimensionPoolManager", "玩家是否已在目标池中", alreadyInTarget,
                "currentDim=" + currentDimensionId + ", sourcePool=" + (sourcePool != null ? sourcePool.id() : "null"));
        if (alreadyInTarget) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.already_in_pool", targetPool.displayName()));
            DebugLogger.exiting("DimensionPoolManager", "teleportToPool", "result=true (already_in_pool)");
            return true;
        }

        // ★★★ 4. 先确定传送到哪个维度，验证可用性，再修改玩家状态 ★★★
        ResourceKey<Level> targetDimensionKey = determineTeleportTarget(player, server, targetPool);
        DebugLogger.branch("DimensionPoolManager", "目标维度是否有效", targetDimensionKey == null, "targetPool=" + targetPool.id());
        if (targetDimensionKey == null) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.no_valid_dimension",
                    targetPool.displayName()));
            DebugLogger.exiting("DimensionPoolManager", "teleportToPool", "result=false (no_valid_dimension)");
            return false;
        }

        ServerLevel targetLevel = server.getLevel(targetDimensionKey);
        DebugLogger.branch("DimensionPoolManager", "目标维度是否已加载", targetLevel == null,
                "dimKey=" + targetDimensionKey.identifier().toString());
        if (targetLevel == null) {
            player.sendSystemMessage(Component.translatable(
                    "youzaiworldcore.message.diminv.dimension_not_found",
                    targetDimensionKey.identifier().toString()));
            DebugLogger.exiting("DimensionPoolManager", "teleportToPool", "result=false (dimension_not_found)");
            return false;
        }

        // 计算传送坐标
        double teleportX = 0.5, teleportY = 100, teleportZ = 0.5;
        float teleportYRot = 90, teleportXRot = 0;

        // ★★★ 优先使用死亡默认出生点（跨池死亡后首次传送） ★★★
        String deathKey = deathSpawnKey(player.getUUID(), targetPool.id());
        DimensionPool.DefaultSpawn defaultSpawn = null;
        boolean hasDeathPending = DEATH_PENDING_SPAWN.contains(deathKey);
        DebugLogger.branch("DimensionPoolManager", "是否有跨池死亡默认出生点待处理", hasDeathPending, "deathKey=" + deathKey);
        if (DEATH_PENDING_SPAWN.remove(deathKey)) {
            DebugLogger.stateChange("DimensionPoolManager", "DEATH_PENDING_SPAWN", "remove", deathKey, "removed");
            defaultSpawn = targetPool.defaultSpawn();
            if (defaultSpawn != null && defaultSpawn.getDimension() != null
                    && targetPool.containsDimension(defaultSpawn.getDimension())) {
                teleportX = defaultSpawn.getX();
                teleportY = defaultSpawn.getY();
                teleportZ = defaultSpawn.getZ();
                teleportYRot = defaultSpawn.getYaw();
                teleportXRot = defaultSpawn.getPitch();
                LOGGER.info("玩家 {} 使用默认出生点传送至池 {}",
                        player.getName().getString(), targetPool.id());
                DebugLogger.info("DimensionPoolManager", "玩家 %s 使用默认出生点传送至池 %s",
                        player.getName().getString(), targetPool.id());
            } else {
                DebugLogger.branch("DimensionPoolManager", "默认出生点配置无效，降级到已保存坐标",
                        defaultSpawn == null || defaultSpawn.getDimension() == null,
                        "defaultSpawn=" + defaultSpawn);
                // 配置了默认出生点但无效，降级到已保存坐标
                defaultSpawn = null;
            }
        }

        DebugLogger.branch("DimensionPoolManager", "是否使用默认出生点坐标", defaultSpawn == null, "");
        if (defaultSpawn == null) {
            boolean hasSavedState = hasSavedPlayerData(player, server, targetPool.id());
            DebugLogger.branch("DimensionPoolManager", "目标池是否有已保存的历史状态", hasSavedState,
                    "targetPoolId=" + targetPool.id());
            if (hasSavedState) {
                PlayerStateData loadedData = getLastLoadedData(player, server, targetPool.id());
                DebugLogger.branch("DimensionPoolManager", "已加载的历史数据是否有效", loadedData != null, "");
                if (loadedData != null) {
                    teleportX = loadedData.getX();
                    teleportY = loadedData.getY();
                    teleportZ = loadedData.getZ();
                    teleportYRot = loadedData.getYRot();
                    teleportXRot = loadedData.getXRot();
                    DebugLogger.info("DimensionPoolManager", "使用已保存坐标: (%s, %s, %s) 维度 %s",
                            teleportX, teleportY, teleportZ, loadedData.getDimension());
                } else {
                    teleportX = 0.5; teleportY = 100; teleportZ = 0.5;
                    teleportYRot = 90; teleportXRot = 0;
                    DebugLogger.warn("DimensionPoolManager", "已保存数据为 null，使用默认坐标");
                }
            } else {
                teleportX = 0.5; teleportY = 100; teleportZ = 0.5;
                teleportYRot = 90; teleportXRot = 0;
                DebugLogger.info("DimensionPoolManager", "无历史状态，使用默认坐标");
            }
        }

        // ★★★ 5. 验证通过，现在才修改玩家状态 ★★★
        // 保存当前状态到源池
        DebugLogger.branch("DimensionPoolManager", "是否有源池需要保存状态", sourcePool != null,
                "sourcePool=" + (sourcePool != null ? sourcePool.id() : "null"));
        if (sourcePool != null) {
            savePlayerState(player, server, sourcePool.id());
        }

        // 清空背包 + 移除效果
        DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(), "inventory", "preserved", "cleared");
        PlayerStateData.clearPlayerInventory(player);
        DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(), "effects", "present", "removed");
        player.removeAllEffects();

        // 加载目标池的历史状态
        loadPlayerState(player, server, targetPool.id());

        // 6. 执行传送
        TELEPORT_IN_PROGRESS.add(player.getUUID());
        DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(), "TELEPORT_IN_PROGRESS", "false", "true");
        try {
            player.teleportTo(targetLevel, teleportX, teleportY, teleportZ,
                    java.util.Set.of(), teleportYRot, teleportXRot, true);
        } finally {
            TELEPORT_IN_PROGRESS.remove(player.getUUID());
            DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(), "TELEPORT_IN_PROGRESS", "true", "false");
        }

        // 7. 强制设置游戏模式
        DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(), "gameMode",
                player.gameMode().toString(), targetPool.gameMode().toString());
        player.setGameMode(targetPool.gameMode());

        // 8. 成功消息
        player.sendSystemMessage(Component.translatable(
                "youzaiworldcore.message.diminv.teleport_success",
                targetPool.displayName()));

        LOGGER.info("玩家 {} 已传送到维度池 {}",
                player.getName().getString(), targetPoolId);
        DebugLogger.info("DimensionPoolManager", "玩家 %s 已传送到维度池 %s",
                player.getName().getString(), targetPoolId);
        DebugLogger.exiting("DimensionPoolManager", "teleportToPool", "result=true (success)");
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

        DebugLogger.entering("DimensionPoolManager", "determineTeleportTarget",
                "player=" + player.getName().getString() + ", targetPool=" + targetPool.id());

        // 优先使用已保存数据中的维度（但必须属于目标池）
        boolean hasSaved = hasSavedPlayerData(player, server, targetPool.id());
        DebugLogger.branch("DimensionPoolManager", "是否有已保存的玩家数据", hasSaved, "poolId=" + targetPool.id());
        if (hasSaved) {
            PlayerStateData loadedData = getLastLoadedData(player, server, targetPool.id());
            DebugLogger.branch("DimensionPoolManager", "已加载数据是否有效", loadedData != null, "");
            if (loadedData != null) {
                String dimStr = loadedData.getDimension();
                boolean dimValid = dimStr != null && !dimStr.isEmpty()
                        && targetPool.containsDimension(dimStr);
                DebugLogger.branch("DimensionPoolManager", "已保存维度是否属于目标池", dimValid,
                        "savedDim=" + dimStr + ", targetPool=" + targetPool.id());
                if (dimValid) {  // ★ 验证维度属于此池
                    ResourceKey<Level> key = ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            Identifier.parse(dimStr));
                    boolean levelLoaded = server.getLevel(key) != null;
                    DebugLogger.branch("DimensionPoolManager", "已保存维度是否已加载", !levelLoaded,
                            "dim=" + dimStr);
                    if (levelLoaded) {
                        DebugLogger.exiting("DimensionPoolManager", "determineTeleportTarget",
                                "result=key=" + key.identifier() + " (from_saved_data)");
                        return key;
                    }
                    LOGGER.warn("保存的维度 {} 不可用，尝试池中的其他维度", dimStr);
                    DebugLogger.warn("DimensionPoolManager", "保存的维度 %s 不可用，尝试池中的其他维度", dimStr);
                } else {
                    LOGGER.debug("忽略已保存的维度 {}（不属于池 {}）", dimStr, targetPool.id());
                    DebugLogger.debug("DimensionPoolManager", "忽略已保存的维度 %s（不属于池 %s）", dimStr, targetPool.id());
                }
            }
        }

        // 逐一遍历池中的维度，返回第一个可用的
        DebugLogger.info("DimensionPoolManager", "遍历池 %s 中的维度，寻找可用目标", targetPool.id());
        for (String dimId : targetPool.dimensions()) {
            if (dimId == null || dimId.isEmpty()) continue;
            ResourceKey<Level> key = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    Identifier.parse(dimId));
            if (server.getLevel(key) != null) {
                DebugLogger.exiting("DimensionPoolManager", "determineTeleportTarget",
                        "result=key=" + key.identifier() + " (from_pool_dimensions)");
                return key;
            }
            LOGGER.warn("池 {} 中的维度 {} 不可用，尝试下一个",
                    targetPool.displayName(), dimId);
            DebugLogger.warn("DimensionPoolManager", "池 %s 中的维度 %s 不可用，尝试下一个",
                    targetPool.displayName(), dimId);
        }

        DebugLogger.exiting("DimensionPoolManager", "determineTeleportTarget",
                "result=null (no_available_dimension)");
        return null;
    }

    /** 检查指定池是否存在玩家的历史状态数据文件 */
    private static boolean hasSavedPlayerData(ServerPlayer player, MinecraftServer server, String poolId) {
        DebugLogger.entering("DimensionPoolManager", "hasSavedPlayerData",
                "player=" + player.getName().getString() + ", poolId=" + poolId);
        try {
            Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
            boolean exists = Files.exists(file);
            DebugLogger.exiting("DimensionPoolManager", "hasSavedPlayerData", "result=" + exists + ", file=" + file);
            return exists;
        } catch (Exception e) {
            DebugLogger.exception("DimensionPoolManager", "hasSavedPlayerData 检查文件是否存在", e);
            DebugLogger.exiting("DimensionPoolManager", "hasSavedPlayerData", "result=false (exception)");
            return false;
        }
    }

    /** 生成死亡默认出生点标记键 */
    private static String deathSpawnKey(UUID playerUuid, String poolId) {
        DebugLogger.entering("DimensionPoolManager", "deathSpawnKey",
                "playerUuid=" + playerUuid + ", poolId=" + poolId);
        String result = playerUuid.toString() + "|" + poolId;
        DebugLogger.exiting("DimensionPoolManager", "deathSpawnKey", "result=" + result);
        return result;
    }

    // ===== 状态保存/加载 =====

    private static void savePlayerState(ServerPlayer player, MinecraftServer server, String poolId) {
        DebugLogger.entering("DimensionPoolManager", "savePlayerState",
                "player=" + player.getName().getString() + ", poolId=" + poolId);
        try {
            PlayerStateData data = PlayerStateData.fromPlayer(player);
            Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
            Files.createDirectories(file.getParent());
            String json = GSON.toJson(data);
            Files.writeString(file, json);
            DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(),
                    "savedState", "none", "pool=" + poolId + ", file=" + file);
            LOGGER.debug("玩家 {} 的状态已保存到池 {}", player.getName().getString(), poolId);
            DebugLogger.debug("DimensionPoolManager", "玩家 %s 的状态已保存到池 %s",
                    player.getName().getString(), poolId);
            DebugLogger.exiting("DimensionPoolManager", "savePlayerState");
        } catch (Exception e) {
            LOGGER.error("保存玩家 {} 状态到池 {} 失败: {}",
                    player.getName().getString(), poolId, e.getMessage());
            DebugLogger.error("DimensionPoolManager", "保存玩家 %s 状态到池 %s 失败: %s",
                    player.getName().getString(), poolId, e.getMessage());
            DebugLogger.exception("DimensionPoolManager", "savePlayerState 保存文件", e);
            player.sendSystemMessage(Component.literal("§c⚠ 数据保存失败，请联系管理员。"));
            DebugLogger.exiting("DimensionPoolManager", "savePlayerState");
        }
    }

    private static boolean loadPlayerState(ServerPlayer player, MinecraftServer server, String poolId) {
        DebugLogger.entering("DimensionPoolManager", "loadPlayerState",
                "player=" + player.getName().getString() + ", poolId=" + poolId);
        try {
            Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
            boolean fileExists = Files.exists(file);
            DebugLogger.branch("DimensionPoolManager", "状态文件是否存在", !fileExists, "file=" + file);
            if (!fileExists) {
                DebugLogger.exiting("DimensionPoolManager", "loadPlayerState", "result=false (file_not_found)");
                return false;
            }
            String json = Files.readString(file);
            PlayerStateData data = GSON.fromJson(json, PlayerStateData.class);
            DebugLogger.branch("DimensionPoolManager", "反序列化数据是否有效", data == null, "");
            if (data == null) {
                DebugLogger.exiting("DimensionPoolManager", "loadPlayerState", "result=false (null_data)");
                return false;
            }

            data.applyToPlayer(player);
            DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(),
                    "loadedState", "none", "pool=" + poolId + ", dim=" + data.getDimension());
            LOGGER.debug("玩家 {} 的状态已从池 {} 加载", player.getName().getString(), poolId);
            DebugLogger.debug("DimensionPoolManager", "玩家 %s 的状态已从池 %s 加载",
                    player.getName().getString(), poolId);
            DebugLogger.exiting("DimensionPoolManager", "loadPlayerState", "result=true");
            return true;
        } catch (Exception e) {
            LOGGER.error("加载玩家 {} 状态从池 {} 失败: {}",
                    player.getName().getString(), poolId, e.getMessage());
            DebugLogger.error("DimensionPoolManager", "加载玩家 %s 状态从池 %s 失败: %s",
                    player.getName().getString(), poolId, e.getMessage());
            DebugLogger.exception("DimensionPoolManager", "loadPlayerState 读取/反序列化文件", e);
            DebugLogger.exiting("DimensionPoolManager", "loadPlayerState", "result=false (exception)");
            return false;
        }
    }

    private static PlayerStateData getLastLoadedData(ServerPlayer player, MinecraftServer server, String poolId) {
        DebugLogger.entering("DimensionPoolManager", "getLastLoadedData",
                "player=" + player.getName().getString() + ", poolId=" + poolId);
        try {
            Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
            boolean fileExists = Files.exists(file);
            DebugLogger.branch("DimensionPoolManager", "状态文件是否存在", !fileExists, "file=" + file);
            if (!fileExists) {
                DebugLogger.exiting("DimensionPoolManager", "getLastLoadedData", "result=null (file_not_found)");
                return null;
            }

            String json = Files.readString(file);
            PlayerStateData result = GSON.fromJson(json, PlayerStateData.class);
            DebugLogger.exiting("DimensionPoolManager", "getLastLoadedData", "result=" + result);
            return result;
        } catch (Exception e) {
            DebugLogger.exception("DimensionPoolManager", "getLastLoadedData 读取文件", e);
            DebugLogger.exiting("DimensionPoolManager", "getLastLoadedData", "result=null (exception)");
            return null;
        }
    }

    // ===== 事件处理 =====

    public static void onPlayerChangeDimension(ServerPlayer player,
                                                ServerLevel origin,
                                                ServerLevel destination) {
        DebugLogger.entering("DimensionPoolManager", "onPlayerChangeDimension",
                "player=" + player.getName().getString()
                        + ", origin=" + origin.dimension().identifier()
                        + ", destination=" + destination.dimension().identifier());

        // 守卫检查：如果该玩家的传送是由 teleportToPool() 主动触发的，
        // 则跳过重复处理（teleportToPool() 已完成了状态保存/加载）。
        boolean teleportInProgress = TELEPORT_IN_PROGRESS.contains(player.getUUID());
        DebugLogger.branch("DimensionPoolManager", "传送守卫检查（是否由 teleportToPool 主动触发）",
                teleportInProgress, "player=" + player.getName().getString());
        if (teleportInProgress) {
            LOGGER.debug("跳过玩家 {} 的维度变化事件（由 teleportToPool 主动触发）",
                    player.getName().getString());
            DebugLogger.debug("DimensionPoolManager", "跳过玩家 %s 的维度变化事件（由 teleportToPool 主动触发）",
                    player.getName().getString());
            DebugLogger.exiting("DimensionPoolManager", "onPlayerChangeDimension");
            return;
        }

        String originDim = origin.dimension().identifier().toString();
        String destDim = destination.dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(originDim, destDim);
        DebugLogger.branch("DimensionPoolManager", "源维度和目标维度是否在同一维度池", samePool,
                "originDim=" + originDim + ", destDim=" + destDim);
        if (samePool) {
            DebugLogger.exiting("DimensionPoolManager", "onPlayerChangeDimension");
            return;
        }

        DimensionPool sourcePool = DimensionPoolSettings.getPoolByDimension(originDim).orElse(null);
        DimensionPool destPool = DimensionPoolSettings.getPoolByDimension(destDim).orElse(null);

        DebugLogger.branch("DimensionPoolManager", "目标维度是否属于任何维度池", destPool == null,
                "destDim=" + destDim);
        if (destPool == null) {
            DebugLogger.exiting("DimensionPoolManager", "onPlayerChangeDimension");
            return;
        }

        MinecraftServer server = getServer(player);

        boolean crossPool = sourcePool != null && !sourcePool.id().equals(destPool.id());
        DebugLogger.branch("DimensionPoolManager", "是否为跨池维度切换", crossPool,
                "sourcePool=" + (sourcePool != null ? sourcePool.id() : "null")
                        + ", destPool=" + destPool.id());
        if (crossPool) {
            savePlayerState(player, server, sourcePool.id());
            DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(),
                    "inventory", "preserved", "cleared (cross-pool)");
            PlayerStateData.clearPlayerInventory(player);
            DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(),
                    "effects", "present", "removed (cross-pool)");
            player.removeAllEffects();
        }

        loadPlayerState(player, server, destPool.id());
        GameType targetGameMode = destPool.gameMode();
        DebugLogger.branch("DimensionPoolManager", "目标池是否配置了游戏模式", targetGameMode == null,
                "destPool=" + destPool.id());
        if (targetGameMode != null) {
            DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(),
                    "gameMode", player.gameMode().toString(), targetGameMode.toString());
            player.setGameMode(targetGameMode);
        }

        DebugLogger.exiting("DimensionPoolManager", "onPlayerChangeDimension");
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
        DebugLogger.entering("DimensionPoolManager", "onPlayerRespawn",
                "oldPlayer=" + oldPlayer.getName().getString()
                        + ", newPlayer=" + newPlayer.getName().getString()
                        + ", alive=" + alive);

        boolean teleportInProgress = TELEPORT_IN_PROGRESS.contains(newPlayer.getUUID());
        DebugLogger.branch("DimensionPoolManager", "传送守卫检查（是否在传送中）", teleportInProgress, "");
        if (teleportInProgress) {
            LOGGER.debug("跳过玩家 {} 的复活事件（teleport 进行中）", newPlayer.getName().getString());
            DebugLogger.debug("DimensionPoolManager", "跳过玩家 %s 的复活事件（teleport 进行中）",
                    newPlayer.getName().getString());
            DebugLogger.exiting("DimensionPoolManager", "onPlayerRespawn");
            return;
        }

        String oldDim = oldPlayer.level().dimension().identifier().toString();
        String newDim = newPlayer.level().dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(oldDim, newDim);
        LOGGER.info("玩家 {} 复活：{} → {} {}",
                newPlayer.getName().getString(), oldDim, newDim,
                samePool ? "（同池，不改状态）" : "（跨池，仅设游戏模式）");
        DebugLogger.info("DimensionPoolManager", "玩家 %s 复活：%s → %s %s",
                newPlayer.getName().getString(), oldDim, newDim,
                samePool ? "（同池，不改状态）" : "（跨池，仅设游戏模式）");

        // 如果跨池死亡，标记源池：下次传送回该池时使用默认出生点
        DebugLogger.branch("DimensionPoolManager", "是否为跨池死亡", !samePool,
                "oldDim=" + oldDim + ", newDim=" + newDim);
        if (!samePool) {
            DimensionPoolSettings.getPoolByDimension(oldDim).ifPresent(sourcePool -> {
                String key = deathSpawnKey(newPlayer.getUUID(), sourcePool.id());
                DEATH_PENDING_SPAWN.add(key);
                DebugLogger.stateChange("DimensionPoolManager", newPlayer.getName().getString(),
                        "DEATH_PENDING_SPAWN", "not_present", "pool=" + sourcePool.id());
                LOGGER.info("标记玩家 {} 传回池 {} 时使用默认出生点",
                        newPlayer.getName().getString(), sourcePool.id());
                DebugLogger.info("DimensionPoolManager", "标记玩家 %s 传回池 %s 时使用默认出生点",
                        newPlayer.getName().getString(), sourcePool.id());

                // 非生存世界维度池死亡时，向玩家发送提示消息
                if (!"survival_world_pool".equals(sourcePool.id())) {
                    newPlayer.sendSystemMessage(Component.translatable(
                            "youzaiworldcore.message.diminv.death_spawn_notice",
                            sourcePool.displayName()));
                    DebugLogger.info("DimensionPoolManager", "已向玩家 %s 发送死亡提示消息（源池：%s）",
                            newPlayer.getName().getString(), sourcePool.displayName());
                }
            });
        }

        // 仅设置游戏模式，不做状态保存/加载
        setPoolGameMode(newPlayer, newDim);

        DebugLogger.exiting("DimensionPoolManager", "onPlayerRespawn");
    }

    /** 根据当前维度设置玩家的游戏模式 */
    private static void setPoolGameMode(ServerPlayer player, String dimensionId) {
        DebugLogger.entering("DimensionPoolManager", "setPoolGameMode",
                "player=" + player.getName().getString() + ", dimensionId=" + dimensionId);
        DimensionPoolSettings.getPoolByDimension(dimensionId).ifPresent(pool -> {
            GameType targetGameMode = pool.gameMode();
            DebugLogger.branch("DimensionPoolManager", "池 %s 是否配置了游戏模式", targetGameMode == null, pool.id());
            if (targetGameMode != null) {
                DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(),
                        "gameMode", player.gameMode().toString(), targetGameMode.toString());
                player.setGameMode(targetGameMode);
            }
        });
        DebugLogger.exiting("DimensionPoolManager", "setPoolGameMode");
    }

    /**
     * 清理指定玩家的传送守卫标记。
     * <p>
     * 在玩家断开连接时调用，防止因断线导致 {@link #TELEPORT_IN_PROGRESS} 留下脏数据，
     * 进而影响该玩家重新加入后的维度切换处理。
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        DebugLogger.entering("DimensionPoolManager", "onPlayerDisconnect",
                "player=" + player.getName().getString());
        UUID uuid = player.getUUID();
        if (TELEPORT_IN_PROGRESS.remove(uuid)) {
            DebugLogger.stateChange("DimensionPoolManager", player.getName().getString(),
                    "TELEPORT_IN_PROGRESS", "true", "false (cleanup)");
            LOGGER.warn("清理玩家 {} 的残留传送守卫标记", player.getName().getString());
            DebugLogger.warn("DimensionPoolManager", "清理玩家 %s 的残留传送守卫标记",
                    player.getName().getString());
        }
        DebugLogger.exiting("DimensionPoolManager", "onPlayerDisconnect");
    }

    public static void onNonPlayerEntityChangeDimension(net.minecraft.world.entity.Entity originalEntity,
                                                         net.minecraft.world.entity.Entity newEntity,
                                                         ServerLevel origin,
                                                         ServerLevel destination) {
        DebugLogger.entering("DimensionPoolManager", "onNonPlayerEntityChangeDimension",
                "originalEntity=" + originalEntity
                        + ", origin=" + origin.dimension().identifier()
                        + ", destination=" + destination.dimension().identifier());

        String originDim = origin.dimension().identifier().toString();
        String destDim = destination.dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(originDim, destDim);
        DebugLogger.branch("DimensionPoolManager", "源维度和目标维度是否在同一维度池", samePool,
                "originDim=" + originDim + ", destDim=" + destDim);
        if (samePool) {
            DebugLogger.exiting("DimensionPoolManager", "onNonPlayerEntityChangeDimension");
            return;
        }

        boolean shouldDiscard = originalEntity instanceof net.minecraft.world.entity.item.ItemEntity
                || originalEntity instanceof net.minecraft.world.entity.projectile.Projectile
                || originalEntity instanceof net.minecraft.world.entity.monster.Monster;
        DebugLogger.branch("DimensionPoolManager", "是否应销毁该实体", shouldDiscard,
                "entityType=" + originalEntity.getClass().getSimpleName());
        if (shouldDiscard) {
            originalEntity.discard();
            DebugLogger.stateChange("DimensionPoolManager", originalEntity.getClass().getSimpleName(),
                    "entity", "active", "discarded (cross-pool)");
        }

        DebugLogger.exiting("DimensionPoolManager", "onNonPlayerEntityChangeDimension");
    }
}
