package top.csituka.youzaiworldcore.client.laowumeme.geo;

import com.geckolib.renderer.GeoReplacedEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.animal.feline.Cat;
import top.csituka.youzaiworldcore.client.laowumeme.LaowuMemeClientState;

/**
 * 用 GeckoLib 模型接管原版 {@code Cat} 的渲染。
 * <p>
 * {@link GeoReplacedEntityRenderer} 直接继承 {@code EntityRenderer}，注册后
 * <b>原版 {@code CatRenderer} 整体不再被调用</b>——它的 extractRenderState 也就不会执行。
 * 因此原本挂在 CatRenderer/CatModel 上的老吴 mixin 全部失效，其职责（放大、姿态、状态传递）
 * 都必须在本类里重新承担。
 * </p>
 * <p>
 * 泛型 {@code R extends CatRenderState & GeoRenderState}：GeckoLib 通过 mixin 让所有
 * {@code EntityRenderState} 在运行期实现 {@code GeoRenderState}，但编译期不可见，
 * 故用交集类型声明。沿用 {@code CatRenderState} 而不是 GeckoLib 默认的
 * {@code LivingEntityRenderState}，是为了拿到原版按 variant 解析好的贴图与坐姿等字段。
 * </p>
 */
@SuppressWarnings({ "null", "unchecked" })
public class LaowuCatRenderer<R extends CatRenderState & GeoRenderState>
        extends GeoReplacedEntityRenderer<LaowuCatAnimatable, Cat, R> {

    /** 对峙时的视觉放大倍率（仅渲染，不改碰撞箱），沿用原 CatRendererLaowuMixin 的 1.25 */
    private static final float LAOWU_SCALE = 1.25f;
    /**
     * 幼猫缩放。原版靠 {@code AgeableMobRenderer} 切换到另一套 {@code BabyFelineModel} 来表现幼年，
     * GeckoLib 这边只有一套 geo 模型，故改用整体缩放近似。
     * 注意：这只还原了"体型变小"，还原不了原版幼猫"头身比更大"的比例差异。
     */
    private static final float BABY_SCALE = 0.5f;

    public LaowuCatRenderer(EntityRendererProvider.Context context) {
        super(context, new LaowuCatGeoModel(), new LaowuCatAnimatable());
    }

    /**
     * GeckoLib 走的是这个两参版本，必须在这里返回 CatRenderState。
     * <p>
     * 父类实现直接 {@code new LivingEntityRenderState()}，<b>不会</b>回调无参的
     * {@code createRenderState()}——那个无参版本在 GeckoLib 里已被 @Deprecated 且实现为返回 null。
     * 无需覆写它：原版 {@code EntityRenderDispatcher} 只调用
     * {@code createRenderState(Entity, float)}（字节码核实），该方法在 GeckoLib 里是 final，
     * 内部转到本方法。
     * </p>
     */
    @Override
    public R createRenderState(LaowuCatAnimatable animatable, Cat cat) {
        return (R) new CatRenderState();
    }

    /**
     * 补齐原版 CatRenderer 会填、而 GeckoLib 不会填的猫专属字段。
     * 取值全部直接读实体（而不是读 state 上已填好的字段），因为
     * {@link #addRenderData} 是在 {@code super.extractRenderState} 内部被回调的，
     * 早于本方法后半段执行——直接读实体可避免这种时序耦合。
     */
    @Override
    public void extractRenderState(Cat cat, R state, float partialTick) {
        super.extractRenderState(cat, state, partialTick);

        state.texture = catTexture(cat);
        state.isCrouching = cat.isCrouching();
        state.isSprinting = cat.isSprinting();
        state.isSitting = cat.isInSittingPose();
        state.lieDownAmount = cat.getLieDownAmount(partialTick);
        state.lieDownAmountTail = cat.getLieDownAmountTail(partialTick);
        state.relaxStateOneAmount = cat.getRelaxStateOneAmount(partialTick);
        state.isLyingOnTopOfSleepingPlayer = cat.isLyingOnTopOfSleepingPlayer();
        state.collarColor = cat.isTame() ? cat.getCollarColor() : null;

        // scale 会被 GeckoLib 的 scaleModelForRender 读取并作用到模型上
        if (cat.isBaby()) {
            state.scale *= BABY_SCALE;
        }
        if (LaowuMemeClientState.get().isActive(cat.getId())) {
            state.scale *= LAOWU_SCALE;
        }
    }

    /** 把动画控制器需要的判定条件写进 render state，供 {@link LaowuCatAnimatable} 的控制器读取。 */
    @Override
    public void addRenderData(LaowuCatAnimatable animatable, Cat cat, R state, float partialTick) {
        super.addRenderData(animatable, cat, state, partialTick);

        state.addGeckolibData(LaowuCatAnimatable.CAT_TEXTURE, catTexture(cat));
        state.addGeckolibData(LaowuCatAnimatable.LAOWU_ACTIVE,
                LaowuMemeClientState.get().isActive(cat.getId()));
        state.addGeckolibData(LaowuCatAnimatable.CAT_SITTING, cat.isInSittingPose());
        state.addGeckolibData(LaowuCatAnimatable.CAT_CROUCHING, cat.isCrouching());
    }

    /** 原版猫贴图：按 variant（虎斑/黑猫/暹罗…）+ 是否幼年解析，与原版 CatRenderer 取法一致。 */
    private static Identifier catTexture(Cat cat) {
        return cat.getVariant().value().assetInfo(cat.isBaby()).texturePath();
    }
}
