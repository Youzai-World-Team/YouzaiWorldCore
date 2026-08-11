package top.csituka.youzaiworldcore.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.item.ModItems;
import top.csituka.youzaiworldcore.respawn.DeferredDeathDropAccess;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnPlayerAccess;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.TrinketHelper;

/**
 * 混合注入 {@link Player} 的死亡掉落逻辑。
 * <p>
 * 守护之心继续阻止原版掉落；启用原地重生时先暂缓物品与经验掉落，
 * 待玩家选择普通重生后再补做掉落，从而允许原地重生安全保留物品栏。
 */
@Mixin(Player.class)
public abstract class PlayerDropEquipmentMixin implements DeferredDeathDropAccess {

    @Shadow
    protected abstract void dropEquipment(ServerLevel level);

    @Unique
    private boolean youzaiworldcore$forceDeferredDrop;

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$onDropEquipment(ServerLevel level, CallbackInfo ci) {
        if (level.isClientSide() || youzaiworldcore$forceDeferredDrop) {
            return;
        }

        Player player = (Player) (Object) this;
        boolean deferredForRespawn = player instanceof ServerPlayer
                && player instanceof InPlaceRespawnPlayerAccess access
                && access.youzaiworldcore$isInPlaceRespawnEnabled();
        if (deferredForRespawn || hasHeartInInventory(player)) {
            ci.cancel();
        }
    }

    /**
     * 原地重生启用时先禁止生成死亡经验球；若最终选择普通重生且无守护之心，
     * 由服务端重生 Mixin 按原版公式补发。
     */
    @Inject(method = "getBaseExperienceReward", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$deferExperienceReward(ServerLevel level,
                                                        CallbackInfoReturnable<Integer> cir) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer
                && player instanceof InPlaceRespawnPlayerAccess access
                && access.youzaiworldcore$isInPlaceRespawnEnabled()) {
            cir.setReturnValue(0);
        }
    }

    @Override
    public void youzaiworldcore$dropDeferredEquipment(ServerLevel level) {
        DebugLogger.entering("InPlaceRespawn", "dropDeferredEquipment");
        youzaiworldcore$forceDeferredDrop = true;
        try {
            dropEquipment(level);
        } finally {
            youzaiworldcore$forceDeferredDrop = false;
            DebugLogger.exiting("InPlaceRespawn", "dropDeferredEquipment");
        }
    }

    /**
     * 遍历玩家背包（含快捷栏、主背包、盔甲栏、副手）以及 Trinkets 饰品槽，
     * 检查是否至少有一个守护之心。
     */
    @Unique
    private static boolean hasHeartInInventory(Player player) {
        // 检查饰品槽
        if (TrinketHelper.isLoaded() && TrinketHelper.isItemEquipped(player, ModItems.HEART_OF_GUARDIANSHIP)) {
            return true;
        }
        // 检查背包
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.HEART_OF_GUARDIANSHIP) {
                return true;
            }
        }
        return false;
    }
}
