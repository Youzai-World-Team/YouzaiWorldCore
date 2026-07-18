package top.csituka.youzaiworldcore.mixin.chargedcreeper;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link Creeper} 的私有静态实体数据访问器 {@code DATA_IS_POWERED}，
 * 用于在不依赖外部库（如 Collective）的前提下将苦力怕标记为"带电"状态。
 * <p>
 * 说明：26.2 的 {@link Creeper} 已移除 public 的 {@code setPowered(boolean)} 方法，
 * 带电状态改由 {@code SynchedEntityData} 中的 {@code DATA_IS_POWERED} 存取，
 * 直接写入实体数据可保证服务端状态正确同步到客户端（闪电光环等渲染）。
 * </p>
 */
@Mixin(Creeper.class)
public interface CreeperChargedAccessor {

    @Accessor("DATA_IS_POWERED")
    EntityDataAccessor<Boolean> youzaiworldcore$getDataIsPowered();
}
