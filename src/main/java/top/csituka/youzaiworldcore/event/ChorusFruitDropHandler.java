package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 紫颂果就近掉落处理器。
 * <p>
 * 参考 Serilum 的 Chorus Fruit Drops Nearby 的设计与行为实现（原生重写，不依赖其前置 Collective）
 * 行为：当紫颂植物（{@link Blocks#CHORUS_PLANT}）被破坏时记录其位置；
 * 紫颂果（{@link Items#CHORUS_FRUIT}）物品实体进入世界时，将其就近传送到
 * 最近一次被破坏的紫颂植物位置（水平欧氏距离 &lt; {@link #MAX_DISTANCE} 且处于
 * {@link #EXPIRY_MS} 时间窗口内），从而避免果实散落满地。
 * </p>
 * <p>
 * 实现说明：
 * <ul>
 * <li>匹配时果实位置与记录位置两侧都需用 {@code BlockPos.containing(x, 1, z)} 将 Y 轴归一化为 1，
 * 使 {@code closerThan} 退化为纯水平（XZ）距离比较，否则高空植物的真实高度差会使三维距离远超阈值；</li>
 * <li>传送目标为记录方块坐标 {@code y + 1}，即被破坏方块上方一格；</li>
 * <li>记录位置在 {@link #EXPIRY_MS} 毫秒后过期移除，避免列表无限增长；</li>
 * <li>仅服务端生效，客户端忽略。</li>
 * </ul>
 * 所有诊断日志均通过 {@link DebugLogger} 输出，受开发者模式 + 日志级别双维度控制，
 * 生产环境无噪音，调试时可在配置中开启 devMode / logToFile 获得完整日志。
 * </p>
 */
@SuppressWarnings("null")
public class ChorusFruitDropHandler {

    private static final String MODULE = "ChorusFruitDrop";

    /** 果实归集的最大水平距离（方块，欧氏距离严格小于该值） */
    private static final double MAX_DISTANCE = 20.0;

    /** 记录位置的过期时间（毫秒），超过视为无效并移除 */
    private static final long EXPIRY_MS = 2000L;

    /** 最近被破坏的紫颂植物方块位置列表（线程安全，支持遍历时安全删除） */
    private static final CopyOnWriteArrayList<BlockPos> lastChorusBlock = new CopyOnWriteArrayList<>();

    /** 每个记录位置对应的最后交互时间戳 */
    private static final Map<BlockPos, Date> lastAction = new HashMap<>();

    private ChorusFruitDropHandler() {
    }

    /**
     * 向 Fabric 事件总线注册紫颂果就近掉落处理器。
     */
    public static void register() {
        DebugLogger.entering(MODULE, "register");

        PlayerBlockBreakEvents.AFTER
                .register((world, player, pos, state, blockEntity) -> onBlockBreak(world, pos, state));

        ServerEntityEvents.ENTITY_LOAD.register((Entity entity, ServerLevel world) -> onChorusFruit(world, entity));

        DebugLogger.info(MODULE, "紫颂果就近掉落处理器已注册 (maxDistance=%.1f, expiry=%dms)",
                MAX_DISTANCE, EXPIRY_MS);
        DebugLogger.exiting(MODULE, "register");
    }

    /**
     * 方块破坏后回调：若被破坏的是紫颂植物，则记录其位置与时间戳。
     *
     * @param world 世界实例
     * @param pos   被破坏方块的坐标
     * @param state 被破坏方块的方块状态
     */
    private static void onBlockBreak(Level world, BlockPos pos, BlockState state) {
        DebugLogger.entering(MODULE, "onBlockBreak", "pos=" + pos + ", block=" + state.getBlock());

        if (world.isClientSide()) {
            DebugLogger.branch(MODULE, "is server side", false, "客户端忽略");
            DebugLogger.exiting(MODULE, "onBlockBreak", "PASS (client)");
            return;
        }

        if (state.getBlock().equals(Blocks.CHORUS_PLANT)) {
            BlockPos immutablePos = pos.immutable();
            lastChorusBlock.add(immutablePos);
            lastAction.put(immutablePos, new Date());
            DebugLogger.info(MODULE, "记录紫颂植物破坏位置: %s (当前记录数=%d)",
                    immutablePos, lastChorusBlock.size());
        } else {
            DebugLogger.branch(MODULE, "block is chorus_plant", false, state.getBlock().toString());
        }

        DebugLogger.exiting(MODULE, "onBlockBreak", "done");
    }

    /**
     * 物品实体进入世界回调：若为紫颂果，则就近传送到最近一次被破坏的紫颂植物处。
     * <p>
     * 注意：本方法会被大量（非紫颂果）物品实体高频触发，因此只在命中紫颂果或发生
     * 传送/过期清理时才输出日志，避免调试日志刷屏。
     * </p>
     *
     * @param world  世界实例（此处保证为 {@link ServerLevel}）
     * @param entity 进入世界的实体
     */
    private static void onChorusFruit(Level world, Entity entity) {
        if (world.isClientSide()) {
            return;
        }

        if (!(entity instanceof ItemEntity itemEntity)) {
            return;
        }

        ItemStack itemStack = itemEntity.getItem();
        if (itemStack.getItem() != Items.CHORUS_FRUIT) {
            return;
        }

        DebugLogger.debug(MODULE, "紫颂果进入世界: %s (当前记录位置数=%d)",
                itemEntity, lastChorusBlock.size());

        // 将 Y 轴归一化为 1，使后续 closerThan 比较退化为纯水平（XZ）距离
        BlockPos lowChorusPos = BlockPos.containing(itemEntity.getX(), 1.0, itemEntity.getZ());

        Date now = new Date();
        for (BlockPos recordedPos : lastChorusBlock) {
            Date last = lastAction.get(recordedPos);
            if (last == null) {
                lastChorusBlock.remove(recordedPos);
                continue;
            }

            // 过期清理：记录位置超过时间窗口则移除
            long ageMs = now.getTime() - last.getTime();
            if (ageMs > EXPIRY_MS) {
                DebugLogger.debug(MODULE, "记录位置已过期，移除: %s (age=%dms)", recordedPos, ageMs);
                lastChorusBlock.remove(recordedPos);
                lastAction.remove(recordedPos);
                continue;
            }

            // 将记录位置同样归一化为 Y=1，与 lowChorusPos 对齐，使 closerThan 退化为纯水平（XZ）距离比较。
            // 若此处直接用真实高度（紫颂植物常位于高空）参与三维欧氏距离计算，垂直分量会让距离远超阈值，导致永远无法匹配。
            BlockPos lowRecordedPos = BlockPos.containing(recordedPos.getX(), 1.0, recordedPos.getZ());
            if (lowChorusPos.closerThan(lowRecordedPos, MAX_DISTANCE)) {
                BlockPos target = new BlockPos(recordedPos.getX(), recordedPos.getY() + 1, recordedPos.getZ());
                itemEntity.teleportTo(target.getX(), target.getY(), target.getZ());
                lastAction.put(recordedPos.immutable(), now);
                DebugLogger.info(MODULE, "紫颂果就近传送至 %s (原位置 x=%.2f, z=%.2f)",
                        target, itemEntity.getX(), itemEntity.getZ());
                break;
            }
        }
    }
}
