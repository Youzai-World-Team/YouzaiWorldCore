package top.csituka.youzaiworldcore.mixin.painting;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

import top.csituka.youzaiworldcore.item.MemePaintingItem;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 覆盖 {@link Painting} 的掉落逻辑，使自定义 meme 画被破坏时 drop 专用物品。
 *
 * <p>
 * 原版 {@code Painting.dropItem} 和 {@code Painting.getPickResult}
 * 硬编码返回 {@link Items#PAINTING}。本 Mixin 在销毁画时检测当前画是否使用了
 * {@link MemePaintingItem#VARIANT_ITEM_MAP} 中注册的自定义变体，若匹配则掉落
 * 对应的专用物品。
 * </p>
 *
 * <p>
 * 26.2: {@link Holder#unwrapKey()} 替代旧版 {@code getKey()}，
 * 返回 {@link Optional}{@code <ResourceKey<T>>}。
 * </p>
 *
 * @see MemePaintingItem
 */
@SuppressWarnings("null")
@Mixin(Painting.class)
public class MemePaintingDropMixin {

    /**
     * 替换 {@code dropItem}：若当前变体是自定义 meme 画，drop {@link MemePaintingItem}。
     */
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void onDropItem(ServerLevel level, Entity entity, CallbackInfo ci) {
        Painting self = (Painting) (Object) this;
        Holder<PaintingVariant> variant = self.getVariant();
        Optional<ResourceKey<PaintingVariant>> keyOpt = variant.unwrapKey();

        if (keyOpt.isEmpty()) {
            return;
        }

        ResourceKey<PaintingVariant> key = keyOpt.get();
        Item customItem = MemePaintingItem.VARIANT_ITEM_MAP.get(key);
        if (customItem == null) {
            return; // 不是自定义画，保持原版逻辑
        }

        DebugLogger.debug("MemePaintingDropMixin",
                "自定义画被破坏，掉落专用物品: variant=%s".formatted(key.identifier()));

        // 播放破坏音效
        self.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);

        // 检查 doEntityDrops 规则
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            self.spawnAtLocation(level, customItem.getDefaultInstance(), 0.0F);
        }

        ci.cancel();
    }

    /**
     * 替换 {@code getPickResult}：中键拾取返回专用物品。
     */
    @Inject(method = "getPickResult", at = @At("HEAD"), cancellable = true)
    private void onGetPickResult(CallbackInfoReturnable<ItemStack> cir) {
        Painting self = (Painting) (Object) this;
        Holder<PaintingVariant> variant = self.getVariant();
        Optional<ResourceKey<PaintingVariant>> keyOpt = variant.unwrapKey();

        if (keyOpt.isEmpty()) {
            return;
        }

        ResourceKey<PaintingVariant> key = keyOpt.get();
        Item customItem = MemePaintingItem.VARIANT_ITEM_MAP.get(key);
        if (customItem == null) {
            return;
        }

        DebugLogger.debug("MemePaintingDropMixin",
                "中键拾取自定义画: variant=%s -> item=%s".formatted(
                        key.identifier(),
                        customItem));

        cir.setReturnValue(new ItemStack(customItem));
    }
}
