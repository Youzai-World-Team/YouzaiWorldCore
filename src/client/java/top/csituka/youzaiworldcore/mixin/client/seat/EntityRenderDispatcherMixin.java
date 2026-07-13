package top.csituka.youzaiworldcore.mixin.client.seat;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.entity.seat.ModSeatEntities;

/**
 * 为 SeatEntity 提供非 {@code null} 的渲染器。
 * <p>
 * Fabric {@code EntityRendererRegistry} 在 26.2 重构后的管线中无法生效，
 * 此处直接拦截 {@code getRenderer(Entity)}：若匹配到 SeatEntity 且返回值为
 * {@code null}，则从已有渲染器映射中选一个安全的占位渲染器。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Accessor("renderers")
    abstract Map<EntityType<?>, EntityRenderer<?, ?>> getRenderers();

    @Inject(
            method = "getRenderer(Lnet/minecraft/world/entity/Entity;)"
                    + "Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void fallbackRendererForSeat(Entity entity,
            CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        if (entity.getType() == ModSeatEntities.SEAT
                && cir.getReturnValue() == null) {
            cir.setReturnValue(getFallbackRenderer());
        }
    }

    /**
     * 拦截 EntityRenderState 重载——submit 阶段使用此路径查找渲染器。
     */
    @Inject(
            method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)"
                    + "Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void fallbackRendererForSeatState(EntityRenderState state,
            CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        if (state.entityType == ModSeatEntities.SEAT
                && cir.getReturnValue() == null) {
            cir.setReturnValue(getFallbackRenderer());
        }
    }

    private EntityRenderer<?, ?> getFallbackRenderer() {
        EntityType<?> fallbackType = BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "area_effect_cloud"));
        return fallbackType != null ? getRenderers().get(fallbackType) : null;
    }
}
