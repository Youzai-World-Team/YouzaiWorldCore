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

/**
 * 监守者鸡自定义渲染器（GeckoLib 5 GeoReplacedEntityRenderer）
 * 将原版 Warden 实体替换渲染为鸡的外观
 * 实现6层渲染以还原生物发光/斑点/心脏等特效
 */
public class ChickenWardenRenderer extends GeoReplacedEntityRenderer<ChickenWardenAnimatable, Warden, net.minecraft.client.renderer.entity.state.WardenRenderState> {

    private static final ChickenWardenAnimatable ANIMATABLE = new ChickenWardenAnimatable();

    public ChickenWardenRenderer(EntityRendererProvider.Context context) {
        super(context, new ChickenWardenModel(), ANIMATABLE);

        // 注册多层渲染 Layer（还原基岩版 render_controllers 效果）
        withRenderLayer(new BioluminescentLayer(this));
        withRenderLayer(new Spots1Layer(this));
        withRenderLayer(new Spots2Layer(this));
        withRenderLayer(new TendrilsLayer(this));
        withRenderLayer(new HeartLayer(this));
    }
}
