package top.csituka.youzaiworldcore.mixin.client.laowumeme;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.animal.feline.AdultFelineModel;
import net.minecraft.client.model.animal.feline.BabyFelineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.client.accessor.LaowuStateAccess;

/**
 * 在猫模型 setupAnim 的 TAIL 设 head.zRot（绕 Z 轴 roll = 歪头杀）。
 * <p>
 * 必须放 TAIL：{@code AdultFelineModel.setupAnim} 自身会读写 head.zRot，
 * 只有在它之后写入，歪头角度才会保留到顶点提交。
 * </p>
 * <p>
 * 取模型用 {@code this}（本 mixin 注入到模型类，{@code this} 就是模型），不需要
 * @Shadow 字段（26.1 mojmap 构建无 refmap，@Shadow vanilla 字段必崩黑屏）。
 * 歪头方向从 {@code CatRenderState} 经 {@link LaowuStateAccess} 读取
 * （extractRenderState 写入）。静态 45° 歪头 + 低头 + 微弓背 + 翘尾尖 + 腿形变，
 * 按需求不实现连续旋转动画。
 * </p>
 */
@SuppressWarnings("null")
@Mixin({ AdultFelineModel.class, BabyFelineModel.class })
public abstract class CatModelLaowuMixin {

    /** 歪头角度：45°（设计稿要求），roll 为 ±1，相乘得镜像歪头 */
    private static final float HEAD_ROLL = (float) (Math.PI / 4.0);
    /** 弓背哈气：头下低（绕 X 轴，正值=头端朝下、低头哈气），叠加在 setupAnim 原动画上 */
    private static final float HEAD_DIP = 0.42f;
    /** 弓背哈气：身体微弓（绕 X 轴，正值=头低尾高）。猫模型 body 是单一部件、腿不随 body 旋转，
     *  角度过大会撕裂腿/头与身体的连接（历史教训：0.18 致后腿插地+前后腿与身体断开），
     *  故保持在 0.14 的温和值，"哈气感"主要由 HEAD_DIP（低头）+ BODY_SCALE_Y（拱背拉伸）+ 翘尾承担。 */
    private static final float BODY_PITCH = 0.14f;
    /** 拱桥式弓背：body 竖直拉伸（yScale，以 body pivot 为中心、沿世界 Y 向上伸展）。
     *  26.2 猫模型 body 为单一 cube（4×6×16，绕 X 轴旋转 90° 挂 root，javap 核实），无法分段弯曲，
     *  故用 yScale 把背部整体向上顶高 + BODY_PITCH 前端下压 + HEAD_DIP 低头 + TAIL_LIFT 翘尾，
     *  组合出"背部呈拱桥状向上弯曲"的视觉。1.35 为经验值，可按需微调（过大会导致身体与腿脱离）。 */
    private static final float BODY_SCALE_Y = 1.35f;
    /** 弓背哈气：尾巴翘起。26.x 猫模型 tail1/tail2 平级挂 root（javap 核实），
     *  旋转 tail1 时 tail2 不跟随 → 两节必脱节。故 tail1 保持不动（只受原版动画），
     *  只翘 tail2：tail2 根部天然落在 tail1 末端初始位置，tail1 不转则末端不动 → 两节严丝合缝。 */
    private static final float TAIL_LIFT = 1.05f;
    /** 腿形变（yScale）：身体弓起时腿不跟随，腿长度补偿身体位移。
     *  后腿 1.55：拉长（臀部抬高后脚尖贴地）；前腿 0.8：缩短（前段下压、消除穿模）。
     *  yScale 不被原版 setupAnim 重置、且模型实例被所有猫共享，故非整活时四条腿 yScale 全部复位到 1.0。 */
    private static final float HIND_SCALE = 1.55f;
    private static final float FRONT_SCALE = 0.8f;
    private static final float LEG_SCALE_DEFAULT = 1.0f;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/FelineRenderState;)V",
            at = @At("TAIL"), require = 0)
    private void youzaiworldcore$laowuTilt(FelineRenderState state, CallbackInfo ci) {
        if (!(state instanceof LaowuStateAccess a)) {
            return;
        }
        try {
            // this 在编译期是 mixin 类；转型到 Model 取 root()（Model.root() 为 public final，
            // 返回非空字段，26.2 jar javap 核实；整个方法体在 try/catch 兜底内）。
            ModelPart root = ((Model<?>) (Object) this).root();

            // 四条腿部件（平级挂 root，javap 核实）。先取到手，下面按是否整活决定形变或复位。
            ModelPart leftHind = root.getChild("left_hind_leg");
            ModelPart rightHind = root.getChild("right_hind_leg");
            ModelPart leftFront = root.getChild("left_front_leg");
            ModelPart rightFront = root.getChild("right_front_leg");

            if (!a.laowuIsActive()) {
                // 还原：腿形变只在整活时生效。yScale 不被原版 setupAnim 重置、
                // 且模型实例被所有猫共享，不复位会让形变永久残留（所有猫腿/身体都变样）。
                // 四条腿 + body 的 yScale 全部复位。
                if (leftHind != null) {
                    leftHind.yScale = LEG_SCALE_DEFAULT;
                }
                if (rightHind != null) {
                    rightHind.yScale = LEG_SCALE_DEFAULT;
                }
                if (leftFront != null) {
                    leftFront.yScale = LEG_SCALE_DEFAULT;
                }
                if (rightFront != null) {
                    rightFront.yScale = LEG_SCALE_DEFAULT;
                }
                ModelPart bodyReset = root.getChild("body");
                if (bodyReset != null) {
                    bodyReset.yScale = LEG_SCALE_DEFAULT;
                }
                return;
            }

            // 头部：歪头（绕 Z 轴 roll，镜像）+ 下低（绕 X 轴，低头哈气）。头不与腿相连，旋转头不会撕裂肢体。
            ModelPart head = root.getChild("head");
            if (head != null) {
                head.zRot = a.laowuGetRoll() * HEAD_ROLL;
                head.xRot += HEAD_DIP;
            }

            // 拱桥式弓背：body 竖直拉伸（yScale）+ 微弓（xRot）。
            // 方向：xRot 绕 X 轴正值 = 头端下沉、尾端上翘；yScale 以 pivot 为中心沿世界 Y 向上顶高背部。
            // 模型只有单一 body 部件、腿不随 body 旋转，body 大幅旋转会撕裂腿/头连接，
            // 故 BODY_PITCH 保持温和，拱形主要由 BODY_SCALE_Y（背部上拱）+ HEAD_DIP（低头）+ 翘尾组合呈现。
            ModelPart body = root.getChild("body");
            if (body != null) {
                body.xRot += BODY_PITCH;
                body.yScale = BODY_SCALE_Y;
            }

            // 尾巴：tail1 保持不动（只受原版动画），只翘 tail2 尾尖。
            // 26.x 猫模型 tail1/tail2 平级挂 root，tail1 不转则 tail2 根部与 tail1 末端始终对齐 → 两节严丝合缝。
            ModelPart tail2 = root.getChild("tail2");
            if (tail2 != null) {
                tail2.xRot += TAIL_LIFT;
            }

            // 后脚拉长：身体弓起后臀部抬高，用 yScale 把后脚向上拉长（脚尖贴地、膝/髋端上抬衔接身体），
            // 消除"后脚与身体断开"。仅改 yScale，脚宽/深度不变，脚的位置不动（不浮空）。
            if (leftHind != null) {
                leftHind.yScale = HIND_SCALE;
            }
            if (rightHind != null) {
                rightHind.yScale = HIND_SCALE;
            }

            // 前脚缩短：身体弓起后前段下压，前脚髋端需随之下压去贴合身体、消除"穿模"。
            // 前脚 cube 自 foot 向上 10 单位，FRONT_SCALE<1 把髋端下压（脚尖仍贴地）。
            if (leftFront != null) {
                leftFront.yScale = FRONT_SCALE;
            }
            if (rightFront != null) {
                rightFront.yScale = FRONT_SCALE;
            }
        } catch (Throwable t) {
            // 静默兜底，绝不崩渲染器
        }
    }
}
