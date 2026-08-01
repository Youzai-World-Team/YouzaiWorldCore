package top.csituka.youzaiworldcore.mixin.client.laowumeme;

import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.world.entity.animal.feline.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.accessor.LaowuStateAccess;
import top.csituka.youzaiworldcore.client.laowumeme.LaowuMemeClientState;

/**
 * 在 {@link CatRenderer} 提取渲染状态时：
 * <ol>
 *   <li>对锁定猫把 scale 放大 25%（仅渲染，不改碰撞箱）；</li>
 *   <li>把「是否锁定 + 歪头方向」写入 {@link CatRenderState}（经 {@link LaowuStateAccess}），
 *       供 {@link CatModelLaowuMixin} 在 setupAnim(TAIL) 读取并设 head.zRot。</li>
 * </ol>
 * <p>
 * 关键事实（MC 26.2 实测，字节码核实）：
 * <ul>
 *   <li>{@code AdultFelineModel.setupAnim} 会主动读写 head.zRot（不只是 xRot/yRot），
 *       所以必须在 setupAnim 的 TAIL 设 zRot，extractRenderState 阶段设会被覆盖；
 *       因此歪头逻辑搬到 {@code CatModelLaowuMixin.setupAnim(TAIL)}，本 mixin 只负责放大 + 写入状态。</li>
 *   <li>{@code scale} 字段位于父类 {@code LivingEntityRenderState}（public float scale）。</li>
 *   <li>用 {@code CatRenderState} 上的 {@code @Unique} 字段传递，同一实例从
 *       extractRenderState 流到 setupAnim，可靠、无需 @Shadow（26.1+ mojmap 无 refmap，
 *       @Shadow vanilla 字段必崩黑屏）。</li>
 * </ul>
 * </p>
 */
@Mixin(CatRenderer.class)
public abstract class CatRendererLaowuMixin {

    /** 整活时的视觉放大倍率（仅渲染，不改碰撞箱） */
    private static final float LAOWU_SCALE = 1.25f;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/feline/Cat;"
            + "Lnet/minecraft/client/renderer/entity/state/CatRenderState;F)V", at = @At("TAIL"))
    private void youzaiworldcore$laowuPopulate(Cat cat, CatRenderState state, float partialTick, CallbackInfo ci) {
        LaowuMemeClientState cs = LaowuMemeClientState.get();
        int id = cat.getId();
        boolean active = cs.isActive(id);
        float roll = cs.getRollSign(id);

        if (active) {
            state.scale *= LAOWU_SCALE;
        }

        // 把整活状态写入 render state，供模型层 setupAnim(TAIL) 读取设歪头。
        // CatRenderState 经 CatRenderStateLaowuMixin 实现 LaowuStateAccess。
        LaowuStateAccess a = (LaowuStateAccess) state;
        a.laowuSetActive(active);
        a.laowuSetRoll(roll);
    }
}
