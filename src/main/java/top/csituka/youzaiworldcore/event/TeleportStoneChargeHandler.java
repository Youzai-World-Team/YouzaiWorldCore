package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.item.tool.TeleportStoneItem;
import top.csituka.youzaiworldcore.item.tool.WarpScrollItem;
import top.csituka.youzaiworldcore.network.TeleportStoneInterruptPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 传送物品蓄力打断处理器：玩家在蓄力打开传送列表的过程中受到伤害时中断蓄力。
 * <p>
 * 共用机制：传送石（{@link TeleportStoneItem}）和传送卷轴（{@link WarpScrollItem}）
 * 都使用 5 秒蓄力 + 弓动画 + 「蓄满才打开列表」的流程，因此本处理器通过组合判断同时覆盖这两类物品。
 * <p>
 * 监听 {@link ServerLivingEntityEvents#AFTER_DAMAGE}，只处理「正在使用传送石/卷轴」的玩家：
 * <ul>
 *   <li>服务端调用 {@code stopUsingItem()} 清除使用状态，顺带触发各自物品的
 *       {@code releaseUsing} 播放中断音效</li>
 *   <li>额外发送 {@link TeleportStoneInterruptPayload}，让客户端同步停止蓄力表现，
 *       否则客户端会继续举手直到玩家自己松开右键——该数据包与具体物品无关，只通知
 *       「停止当前使用物品的客户端表现」</li>
 *   <li>动作栏提示玩家蓄力被打断（共用 {@link TeleportStoneItem#CHARGE_INTERRUPTED_KEY} 翻译键，
 *       提示文本两种物品完全一致，避免重复翻译）</li>
 * </ul>
 * 中断只是停止使用物品，不会记录冷却、不消耗耐久/卷轴——冷却与代价只在传送真正成功时结算。
 */
public final class TeleportStoneChargeHandler {

    private TeleportStoneChargeHandler() {
    }

    /**
     * 向 Fabric 事件总线注册受伤打断监听。
     */
    public static void register() {
        DebugLogger.entering("TeleportStoneChargeHandler", "register");

        ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (entity, source, baseDamageTaken, damageTaken, blocked) -> {
                    if (!(entity instanceof ServerPlayer player)) {
                        return;
                    }
                    // 同时识别两类物品：正在使用传送石或传送卷轴时，受伤均触发蓄力中断
                    if (!player.isUsingItem()) {
                        return;
                    }
                    var usedItem = player.getUseItem().getItem();
                    boolean usingTeleportItem =
                            usedItem instanceof TeleportStoneItem || usedItem instanceof WarpScrollItem;
                    if (!usingTeleportItem) {
                        return;
                    }

                    // 停止使用 → 触发 releaseUsing（中断音效），不进入冷却
                    player.stopUsingItem();
                    ServerPlayNetworking.send(player, new TeleportStoneInterruptPayload());
                    TeleportStoneItem.sendActionBar(player,
                            Component.translatable(TeleportStoneItem.CHARGE_INTERRUPTED_KEY)
                                    .withStyle(ChatFormatting.RED));

                    DebugLogger.info("TeleportStoneChargeHandler",
                            "玩家 %s 受到伤害，传送物品蓄力被打断（物品=%s）",
                            player.getName().getString(), usedItem);
                });

        DebugLogger.info("TeleportStoneChargeHandler",
                "传送物品蓄力打断处理器已注册 (ServerLivingEntityEvents.AFTER_DAMAGE)");
        DebugLogger.exiting("TeleportStoneChargeHandler", "register");
    }
}
