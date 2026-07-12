package top.csituka.youzaiworldcore.world;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import top.csituka.youzaiworldcore.YouzaiworldCore;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 将 {@code village_teleport_anchor_ruins.nbt} 注入到所有原版村庄的
 * {@code decor} 模板池中，使遗迹作为村庄装饰物自然生成。
 *
 * <p>使用反射访问 {@link StructureTemplatePool} 的私有字段
 * {@code rawTemplates} 和 {@code templates}，避免 access widener 在 IDE 中
 * 产生的可见性误报。</p>
 */
public class VillageStructureInjector {

    private static final String[] VILLAGE_TYPES = {"plains", "desert", "savanna", "snowy", "taiga"};
    private static final int WEIGHT = 5;

    /** {@link StructureTemplatePool#rawTemplates} 字段引用（反射） */
    private static final Field RAW_TEMPLATES_FIELD;
    /** {@link StructureTemplatePool#templates} 字段引用（反射） */
    private static final Field TEMPLATES_FIELD;

    static {
        try {
            RAW_TEMPLATES_FIELD = StructureTemplatePool.class.getDeclaredField("rawTemplates");
            RAW_TEMPLATES_FIELD.setAccessible(true);
            TEMPLATES_FIELD = StructureTemplatePool.class.getDeclaredField("templates");
            TEMPLATES_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("无法反射访问 StructureTemplatePool 字段，请检查 MC 版本兼容性", e);
        }
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Registry<StructureTemplatePool> registry =
                    server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);

            for (String type : VILLAGE_TYPES) {
                Identifier poolId = Identifier.withDefaultNamespace(
                        "village/" + type + "/decor");
                registry.getOptional(poolId).ifPresentOrElse(
                        pool -> {
                            injectIntoPool(pool);
                            YouzaiworldCore.LOGGER.info(
                                    "已向村庄模板池 {} 注入传送锚点遗迹", poolId);
                        },
                        () -> YouzaiworldCore.LOGGER.warn(
                                "未找到村庄模板池: {}", poolId)
                );
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void injectIntoPool(StructureTemplatePool pool) {
        SinglePoolElement element = StructurePoolElement.single(
                "youzaiworldcore:village_teleport_anchor_ruins"
        ).apply(StructureTemplatePool.Projection.RIGID);

        try {
            List<Pair<StructurePoolElement, Integer>> rawTemplates =
                    (List<Pair<StructurePoolElement, Integer>>) RAW_TEMPLATES_FIELD.get(pool);
            rawTemplates.add(Pair.of(element, WEIGHT));

            ObjectArrayList<StructurePoolElement> templates =
                    (ObjectArrayList<StructurePoolElement>) TEMPLATES_FIELD.get(pool);
            for (int i = 0; i < WEIGHT; i++) {
                templates.add(element);
            }
        } catch (IllegalAccessException e) {
            YouzaiworldCore.LOGGER.error("反射注入村庄模板池失败", e);
        }
    }
}
