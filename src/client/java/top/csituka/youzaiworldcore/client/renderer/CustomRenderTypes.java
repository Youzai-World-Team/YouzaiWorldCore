package top.csituka.youzaiworldcore.client.renderer;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.util.Util;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * 自定义 RenderType 工厂。
 * <p>
 * 26.2 没有公开的不透明+自发光实体管线（entity_translucent_emissive 使用 TRANSLUCENT 混合导致
 * 半透明），因此通过反射获取私有的 {@code ENTITY_EMISSIVE_SNIPPET} 骨架，构建一个
 * 使用 {@link ColorTargetState#DEFAULT}（不透明无混合）的自定义管线。
 */
public final class CustomRenderTypes {

    /** 缓存的 EMISSIVE_SNIPPET — 使用反射从 RenderPipelines 中提取，仅初始化一次 */
    private static final RenderPipeline.Snippet ENTITY_EMISSIVE_SNIPPET = loadEntityEmissiveSnippet();

    /** 为 tp_anchor 构建的 RenderPipeline — 基于 EMISSIVE_SNIPPET + OPAQUE 混合 + 禁用面剔除 */
    private static final RenderPipeline TP_ANCHOR_PIPELINE = RenderPipeline.builder(ENTITY_EMISSIVE_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("youzaiworldcore", "pipeline/tp_anchor"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withCull(false)
            .build();

    /**
     * tp_anchor 的 RenderType，按纹理标识符 memoized。
     * 每次调用传入相同纹理时返回同一实例。
     */
    public static final Function<Identifier, RenderType> TP_ANCHOR =
            Util.memoize(texture -> RenderType.create(
                    "tp_anchor_emissive",
                    RenderSetup.builder(TP_ANCHOR_PIPELINE)
                            .withTexture("Sampler0", texture)
                            .useOverlay()
                            .affectsCrumbling()
                            .sortOnUpload()
                            .createRenderSetup()
            ));

    private CustomRenderTypes() {}

    // ---- 反射辅助 ----

    @SuppressWarnings({"JavaLangInvokeHandleSignature", "unchecked"})
    private static RenderPipeline.Snippet loadEntityEmissiveSnippet() {
        try {
            Field field = RenderPipelines.class.getDeclaredField("ENTITY_EMISSIVE_SNIPPET");
            field.setAccessible(true);
            return (RenderPipeline.Snippet) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(
                    "Failed to reflect RenderPipelines.ENTITY_EMISSIVE_SNIPPET — "
                            + "was the field renamed in a newer Minecraft version?", e);
        }
    }
}
