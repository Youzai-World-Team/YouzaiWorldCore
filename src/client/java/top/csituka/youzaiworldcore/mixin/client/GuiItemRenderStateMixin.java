package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.csituka.youzaiworldcore.client.hud.YzHudItemOpacityAccess;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/**
 * 保存提取时的页面/弹窗透明度，并与显式设置的 YZHUD 图标透明度相乘。
 */
@Mixin(GuiItemRenderState.class)
public abstract class GuiItemRenderStateMixin implements YzHudItemOpacityAccess {

    @Unique
    private float youzaiworldcore$opacity = GuiAnimationController.contentOpacity();

    @Override
    public void youzaiworldcore$setOpacity(float opacity) {
        youzaiworldcore$opacity = Math.clamp(opacity, 0.0F, 1.0F) * GuiAnimationController.contentOpacity();
    }

    @Override
    public float youzaiworldcore$getOpacity() {
        return youzaiworldcore$opacity;
    }
}
