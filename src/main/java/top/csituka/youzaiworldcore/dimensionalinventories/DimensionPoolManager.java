package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.config.ModPaths;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 跳过由主动切换和回滚发起的原版传送。 */
    private static final Set<UUID> TELEPORT_IN_PROGRESS = new HashSet<>();

    static final String RECOVERY_DIRECTORY = "_recovery";
    private static final Map<MinecraftServer, Map<UUID, PlayerStateData>> RECOVERIES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MinecraftServer, Set<UUID>> SURVIVAL_RETURNS = new WeakHashMap<>();

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
        String result = DimensionPoolSettings.storageDirectory(poolId);
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
        if (isRecoveryPending(player) || hasSurvivalReturn(player)) {
            sendTeleportMessage(player, feedback, Component.translatable("screen.youzaiworldcore.world_menu.failed"));
            return false;
        }
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

        PlayerStateData targetState;
        try {
            targetState = readPlayerState(player, server, targetPool.id());
        } catch (IOException | RuntimeException error) {
            LOGGER.error("读取玩家 {} 在维度池 {} 的存档失败", player.getScoreboardName(), targetPool.id(), error);
            sendTeleportMessage(player, feedback, transferFailure(targetPool));
            return false;
        }

        TeleportTarget target = determineTeleportTarget(player, targetPool, targetState, fallback);
        Component failure = transferPlayer(player, poolId(sourcePool), target.level(), targetState, targetPool.gameMode(),
                () -> target.move(player), () -> target.hasArrived(player));
        if (failure != null) {
            sendTeleportMessage(player, feedback, failure);
            return false;
        }

        sendTeleportMessage(player, feedback, Component.translatable(
                "youzaiworldcore.message.diminv.teleport_success", targetPool.displayName()));
        return true;
    }

    /** 在原版修改位置之前完成源池存档，传送门和其他跨维度传送共用此事务。 */
    public static ServerPlayer onTeleport(ServerPlayer player, TeleportTransition transition,
                                          Supplier<ServerPlayer> vanilla) {
        return onTeleport(player, transition, vanilla, null);
    }

    /** 传送费用属于源池，跨池失败时由原有回滚快照恢复。 */
    public static ServerPlayer onTeleport(ServerPlayer player, TeleportTransition transition,
                                          Supplier<ServerPlayer> vanilla, @Nullable Runnable chargeSource) {
        if (TELEPORT_IN_PROGRESS.contains(player.getUUID()) || player.level() == transition.newLevel()) {
            ServerPlayer moved = vanilla.get();
            if (moved == player && chargeSource != null) chargeSource.run();
            return moved;
        }
        MinecraftServer server = getServer(player);
        String sourceDimension = player.level().dimension().identifier().toString();
        String targetDimension = transition.newLevel().dimension().identifier().toString();
        var sourcePool = DimensionPoolSettings.getPoolByDimension(sourceDimension).orElse(null);
        var targetPool = DimensionPoolSettings.getPoolByDimension(targetDimension).orElse(null);
        String originId = poolId(sourcePool);
        String targetId = poolId(targetPool);

        if (isRecoveryPending(player) || hasSurvivalReturn(player)) {
            // 登录大厅仍需允许完成认证，恢复快照在认证后重新应用。
            return chargeSource == null
                    && (DimensionPoolSettings.isHiddenPool(originId) || DimensionPoolSettings.isHiddenPool(targetId))
                    ? vanilla.get() : null;
        }
        if (originId.equals(targetId)) {
            ServerPlayer moved = vanilla.get();
            if (moved == player && chargeSource != null) chargeSource.run();
            return moved;
        }
        try {
            PlayerStateData targetState = readPlayerState(player, server, targetId);
            GameType mode = targetPool == null ? server.getDefaultGameType() : targetPool.gameMode();
            if (targetPool == null && targetState != null) {
                mode = targetState.savedGameMode(mode);
            }
            Component failure = transferPlayer(player, originId, transition.newLevel(), targetState, mode,
                    () -> vanilla.get() == player, () -> player.level() == transition.newLevel(), chargeSource);
            if (failure == null) {
                return player;
            }
            sendTeleportMessage(player, null, failure);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("玩家 {} 的跨维度切换失败", player.getScoreboardName(), error);
            sendTeleportMessage(player, null, Component.translatable("screen.youzaiworldcore.world_menu.failed"));
        }
        return null;
    }

    private static String poolId(DimensionPool pool) {
        return pool == null ? DimensionPoolSettings.UNASSIGNED_POOL_ID : pool.id();
    }

    static boolean sharesStatePool(ServerLevel origin, ServerLevel destination) {
        if (origin == destination) {
            return true;
        }
        return DimensionPoolSettings.getPoolByDimension(origin.dimension().identifier().toString())
                .equals(DimensionPoolSettings.getPoolByDimension(destination.dimension().identifier().toString()));
    }

    private static Component transferPlayer(ServerPlayer player, String originId, ServerLevel targetLevel,
                                            PlayerStateData targetState,
                                            GameType mode, BooleanSupplier move, BooleanSupplier arrived) {
        return transferPlayer(player, originId, targetLevel, targetState, mode, move, arrived, null);
    }

    private static Component transferPlayer(ServerPlayer player, String originId, ServerLevel targetLevel,
                                            PlayerStateData targetState, GameType mode, BooleanSupplier move,
                                            BooleanSupplier arrived, @Nullable Runnable chargeSource) {
        if (!player.isAlive() || TELEPORT_IN_PROGRESS.contains(player.getUUID()) || isRecoveryPending(player)
                || !PoolScoreboards.isAvailable(targetLevel)) {
            return Component.translatable("screen.youzaiworldcore.world_menu.failed");
        }

        MinecraftServer server = getServer(player);
        PoolTransferSnapshot origin;
        PlayerStateData backup;
        List<Mob> departingPets;
        var petOrigin = player.level();
        var petOriginPosition = player.position();
        try {
            PoolRespawn.settleInventoryMenu(player);
            departingPets = PoolPets.departingPets(player, originId);
            origin = new PoolTransferSnapshot(player);
            backup = PlayerStateData.fromPlayer(player);
            // 在修改池存档或移动玩家之前，先留下供下次启动恢复的源状态。
            writePlayerState(recoveryFile(player), backup);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("保存玩家 {} 的源池 {} 状态失败", player.getScoreboardName(), originId, error);
            return Component.translatable("screen.youzaiworldcore.world_menu.failed");
        }

        return withTeleportGuard(player.getUUID(), () -> {
            boolean switched = false;
            try {
                if (chargeSource != null) chargeSource.run();
                writeVanillaPlayerState(player);
                writePlayerState(getPlayerDataFile(server, originId, player.getUUID().toString()),
                        chargeSource == null ? backup : PlayerStateData.fromPlayer(player));
                // 原版传送包会引用最近死亡点，发送前应用目标池的字段。
                PoolPlayerState.apply(player, targetState == null ? null : targetState.poolPlayerState(),
                        targetLevel.dimension());
                if (move.getAsBoolean() && arrived.getAsBoolean()) {
                    applyPoolState(player, targetState, mode);
                    if (arrived.getAsBoolean()) {
                        finishTransaction(player);
                        switched = true;
                    }
                }
            } catch (IOException | RuntimeException error) {
                LOGGER.error("玩家 {} 的维度池切换失败", player.getScoreboardName(), error);
            }
            if (switched) {
                try {
                    PoolPets.afterTransfer(player, originId, departingPets, petOrigin, petOriginPosition);
                } catch (RuntimeException error) {
                    LOGGER.error("玩家 {} 已切换维度池，宠物处理失败", player.getScoreboardName(), error);
                }
                return null;
            }
            try {
                if (origin.restore(player)) {
                    if (chargeSource != null) {
                        writePlayerState(getPlayerDataFile(server, originId, player.getUUID().toString()), backup);
                    }
                    finishTransaction(player);
                    return Component.translatable("screen.youzaiworldcore.world_menu.failed");
                }
            } catch (IOException | RuntimeException error) {
                LOGGER.error("完成玩家 {} 的回滚存档失败", player.getScoreboardName(), error);
            }
            recordRecovery(player, backup);
            return Component.translatable("youzaiworldcore.message.diminv.rollback_failed");
        });
    }

    private static void applyPoolState(ServerPlayer player, PlayerStateData state, GameType mode) {
        PlayerStateData.clearPlayerInventory(player);
        (state == null ? PlayerStateData.initialState(player) : state).applyToPlayer(player, true);
        if ((player.gameMode() != mode && !player.setGameMode(mode)) || player.gameMode() != mode) {
            throw new IllegalStateException("无法应用维度池游戏模式 " + mode);
        }
        player.resetSentInfo();
        player.containerMenu.broadcastChanges();
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
        boolean move(ServerPlayer player) {
            return player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
        }

        boolean hasArrived(ServerPlayer player) {
            var position = player.position();
            return player.level() == level && position.x == x && position.y == y && position.z == z;
        }
    }

    private static TeleportTarget determineTeleportTarget(ServerPlayer player, DimensionPool pool,
                                                          PlayerStateData state, ServerLevel fallback) {
        TeleportTarget target = savedTarget(fallback.getServer(), pool, state);
        if (target != null) {
            return target;
        }
        TeleportTarget configured = configuredSpawnTarget(player, pool);
        return configured != null ? configured : new TeleportTarget(fallback, 0.5, 100, 0.5, 90, 0);
    }

    private static TeleportTarget savedTarget(MinecraftServer server, DimensionPool pool, PlayerStateData state) {
        if (state == null || state.getDimension() == null || !state.hasValidPosition()
                || (pool != null && !pool.containsDimension(state.getDimension()))) {
            return null;
        }
        ServerLevel level = getLoadedLevel(server, state.getDimension());
        return level == null ? null
                : new TeleportTarget(level, state.getX(), state.getY(), state.getZ(), state.getYRot(), state.getXRot());
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

    static ServerLevel getLoadedLevel(MinecraftServer server, String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(dimensionId);
        return id == null ? null : server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, id));
    }

    private static void writePlayerState(Path file, PlayerStateData state) throws IOException {
        writeAtomically(file, path -> Files.writeString(path, GSON.toJson(state)));
    }

    @FunctionalInterface
    interface StateWriter {
        void write(Path path) throws IOException;
    }

    static void writeAtomically(Path file, StateWriter writer) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".player-state-", ".tmp");
        try {
            writer.write(temporary);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
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
        JsonObject document = GSON.fromJson(json, JsonObject.class);
        PlayerStateData data = document == null ? null : GSON.fromJson(document, PlayerStateData.class);
        if (data == null || !data.hasCompleteSavedState(document)) {
            throw new IOException("无效的维度池玩家存档：" + file);
        }
        return data;
    }

    /** 死亡返回使用生存池原位置和原模式；已有存档无法读取或无法到达时保留当前物品。 */
    static boolean returnToSurvival(ServerPlayer player) {
        MinecraftServer server = getServer(player);
        DimensionPool pool = DimensionPoolSettings.getPool(DimensionPoolSettings.SURVIVAL_POOL_ID);
        if (pool == null) {
            return false;
        }
        try {
            String sourceDimension = player.level().dimension().identifier().toString();
            var sourcePool = DimensionPoolSettings.getPoolByDimension(sourceDimension).orElse(null);
            if (pool.id().equals(poolId(sourcePool))) {
                return true;
            }
            PlayerStateData state = readPlayerState(player, server, pool.id());
            TeleportTarget target = savedTarget(server, pool, state);
            if (state == null || state.getDimension() == null || !state.hasValidPosition()
                    || !pool.containsDimension(state.getDimension())) {
                target = survivalSpawn(player, server, pool);
            }
            if (target == null) {
                return false;
            }
            GameType mode = state == null ? pool.gameMode() : state.savedGameMode(pool.gameMode());
            TeleportTarget destination = target;
            return transferPlayer(player, poolId(sourcePool), destination.level(), state, mode,
                    () -> destination.move(player), () -> destination.hasArrived(player)) == null;
        } catch (IOException | RuntimeException error) {
            LOGGER.error("玩家 {} 死亡后无法恢复生存池存档", player.getScoreboardName(), error);
            return false;
        }
    }

    private static TeleportTarget survivalSpawn(ServerPlayer player, MinecraftServer server, DimensionPool pool) {
        var configured = configuredSpawnTarget(player, pool);
        if (configured != null) {
            return configured;
        }
        ServerLevel first = findLoadedLevel(server, pool);
        if (first == null) {
            return null;
        }
        ServerLevel overworld = server.overworld();
        ServerLevel level = pool.containsDimension(overworld.dimension().identifier().toString()) ? overworld : first;
        var spawn = level.getRespawnData();
        Vec3 position = Vec3.atBottomCenterOf(player.adjustSpawnLocation(level, spawn.pos()));
        return new TeleportTarget(level, position.x, position.y, position.z, spawn.yaw(), spawn.pitch());
    }

    private static TeleportTarget configuredSpawnTarget(ServerPlayer player, DimensionPool pool) {
        var configured = PoolRespawn.configuredSpawn(player, pool);
        if (configured == null) {
            return null;
        }
        var position = configured.position();
        return new TeleportTarget(configured.newLevel(), position.x, position.y, position.z,
                configured.yRot(), configured.xRot());
    }

    /** 生存池暂时不可用时，保留复活玩家的源池物品并在可用后重试。 */
    static void queueSurvivalReturn(ServerPlayer player) {
        var server = getServer(player);
        SURVIVAL_RETURNS.computeIfAbsent(server, key -> new HashSet<>()).add(player.getUUID());
        try {
            Path marker = survivalReturnMarker(player);
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "");
        } catch (IOException error) {
            LOGGER.error("保存玩家 {} 的待返回生存池标记失败", player.getScoreboardName(), error);
        }
    }

    static boolean finishSurvivalReturn(ServerPlayer player) {
        if (!returnToSurvival(player)) {
            return false;
        }
        try {
            Files.deleteIfExists(survivalReturnMarker(player));
            return true;
        } catch (IOException error) {
            LOGGER.error("清除玩家 {} 的待返回生存池标记失败", player.getScoreboardName(), error);
            return false;
        }
    }

    static void forgetSurvivalReturn(ServerPlayer player) {
        var pending = SURVIVAL_RETURNS.get(getServer(player));
        if (pending != null) {
            pending.remove(player.getUUID());
        }
    }

    private static Path survivalReturnMarker(ServerPlayer player) {
        return getDataRoot(getServer(player)).resolve(RECOVERY_DIRECTORY).resolve(player.getUUID() + ".return");
    }

    public static boolean isRecoveryPending(ServerPlayer player) {
        return RECOVERIES.getOrDefault(player.level().getServer(), Map.of()).containsKey(player.getUUID());
    }

    private static boolean hasSurvivalReturn(ServerPlayer player) {
        return SURVIVAL_RETURNS.getOrDefault(getServer(player), Set.of()).contains(player.getUUID());
    }

    private static Path recoveryFile(ServerPlayer player) {
        return getPlayerDataFile(getServer(player), RECOVERY_DIRECTORY, player.getUUID().toString());
    }

    private static void recordRecovery(ServerPlayer player, PlayerStateData state) {
        RECOVERIES.computeIfAbsent(getServer(player), key -> new ConcurrentHashMap<>()).put(player.getUUID(), state);
        LOGGER.error("玩家 {} 尚未返回原维度，重新连接后重试恢复", player.getScoreboardName());
        player.connection.disconnect(Component.translatable("youzaiworldcore.message.diminv.rollback_failed"));
    }

    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = getServer(player);
        PoolPets.discardStaleShoulderPets(player);
        if (Files.exists(survivalReturnMarker(player))) {
            SURVIVAL_RETURNS.computeIfAbsent(server, key -> new HashSet<>()).add(player.getUUID());
        }
        try {
            PlayerStateData data = readPlayerState(player, server, RECOVERY_DIRECTORY);
            if (data != null) {
                if (PoolRespawn.isConnectionReady(player) && canRecover(player) && recoverPlayer(player, data)) {
                    var pending = RECOVERIES.get(server);
                    if (pending != null) {
                        pending.remove(player.getUUID());
                    }
                } else {
                    RECOVERIES.computeIfAbsent(server, key -> new ConcurrentHashMap<>()).put(player.getUUID(), data);
                }
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.error("读取玩家 {} 的回滚档案失败", player.getScoreboardName(), error);
            player.connection.disconnect(Component.translatable("youzaiworldcore.message.diminv.rollback_failed"));
            return;
        }
        if (!hasSurvivalReturn(player)) {
            PoolPets.restore(player);
        }
    }

    /** 服务器线程处理重连后的回滚和暂时无法完成的跨池复活。 */
    public static void tickRecovery(MinecraftServer server) {
        PoolRespawn.tickPendingRespawns(server);
        var recoveries = RECOVERIES.get(server);
        if (recoveries != null && !recoveries.isEmpty()) {
            List<ServerPlayer> recovered = new ArrayList<>();
            recoveries.entrySet().removeIf(entry -> {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null || !PoolRespawn.isConnectionReady(player) || !canRecover(player)
                        || !recoverPlayer(player, entry.getValue())) {
                    return false;
                }
                recovered.add(player);
                return true;
            });
            recovered.stream().filter(player -> !hasSurvivalReturn(player)).forEach(PoolPets::restore);
        }
        var returns = SURVIVAL_RETURNS.get(server);
        if (returns != null && server.getTickCount() % 20 == 0) {
            returns.removeIf(uuid -> {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                return player != null && PoolRespawn.isConnectionReady(player) && player.isAlive() && canRecover(player)
                        && !isRecoveryPending(player) && finishSurvivalReturn(player);
            });
        }
        if (server.getTickCount() % 20 == 0) {
            PoolPets.tickPendingRestores(server);
            PoolPets.tickPendingOriginReturns(server);
        }
    }

    private static boolean canRecover(ServerPlayer player) {
        return AuthPlayerHelper.isAuthenticated(player) || AuthPlayerHelper.canSkipAuth(player);
    }

    private static void finishTransaction(ServerPlayer player) throws IOException {
        writeVanillaPlayerState(player);
        Files.deleteIfExists(recoveryFile(player));
    }

    private static void writeVanillaPlayerState(ServerPlayer player) throws IOException {
        // 原版 PlayerDataStorage.save 捕获写入异常，事务必须先确认玩家档案已成功替换。
        var problems = new ProblemReporter.Collector(player.problemPath());
        var output = TagValueOutput.createWithContext(problems, player.registryAccess());
        player.saveWithoutId(output);
        if (!problems.isEmpty()) {
            throw new IOException(problems.getReport());
        }
        Path file = ModPaths.vanillaPlayerData(getServer(player), player.getUUID());
        var data = output.buildResult();
        writeAtomically(file, path -> NbtIo.writeCompressed(data, path));
        PoolPets.afterPlayerWrite(getServer(player), data);
    }

    private static boolean recoverPlayer(ServerPlayer player, PlayerStateData state) {
        var target = savedTarget(getServer(player), null, state);
        try {
            if (target != null && withTeleportGuard(player.getUUID(), () -> {
                if (state.poolPlayerState() != null) {
                    PoolPlayerState.apply(player, state.poolPlayerState(), target.level().dimension());
                }
                if (!target.hasArrived(player) && !target.move(player)) {
                    return false;
                }
                if (!target.hasArrived(player)) {
                    return false;
                }
                applyPoolState(player, state, state.savedGameMode(player.gameMode()));
                return target.hasArrived(player);
            })) {
                // 在解除恢复限制前写入原版玩家档案，重启后不会再次还原已使用的物品。
                finishTransaction(player);
                return true;
            }
        } catch (IOException | RuntimeException error) {
            LOGGER.error("玩家 {} 重连时恢复原状态失败", player.getScoreboardName(), error);
        }
        player.connection.disconnect(Component.translatable("youzaiworldcore.message.diminv.rollback_failed"));
        return false;
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

        boolean samePool = sharesStatePool(origin, destination);
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
            newEntity.discard();
            DebugLogger.SERVER.stateChange("DimensionPoolManager", originalEntity.getClass().getSimpleName(),
                    "entity", "active", "discarded (cross-pool)");
        }

        DebugLogger.SERVER.exiting("DimensionPoolManager", "onNonPlayerEntityChangeDimension");
    }
}
