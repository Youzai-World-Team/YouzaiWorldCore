package top.csituka.youzaiworldcore.mixin;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketInventory;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin 注入 {@code net.p3pp3rf1y.sophisticatedbackpacks.client.KeybindHandler#sendBackpackOpenOrCloseMessage()},
 * 在其库存扫描之外额外检查 Trinket 饰品槽中是否有精妙背包。
 * 若饰品槽中有背包，则发送 {@code BackpackOpenPayload} 让服务端打开该背包。
 */
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.client.KeybindHandler")
public class BackpackTrinketMixin {

    @Unique
    private static final String BACKPACK_PAYLOAD_CLASS =
            "net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenPayload";
    @Unique
    private static final String CLIENT_DISTRIBUTOR =
            "net.neoforged.neoforge.client.network.ClientPacketDistributor";

    @Inject(
            method = "sendBackpackOpenOrCloseMessage",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void yzwc$checkTrinketsForBackpack(CallbackInfoReturnable<Boolean> cir) {
        // 原方法已找到背包 → 无需干预
        if (cir.getReturnValueZ()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        try {
            TrinketAttachment attachment = TrinketsApi.getAttachment(player);
            if (attachment == null) return;

            for (TrinketInventory inv : attachment.getInventories().values()) {
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (!inv.isVisible(i)) continue;
                    ItemStack stack = inv.getItem(i);
                    if (stack.isEmpty()) continue;

                    // 检测是否精妙背包
                    Class<?> backpackItemClass = Class.forName(
                            "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem");
                    if (backpackItemClass.isInstance(stack.getItem())) {
                        // 发送 BackpackOpenPayload 到服务端，让服务端打开背包
                        Class<?> payloadClass = Class.forName(BACKPACK_PAYLOAD_CLASS);
                        Object payload = payloadClass.getConstructor().newInstance();

                        Class<?> distributorClass = Class.forName(CLIENT_DISTRIBUTOR);
                        // ClientPacketDistributor.sendToServer(CustomPacketPayload)
                        distributorClass.getMethod("sendToServer",
                                        net.minecraft.network.protocol.common.custom.CustomPacketPayload.class)
                                .invoke(null, payload);

                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // 反射失败 = 精妙背包模组未加载或 API 变更，静默忽略
        }
    }
}
