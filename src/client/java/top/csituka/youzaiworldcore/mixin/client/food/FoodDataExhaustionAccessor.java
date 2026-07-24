package top.csituka.youzaiworldcore.mixin.client.food;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link FoodData#exhaustionLevel} 字段供 HUD 渲染使用。
 *
 * <p>26.2 中 exhaustionLevel 为 private 字段，YZUI 双条中的饥饿值条
 * 需要读取该值以绘制消耗度（灰色网格）叠加层。</p>
 */
@Mixin(FoodData.class)
public interface FoodDataExhaustionAccessor {

    /** @return 当前消耗度等级（0.0 ~ 4.0，满 4.0 触发一次饥饿tick） */
    @Accessor("exhaustionLevel")
    float youzaiworldcore$getExhaustionLevel();
}
