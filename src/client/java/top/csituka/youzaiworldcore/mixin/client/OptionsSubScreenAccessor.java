package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link OptionsSubScreen} 的 {@code list} 字段，
 * 用于在 {@link AccessibilityOptionsScreenMixin} 中查找和移除选项按钮。
 */
@Mixin(OptionsSubScreen.class)
public interface OptionsSubScreenAccessor {

    @Accessor("list")
    OptionsList youzaiworldcore$getList();
}
