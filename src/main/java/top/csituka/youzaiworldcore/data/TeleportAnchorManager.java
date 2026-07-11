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
 */
@SuppressWarnings("null")
public class TeleportAnchorManager extends SavedData {

    private final Map<UUID, List<TeleportAnchorData>> playerPoints = new HashMap<>();

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
     * 为玩家添加一个传送锚点。
     *
     * @return 自动生成的显示名称
     */
    public String addPoint(ServerPlayer player, BlockPos pos, ResourceKey<Level> dimension) {
        UUID uuid = player.getUUID();
        List<TeleportAnchorData> points = playerPoints.computeIfAbsent(uuid, k -> new ArrayList<>());

        int index = points.size() + 1;
        String name = "传送点 #" + index;

        points.add(new TeleportAnchorData(pos.immutable(), dimension, name));
        setDirty();
        return name;
    }

    /**
     * 为玩家添加一个传送锚点，使用指定的自定义名称。
     */
    public void addPointWithName(ServerPlayer player, BlockPos pos, ResourceKey<Level> dimension, String name) {
        UUID uuid = player.getUUID();
        List<TeleportAnchorData> points = playerPoints.computeIfAbsent(uuid, k -> new ArrayList<>());
        points.add(new TeleportAnchorData(pos.immutable(), dimension, name));
        setDirty();
    }

    /**
     * 获取某个玩家的所有传送锚点。
     */
    public List<TeleportAnchorData> getPointsForPlayer(ServerPlayer player) {
        return playerPoints.getOrDefault(player.getUUID(), List.of());
    }

    /**
     * 移除某个玩家的一个传送锚点，并更新对应方块的 BlockEntity 激活者列表。
     * 如果 BlockEntity 中不再有任何激活者，方块回到非激活状态。
     */
    public void removePoint(ServerPlayer player, int index) {
        List<TeleportAnchorData> points = playerPoints.get(player.getUUID());
        if (points != null && index >= 0 && index < points.size()) {
            TeleportAnchorData removed = points.remove(index);
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
        }
    }

    /**
     * 重命名某个玩家的一个传送锚点。
     */
    public void renamePoint(ServerPlayer player, int index, String newName) {
        List<TeleportAnchorData> points = playerPoints.get(player.getUUID());
        if (points != null && index >= 0 && index < points.size()) {
            TeleportAnchorData old = points.get(index);
            points.set(index, new TeleportAnchorData(old.pos(), old.dimension(), newName));
            setDirty();
        }
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
}
