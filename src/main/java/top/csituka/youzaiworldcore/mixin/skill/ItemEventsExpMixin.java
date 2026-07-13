package top.csituka.youzaiworldcore.mixin.skill;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.skill.AdventureLevelManager;

/**
 * 物品事件 → 冒险经验。
 * 合成 / 物品耐久耗尽 / 拾取 / 丢弃 / 投掷 / 烟花燃放
 */
@Mixin(ServerPlayer.class)
public class ItemEventsExpMixin {

    // ─── 合成物品 ───
    // 抓取任何通过配方系统产出物品的路径
    @Inject(method = "awardTradeRecipes", at = @At("TAIL"))
    private void onRecipeCrafted(CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AdventureLevelManager.grantExp(self, AdventureLevelManager.EXP_CRAFT_ITEM);
    }

    // ─── 工具 / 武器耐久耗尽 ───
    @Inject(method = "breakItem", at = @At("HEAD"))
    private void onBreakItem(ItemStack stack, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AdventureLevelManager.grantExp(self, AdventureLevelManager.EXP_ITEM_BREAK);
    }

    // ─── 拾取堆叠物品 ───
    @Inject(method = "take", at = @At("TAIL"))
    private void onTakeItem(ItemEntity itemEntity, int count, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AdventureLevelManager.incrementPickupCounter(self);
    }

    // ─── 丢弃物品 ───
    @Inject(method = "drop(Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("RETURN"))
    private void onDropItem(boolean dropAll, CallbackInfoReturnable<ItemEntity> cir) {
        if (cir.getReturnValue() != null) {
            ServerPlayer self = (ServerPlayer) (Object) this;
            AdventureLevelManager.incrementDropCounter(self);
        }
    }

    // ─── 投掷物品（Q 键） ───
    @Inject(method = "drop(Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"))
    private void onThrowItem(CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        AdventureLevelManager.grantExp(self, AdventureLevelManager.EXP_THROW_ITEM);
    }

    // ─── 烟花燃放 ───
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void onFireworkCheck(InteractionHand hand, boolean b, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        ItemStack stack = self.getItemInHand(hand);
        if (stack.is(Items.FIREWORK_ROCKET) && self.isFallFlying()) {
            // 鞘翅飞行中燃放烟花
        }
    }
}
