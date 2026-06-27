package top.csituka.youzaiworldcore.client.renderer.entity;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;

/**
 * 监守者鸡动画对象
 * 作为 GeoReplacedEntityRenderer 的动画数据源，代表 Warden 的动画状态
 */
public class ChickenWardenAnimatable implements GeoAnimatable {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // 预定义动画定义
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenPlay("animation.warden.bob");
    private static final RawAnimation MOVE_ANIM = RawAnimation.begin().thenPlay("animation.warden.move");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 注册主动画控制器
        controllers.add(new AnimationController<>("warden_controller", 0, state -> {
            // 根据移动状态切换动画
            if (state.isMoving()) {
                state.setAnimation(MOVE_ANIM);
            } else {
                state.setAnimation(IDLE_ANIM);
            }
            return PlayState.CONTINUE;
        }));

        // 注册攻击动画控制器
        controllers.add(new AnimationController<>("attack_controller", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
