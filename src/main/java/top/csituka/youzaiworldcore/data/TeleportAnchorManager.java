package top.csituka.youzaiworldcore.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload.EntryType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

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

    /**
     * 运行时传送冷却记录（玩家UUID → 上次传送的游戏时间），不持久化。
     */
    private final transient Map<UUID, Long> teleportCooldowns = new HashMap<>();

    /**
     * 运行时记录：玩家最近一次传送列表是「用传送石或传送卷轴打开」的，包括入口类型与打开时握持物品的那只手。
     * 通过传送锚点方块打开列表时会清除该标记（{@link #markListOpenedByAnchor}）。
     * 传送执行时据此判定要扣哪一种资源——
     * <ul>
     *   <li>{@link EntryType#STONE}：扣传送石耐久 + 60 秒物品冷却</li>
     *   <li>{@link EntryType#SCROLL}：扣 1 张卷轴 + 120 秒物品冷却</li>
     * </ul>
     * 不持久化。
     */
    private final transient Map<UUID, TeleportOpenerSource> openedLists = new HashMap<>();

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
     * 为玩家添加一个传送锚点，使用指定的自定义名称和维度池标识。
     *
     * @param poolId 当前维度所属的维度池 ID，null 表示未加入任何池
     * @return true 表示添加成功
     */
    public boolean addPointWithName(ServerPlayer player, BlockPos pos, ResourceKey<Level> dimension,
                                     String name, @Nullable String poolId) {
        UUID uuid = player.getUUID();
        List<TeleportAnchorData> points = playerPoints.computeIfAbsent(uuid, k -> new ArrayList<>());
        points.add(new TeleportAnchorData(pos.immutable(), dimension, name, poolId));
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
     * 获取玩家当前「可用」的传送锚点列表，供打开传送 GUI 时使用。
     * <p>
     * 在 {@link #getPointsForPlayer(ServerPlayer)} 的基础上过滤掉两类条目：
     * <ul>
     *   <li>目标坐标处的方块已不是传送锚点，或锚点已不再处于激活状态（失效锚点）</li>
     *   <li>与 {@code fromDimension} 不属于同一维度池（任意一方未加入池时不做限制）</li>
     * </ul>
     * 传送锚点方块右键与传送石右键共用此方法，保证两条入口的可用列表完全一致。
     *
     * @param fromDimension 打开列表时玩家所处的维度，用于维度池隔离判定
     * @return 过滤后的不可变列表
     */
    public List<TeleportAnchorData> getValidPointsForPlayer(ServerPlayer player, ResourceKey<Level> fromDimension) {
        MinecraftServer server = player.level().getServer();

        // 当前维度所属的维度池 ID；未加入任何池时为 null（不做隔离）
        String currentPoolId = top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings
                .getPoolByDimension(fromDimension.identifier().toString())
                .map(p -> p.id())
                .orElse(null);

        return getPointsForPlayer(player).stream()
                .filter(p -> {
                    ServerLevel targetLevel = server.getLevel(p.dimension());
                    if (targetLevel == null) return false;
                    BlockState anchorState = targetLevel.getBlockState(p.pos());
                    if (!(anchorState.getBlock() instanceof TeleportAnchorBlock)) return false;
                    if (!anchorState.getValue(TeleportAnchorBlock.ACTIVE)) return false;
                    // 维度池隔离过滤：同池或至少一方未加入任何池时可通过
                    if (p.poolId() == null || currentPoolId == null) return true;
                    return p.poolId().equals(currentPoolId);
                })
                .toList();
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
                points.set(i, new TeleportAnchorData(p.pos(), p.dimension(), newName, p.poolId()));
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

    /**
     * 移动某个玩家的一个传送锚点到新索引位置（用于持久化排序）。
     * 将 {@code fromIndex} 处的元素移到 {@code toIndex}，其余元素保持相对顺序。
     *
     * @return true 如果成功；false 如果索引非法或列表不存在
     */
    public boolean movePoint(ServerPlayer player, int fromIndex, int toIndex) {
        List<TeleportAnchorData> points = playerPoints.get(player.getUUID());
        if (points == null || fromIndex < 0 || fromIndex >= points.size()
                || toIndex < 0 || toIndex >= points.size()) {
            return false;
        }
        TeleportAnchorData moved = points.remove(fromIndex);
        points.add(toIndex, moved);
        setDirty();
        return true;
    }

    // ===== 传送列表打开来源（用于判定扣哪种资源） =====

    /**
     * 列表入口类型的轻量快照：入口类型 + 玩家当时握持该物品的手。
     * 仅在 {@link #openedLists} 中保存，持久化时不被序列化。
     */
    public record TeleportOpenerSource(EntryType type, InteractionHand hand) {
    }

    /**
     * 记录玩家通过传送锚点方块打开了传送列表，清除传送石/卷轴标记。
     * 走方块入口的传送不消耗任何物品。
     */
    public void markListOpenedByAnchor(ServerPlayer player) {
        openedLists.remove(player.getUUID());
    }

    /**
     * 记录玩家用传送石打开了传送列表。
     *
     * @param hand 玩家使用传送石的那只手
     */
    public void markListOpenedByStone(ServerPlayer player, InteractionHand hand) {
        openedLists.put(player.getUUID(), new TeleportOpenerSource(EntryType.STONE, hand));
    }

    /**
     * 记录玩家用传送卷轴打开了传送列表。
     * <p>
     * 与 {@link #markListOpenedByStone} 同源逻辑，仅入口类型不同——传送处理器据此走
     * 「扣 1 张卷轴 + 120 秒冷却」的结算路径，而不是「扣耐久 + 60 秒冷却」。
     *
     * @param hand 玩家使用传送卷轴的那只手
     */
    public void markListOpenedByScroll(ServerPlayer player, InteractionHand hand) {
        openedLists.put(player.getUUID(), new TeleportOpenerSource(EntryType.SCROLL, hand));
    }

    /**
     * 取出并清除玩家的「传送物品打开」标记。
     * <p>
     * 一次传送请求只对应一次标记：无论本次传送最终成功还是被各项校验拒绝，
     * 标记都已消费——玩家需要重新用传送物品打开列表才会再次进入扣资源流程。
     *
     * @return 入口快照（含入口类型与使用物品的手），{@code null} 表示本次列表不是
     *         通过传送物品打开的（即 {@link EntryType#ANCHOR} 入口，无资源消耗）
     */
    @Nullable
    public TeleportOpenerSource consumeTeleportSourceMark(ServerPlayer player) {
        return openedLists.remove(player.getUUID());
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
