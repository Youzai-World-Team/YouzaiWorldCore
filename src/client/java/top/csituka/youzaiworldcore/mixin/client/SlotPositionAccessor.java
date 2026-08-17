package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 允许客户端 YZUI 屏幕调整槽位的显示与命中坐标。
 * <p>
 * 槽位编号、容器引用和服务端校验均不变，仅修改客户端菜单实例中的 {@code x/y}。
 */
@Mixin(Slot.class)
public interface SlotPositionAccessor {

    /** 设置槽位相对容器面板的横坐标。 */
    @Mutable
    @Accessor("x")
    void youzaiworldcore$setX(int x);

    /** 设置槽位相对容器面板的纵坐标。 */
    @Mutable
    @Accessor("y")
    void youzaiworldcore$setY(int y);
}
