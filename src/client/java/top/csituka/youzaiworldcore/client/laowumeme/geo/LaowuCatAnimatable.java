package top.csituka.youzaiworldcore.client.laowumeme.geo;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.resources.Identifier;

/**
 * GeckoLib 的「被替换实体」载体：原版 {@code Cat} 的动画驱动。
 * <p>
 * GeckoLib 的 replaced-entity 是<b>按 EntityType 整体接管渲染器</b>的，没有"只替换某一只猫"的开关，
 * 所以本类接管的是<b>所有猫</b>：正常状态跑 idle/sit/running/crouching，
 * 进入老吴对峙状态时切到 {@code ha_qi}（哈气）。这也正是美术给的 cat.animation.json 的组织方式
 * （同一份文件里既有正常态动画也有 ha_qi）。
 * </p>
 * <p>
 * 本类是<b>单例</b>：{@link com.geckolib.renderer.GeoReplacedEntityRenderer} 只持有一个 animatable 实例，
 * 每只猫的动画进度由 {@code getInstanceId()}（返回实体 id）分别缓存，故单例不会让所有猫动作同步。
 * </p>
 */
@SuppressWarnings("null")
public final class LaowuCatAnimatable implements GeoReplacedEntity {

    /** 该猫当前是否处于老吴对峙状态（由渲染器从 {@code LaowuMemeClientState} 读入） */
    public static final DataTicket<Boolean> LAOWU_ACTIVE =
            DataTicket.create("youzaiworldcore:laowu_active", Boolean.class);
    /** 原版坐下姿态 */
    public static final DataTicket<Boolean> CAT_SITTING =
            DataTicket.create("youzaiworldcore:cat_sitting", Boolean.class);
    /** 原版潜行/伏击姿态 */
    public static final DataTicket<Boolean> CAT_CROUCHING =
            DataTicket.create("youzaiworldcore:cat_crouching", Boolean.class);
    /** 原版猫花色贴图（按 variant + 是否幼年解析），供 {@link LaowuCatGeoModel} 取用 */
    public static final DataTicket<Identifier> CAT_TEXTURE =
            DataTicket.create("youzaiworldcore:cat_texture", Identifier.class);

    /** 对峙态：哈气。0.375s 非循环，播完保持在最终姿态（thenPlayAndHold），即"对峙定格" */
    private static final RawAnimation HA_QI = RawAnimation.begin().thenPlayAndHold("ha_qi");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation RUNNING = RawAnimation.begin().thenLoop("running");
    private static final RawAnimation CROUCHING = RawAnimation.begin().thenLoop("crouching");
    private static final RawAnimation CROUCHING_IDLE = RawAnimation.begin().thenLoop("crouching_idle");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    /** 姿态切换的过渡帧数：让 idle↔run↔sit↔ha_qi 之间平滑过渡，而不是硬切 */
    private static final int TRANSITION_TICKS = 5;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    /**
     * 单控制器设计：所有姿态互斥，由一条链决定当前该播哪个动画。
     * <p>
     * 用单控制器而非多控制器，是因为这些动画都作用于同一批骨骼（head/body/腿/尾），
     * 多控制器并行会互相覆盖同一骨骼、结果不可预测。
     * </p>
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<LaowuCatAnimatable>("pose", TRANSITION_TICKS, test -> {
            // 对峙优先级最高：一旦锁定就压过所有原版姿态
            if (test.getDataOrDefault(LAOWU_ACTIVE, false)) {
                return test.setAndContinue(HA_QI);
            }
            if (test.getDataOrDefault(CAT_SITTING, false)) {
                return test.setAndContinue(SIT);
            }
            if (test.getDataOrDefault(CAT_CROUCHING, false)) {
                return test.setAndContinue(test.isMoving() ? CROUCHING : CROUCHING_IDLE);
            }
            if (test.isMoving()) {
                return test.setAndContinue(RUNNING);
            }
            return test.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }
}
