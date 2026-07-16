package top.csituka.youzaiworldcore.client.pickup;

import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.pickup.display.ExperienceDisplayEntry;
import top.csituka.youzaiworldcore.client.pickup.display.ItemDisplayEntry;
import top.csituka.youzaiworldcore.util.DebugLogger;

import static top.csituka.youzaiworldcore.client.pickup.PendingPickupQueue.Item;
import static top.csituka.youzaiworldcore.client.pickup.PendingPickupQueue.Experience;

/**
 * 拾取通知条目的添加处理器。
 * <p>
 * 消费由 {@link PendingPickupQueue} 在 Netty 线程捕获的待处理拾取事件，
 * 在主线程上创建对应的显示条目并提交给 {@link DrawEntriesHandler}。
 * </p>
 *
 * <p>支持的实体类型通过 PickUpNotifyMixin 的实体检查分支实现：</p>
 * <ul>
 *   <li>{@code ItemEntity} — 掉落物拾取</li>
 *   <li>{@code AbstractArrow} — 箭矢回收</li>
 *   <li>{@code ExperienceOrb} — 经验球拾取</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class AddEntriesHandler {

    private AddEntriesHandler() {
    }

    /**
     * 在主线程上消费待处理的拾取事件队列。
     * <p>
     * 由 {@code Client.onClientTick()} 每帧调用，从线程安全队列中取出
     * 已捕获的拾取数据，创建对应的显示条目。
     * </p>
     */
    public static void drainQueue() {
        if (PendingPickupQueue.isEmpty()) return;

        DebugLogger.entering("AddEntriesHandler", "drainQueue",
                "queueSize=" + PendingPickupQueue.size());

        PendingPickupQueue.PendingPickup pickup;
        while ((pickup = PendingPickupQueue.dequeue()) != null) {
            switch (pickup) {
                case Item itemPickup -> {
                    ItemStack stack = itemPickup.stack();
                    if (stack.isEmpty()) continue;

                    DebugLogger.info("AddEntriesHandler", "Item pickup: %s x%d",
                            stack.getHoverName().getString(), itemPickup.amount());

                    ItemDisplayEntry entry = new ItemDisplayEntry(
                            stack, itemPickup.amount(), DrawEntriesHandler.DISPLAY_TIME);
                    DrawEntriesHandler.INSTANCE.addEntry(entry.getKey(), entry);
                }
                case Experience xpPickup -> {
                    int xpValue = xpPickup.xpValue();
                    DebugLogger.info("AddEntriesHandler", "Experience pickup: value=%d", xpValue);

                    ExperienceDisplayEntry entry = new ExperienceDisplayEntry(
                            xpValue, DrawEntriesHandler.DISPLAY_TIME);
                    DrawEntriesHandler.INSTANCE.addEntry(entry.getKey(), entry);
                }
            }
        }

        DebugLogger.exiting("AddEntriesHandler", "drainQueue");
    }
}
