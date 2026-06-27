package top.csituka.youzaiworldcore.client.renderer.entity.model;

import com.geckolib.model.GeoModel;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.client.renderer.entity.ChickenWardenAnimatable;

/**
 * 监守者鸡模型
 * 加载基岩版 warden.geo.json（GeckoLib 5 原生支持基岩版 .geo.json 格式）
 */
public class ChickenWardenModel extends GeoModel<ChickenWardenAnimatable> {

    private static final Identifier MODEL_LOC = Identifier.parse(
            "youzaiworldcore:entity/warden"
    );
    private static final Identifier TEXTURE_LOC = Identifier.parse(
            "youzaiworldcore:textures/entity/warden/chicken.png"
    );
    private static final Identifier ANIMATION_LOC = Identifier.parse(
            "youzaiworldcore:entity/warden"
    );

    @Override
    public Identifier getModelResource(com.geckolib.renderer.base.GeoRenderState renderState) {
        return MODEL_LOC;
    }

    @Override
    public Identifier getTextureResource(com.geckolib.renderer.base.GeoRenderState renderState) {
        return TEXTURE_LOC;
    }

    @Override
    public Identifier getAnimationResource(ChickenWardenAnimatable animatable) {
        return ANIMATION_LOC;
    }
}
