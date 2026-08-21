package top.csituka.youzaiworldcore.redstone;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无线红石网络索引：记录当前<b>处于激活态</b>的无线红石发射器，供接收器查询。
 * <p>
 * <b>为什么需要索引</b>：接收器要回答「以我为中心 {@value #RANGE} 格内，有没有同频道的
 * 激活发射器」。直接扫方块要遍历 {@code 65³ ≈ 27 万} 个坐标，扫附近区块的方块实体表
 * 也会随基地规模膨胀；而激活的发射器在一个世界里通常只有个位数到几十个，
 * 按「维度 → 频道 → 坐标集合」建索引后，单次查询只需遍历<b>同频道</b>的那几个坐标。
 * <p>
 * <b>生命周期（全部由方块实体驱动，纯内存、不写存档）</b>：
 * <ul>
 *   <li>入索引：{@code WirelessRedstoneTransmitterBlockEntity.setLevel} —— 放置与区块加载
 *       都会走到，且原版在此之前已完成 NBT 读档（{@code BlockEntity.loadStatic} 先于
 *       {@code setLevel}），因此拿到的频道号是存档里的真实值；</li>
 *   <li>出索引：{@code setRemoved()} —— 破坏方块与<b>区块卸载</b>共用这一个钩子
 *       （{@code LevelChunk.clearAllBlockEntities} 会逐个调用），所以
 *       「发射器所在区块被卸载 → 索引条目消失 → 范围内接收器下一 tick 自动断电」，
 *       与原版红石「不加载就不工作」的直觉一致；</li>
 *   <li>状态/频道变化：由发射器方块的 {@code tick} 与频道设置流程调用
 *       {@link #setTransmitterActive}。</li>
 * </ul>
 * 索引不需要持久化：世界重新加载时每个发射器方块实体都会重新走一遍 {@code setLevel}。
 * <p>
 * <b>线程安全</b>：区块加载/卸载与方块实体 tick 都在服务端主线程，但区块反序列化路径
 * 存在被工作线程触达的可能，故容器一律用并发实现——代价可忽略，换掉一类难复现的偶发错。
 *
 * @see WirelessRedstoneChannel
 * @see top.csituka.youzaiworldcore.block.WirelessRedstoneTransmitterBlock
 * @see top.csituka.youzaiworldcore.block.WirelessRedstoneReceiverBlock
 */
public final class WirelessRedstoneNetwork {

    private static final String MODULE = "WirelessRedstoneNetwork";

    /**
     * 无线传输半径（格）。
     * <p>
     * 判定用<b>欧几里得距离</b>（球形范围）：接收器与发射器的直线距离
     * {@code <= 32} 才生效，即「以发射器为中心的周围 32 格范围内」。
     */
    public static final int RANGE = 32;

    /** {@link #RANGE} 的平方，避免每次判定开平方。 */
    private static final double RANGE_SQR = (double) RANGE * RANGE;

    /** 维度 → 该维度的激活发射器索引。 */
    private static final Map<ResourceKey<Level>, LevelIndex> INDEXES = new ConcurrentHashMap<>();

    /**
     * 索引版本号：<b>只要索引内容真的变了就自增</b>（发射器进出索引、改频道、清空）。
     * <p>
     * 这是接收器每 tick 开销的关键：接收器把「上次查询结果」连同当时的版本号一起缓存，
     * 版本号没变就说明<b>没有任何发射器发生过变化</b>，查询结果不可能改变，
     * 于是稳态下每个接收器每 tick 只做一次 {@code long} 比较，不碰哈希表。
     * <p>
     * 之所以正确：查询结果只取决于「激活发射器的集合 + 各自频道与坐标」和
     * 「接收器自己的频道」两件事。前者的任何变化都会走到本类的写入口并自增版本号；
     * 后者由接收器在 {@code onChannelChanged} 里主动作废自己的缓存。
     * <p>
     * 用 {@code long} 而非 {@code int}：彻底免去回绕后与哨兵值相撞的讨论。
     * <p>
     * <b>不需要 {@code AtomicLong}</b>：{@code generation++} 在 volatile 上不是原子操作，
     * 但这里只依赖「内容变了则数值必然与之前不同」，不依赖计数精确。
     * 即便两次自增撞在一起只推进了 1，数值仍与接收器缓存的旧值不同，
     * 缓存照样会作废并重查，两次变化都会被看到。
     */
    private static volatile long generation;

    private WirelessRedstoneNetwork() {
    }

    /**
     * 注册服务器停止时的索引清理。
     * <p>
     * 正常流程下所有区块卸载会把索引清空（每个发射器的 {@code setRemoved()} 都会执行），
     * 这里是兜底：万一某次关服没走完卸载流程，残留的坐标会在<b>下一个同维度 ID 的世界</b>
     * 里让接收器凭空通电。清空一次即可根除这种跨存档串味。
     */
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
        DebugLogger.info(MODULE, "无线红石网络索引已就绪（传输半径 %d 格，频道 %d~%d）",
                RANGE, WirelessRedstoneChannel.MIN, WirelessRedstoneChannel.MAX);
    }

    /**
     * @return 当前索引版本号，供接收器判断自己缓存的查询结果是否还有效
     * @see #generation
     */
    public static long generation() {
        return generation;
    }

    /**
     * 更新一个发射器在索引中的登记情况。
     * <p>
     * 这是唯一的写入口：无论「刚加载」「通电/断电」还是「改频道」，都调用它并传入
     * 当前的频道与激活状态，由方法内部负责把旧频道下的条目摘掉。
     * <p>
     * <b>只有真的改变了索引内容才自增版本号</b>。这一点很关键：发射器方块每次
     * 调度 tick 都会无条件调一次本方法做幂等刷新，若无脑自增版本号，
     * 全世界接收器的缓存都会被这种「其实什么都没变」的刷新打掉。
     *
     * @param level   发射器所在世界（客户端 Level 会被直接忽略）
     * @param pos     发射器坐标
     * @param channel 发射器当前频道
     * @param active  发射器当前是否处于激活态（侧边有红石信号进入）
     */
    public static void setTransmitterActive(Level level, BlockPos pos, int channel, boolean active) {
        if (level == null || level.isClientSide()) {
            return;
        }
        LevelIndex index = INDEXES.computeIfAbsent(level.dimension(), key -> new LevelIndex());
        boolean changed = active ? index.put(pos.immutable(), channel) : index.remove(pos);
        if (!changed) {
            return;
        }

        generation++;
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(MODULE, "发射器索引更新 @%s: channel=%d, active=%s, 该维度激活数=%d",
                    pos.toShortString(), channel, active, index.size());
        }
    }

    /**
     * 把一个发射器从索引中彻底摘除（破坏方块或区块卸载时调用）。
     *
     * @param level 发射器所在世界
     * @param pos   发射器坐标
     */
    public static void removeTransmitter(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) {
            return;
        }
        LevelIndex index = INDEXES.get(level.dimension());
        if (index == null || !index.remove(pos)) {
            return;
        }

        generation++;
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(MODULE, "发射器移出索引 @%s，该维度激活数=%d",
                    pos.toShortString(), index.size());
        }
    }

    /**
     * 查询某个接收器是否应当通电。
     * <p>
     * 条件：同维度、频道号相同、且与某个激活发射器的直线距离不超过 {@link #RANGE} 格。
     * <p>
     * 常见的「附近没有任何同频道发射器」情况只需一次哈希查表即可返回，
     * 因此可以放心地每 tick 调用。
     *
     * @param level         接收器所在世界
     * @param receiverPos   接收器坐标
     * @param channel       接收器频道
     * @return 范围内存在同频道的激活发射器时返回 true
     */
    public static boolean hasActiveTransmitterInRange(Level level, BlockPos receiverPos, int channel) {
        if (level == null || level.isClientSide()) {
            return false;
        }
        LevelIndex index = INDEXES.get(level.dimension());
        if (index == null) {
            return false;
        }
        Set<BlockPos> candidates = index.byChannel(channel);
        if (candidates == null) {
            return false;
        }
        for (BlockPos transmitterPos : candidates) {
            if (receiverPos.distSqr(transmitterPos) <= RANGE_SQR) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清空全部索引（关服兜底）。
     */
    public static void clear() {
        if (!INDEXES.isEmpty()) {
            DebugLogger.info(MODULE, "清空无线红石网络索引（%d 个维度）", INDEXES.size());
            INDEXES.clear();
            // 索引内容变了，作废所有接收器的缓存结果
            generation++;
        }
    }

    /**
     * 单个维度的激活发射器索引。
     * <p>
     * 同时维护两个方向的映射：
     * <ul>
     *   <li>{@code byChannel}：频道 → 坐标集合，供接收器只遍历同频道的发射器；</li>
     *   <li>{@code channelOfPos}：坐标 → 频道，使「摘除某坐标」不必扫遍所有频道。</li>
     * </ul>
     */
    private static final class LevelIndex {

        private final Map<Integer, Set<BlockPos>> byChannel = new ConcurrentHashMap<>();
        private final Map<BlockPos, Integer> channelOfPos = new ConcurrentHashMap<>();

        /**
         * 登记（或改频）一个激活发射器。
         *
         * @param pos     发射器坐标（须为不可变实例）
         * @param channel 目标频道
         * @return 索引内容确实发生了变化时返回 true；
         *         「本来就在索引里、且频道没变」返回 false，用于避免无谓地推高版本号
         */
        boolean put(BlockPos pos, int channel) {
            Integer previous = channelOfPos.put(pos, channel);
            if (previous != null && previous == channel) {
                return false;
            }
            if (previous != null) {
                // 改了频道：先把旧频道下的条目摘掉，否则会在两个频道里同时生效
                dropFromChannel(previous, pos);
            }
            byChannel.computeIfAbsent(channel, key -> ConcurrentHashMap.newKeySet()).add(pos);
            return true;
        }

        /**
         * 摘除一个发射器。
         *
         * @param pos 发射器坐标
         * @return 原先确实在索引里时返回 true
         */
        boolean remove(BlockPos pos) {
            Integer channel = channelOfPos.remove(pos);
            if (channel == null) {
                return false;
            }
            dropFromChannel(channel, pos);
            return true;
        }

        /**
         * @param channel 频道号
         * @return 该频道下的激活发射器坐标集合，没有则返回 null
         */
        Set<BlockPos> byChannel(int channel) {
            return byChannel.get(channel);
        }

        /**
         * @return 本维度当前激活的发射器总数
         */
        int size() {
            return channelOfPos.size();
        }

        private void dropFromChannel(int channel, BlockPos pos) {
            Set<BlockPos> positions = byChannel.get(channel);
            if (positions == null) {
                return;
            }
            positions.remove(pos);
            if (positions.isEmpty()) {
                // 空集合及时回收，让「附近无同频道发射器」的查询走最快的 null 分支
                byChannel.remove(channel, positions);
            }
        }
    }
}
