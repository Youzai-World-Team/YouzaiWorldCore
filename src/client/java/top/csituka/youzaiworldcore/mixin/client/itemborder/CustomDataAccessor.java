package top.csituka.youzaiworldcore.mixin.client.itemborder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link CustomData} 内部 NBT 的<b>只读</b>访问入口。
 *
 * <p>
 * 26.2 的 {@code CustomData} 只提供 {@link CustomData#copyTag()}，而它是整棵 NBT 树的
 * <b>深拷贝</b>。物品边框渲染需要读取自定义标签 {@code yzwc_border_colors}，
 * 该读取发生在「每个带 CUSTOM_DATA 的物品、每帧」的路径上（创造物品栏一屏 100+ 槽位），
 * 为两次只读查询付出一次深拷贝的代价完全没有必要。
 * </p>
 *
 * <p>
 * <b>使用约定：</b>通过本接口取得的 {@link CompoundTag} 是组件持有的实例本身，
 * {@code CustomData} 依赖它的不可变性（{@code hashCode} 已缓存、组件间可共享），
 * 因此<b>只允许读取，绝不可写入</b>。需要修改时请使用
 * {@link CustomData#update(java.util.function.Consumer)}。
 * </p>
 */
@Mixin(CustomData.class)
public interface CustomDataAccessor {

    /** 直接返回内部 NBT，不做拷贝。仅供只读使用。 */
    @Accessor("tag")
    CompoundTag yzwc$getTag();
}
