package top.csituka.youzaiworldcore.dimensionalinventories;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnManager;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnPlayerAccess;
import top.csituka.youzaiworldcore.util.LoadedChunks;

/** 解析非生存池的复活位置，并在原版创建复活玩家后切换到生存池。 */
public final class PoolRespawn {
    private static final Map<ServerPlayer, TeleportTransition> LIVING_TRANSFERS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Consumer<ServerPlayer>> PENDING_RESPAWNS = new WeakHashMap<>();

    private PoolRespawn() {
    }

    public static boolean isManagedDeath(ServerPlayer player) {
        var pool = currentPool(player);
        return pool != null && !DimensionPoolSettings.SURVIVAL_POOL_ID.equals(pool.id())
                && !DimensionPoolSettings.isHiddenPool(pool.id());
    }

    public static boolean isSurvivalPool(ServerPlayer player) {
        var pool = currentPool(player);
        return pool != null && DimensionPoolSettings.SURVIVAL_POOL_ID.equals(pool.id());
    }

    public static boolean needsSurvivalReturn(ServerPlayer player) {
        return isManagedDeath(player) && poolSpawn(player) == null && !mayHaveUsablePersonalSpawn(player);
    }

    @SuppressWarnings("null")
    public static ServerLevel personalSpawnLevel(ServerPlayer player) {
        var pool = currentPool(player);
        var config = player.getRespawnConfig();
        if (pool == null || config == null
                || !pool.containsDimension(config.respawnData().dimension().identifier().toString())) {
            return null;
        }
        ServerLevel level = player.level().getServer().getLevel(config.respawnData().dimension());
        return level != null && level.isInWorldBounds(config.respawnData().pos()) ? level : null;
    }

    /** 死亡时只预查已加载的个人重生点，缺失区块留给实际重生流程处理。 */
    @SuppressWarnings("null")
    private static boolean mayHaveUsablePersonalSpawn(ServerPlayer player) {
        var level = personalSpawnLevel(player);
        var config = player.getRespawnConfig();
        if (level == null || config == null) return false;
        BlockPos pos = config.respawnData().pos();
        // 床会搜索距床头最多两格的落脚点，方块碰撞和环境属性还会读取其附近。
        if (!LoadedChunks.covers(level, pos, pos, 4)) return true;

        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof RespawnAnchorBlock
                && (config.forced() || state.getValue(RespawnAnchorBlock.CHARGE) > 0)
                && RespawnAnchorBlock.canSetSpawn(level, pos)) {
            return RespawnAnchorBlock.findStandUpPosition(EntityTypes.PLAYER, level, pos).isPresent();
        }
        if (state.getBlock() instanceof AbstractBedBlock bed && bed.getBedRule(level, pos).canSetSpawn(level)) {
            return AbstractBedBlock.findStandUpPosition(EntityTypes.PLAYER, level, pos,
                    state.getValue(AbstractBedBlock.FACING), config.respawnData().yaw()).isPresent();
        }
        if (!config.forced() || !state.getBlock().isPossibleToRespawnInThis(state)) return false;
        var above = level.getBlockState(pos.above());
        return above.getBlock().isPossibleToRespawnInThis(above);
    }

    @SuppressWarnings("null")
    public static TeleportTransition poolSpawn(ServerPlayer player) {
        return configuredSpawn(player, currentPool(player));
    }

    @SuppressWarnings("null")
    static TeleportTransition configuredSpawn(ServerPlayer player, DimensionPool pool) {
        var spawn = pool == null ? null : pool.defaultSpawn();
        if (spawn == null || spawn.getDimension() == null || !pool.containsDimension(spawn.getDimension())
                || !Double.isFinite(spawn.getX()) || !Double.isFinite(spawn.getY()) || !Double.isFinite(spawn.getZ())
                || !Float.isFinite(spawn.getYaw()) || !Float.isFinite(spawn.getPitch())) {
            return null;
        }
        var level = DimensionPoolManager.getLoadedLevel(player.level().getServer(), spawn.getDimension());
        if (level == null || !level.isInWorldBounds(BlockPos.containing(spawn.getX(), spawn.getY(), spawn.getZ()))) {
            return null;
        }
        return new TeleportTransition(level, new Vec3(spawn.getX(), spawn.getY(), spawn.getZ()), Vec3.ZERO,
                spawn.getYaw(), spawn.getPitch(), TeleportTransition.DO_NOTHING);
    }

    /** 原版先在源池创建存活玩家，成功恢复生存档前始终保留源池物品。 */
    @SuppressWarnings("null")
    public static TeleportTransition sourceSpawn(ServerPlayer player) {
        ServerLevel level = player.level();
        var spawn = level.getRespawnData();
        var position = player.adjustSpawnLocation(level, spawn.pos());
        return new TeleportTransition(level, Vec3.atBottomCenterOf(position), Vec3.ZERO,
                spawn.yaw(), spawn.pitch(), TeleportTransition.DO_NOTHING);
    }

    @SuppressWarnings("null")
    private static void finishReturn(ServerPlayer player, GameType sourceMode) {
        // 极限模式可能在复活后将玩家设为旁观者，源池存档应保留死亡前的模式。
        if (player.gameMode() != sourceMode) {
            player.setGameMode(sourceMode);
        }
        if (DimensionPoolManager.finishSurvivalReturn(player)) {
            DimensionPoolManager.forgetSurvivalReturn(player);
        } else {
            DimensionPoolManager.queueSurvivalReturn(player);
            if (!player.hasDisconnected()) {
                player.sendSystemMessage(Component.translatable("screen.youzaiworldcore.world_menu.failed"));
            }
        }
    }

    public static void reserveSurvivalReturn(ServerPlayer player) {
        DimensionPoolManager.queueSurvivalReturn(player);
    }

    /** 先把光标和合成格物品交还玩家，背包快照才能覆盖这些物品。 */
    public static void settleInventoryMenu(ServerPlayer player) {
        if (player.containerMenu != player.inventoryMenu
                || !player.inventoryMenu.getCarried().isEmpty() || !player.inventoryMenu.getCraftSlots().isEmpty()) {
            player.closeContainer();
        }
    }

    /** 原版的存活替换玩家也必须经过同一个跨池事务。 */
    public static TeleportTransition stageLivingTransfer(ServerPlayer player, TeleportTransition transition) {
        if (DimensionPoolManager.sharesStatePool(player.level(), transition.newLevel())) {
            return transition;
        }
        settleInventoryMenu(player);
        LIVING_TRANSFERS.put(player, transition);
        return new TeleportTransition(player.level(), player.position(), Vec3.ZERO,
                player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING);
    }

    public static boolean staysInPool(ServerPlayer player, TeleportTransition transition) {
        return DimensionPoolManager.sharesStatePool(player.level(), transition.newLevel());
    }

    /** 此时装备已复制，连接仍可能指向旧玩家。 */
    @SuppressWarnings("null")
    public static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive) {
        TeleportTransition continuation = LIVING_TRANSFERS.remove(oldPlayer);
        if (alive && continuation != null) {
            PENDING_RESPAWNS.put(newPlayer, player -> player.teleport(continuation));
            return;
        }
        if (!alive && oldPlayer instanceof InPlaceRespawnPlayerAccess access) {
            if (access.youzaiworldcore$isReturningToSurvival()) {
                GameType sourceMode = oldPlayer.gameMode();
                PENDING_RESPAWNS.put(newPlayer, player -> finishReturn(player, sourceMode));
                return;
            }
            if (access.youzaiworldcore$isInPlaceRespawnSelected()) {
                PENDING_RESPAWNS.put(newPlayer, player -> InPlaceRespawnManager.finishRespawn(access, player));
                return;
            }
        }
        var pool = currentPool(newPlayer);
        if (pool != null && newPlayer.gameMode() != pool.gameMode()) {
            newPlayer.setGameMode(pool.gameMode());
        }
    }

    public static void finishPendingRespawn(ServerPlayer player) {
        if (!isConnectionReady(player)) {
            return;
        }
        var finish = PENDING_RESPAWNS.remove(player);
        if (finish != null) {
            finish.accept(player);
        }
    }

    static void tickPendingRespawns(MinecraftServer server) {
        if (!PENDING_RESPAWNS.isEmpty()) {
            List.copyOf(server.getPlayerList().getPlayers()).forEach(PoolRespawn::finishPendingRespawn);
        }
    }

    static boolean isConnectionReady(ServerPlayer player) {
        return player.connection != null && player.connection.player == player && !player.hasDisconnected();
    }

    private static DimensionPool currentPool(ServerPlayer player) {
        return DimensionPoolSettings.getPoolByDimension(player.level().dimension().identifier().toString())
                .orElse(null);
    }
}
