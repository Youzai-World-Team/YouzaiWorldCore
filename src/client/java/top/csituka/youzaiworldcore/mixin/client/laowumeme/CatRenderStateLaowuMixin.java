package top.csituka.youzaiworldcore.mixin.client.laowumeme;

import net.minecraft.client.renderer.entity.state.CatRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.csituka.youzaiworldcore.client.accessor.LaowuStateAccess;

/**
 * 给 {@link CatRenderState} 加两个 @Unique 字段，承载「是否锁定 + 歪头方向」，
 * 由 {@link CatRendererLaowuMixin} 在 extractRenderState 写入，
 * 由 {@link CatModelLaowuMixin} 在 setupAnim(TAIL) 读取。
 * <p>
 * 同一 CatRenderState 实例从 extractRenderState 流到 setupAnim，故可靠传递，
 * 不需要 WeakHashMap 跨 mixin 桥（那套在 26.1 渲染管线里不可靠）。
 * </p>
 */
@Mixin(CatRenderState.class)
public abstract class CatRenderStateLaowuMixin implements LaowuStateAccess {

    @Unique
    public boolean laowuActive;

    @Unique
    public float laowuRoll;

    @Override
    public boolean laowuIsActive() {
        return laowuActive;
    }

    @Override
    public float laowuGetRoll() {
        return laowuRoll;
    }

    @Override
    public void laowuSetActive(boolean v) {
        laowuActive = v;
    }

    @Override
    public void laowuSetRoll(float v) {
        laowuRoll = v;
    }
}
