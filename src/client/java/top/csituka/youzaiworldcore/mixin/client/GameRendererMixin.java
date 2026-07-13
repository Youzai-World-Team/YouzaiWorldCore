package top.csituka.youzaiworldcore.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.effect.TeleportFovEffect;

/**
 * 修改 {@link GameRenderer#renderLevel(net.minecraft.client.DeltaTracker)} 中的
 * 摄像机投影矩阵和 HUD FOV，在传送动画激活时对画面进行平滑缩放，
 * 实现传送前视野快速放大、传送后视野快速缩小的视觉效果。
 * <p>
 * 工作原理：
 * <ul>
 *   <li>在每帧渲染前（renderLevel HEAD），修改 CameraRenderState 中的
 *       projectionMatrix（影响 3D 世界渲染）和 hudFov（影响手部/叠加渲染）</li>
 *   <li>投影矩阵的 m00 和 m11 控制透视的水平和垂直缩放，
 *       乘以 zoomScale（= 1/fovModifier）实现放大/缩小</li>
 *   <li>由于 extract 阶段每帧会重新创建投影矩阵，此处直接修改是安全的</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private GameRenderState gameRenderState;

    /**
     * 在渲染世界前修改投影矩阵和 HUD FOV，实现传送缩放效果。
     * <p>
     * 传入 DeltaTracker 以获取 partialTick，传递给 FOV 管理器实现逐帧平滑插值。
     */
    @Inject(
            method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD")
    )
    private void youzaiworldcore$modifyProjectionForTeleport(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!TeleportFovEffect.isActive()) return;

        // 获取带 partialTick 插值的 FOV 倍率（逐帧平滑，无阶跃感）
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        float modifier = TeleportFovEffect.getFovModifier(partialTick);
        if (modifier >= 1.0f) return;

        CameraRenderState cameraState = this.gameRenderState.levelRenderState.cameraRenderState;
        if (cameraState == null || cameraState.projectionMatrix == null) return;

        // zoomScale = 1/modifier → modifier 越小，zoomScale 越大，画面越放大
        float zoomScale = 1.0f / modifier;

        // 修改投影矩阵的水平和垂直缩放因子
        // 透视投影矩阵中 m00 = 1/(aspect * tan(fov/2)), m11 = 1/tan(fov/2)
        // 将它们放大 = 缩小 FOV = 放大画面
        cameraState.projectionMatrix.m00(cameraState.projectionMatrix.m00() * zoomScale);
        cameraState.projectionMatrix.m11(cameraState.projectionMatrix.m11() * zoomScale);

        // 同步调整 hudFov 使手部/物品的渲染与世界一致
        cameraState.hudFov *= modifier;
    }
}
