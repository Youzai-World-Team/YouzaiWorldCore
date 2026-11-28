package top.csituka.youzaiworldcore.dimensionalinventories;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreboardSaveData;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.mixin.pool.PlayerListScoreboardAccessor;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 使用原版计分板实现，每个维度池拥有独立的计分板及客户端订阅。 */
public final class PoolScoreboards {
    private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PoolScoreboards");
    private final Map<String, Board> boards = new HashMap<>();

    public ServerScoreboard get(ServerLevel level) {
        // 原版已有的计分板只留在主世界所属池。
        Board board = additionalBoard(level);
        return board == null ? level.getServer().getScoreboard() : board.scoreboard;
    }

    private @Nullable Board additionalBoard(ServerLevel level) {
        String pool = DimensionPoolSettings.statePoolId(level.dimension());
        return pool.equals(DimensionPoolSettings.statePoolId(Level.OVERWORLD))
                ? null
                : boards.computeIfAbsent(pool, id -> load(level.getServer(), id));
    }

    static boolean isAvailable(ServerLevel level) {
        var board = ((ServerAccess) level.getServer()).youzaiworldcore$getPoolScoreboards().additionalBoard(level);
        return board == null || board.data != null;
    }

    @SuppressWarnings("null")
    private static Board load(MinecraftServer server, String pool) {
        Path path = ModPaths.poolScoreboard(server, DimensionPoolSettings.storageDirectory(pool));
        @SuppressWarnings("null")
        var board = new ServerScoreboard(server);
        ((Access) board).youzaiworldcore$setScoreboardPool(pool);
        ScoreboardSaveData data;
        try {
            var storage = server.getDataStorage();
            @SuppressWarnings("null")
            CompoundTag root = storage.readTagFromDisk(path, DataFixTypes.SAVED_DATA_SCOREBOARD,
                    SharedConstants.getCurrentVersion().dataVersion().version());
            var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            data = new ScoreboardSaveData(ScoreboardSaveData.Packed.CODEC.parse(ops,
                    root.getCompound("data").orElseThrow()).getOrThrow());
            board.load(data.getData());
        } catch (NoSuchFileException ignored) {
            data = new ScoreboardSaveData(ScoreboardSaveData.Packed.EMPTY);
        } catch (IOException | RuntimeException error) {
            LOGGER.error("维度池 {} 的计分板无法加载，保留原文件 {}，暂时禁止进入该池", pool, path, error);
            @SuppressWarnings("null")
            var emptyBoard = new ServerScoreboard(server);
            ((Access) emptyBoard).youzaiworldcore$setScoreboardPool(pool);
            return new Board(emptyBoard, path, null);
        }
        return new Board(board, path, data);
    }

    @SuppressWarnings("null")
    public void save(MinecraftServer server) {
        for (Board board : boards.values()) {
            if (board.data == null) {
                continue;
            }
            board.scoreboard.storeToSaveDataIfDirty(board.data);
            if (board.data.isDirty()) {
                try {
                    var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                    var root = new CompoundTag();
                    root.put("data",
                            ScoreboardSaveData.Packed.CODEC.encodeStart(ops, board.data.getData()).getOrThrow());
                    NbtUtils.addCurrentDataVersion(root);
                    DimensionPoolManager.writeAtomically(board.path, file -> NbtIo.writeCompressed(root, file));
                    board.data.setDirty(false);
                } catch (IOException | RuntimeException error) {
                    LOGGER.error("维度池计分板保存失败，将在下次保存时重试：{}", board.path, error);
                }
            }
        }
    }

    @SuppressWarnings("null")
    public static void flushForBackup(MinecraftServer server) {
        ((ServerAccess) server).youzaiworldcore$getPoolScoreboards().save(server);
        var storage = server.getDataStorage();
        server.getScoreboard().storeToSaveDataIfDirty(storage.computeIfAbsent(ScoreboardSaveData.TYPE));
        storage.saveAndJoin();
    }

    public static boolean receives(ServerPlayer player, @Nullable String pool) {
        return DimensionPoolSettings.statePoolId(player.level().dimension()).equals(
                pool == null ? DimensionPoolSettings.statePoolId(Level.OVERWORLD) : pool);
    }

    @SuppressWarnings("null")
    public static void sync(ServerPlayer player, ServerLevel oldLevel, ServerLevel newLevel) {
        if (DimensionPoolManager.sharesStatePool(oldLevel, newLevel)) {
            return;
        }
        var oldBoard = oldLevel.getScoreboard();
        var newBoard = newLevel.getScoreboard();
        for (var team : oldBoard.getPlayerTeams()) {
            player.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        }
        Set<Objective> removed = new HashSet<>();
        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective objective = oldBoard.getDisplayObjective(slot);
            if (objective != null && removed.add(objective)) {
                player.connection.send(new ClientboundSetObjectivePacket(objective, 1));
            }
        }
        ((PlayerListScoreboardAccessor) newLevel.getServer().getPlayerList())
                .youzaiworldcore$sendScoreboard(newBoard, player);
    }

    private record Board(ServerScoreboard scoreboard, Path path, @Nullable ScoreboardSaveData data) {
    }

    public interface Access {
        void youzaiworldcore$setScoreboardPool(String pool);
    }

    public interface ServerAccess {
        PoolScoreboards youzaiworldcore$getPoolScoreboards();
    }
}
