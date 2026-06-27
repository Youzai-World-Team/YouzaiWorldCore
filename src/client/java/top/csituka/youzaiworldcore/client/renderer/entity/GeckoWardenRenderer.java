package top.csituka.youzaiworldcore.client.renderer.entity;

import com.geckolib.renderer.GeoReplacedEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.monster.warden.Warden;
import top.csituka.youzaiworldcore.client.renderer.entity.layer.BioluminescentLayer;
import top.csituka.youzaiworldcore.client.renderer.entity.layer.Spots1Layer;
import top.csituka.youzaiworldcore.client.renderer.entity.layer.Spots2Layer;
import top.csituka.youzaiworldcore.client.renderer.entity.layer.TendrilsLayer;
import top.csituka.youzaiworldcore.client.renderer.entity.layer.HeartLayer;
import top.csituka.youzaiworldcore.client.renderer.entity.model.ChickenWardenModel;
import net.minecraft.client.renderer.entity.state.WardenRenderState;

/**
 * GeckoLib 渲染器—仅用于鸡模型渲染，不涉及特征切换。
 */
public class GeckoWardenRenderer extends GeoReplacedEntityRenderer<ChickenWardenAnimatable, Warden, WardenRenderState> {

    private static final ChickenWardenAnimatable ANIMATABLE = new ChickenWardenAnimatable();

    public GeckoWardenRenderer(EntityRendererProvider.Context context) {
        super(context, new ChickenWardenModel(), ANIMATABLE);

        withRenderLayer(new BioluminescentLayer(this));
        withRenderLayer(new Spots1Layer(this));
        withRenderLayer(new Spots2Layer(this));
        withRenderLayer(new TendrilsLayer(this));
        withRenderLayer(new HeartLayer(this));
    }

    @Override
    public WardenRenderState createRenderState() {
        return new WardenRenderState();
    }
}
