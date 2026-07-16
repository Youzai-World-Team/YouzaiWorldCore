package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.pickup.PendingPickupQueue;

/**
 * 客户端数据包监听器的 Mixin，用于拦截物品/经验拾取数据包。
 * <p>
 * 在 {@code handleTakeItemEntity} 处理前（HEAD）捕获实体信息写入线程安全队列，
 * 再由客户端主线程消费并创建显示条目。
 * 这种生产者-消费者模式避免了 Netty IO 线程上的不安全的组件构建操作。
 * </p>
 *
 * <p>此 Mixin 对所有实现通用，无需服务端额外支持——即使服务端未安装本模组，
 * 客户端也能通过原生数据包识别拾取事件。</p>
 */
@SuppressWarnings("null")
@Mixin(ClientPacketListener.class)
public abstract class PickUpNotifyMixin {

    /**
     * 在 {@code handleTakeItemEntity} 方法执行前捕获实体信息。
     * <p>
     * HEAD 注入点确保被拾取实体仍在世界中。此处仅做线程安全的字段读取和拷贝，
     * 不进行任何文本组件构建或渲染操作，以避免 Netty 线程上的类加载/线程安全问题。
     * 实际的条目创建由 {@link top.csituka.youzaiworldcore.client.pickup.AddEntriesHandler#drainQueue()} 在主线程上完成。
     * </p>
     */
    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void onHandleTakeItemEntity(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        if (client.player.getId() != packet.getPlayerId()) return;

        Entity entity = client.level.getEntity(packet.getItemId());
        if (entity == null) return;

        int amount = packet.getAmount();

        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                PendingPickupQueue.enqueue(new PendingPickupQueue.Item(stack.copy(), amount));
            }
        } else if (entity instanceof ExperienceOrb xpOrb) {
            PendingPickupQueue.enqueue(new PendingPickupQueue.Experience(xpOrb.getValue()));
        } else if (entity instanceof AbstractArrow arrow) {
            ItemStack pickupStack = arrow.getPickupItemStackOrigin();
            if (!pickupStack.isEmpty()) {
                PendingPickupQueue.enqueue(new PendingPickupQueue.Item(pickupStack.copy(), 1));
            }
        }
    }
}
