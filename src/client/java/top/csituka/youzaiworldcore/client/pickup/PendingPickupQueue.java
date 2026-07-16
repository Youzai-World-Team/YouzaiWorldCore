package top.csituka.youzaiworldcore.client.pickup;

import net.minecraft.world.item.ItemStack;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 拾取事件的待处理队列（线程安全）。
 * <p>
 * 由 {@link top.csituka.youzaiworldcore.mixin.client.PickUpNotifyMixin} 在 Netty 线程写入，
 * 由 {@link AddEntriesHandler#drainQueue()} 在主线程消费。
 * </p>
 */
public final class PendingPickupQueue {

    private static final Queue<PendingPickup> QUEUE = new ConcurrentLinkedQueue<>();

    private PendingPickupQueue() {}

    /**
     * 入队一个待处理拾取事件。
     */
    public static void enqueue(PendingPickup pickup) {
        QUEUE.add(pickup);
    }

    /**
     * 出队一个待处理拾取事件（若队列为空则返回 null）。
     */
    public static PendingPickup dequeue() {
        return QUEUE.poll();
    }

    /**
     * 检查队列是否为空。
     */
    public static boolean isEmpty() {
        return QUEUE.isEmpty();
    }

    /**
     * 获取队列中的事件数。
     */
    public static int size() {
        return QUEUE.size();
    }

    /**
     * 拾取事件的待处理数据封装，由 sealed 接口约束其类型体系。
     * <p>
     * 在 Netty 线程上完成字段捕获，避免在主线程上实体已移除的问题。
     * </p>
     */
    public sealed interface PendingPickup permits Item, Experience {
    }

    /** 物品拾取事件 */
    public record Item(ItemStack stack, int amount) implements PendingPickup {}

    /** 经验拾取事件 */
    public record Experience(int xpValue) implements PendingPickup {}
}
