package top.csituka.youzaiworldcore.client.accessor;

/**
 * 让 {@code CatRenderState} 通过 mixin 携带「老吴贴贴」的渲染状态。
 * <p>
 * {@code CatRenderStateLaowuMixin} 实现本接口并加 {@code @Unique} 字段；同一
 * {@code CatRenderState} 实例从 {@code CatRendererLaowuMixin.extractRenderState}
 * （有 {@code Cat} 实体，能取 id/roll）流到 {@code CatModelLaowuMixin.setupAnim}
 * （只有 state，无 id），靠本接口在两者间传递 active/roll。
 * </p>
 * <p>
 * 注意：此接口位于 Mixin 保护包之外，允许非 Mixin 类直接引用（Mixin 0.8.7 的
 * {@code IllegalClassLoadError} 问题，同 {@code RenderCrownDuck} 的处理方式）。
 * </p>
 */
public interface LaowuStateAccess {

    boolean laowuIsActive();

    float laowuGetRoll();

    void laowuSetActive(boolean v);

    void laowuSetRoll(float v);
}
