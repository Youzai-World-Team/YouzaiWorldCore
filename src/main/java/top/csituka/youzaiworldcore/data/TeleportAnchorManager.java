package top.csituka.youzaiworldcore.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理所有玩家的传送锚点数据。
 * <p>
 * 使用 Minecraft 的 {@link SavedData} 系统持久化，自动保存到世界存档。
 * 每个玩家关联一个传送锚点列表。
 * <p>
 * 传送冷却时间为运行时状态，不参与持久化。
 */
@SuppressWarnings("null")
public class TeleportAnchorManager extends SavedData {

    /** 传送冷却时间（tick），3 秒 = 60 tick。 */
    public static final long TELEPORT_COOLDOWN_TICKS = 60L;

    private final Map<UUID, List<TeleportAnchorData>> playerPoints = new HashMap<>();

    /** 运行时传送冷却记录（玩家UUID → 上次传送的游戏时间），不持久化。 */
    private final transient Map<UUID, Long> teleportCooldowns = new HashMap<>();

    private static final Codec<TeleportAnchorManager> CODEC = Codec.unboundedMap(
            Codec.STRING,               // UUID → String
            TeleportAnchorData.CODEC.listOf()  // 值：传送点列表
    ).xmap(
            map -> {
                TeleportAnchorManager manager = new TeleportAnchorManager();
                map.forEach((uuidStr, points) ->
                        manager.playerPoints.put(UUID.fromString(uuidStr), new ArrayList<>(points)));
                return manager;
            },
            manager -> {
                Map<String, List<TeleportAnchorData>> map = new HashMap<>();
                manager.playerPoints.forEach((uuid, points) ->
                        map.put(uuid.toString(), points));
                return map;
            }
    );

    public static final SavedDataType<TeleportAnchorManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "teleport_anchors"),
            TeleportAnchorManager::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private TeleportAnchorManager() {
    }

    /**
     * 从服务端获取传送锚点管理器（自动加载/创建持久化数据）。
     */
    public static TeleportAnchorManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * 为玩家添加一个传送锚点，使用指定的自定义名称。
     *
     * @return true 如果添加成功；false 如果已达上限
     */
    public boolean addPointWithName(ServerPlayer player, BlockPos pos, ResourceKey<Level> dimension, String name) {
        UUID uuid = player.getUUID();
        List<TeleportAnchorData> points = playerPoints.computeIfAbsent(uuid, k -> new ArrayList<>());
        points.add(new TeleportAnchorData(pos.immutable(), dimension, name));
        setDirty();
        return true;
    }

    /**
     * 获取某个玩家的所有传送锚点。
     */
    public List<TeleportAnchorData> getPointsForPlayer(ServerPlayer player) {
        return playerPoints.getOrDefault(player.getUUID(), List.of());
    }

    /**
     * 获取某个玩家当前已激活的传送锚点数量。
     */
    public int getPointCount(ServerPlayer player) {
        List<TeleportAnchorData> points = playerPoints.get(player.getUUID());
        return points != null ? points.size() : 0;
    }

    /**
     * 按坐标查找玩家的传送锚点数据。
     *
     * @return 匹配的数据；未找到返回 null
     */
    public TeleportAnchorData findPoint(ServerPlayer player, BlockPos pos, ResourceKey<Level> dimension) {
        List<TeleportAnchorData> points = playerPoints.get(player.getUUID());
        if (points == null) return null;
        for (TeleportAnchorData p : points) {
            if (p.pos().equals(pos) && p.dimension().equals(dimension)) {
                return p;
            }
        }
        return null;
    }

    /**
     * 按坐标移除某个玩家的一个传送锚点，并更新对应方块的 BlockEntity 激活者列表。
     * 如果 BlockEntity 中不再有任何激活者，方块回到非激活状态。
     *
     * @return true 如果成功移除；false 如果未找到匹配项
     */
    public boolean removePointByPos(ServerPlayer player, BlockPos pos, ResourceKey<Level> dimension) {
        List<TeleportAnchorData> points = playerPoints.get(player.getUUID());
        if (points == null) return false;

        int idx = -1;
        for (int i = 0; i < points.size(); i++) {
            TeleportAnchorData p = points.get(i);
            if (p.pos().equals(pos) && p.dimension().equals(dimension)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;

        TeleportAnchorData removed = points.remove(idx);
        setDirty();

        // 更新对应方块 BlockEntity 的激活者集合
        ServerLevel targetLevel = player.level().getServer().getLevel(removed.dimension());
        if (targetLevel != null) {
            BlockEntity be = targetLevel.getBlockEntity(removed.pos());
            if (be instanceof TeleportAnchorBlockEntity anchorBE) {
                boolean nowEmpty = anchorBE.removeActivator(player.getUUID());
                if (nowEmpty) {
                    BlockState newState = targetLevel.getBlockState(removed.pos())
                            .setValue(TeleportAnchorBlock.ACTIVE, false);
                    targetLevel.setBlock(removed.pos(), newState, 3);
                    targetLevel.sendBlockUpdated(removed.pos(),
                            targetLevel.getBlockState(removed.pos()), newState, 3);
                }
            }
        }
        return true;
    }

    /**
     * 按坐标重命名某个玩家的一个传送锚点。
     *
     * @return true 如果成功重命名；false 如果未找到匹配项
     */
    public boolean renamePointByPos(ServerPlayer player, BlockPos pos, ResourceKey<Level> dimension, String newName) {
        List<TeleportAnchorData> points = playerPoints.get(player.getUUID());
        if (points == null) return false;

        for (int i = 0; i < points.size(); i++) {
            TeleportAnchorData p = points.get(i);
            if (p.pos().equals(pos) && p.dimension().equals(dimension)) {
                points.set(i, new TeleportAnchorData(p.pos(), p.dimension(), newName));
                setDirty();
                return true;
            }
        }
        return false;
    }

    /**
     * 移除所有玩家列表中指向指定坐标和维度的传送锚点（方块被破坏时调用）。
     */
    public void removeAnchorAt(BlockPos pos, ResourceKey<Level> dimension) {
        boolean changed = false;
        for (List<TeleportAnchorData> points : playerPoints.values()) {
            if (points.removeIf(p -> p.pos().equals(pos) && p.dimension().equals(dimension))) {
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
    }

    // ===== 传送冷却 =====

    /**
     * 检查玩家是否可以传送（冷却是否已过）。
     *
     * @param currentGameTime 当前游戏时间（{@code level.getGameTime()}）
     * @return true 如果冷却已过或从未传送过
     */
    public boolean canTeleport(ServerPlayer player, long currentGameTime) {
        Long lastTime = teleportCooldowns.get(player.getUUID());
        if (lastTime == null) return true;
        return currentGameTime - lastTime >= TELEPORT_COOLDOWN_TICKS;
    }

    /**
     * 获取玩家剩余冷却秒数（向上取整）。0 表示可以传送。
     */
    public int getRemainingCooldownSeconds(ServerPlayer player, long currentGameTime) {
        Long lastTime = teleportCooldowns.get(player.getUUID());
        if (lastTime == null) return 0;
        long remaining = TELEPORT_COOLDOWN_TICKS - (currentGameTime - lastTime);
        if (remaining <= 0) return 0;
        return (int) Math.ceil(remaining / 20.0);
    }

    /**
     * 记录玩家本次传送的时间，用于冷却计算。
     */
    public void recordTeleport(ServerPlayer player, long currentGameTime) {
        teleportCooldowns.put(player.getUUID(), currentGameTime);
    }
}
