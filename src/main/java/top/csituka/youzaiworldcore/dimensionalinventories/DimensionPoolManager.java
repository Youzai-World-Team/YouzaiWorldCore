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
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

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

    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("DimensionPoolManager");

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

    /**
     * 在维度池状态切换守卫下执行内部传送或复活操作。
     * 用于原地重生，避免“出生点维度 → 死亡维度”的内部传送触发跨池背包切换。
     */
    public static <T> T withTeleportGuard(UUID playerUuid, Supplier<T> action) {
        boolean added = TELEPORT_IN_PROGRESS.add(playerUuid);
        try {
            return action.get();
        } finally {
            if (added) {
                TELEPORT_IN_PROGRESS.remove(playerUuid);
            }
        }
    }

    // ===== 路径辅助 =====

    /**
     * 将含有非法文件名字符（如 {@code :}）的池 ID 转换为安全的目录名。
     * <p>
     * Minecraft 资源标识符含冒号 {@code :}，但 Windows 文件系统不允许冒号出现在路径中。
     * 转换为 {@code _} 以兼容各操作系统。
     */
    private static String sanitizePoolId(String poolId) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "sanitizePoolId", "poolId=" + poolId);
        String result = poolId.replace(':', '_');
        DebugLogger.SERVER.exiting("DimensionPoolManager", "sanitizePoolId", "result=" + result);
        return result;
    }

    /** 玩家状态数据根目录：{@code <world_name>/data/yzwc/data/dimensional_inventories_module/} */
    private static Path getDataRoot(MinecraftServer server) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "getDataRoot", "server=" + server);
        Path result = top.csituka.youzaiworldcore.config.ModPaths.worldData(
                server, top.csituka.youzaiworldcore.config.GlobalSettings.DIMENSIONAL_INVENTORIES_MODULE);
        DebugLogger.SERVER.exiting("DimensionPoolManager", "getDataRoot", "result=" + result);
        return result;
    }

    /** 获取指定池中指定玩家的状态文件路径 */
    private static Path getPlayerDataFile(MinecraftServer server, String poolId, String playerUuid) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "getPlayerDataFile",
                "server=" + server + ", poolId=" + poolId + ", playerUuid=" + playerUuid);
        Path result = getDataRoot(server).resolve(sanitizePoolId(poolId)).resolve(playerUuid + ".json");
        DebugLogger.SERVER.exiting("DimensionPoolManager", "getPlayerDataFile", "result=" + result);
        return result;
    }

    private static MinecraftServer getServer(ServerPlayer player) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "getServer", "player=" + player.getName().getString());
        MinecraftServer result = player.level().getServer();
        DebugLogger.SERVER.exiting("DimensionPoolManager", "getServer", "result=" + result);
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
     *   <li>将玩家传送到目标池保存的维度与坐标</li>
     *   <li>传送成功后清空背包并加载目标池的玩家历史状态</li>
     *   <li>强制设置目标池的游戏模式</li>
     * </ol>
     */
    public static boolean teleportToPool(ServerPlayer player, String targetPoolId) {
        return teleportToPool(player, targetPoolId, null);
    }

    /** 菜单可同时接收原有聊天反馈，不改变其他调用者的传送行为。 */
    public static boolean teleportToPool(ServerPlayer player, String targetPoolId,
                                         java.util.function.Consumer<Component> feedback) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "teleportToPool",
                "player=" + player.getName().getString() + ", targetPoolId=" + targetPoolId);
        MinecraftServer server = getServer(player);

        // 1. 查找目标池（隐藏池视为不存在，防止恶意客户端伪造隐藏池 ID 传送）
        DimensionPool targetPool = DimensionPoolSettings.getPool(targetPoolId);
        boolean hiddenPool = DimensionPoolSettings.isHiddenPool(targetPoolId);
        DebugLogger.SERVER.branch("DimensionPoolManager", "目标池是否存在",
                targetPool == null || hiddenPool, "poolId=" + targetPoolId + ", hidden=" + hiddenPool);
        if (targetPool == null || hiddenPool) {
            sendTeleportMessage(player, feedback, Component.translatable(
                    "youzaiworldcore.message.diminv.pool_not_found", targetPoolId));
            DebugLogger.SERVER.exiting("DimensionPoolManager", "teleportToPool",
                    "result=false (pool_not_found" + (hiddenPool ? " / hidden_pool" : "") + ")");
            return false;
        }

        DebugLogger.SERVER.branch("DimensionPoolManager", "目标池是否为空", targetPool.isEmpty(), "pool=" + targetPool.displayName());
        if (targetPool.isEmpty()) {
            sendTeleportMessage(player, feedback, Component.translatable(
                    "youzaiworldcore.message.diminv.pool_empty", targetPool.displayName()));
            DebugLogger.SERVER.exiting("DimensionPoolManager", "teleportToPool", "result=false (pool_empty)");
            return false;
        }

        // 3. 确定源池 & 已在目标池中则跳过
        String currentDimensionId = player.level().dimension().identifier().toString();
        DimensionPool sourcePool = DimensionPoolSettings.getPoolByDimension(currentDimensionId).orElse(null);

        boolean alreadyInTarget = sourcePool != null && sourcePool.id().equals(targetPoolId);
        DebugLogger.SERVER.branch("DimensionPoolManager", "玩家是否已在目标池中", alreadyInTarget,
                "currentDim=" + currentDimensionId + ", sourcePool=" + (sourcePool != null ? sourcePool.id() : "null"));
        if (alreadyInTarget) {
            sendTeleportMessage(player, feedback, Component.translatable(
                    "youzaiworldcore.message.diminv.already_in_pool", targetPool.displayName()));
            DebugLogger.SERVER.exiting("DimensionPoolManager", "teleportToPool", "result=true (already_in_pool)");
            return true;
        }

        ServerLevel fallback = findLoadedLevel(server, targetPool);
        if (fallback == null) {
            sendTeleportMessage(player, feedback, Component.translatable(
                    "youzaiworldcore.message.diminv.no_valid_dimension", targetPool.displayName()));
            DebugLogger.SERVER.exiting("DimensionPoolManager", "teleportToPool", "result=false (no_valid_dimension)");
            return false;
        }

        if (TELEPORT_IN_PROGRESS.contains(player.getUUID())) {
            sendTeleportMessage(player, feedback, transferFailure(targetPool));
            return false;
        }

        PlayerStateData targetState;
        PoolTransferSnapshot origin;
        try {
            targetState = readPlayerState(player, server, targetPool.id());
            origin = new PoolTransferSnapshot(player);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("准备玩家 {} 的维度池切换失败", player.getScoreboardName(), error);
            sendTeleportMessage(player, feedback, transferFailure(targetPool));
            return false;
        }

        String deathKey = deathSpawnKey(player.getUUID(), targetPool.id());
        boolean hasDeathPending = DEATH_PENDING_SPAWN.contains(deathKey);
        TeleportTarget target = determineTeleportTarget(server, targetPool, targetState, fallback, hasDeathPending);

        // 源池文件必须保存在原位置，保存失败时保留玩家的当前状态。
        if (sourcePool != null && !savePlayerState(player, server, sourcePool.id())) {
            sendTeleportMessage(player, feedback, transferFailure(targetPool));
            return false;
        }

        Component failure = withTeleportGuard(player.getUUID(),
                () -> transferPlayer(player, targetPool, targetState, origin, target));
        if (failure != null) {
            sendTeleportMessage(player, feedback, failure);
            DebugLogger.SERVER.exiting("DimensionPoolManager", "teleportToPool", "result=false (transfer_failed)");
            return false;
        }

        if (hasDeathPending) {
            DEATH_PENDING_SPAWN.remove(deathKey);
        }
        sendTeleportMessage(player, feedback, Component.translatable(
                "youzaiworldcore.message.diminv.teleport_success", targetPool.displayName()));
        DebugLogger.SERVER.info("DimensionPoolManager", "玩家 {} 已传送到维度池 {}",
                player.getName().getString(), targetPoolId);
        DebugLogger.SERVER.exiting("DimensionPoolManager", "teleportToPool", "result=true (success)");
        return true;
    }

    private static Component transferPlayer(ServerPlayer player, DimensionPool targetPool, PlayerStateData targetState,
                                            PoolTransferSnapshot origin, TeleportTarget target) {
        try {
            if (player.teleportTo(target.level(), target.x(), target.y(), target.z(),
                    Set.of(), target.yaw(), target.pitch(), true) && target.hasArrived(player)) {
                PlayerStateData.clearPlayerInventory(player);
                player.removeAllEffects();
                if (targetState != null) {
                    targetState.applyToPlayer(player, true);
                }
                GameType mode = targetPool.gameMode();
                boolean modeApplied = player.gameMode() == mode || player.setGameMode(mode);
                if (modeApplied && player.gameMode() == mode && target.hasArrived(player)) {
                    return null;
                }
            }
        } catch (RuntimeException error) {
            LOGGER.error("玩家 {} 切换至维度池 {} 时失败", player.getScoreboardName(), targetPool.id(), error);
        }

        if (origin.restore(player)) {
            return transferFailure(targetPool);
        }
        LOGGER.error("玩家 {} 切换至维度池 {} 失败，且未能完整恢复源状态",
                player.getScoreboardName(), targetPool.id());
        return Component.translatable("youzaiworldcore.message.diminv.rollback_failed");
    }

    private static Component transferFailure(DimensionPool pool) {
        return Component.translatable("youzaiworldcore.message.diminv.teleport_failed", pool.displayName());
    }

    private static void sendTeleportMessage(ServerPlayer player, java.util.function.Consumer<Component> feedback,
                                            Component message) {
        if (feedback != null) {
            feedback.accept(message);
        }
        if (!player.hasDisconnected()) {
            try {
                player.sendSystemMessage(message);
            } catch (RuntimeException error) {
                LOGGER.error("向玩家 {} 发送维度池切换结果失败", player.getScoreboardName(), error);
            }
        }
    }

    private record TeleportTarget(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        boolean hasArrived(ServerPlayer player) {
            var position = player.position();
            return player.level() == level && position.x == x && position.y == y && position.z == z;
        }
    }

    /** 优先使用已加载的死亡出生点或历史维度，未加载时使用池内的第一个可用维度。 */
    private static TeleportTarget determineTeleportTarget(MinecraftServer server, DimensionPool pool,
                                                          PlayerStateData state, ServerLevel fallback,
                                                          boolean useDeathSpawn) {
        var spawn = useDeathSpawn ? pool.defaultSpawn() : null;
        if (spawn != null && spawn.getDimension() != null && pool.containsDimension(spawn.getDimension())) {
            ServerLevel level = getLoadedLevel(server, spawn.getDimension());
            if (level != null) {
                return new TeleportTarget(level, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());
            }
        }

        if (state != null && state.getDimension() != null && pool.containsDimension(state.getDimension())) {
            ServerLevel level = getLoadedLevel(server, state.getDimension());
            if (level != null) {
                return new TeleportTarget(level, state.getX(), state.getY(), state.getZ(), state.getYRot(), state.getXRot());
            }
        }

        return new TeleportTarget(fallback, 0.5, 100, 0.5, 90, 0);
    }

    /** 查找池中第一个已加载的维度，供菜单目录和传送共用。 */
    static ServerLevel findLoadedLevel(MinecraftServer server, DimensionPool pool) {
        for (String dimensionId : pool.dimensions()) {
            ServerLevel level = getLoadedLevel(server, dimensionId);
            if (level != null) {
                return level;
            }
        }
        return null;
    }

    private static ServerLevel getLoadedLevel(MinecraftServer server, String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(dimensionId);
        return id == null ? null : server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    private static String deathSpawnKey(UUID playerUuid, String poolId) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "deathSpawnKey",
                "playerUuid=" + playerUuid + ", poolId=" + poolId);
        String result = playerUuid.toString() + "|" + poolId;
        DebugLogger.SERVER.exiting("DimensionPoolManager", "deathSpawnKey", "result=" + result);
        return result;
    }

    // ===== 状态保存/加载 =====

    private static boolean savePlayerState(ServerPlayer player, MinecraftServer server, String poolId) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "savePlayerState",
                "player=" + player.getName().getString() + ", poolId=" + poolId);
        try {
            PlayerStateData data = PlayerStateData.fromPlayer(player);
            Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".player-state-", ".tmp");
            try {
                Files.writeString(temporary, GSON.toJson(data));
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            DebugLogger.SERVER.stateChange("DimensionPoolManager", player.getName().getString(),
                    "savedState", "none", "pool=" + poolId + ", file=" + file);
            LOGGER.debug("玩家 {} 的状态已保存到池 {}", player.getName().getString(), poolId);
            DebugLogger.SERVER.debug("DimensionPoolManager", "玩家 {} 的状态已保存到池 {}", player.getName().getString(), poolId);
            DebugLogger.SERVER.exiting("DimensionPoolManager", "savePlayerState");
            return true;
        } catch (Exception e) {
            LOGGER.error("保存玩家 {} 状态到池 {} 失败: {}",
                    player.getName().getString(), poolId, e.getMessage());
            DebugLogger.SERVER.error("DimensionPoolManager", "保存玩家 {} 状态到池 {} 失败: {}", player.getName().getString(), poolId, e.getMessage());
            DebugLogger.SERVER.exception("DimensionPoolManager", "savePlayerState 保存文件", e);
            player.sendSystemMessage(Component.literal("§c⚠ 数据保存失败，请联系管理员。"));
            DebugLogger.SERVER.exiting("DimensionPoolManager", "savePlayerState");
            return false;
        }
    }

    private static boolean loadPlayerState(ServerPlayer player, MinecraftServer server, String poolId) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "loadPlayerState",
                "player=" + player.getName().getString() + ", poolId=" + poolId);
        try {
            PlayerStateData data = readPlayerState(player, server, poolId);
            if (data == null) {
                DebugLogger.SERVER.exiting("DimensionPoolManager", "loadPlayerState", "result=false (file_not_found)");
                return false;
            }

            data.applyToPlayer(player);
            DebugLogger.SERVER.stateChange("DimensionPoolManager", player.getName().getString(),
                    "loadedState", "none", "pool=" + poolId + ", dim=" + data.getDimension());
            LOGGER.debug("玩家 {} 的状态已从池 {} 加载", player.getName().getString(), poolId);
            DebugLogger.SERVER.debug("DimensionPoolManager", "玩家 {} 的状态已从池 {} 加载", player.getName().getString(), poolId);
            DebugLogger.SERVER.exiting("DimensionPoolManager", "loadPlayerState", "result=true");
            return true;
        } catch (Exception e) {
            LOGGER.error("加载玩家 {} 状态从池 {} 失败: {}",
                    player.getName().getString(), poolId, e.getMessage());
            DebugLogger.SERVER.error("DimensionPoolManager", "加载玩家 {} 状态从池 {} 失败: {}", player.getName().getString(), poolId, e.getMessage());
            DebugLogger.SERVER.exception("DimensionPoolManager", "loadPlayerState 读取/反序列化文件", e);
            DebugLogger.SERVER.exiting("DimensionPoolManager", "loadPlayerState", "result=false (exception)");
            return false;
        }
    }

    /** 缺少存档代表首次进入；读取失败或无效内容由调用方处理。 */
    private static PlayerStateData readPlayerState(ServerPlayer player, MinecraftServer server, String poolId)
            throws IOException {
        Path file = getPlayerDataFile(server, poolId, player.getUUID().toString());
        String json;
        try {
            json = Files.readString(file);
        } catch (NoSuchFileException ignored) {
            return null;
        }
        PlayerStateData data = GSON.fromJson(json, PlayerStateData.class);
        if (data == null) {
            throw new IOException("无效的维度池玩家存档：" + file);
        }
        return data;
    }

    // ===== 事件处理 =====

    public static void onPlayerChangeDimension(ServerPlayer player,
                                                ServerLevel origin,
                                                ServerLevel destination) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "onPlayerChangeDimension",
                "player=" + player.getName().getString()
                        + ", origin=" + origin.dimension().identifier()
                        + ", destination=" + destination.dimension().identifier());

        // 守卫检查：如果该玩家的传送是由 teleportToPool() 主动触发的，
        // 则由 teleportToPool() 在移动成功后统一切换玩家状态。
        boolean teleportInProgress = TELEPORT_IN_PROGRESS.contains(player.getUUID());
        DebugLogger.SERVER.branch("DimensionPoolManager", "传送守卫检查（是否由 teleportToPool 主动触发）",
                teleportInProgress, "player=" + player.getName().getString());
        if (teleportInProgress) {
            LOGGER.debug("跳过玩家 {} 的维度变化事件（由 teleportToPool 主动触发）",
                    player.getName().getString());
            DebugLogger.SERVER.debug("DimensionPoolManager", "跳过玩家 {} 的维度变化事件（由 teleportToPool 主动触发）", player.getName().getString());
            DebugLogger.SERVER.exiting("DimensionPoolManager", "onPlayerChangeDimension");
            return;
        }

        String originDim = origin.dimension().identifier().toString();
        String destDim = destination.dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(originDim, destDim);
        DebugLogger.SERVER.branch("DimensionPoolManager", "源维度和目标维度是否在同一维度池", samePool,
                "originDim=" + originDim + ", destDim=" + destDim);
        if (samePool) {
            DebugLogger.SERVER.exiting("DimensionPoolManager", "onPlayerChangeDimension");
            return;
        }

        DimensionPool sourcePool = DimensionPoolSettings.getPoolByDimension(originDim).orElse(null);
        DimensionPool destPool = DimensionPoolSettings.getPoolByDimension(destDim).orElse(null);

        DebugLogger.SERVER.branch("DimensionPoolManager", "目标维度是否属于任何维度池", destPool == null,
                "destDim=" + destDim);
        if (destPool == null) {
            DebugLogger.SERVER.exiting("DimensionPoolManager", "onPlayerChangeDimension");
            return;
        }

        MinecraftServer server = getServer(player);

        boolean crossPool = sourcePool != null && !sourcePool.id().equals(destPool.id());
        DebugLogger.SERVER.branch("DimensionPoolManager", "是否为跨池维度切换", crossPool,
                "sourcePool=" + (sourcePool != null ? sourcePool.id() : "null")
                        + ", destPool=" + destPool.id());
        if (crossPool) {
            savePlayerState(player, server, sourcePool.id());
            DebugLogger.SERVER.stateChange("DimensionPoolManager", player.getName().getString(),
                    "inventory", "preserved", "cleared (cross-pool)");
            PlayerStateData.clearPlayerInventory(player);
            DebugLogger.SERVER.stateChange("DimensionPoolManager", player.getName().getString(),
                    "effects", "present", "removed (cross-pool)");
            player.removeAllEffects();
        }

        loadPlayerState(player, server, destPool.id());
        GameType targetGameMode = destPool.gameMode();
        DebugLogger.SERVER.branch("DimensionPoolManager", "目标池是否配置了游戏模式", targetGameMode == null,
                "destPool=" + destPool.id());
        if (targetGameMode != null) {
            DebugLogger.SERVER.stateChange("DimensionPoolManager", player.getName().getString(),
                    "gameMode", player.gameMode().toString(), targetGameMode.toString());
            player.setGameMode(targetGameMode);
        }

        DebugLogger.SERVER.exiting("DimensionPoolManager", "onPlayerChangeDimension");
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
        DebugLogger.SERVER.entering("DimensionPoolManager", "onPlayerRespawn",
                "oldPlayer=" + oldPlayer.getName().getString()
                        + ", newPlayer=" + newPlayer.getName().getString()
                        + ", alive=" + alive);

        if (oldPlayer instanceof top.csituka.youzaiworldcore.respawn.InPlaceRespawnPlayerAccess access
                && access.youzaiworldcore$isInPlaceRespawnSelected()) {
            DebugLogger.SERVER.debug("DimensionPoolManager", "跳过玩家 {} 的维度池复活处理（原地重生）", newPlayer.getName().getString());
            DebugLogger.SERVER.exiting("DimensionPoolManager", "onPlayerRespawn", "in_place_respawn");
            return;
        }

        boolean teleportInProgress = TELEPORT_IN_PROGRESS.contains(newPlayer.getUUID());
        DebugLogger.SERVER.branch("DimensionPoolManager", "传送守卫检查（是否在传送中）", teleportInProgress, "");
        if (teleportInProgress) {
            LOGGER.debug("跳过玩家 {} 的复活事件（teleport 进行中）", newPlayer.getName().getString());
            DebugLogger.SERVER.debug("DimensionPoolManager", "跳过玩家 {} 的复活事件（teleport 进行中）", newPlayer.getName().getString());
            DebugLogger.SERVER.exiting("DimensionPoolManager", "onPlayerRespawn");
            return;
        }

        String oldDim = oldPlayer.level().dimension().identifier().toString();
        String newDim = newPlayer.level().dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(oldDim, newDim);

        DebugLogger.SERVER.info("DimensionPoolManager", "玩家 {} 复活：{} → {} {}", newPlayer.getName().getString(), oldDim, newDim, samePool ? "（同池，不改状态）" : "（跨池，仅设游戏模式）");

        // 如果跨池死亡，标记源池：下次传送回该池时使用默认出生点
        DebugLogger.SERVER.branch("DimensionPoolManager", "是否为跨池死亡", !samePool,
                "oldDim=" + oldDim + ", newDim=" + newDim);
        if (!samePool) {
            DimensionPoolSettings.getPoolByDimension(oldDim).ifPresent(sourcePool -> {
                String key = deathSpawnKey(newPlayer.getUUID(), sourcePool.id());
                DEATH_PENDING_SPAWN.add(key);
                DebugLogger.SERVER.stateChange("DimensionPoolManager", newPlayer.getName().getString(),
                        "DEATH_PENDING_SPAWN", "not_present", "pool=" + sourcePool.id());

                DebugLogger.SERVER.debug("DimensionPoolManager", "标记玩家 {} 传回池 {} 时使用默认出生点", newPlayer.getName().getString(), sourcePool.id());

                // 非生存世界维度池死亡时，向玩家发送提示消息
                if (!"survival_world_pool".equals(sourcePool.id())) {
                    newPlayer.sendSystemMessage(Component.translatable(
                            "youzaiworldcore.message.diminv.death_spawn_notice",
                            sourcePool.displayName()));
                    DebugLogger.SERVER.debug("DimensionPoolManager", "已向玩家 {} 发送死亡提示消息（源池：{}）", newPlayer.getName().getString(), sourcePool.displayName());
                }
            });
        }

        // 仅设置游戏模式，不做状态保存/加载
        setPoolGameMode(newPlayer, newDim);

        DebugLogger.SERVER.exiting("DimensionPoolManager", "onPlayerRespawn");
    }

    /** 根据当前维度设置玩家的游戏模式 */
    private static void setPoolGameMode(ServerPlayer player, String dimensionId) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "setPoolGameMode",
                "player=" + player.getName().getString() + ", dimensionId=" + dimensionId);
        DimensionPoolSettings.getPoolByDimension(dimensionId).ifPresent(pool -> {
            GameType targetGameMode = pool.gameMode();
            DebugLogger.SERVER.branch("DimensionPoolManager", "池 %s 是否配置了游戏模式", targetGameMode == null, pool.id());
            if (targetGameMode != null) {
                DebugLogger.SERVER.stateChange("DimensionPoolManager", player.getName().getString(),
                        "gameMode", player.gameMode().toString(), targetGameMode.toString());
                player.setGameMode(targetGameMode);
            }
        });
        DebugLogger.SERVER.exiting("DimensionPoolManager", "setPoolGameMode");
    }

    /**
     * 清理指定玩家的传送守卫标记。
     * <p>
     * 在玩家断开连接时调用，防止因断线导致 {@link #TELEPORT_IN_PROGRESS} 留下脏数据，
     * 进而影响该玩家重新加入后的维度切换处理。
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "onPlayerDisconnect",
                "player=" + player.getName().getString());
        UUID uuid = player.getUUID();
        if (TELEPORT_IN_PROGRESS.remove(uuid)) {
            DebugLogger.SERVER.stateChange("DimensionPoolManager", player.getName().getString(),
                    "TELEPORT_IN_PROGRESS", "true", "false (cleanup)");

            DebugLogger.SERVER.debug("DimensionPoolManager", "清理玩家 {} 的残留传送守卫标记", player.getName().getString());
        }
        DebugLogger.SERVER.exiting("DimensionPoolManager", "onPlayerDisconnect");
    }

    public static void onNonPlayerEntityChangeDimension(net.minecraft.world.entity.Entity originalEntity,
                                                         net.minecraft.world.entity.Entity newEntity,
                                                         ServerLevel origin,
                                                         ServerLevel destination) {
        DebugLogger.SERVER.entering("DimensionPoolManager", "onNonPlayerEntityChangeDimension",
                "originalEntity=" + originalEntity
                        + ", origin=" + origin.dimension().identifier()
                        + ", destination=" + destination.dimension().identifier());

        String originDim = origin.dimension().identifier().toString();
        String destDim = destination.dimension().identifier().toString();

        boolean samePool = DimensionPoolSettings.dimensionsInSamePool(originDim, destDim);
        DebugLogger.SERVER.branch("DimensionPoolManager", "源维度和目标维度是否在同一维度池", samePool,
                "originDim=" + originDim + ", destDim=" + destDim);
        if (samePool) {
            DebugLogger.SERVER.exiting("DimensionPoolManager", "onNonPlayerEntityChangeDimension");
            return;
        }

        boolean shouldDiscard = originalEntity instanceof net.minecraft.world.entity.item.ItemEntity
                || originalEntity instanceof net.minecraft.world.entity.projectile.Projectile
                || originalEntity instanceof net.minecraft.world.entity.monster.Monster;
        DebugLogger.SERVER.branch("DimensionPoolManager", "是否应销毁该实体", shouldDiscard,
                "entityType=" + originalEntity.getClass().getSimpleName());
        if (shouldDiscard) {
            originalEntity.discard();
            DebugLogger.SERVER.stateChange("DimensionPoolManager", originalEntity.getClass().getSimpleName(),
                    "entity", "active", "discarded (cross-pool)");
        }

        DebugLogger.SERVER.exiting("DimensionPoolManager", "onNonPlayerEntityChangeDimension");
    }
}
