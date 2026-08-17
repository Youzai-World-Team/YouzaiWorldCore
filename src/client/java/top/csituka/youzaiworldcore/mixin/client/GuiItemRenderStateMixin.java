package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.csituka.youzaiworldcore.client.hud.YzHudItemOpacityAccess;

/**
 * 给 GUI 物品渲染状态附加 YZHUD 专用透明度，不影响其他界面的物品图标。
 */
@Mixin(GuiItemRenderState.class)
public abstract class GuiItemRenderStateMixin implements YzHudItemOpacityAccess {

    @Unique
    private float youzaiworldcore$opacity = 1.0F;

    @Override
    public void youzaiworldcore$setOpacity(float opacity) {
        youzaiworldcore$opacity = Math.clamp(opacity, 0.0F, 1.0F);
    }

    @Override
    public float youzaiworldcore$getOpacity() {
        return youzaiworldcore$opacity;
    }
}
