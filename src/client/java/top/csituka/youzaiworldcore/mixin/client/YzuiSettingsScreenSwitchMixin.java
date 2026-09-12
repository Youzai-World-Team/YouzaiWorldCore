package top.csituka.youzaiworldcore.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import top.csituka.youzaiworldcore.client.screen.options.YzuiSettingsRouter;

/** 先确定目标设置屏幕，再由已有动画系统处理切换；不修改 OptionsScreen 的页面实现。 */
@Mixin(Gui.class)
public abstract class YzuiSettingsScreenSwitchMixin {
    @WrapMethod(method = "setScreen")
    private void youzaiworldcore$routeSettings(Screen screen, Operation<Void> original) {
        // 外层包装保证动画的 HEAD 注入收到最终屏幕，不依赖不同 Mixin 的注入顺序。
        original.call(YzuiSettingsRouter.resolve(screen));
    }
}
