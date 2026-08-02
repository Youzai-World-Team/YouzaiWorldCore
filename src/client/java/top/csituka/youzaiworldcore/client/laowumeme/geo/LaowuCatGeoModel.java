package top.csituka.youzaiworldcore.client.laowumeme.geo;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/**
 * 猫的 GeckoLib 模型定位器。
 * <p>
 * {@link DefaultedEntityGeoModel} 会按传入 id 自动推导资源路径，本模组即：
 * <ul>
 *   <li>模型：{@code assets/youzaiworldcore/geckolib/models/entity/cat.geo.json}</li>
 *   <li>动画：{@code assets/youzaiworldcore/geckolib/animations/entity/cat.animation.json}</li>
 * </ul>
 * </p>
 * <p>
 * 贴图<b>不走</b>默认推导：按需求全部复用原版猫贴图。原版贴图是按 variant（虎斑/黑猫/暹罗…）
 * 与是否幼年分别解析的，故由渲染器在提取渲染状态时写入 {@link LaowuCatAnimatable#CAT_TEXTURE}，
 * 这里取出即可。美术给的 cat.geo.json 声明的是 64×32 UV，与原版猫贴图布局一致，可直接套用。
 * </p>
 */
@SuppressWarnings("null")
public final class LaowuCatGeoModel extends DefaultedEntityGeoModel<LaowuCatAnimatable> {

    /** 贴图兜底：极端情况下 CAT_TEXTURE 未写入时用虎斑，避免渲染成黑紫丢失材质 */
    private static final Identifier FALLBACK_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/cat/tabby.png");

    public LaowuCatGeoModel() {
        super(Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "cat"));
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return renderState.getOrDefaultGeckolibData(LaowuCatAnimatable.CAT_TEXTURE, FALLBACK_TEXTURE);
    }
}
