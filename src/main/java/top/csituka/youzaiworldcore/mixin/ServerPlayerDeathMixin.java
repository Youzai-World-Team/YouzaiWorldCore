package top.csituka.youzaiworldcore.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.item.ModItems;

/**
 * 混合注入 {@link ServerPlayer#die(DamageSource)}，
 * 在玩家死亡时处理「守护之心」的消耗、消息提示、成就授予与数量警告。
 *
 * <p>逻辑：</p>
 * <ol>
 *   <li>如果玩家死亡前背包中拥有守护之心 → 消耗 1 个、发送消息、授予成就</li>
 *   <li>消耗后统计剩余守护之心数量 → 若等于 10/5/3/2/1 则发送警告</li>
 *   <li>实际的物品保留由 {@link PlayerDropEquipmentMixin} 负责</li>
 * </ol>
 *
 * <p>原本由数据包 {@code tick.mcfunction} 中的 {@code youzaiworld.death} 计分板 + 定时提醒逻辑实现，
 * 现改用 Mixin 方式精确控制，仅在消耗时检查一次。</p>
 */
@Mixin(ServerPlayer.class)
public class ServerPlayerDeathMixin {

    @Shadow
    private MinecraftServer server;

    /**
     * 在 {@link ServerPlayer#die(DamageSource)} 执行之初注入，
     * 优先处理守护之心的消耗与反馈。
     */
    @Inject(method = "die", at = @At("HEAD"))
    private void youzaiworldcore$onDie(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // 旁观者模式不处理
        if (player.isSpectator()) {
            return;
        }

        // 检查玩家背包中是否有守护之心
        if (hasHeartInInventory(player)) {
            // 消耗 1 个守护之心
            consumeOneHeart(player);

            // 发送消耗提醒
            player.sendSystemMessage(
                    Component.translatable("youzaiworldcore.tellraw.format")
                            .append(Component.translatable("item.youzaiworldcore.heart_of_guardianship.consumed"))
            );

            // 授予成就（used_heart_of_guardianship）
            AdvancementHolder advancement = server.getAdvancements().get(
                    Identifier.fromNamespaceAndPath("youzaiworldcore", "youzaiworld/used_heart_of_guardianship")
            );
            if (advancement != null) {
                player.getAdvancements().award(advancement, "manual_grant");
            }

            // 消耗后统计剩余守护之心数量，触发相应阈值警告
            int remaining = countHearts(player);
            warnIfThreshold(player, remaining);
        }
    }

    /**
     * 从玩家背包中清除第一个找到的守护之心。
     */
    @Unique
    private static void consumeOneHeart(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                stack.shrink(1);
                break;
            }
        }
    }

    /**
     * 统计玩家背包中所有守护之心的总数量（所有槽位堆叠之和）。
     */
    @Unique
    private static int countHearts(ServerPlayer player) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 检查玩家背包中是否有至少一个守护之心。
     */
    @Unique
    private static boolean hasHeartInInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当剩余守护之心数量等于预设阈值（10、5、3、2、1）时发送警告消息。
     */
    @Unique
    private static void warnIfThreshold(ServerPlayer player, int count) {
        if (count == 10 || count == 5 || count == 3 || count == 2 || count == 1) {
            player.sendSystemMessage(
                    Component.translatable("youzaiworldcore.tellraw.format")
                            .append(Component.translatable(
                                    "youzaiworldcore.heart_of_guardianship.warning." + count
                            ))
            );
        }
    }
}
