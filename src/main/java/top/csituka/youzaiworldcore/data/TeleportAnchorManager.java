package top.csituka.youzaiworldcore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import net.minecraft.world.phys.Vec3;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolSettings;
import top.csituka.youzaiworldcore.network.TeleportAnchorListPayload.EntryType;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.LoadedChunks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private record Deactivation(GlobalPos anchor, UUID player) {
        private static final Codec<Deactivation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                GlobalPos.CODEC.fieldOf("anchor").forGetter(Deactivation::anchor),
                UUIDUtil.CODEC.fieldOf("player").forGetter(Deactivation::player)
        ).apply(instance, Deactivation::new));
    }

    private final Map<GlobalPos, Set<UUID>> pendingDeactivations = new HashMap<>();
    private static final Map<TeleportAnchorBlockEntity, ServerLevel> LOADING_ANCHORS = new HashMap<>();
    private static final Codec<Map<String, List<TeleportAnchorData>>> POINTS_CODEC =
            Codec.unboundedMap(Codec.STRING, TeleportAnchorData.CODEC.listOf());
    private static final Codec<TeleportAnchorManager> CODEC = Codec.withAlternative(
            RecordCodecBuilder.create(instance -> instance.group(
                    POINTS_CODEC.fieldOf("points").forGetter(TeleportAnchorManager::encodePoints),
                    Deactivation.CODEC.listOf().optionalFieldOf("deactivations", List.of())
                            .forGetter(TeleportAnchorManager::encodeDeactivations)
            ).apply(instance, TeleportAnchorManager::new)),
            POINTS_CODEC, points -> new TeleportAnchorManager(points, List.of()));

    public static final SavedDataType<TeleportAnchorManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "teleport_anchors"),
            TeleportAnchorManager::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    private TeleportAnchorManager() {
    }

    private TeleportAnchorManager(Map<String, List<TeleportAnchorData>> points, List<Deactivation> removals) {
        points.forEach((uuid, anchors) -> playerPoints.put(UUID.fromString(uuid), new ArrayList<>(anchors)));
        removals.forEach(removal -> queueDeactivation(removal.anchor(), removal.player()));
    }

    private Map<String, List<TeleportAnchorData>> encodePoints() {
        Map<String, List<TeleportAnchorData>> points = new HashMap<>();
        playerPoints.forEach((uuid, anchors) -> points.put(uuid.toString(), anchors));
        return points;
    }

    private List<Deactivation> encodeDeactivations() {
        List<Deactivation> removals = new ArrayList<>();
        pendingDeactivations.forEach((anchor, players) -> players.forEach(
                player -> removals.add(new Deactivation(anchor, player))));
        return removals;
    }

    public static void register() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof TeleportAnchorBlockEntity anchor) {
                LOADING_ANCHORS.put(anchor, level);
            }
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, level) -> LOADING_ANCHORS.remove(entity));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADING_ANCHORS.clear());
        // 区块加载回调可能早于方块实体读完 NBT，等到区块可用后再更新。
        ServerTickEvents.END_SERVER_TICK.register(server -> LOADING_ANCHORS.entrySet().removeIf(entry -> {
            var anchor = entry.getKey();
            var level = entry.getValue();
            if (anchor.isRemoved()) return true;
            var chunk = LoadedChunks.get(level, anchor.getBlockPos());
            if (chunk == null) return false;
            get(server).applyDeactivations(level, chunk, anchor.getBlockPos());
            return true;
        }));
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

        return getPointsForPlayer(player).stream()
                .filter(point -> allowsDimension(fromDimension, point.dimension()))
                .filter(point -> {
                    ServerLevel level = server.getLevel(point.dimension());
                    if (level == null || !level.isInWorldBounds(point.pos())) return false;
                    var chunk = LoadedChunks.get(level, point.pos());
                    return chunk == null || isActiveAnchor(chunk.getBlockState(point.pos()));
                })
                .toList();
    }

    public static boolean allowsDimension(ResourceKey<Level> from, ResourceKey<Level> to) {
        if (from.equals(to)) return true;
        var sourcePool = DimensionPoolSettings.getPoolByDimension(from.identifier().toString());
        var targetPool = DimensionPoolSettings.getPoolByDimension(to.identifier().toString());
        return sourcePool.isEmpty() || targetPool.isEmpty() || sourcePool.get().id().equals(targetPool.get().id());
    }

    private static boolean isActiveAnchor(BlockState state) {
        return state.getBlock() instanceof TeleportAnchorBlock && state.getValue(TeleportAnchorBlock.ACTIVE);
    }

    /** 在传送请求通过其他校验后，只读取所选目的地。 */
    public boolean teleportToPoint(ServerPlayer player, TeleportAnchorData point, @Nullable Runnable chargeSource) {
        ServerLevel target = player.level().getServer().getLevel(point.dimension());
        if (target == null || !target.isInWorldBounds(point.pos())
                || !allowsDimension(player.level().dimension(), point.dimension())) return false;
        try {
            BlockPos pos = point.pos();
            if (!isActiveAnchor(target.getBlockState(pos))) {
                removePointByPos(player, pos, point.dimension());
                return false;
            }
            Vec3 destination = Vec3.atBottomCenterOf(pos.above());
            var transition = new TeleportTransition(target, destination, Vec3.ZERO,
                    player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING);
            return DimensionPoolManager.onTeleport(player, transition,
                    () -> player.teleportTo(target, destination.x, destination.y, destination.z,
                            Set.of(), player.getYRot(), player.getXRot(), true) ? player : null,
                    chargeSource) == player;
        } catch (RuntimeException error) {
            DebugLogger.SERVER.exception("TeleportAnchor", "传送至锚点 " + point.pos(), error);
            return false;
        }
    }

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
        return findPoint(player.getUUID(), pos, dimension);
    }

    private TeleportAnchorData findPoint(UUID player, BlockPos pos, ResourceKey<Level> dimension) {
        List<TeleportAnchorData> points = playerPoints.get(player);
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
        queueDeactivation(GlobalPos.of(removed.dimension(), removed.pos()), player.getUUID());

        ServerLevel level = player.level().getServer().getLevel(removed.dimension());
        if (level != null) {
            var chunk = LoadedChunks.get(level, removed.pos());
            if (chunk != null) applyDeactivations(level, chunk, removed.pos());
        }
        return true;
    }

    private void queueDeactivation(GlobalPos anchor, UUID player) {
        pendingDeactivations.computeIfAbsent(anchor, ignored -> new HashSet<>()).add(player);
    }

    private void applyDeactivations(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        GlobalPos anchor = GlobalPos.of(level.dimension(), pos);
        Set<UUID> players = pendingDeactivations.get(anchor);
        if (players == null) return;
        BlockState state = chunk.getBlockState(pos);
        if (state.getBlock() instanceof TeleportAnchorBlock
                && chunk.getBlockEntity(pos) instanceof TeleportAnchorBlockEntity entity) {
            for (UUID player : players) {
                if (findPoint(player, pos, level.dimension()) == null) entity.removeActivator(player);
            }
            if (entity.getActivatorCount() == 0 && state.getValue(TeleportAnchorBlock.ACTIVE)) {
                level.setBlock(pos, state.setValue(TeleportAnchorBlock.ACTIVE, false),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        pendingDeactivations.remove(anchor);
        setDirty();
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
        boolean changed = pendingDeactivations.remove(GlobalPos.of(dimension, pos)) != null;
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

    /**
     * 在玩家当前维度内找到距离玩家最近的可用传送锚点。
     * <p>
     * 「可用」与 {@link #getValidPointsForPlayer(ServerPlayer, ResourceKey)} 同口径：
     * 已加载的锚点必须有效，未加载的锚点交由实际传送时验证。
     * <p>
     * 距离按传送石耐久计算公式的口径取：玩家坐标对锚点上表面中心
     * (pos.getX()+0.5, pos.getY()+1.0, pos.getZ()+0.5) 的欧氏距离。
     *
     * @return 最近锚点；当前维度一个可用锚点都没有时返回 {@link Optional#empty()}
     */
    public java.util.Optional<TeleportAnchorData> findNearestActiveAnchorInDimension(
            ServerPlayer player, ResourceKey<Level> dimension) {
        List<TeleportAnchorData> candidates = getValidPointsForPlayer(player, dimension);
        if (candidates.isEmpty()) {
            return java.util.Optional.empty();
        }
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        TeleportAnchorData nearest = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        for (TeleportAnchorData p : candidates) {
            if (!p.dimension().equals(dimension)) continue;
            double dx = (p.pos().getX() + 0.5) - px;
            double dy = (p.pos().getY() + 1.0) - py;
            double dz = (p.pos().getZ() + 0.5) - pz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                nearest = p;
            }
        }
        return java.util.Optional.ofNullable(nearest);
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
     * 查询本次传送的物品入口，传送成功后清除。
     */
    @Nullable
    public TeleportOpenerSource getTeleportSourceMark(ServerPlayer player) {
        return openedLists.get(player.getUUID());
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
