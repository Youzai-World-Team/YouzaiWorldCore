package top.csituka.youzaiworldcore.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 生物感压板（Mob Pressure Plate）。
 * <p>
 * 该方块是一种特殊压力板，<b>仅</b>非玩家 {@link LivingEntity}（即动物、怪物等）能够触发，
 * 向相邻方块输出红石信号（{@code POWERED=true} 时强度为 15）。
 * <ul>
 *   <li>玩家踩踏：<b>不触发</b></li>
 *   <li>掉落物实体（{@link net.minecraft.world.entity.item.ItemEntity} 等）：
 *       由于它们不是 {@link LivingEntity}，<b>不触发</b></li>
 *   <li>生物（动物、怪物、Boss 等）：<b>触发</b></li>
 * </ul>
 * <p>
 * 实现要点：
 * <ol>
 *   <li>继承 {@link BasePressurePlateBlock}，直接复用基类的 {@code entityInside} →
 *       {@code checkPressed} → {@code tick}（20 ticks）→ 松开调度器整套机制。</li>
 *   <li>{@code POWERED} 属性使用原版 {@link BlockStateProperties#POWERED}。</li>
 *   <li>声音与发光特性复用原版石系
 *       {@link BlockSetType#STONE}（与原版"石压力板"一致）。</li>
 *   <li>实体过滤通过自定义 Predicate 排除 {@link Player}，再委托基类扫描 LivingEntity。</li>
 * </ol>
 *
 * @see BasePressurePlateBlock
 * @see BlockSetType#STONE
 */
@SuppressWarnings("null")
public class MobPressurePlateBlock extends BasePressurePlateBlock {

    /**
     * 数据驱动的红石信号属性（{@code powered=true} 时输出强度 15）。
     * <p>
     * 与原版压力板的 {@link BlockStateProperties#POWERED} 保持一致，使得
     * {@code blockstates/} JSON 中的 {@code "powered=true/false"} 变体能被基类
     * 自动消费。
     */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public static final MapCodec<MobPressurePlateBlock> CODEC = simpleCodec(MobPressurePlateBlock::new);

    /**
     * 构造生物感压板。
     * <p>
     * 强制传入 {@link BlockSetType#STONE}：石系的 {@code pressurePlateClickOn/Off}
     * 音效与本方块的金属感外观最为契合；如未来需替换为铜/金/木质等声音，仅需扩展
     * 一个接受 {@link BlockSetType} 参数的重载即可。
     *
     * @param properties 方块物理属性（硬度、爆炸抗性、声音类别等）。
     */
    public MobPressurePlateBlock(BlockBehaviour.Properties properties) {
        super(properties, BlockSetType.STONE);
        DebugLogger.entering("MobPressurePlateBlock", "constructor",
                "type=STONE, sensitivity=MOBS(excluding Player)");
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, Boolean.FALSE));
        DebugLogger.exiting("MobPressurePlateBlock", "constructor", "defaultState=powered=false");
    }

    @Override
    @NonNull
    protected MapCodec<? extends BasePressurePlateBlock> codec() {
        return CODEC;
    }

    /**
     * 信号强度计算：返回 {@code 15} 当且仅当压力板上方 {@code TOUCH_AABB} 范围内
     * 存在任意 <b>非玩家的 {@link LivingEntity}</b>，否则返回 {@code 0}。
     * <p>
     * 关键过滤规则：
     * <ul>
     *   <li>{@link Player} 被显式排除（即便 Player 是 {@code LivingEntity} 子类）</li>
     *   <li>{@link EntitySelector#NO_SPECTATORS} 排除旁观模式玩家与不可见实体（基类约定）</li>
     *   <li>物品/掉落物/经验球等不是 {@code LivingEntity}，被 {@code getEntitiesOfClass}
     *       的 {@code Class} 参数在数据库层过滤</li>
     * </ul>
     *
     * @param level 所在 Level
     * @param pos   压力板位置（{@code TOUCH_AABB} 会基于此 {@code move}）
     * @return 信号强度 —— {@code 15} 表示被生物踩下，{@code 0} 表示未触发
     */
    @Override
    protected int getSignalStrength(@NonNull Level level, @NonNull BlockPos pos) {
        AABB touchZone = TOUCH_AABB.move(pos);
        // LivingEntity ∩ NO_SPECTATORS ∩ (NOT Player)
        boolean hasMob = !level.getEntitiesOfClass(
                LivingEntity.class,
                touchZone,
                EntitySelector.NO_SPECTATORS.and(MobPressurePlateBlock::isCountableMob)
        ).isEmpty();

        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.branch("MobPressurePlateBlock", "getSignalStrength",
                    hasMob,
                    "pos=" + pos + ", sensitivity=LIVING_NOT_PLAYER");
        }

        return hasMob ? 15 : 0;
    }

    /**
     * 实体筛选 Predicate：仅"非玩家的 LivingEntity"计为可触发。
     * <p>
     * 单独抽成静态方法，便于将来扩展（例如：允许特定 NBT 标签的玩家能触发，
     * 或把盔甲架/画等也纳入计算）。
     */
    private static boolean isCountableMob(Entity entity) {
        return !(entity instanceof Player);
    }

    @Override
    protected int getSignalForState(@NonNull BlockState state) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    @NonNull
    protected BlockState setSignalForState(@NonNull BlockState state, int signal) {
        return state.setValue(POWERED, signal > 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    /**
     * 覆写入口以拦截 {@link Player}，避免基类在玩家身上做无意义的 {@code checkPressed}。
     * <p>
     * 之所以仍然走 {@code super}：基类的客户端短路、{@code updateNeighbours}、
     * 按下/弹起音效（{@link BlockSetType#STONE}）、GameEvent 触发
     * （{@code BLOCK_ACTIVATE} / {@code BLOCK_DEACTIVATE}）、以及 20 ticks
     * 的 {@code scheduleTick} 解压调度器都是我们需要的。
     * <p>
     * 注意：此处的拦截与 {@link #getSignalStrength} 中的过滤是<b>两层冗余</b>——
     * 即便不覆盖此方法、仅靠 {@code getSignalStrength} 也能得到正确逻辑；
     * 这里覆盖是因为玩家高频踩踏时不应当消耗一次完整的状态写入路径。
     *
     * @param state    当前方块状态
     * @param level    所在 Level
     * @param pos      压力板位置
     * @param entity   接触方块的实体
     * @param applier  InsideBlockEffectApplier（26.2 新 API，传给 super 即可）
     * @param isInside 当前实体是否仍处于方块内部（用于判定"刚进入"）
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level,
                                @NonNull BlockPos pos,
                                @NonNull Entity entity,
                                @NonNull InsideBlockEffectApplier applier,
                                boolean isInside) {
        if (entity instanceof Player player) {
            // 玩家永远不触发本压力板。基类 entityInside 的非客户端侧逻辑都是基于
            // getSignalStrength 的判定，因此即便真的调用 super，玩家在
            // getSignalStrength 里也会被 Predicate 排除。
            // 此处显式短路是性能优化：避免每 tick 的玩家碰撞调用链路。
            // 热路径：手动包一层 isEnabled 判定，避免 varargs 数组分配。
            if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
                DebugLogger.trace("MobPressurePlateBlock", "entityInside: player IGNORED",
                        "player=" + player.getName().getString() + ", pos=" + pos);
            }
            return;
        }
        // 非玩家 → 交给基类。基类在服务端会调用 getSignalStrength 并按结果触发
        // BLOCK_ACTIVATE / BLOCK_DEACTIVATE 与声音。
        super.entityInside(state, level, pos, entity, applier, isInside);
    }
}
