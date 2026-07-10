package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * 访问 {@link Screen} 的私有/受保护字段，用于在 {@link TitleScreenMixin} 中
 * 操作组件列表和访问 Minecraft 实例。
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Accessor("renderables")
    List<Renderable> youzaiworldcore$getRenderables();

    @Accessor("narratables")
    List<NarratableEntry> youzaiworldcore$getNarratables();

    /** 需要读写 {@code children} 列表时使用；添加新组件也需同时加入三个列表 */
    @Accessor("children")
    List<net.minecraft.client.gui.components.events.GuiEventListener> youzaiworldcore$getChildren();

    /** {@code Minecraft} 实例，用于创建 DirectConnect 或 JoinMultiplayerScreen */
    @Accessor("minecraft")
    Minecraft youzaiworldcore$getMinecraft();

    /** {@link Font} 实例，用于在标题屏幕上绘制文本 */
    @Accessor("font")
    Font youzaiworldcore$getFont();

    /**
     * 触发重新布局，用于在替换 GridLayout 内部子元素后刷新位置。
     * <p>
     * 为什么需要 {@link Invoker} 而不是直接调用：{@code repositionElements} 是
     * {@link Screen} 的 {@code protected} 方法，从 Mixin 类中无法直接访问。
     */
    @Invoker("repositionElements")
    void youzaiworldcore$repositionElements();
}
