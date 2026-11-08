package top.csituka.youzaiworldcore.mixin.client;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问窗口关闭标志。
 * <p>
 * 26.3 的关闭标志由 SDL 事件写入 {@code Window.shouldClose} 字段，原版没有清除入口；
 * 退出确认弹窗需要在玩家取消后让窗口继续运行，因此通过访问器清除。
 */
@Mixin(Window.class)
public interface WindowAccessor {

    @Accessor("shouldClose")
    void youzaiworldcore$setShouldClose(boolean shouldClose);
}
