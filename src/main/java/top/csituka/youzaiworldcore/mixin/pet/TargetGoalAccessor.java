package top.csituka.youzaiworldcore.mixin.pet;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor — 暴露 {@link TargetGoal#mob} 字段供子类 Mixin 使用。
 * <p>
 * {@code mob} 字段声明在父类 {@code TargetGoal} 中，子类 {@code HurtByTargetGoal} 通过继承获得，
 * 但 Mixin {@code @Shadow} 无法直接引用父类声明的字段。需要通过 Accessor 间接访问。
 * </p>
 */
@Mixin(TargetGoal.class)
public interface TargetGoalAccessor {

    @Accessor("mob")
    Mob getMob();
}
