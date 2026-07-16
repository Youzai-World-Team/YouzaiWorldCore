package top.csituka.youzaiworldcore.worldgen;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.pools.LegacySinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.mixin.StructureTemplatePoolAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * 村庄传送锚点结构注入器。
 *
 * <p>参考 Waystones mod 的 Template Pool 注入机制，在服务器启动时将传送锚点废墟
 * 结构直接替换原版村庄的 meeting point（钟所在的位置），使传送锚点作为村庄核心
 * 出现在每个村庄的正中央。</p>
 *
 * <h3>机制</h3>
 * <p>村庄 Jigsaw 生成以 {@code town_centers} 池为起点。将该池内容替换为仅含
 * 传送锚点结构的一元素，确保每个村庄中心必然生成传送锚点。</p>
 *
 * <h3>覆盖的村庄类型</h3>
 * <ul>
 *   <li>plains (平原)</li>
 *   <li>desert (沙漠)</li>
 *   <li>savanna (热带草原)</li>
 *   <li>snowy (雪原)</li>
 *   <li>taiga (针叶林)</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class VillageStructureInjector {

    /** minecraft:empty 处理器列表的 ResourceKey */
    private static final ResourceKey<StructureProcessorList> EMPTY_PROCESSOR_LIST_KEY =
            ResourceKey.create(Registries.PROCESSOR_LIST,
                    Identifier.fromNamespaceAndPath("minecraft", "empty"));

    /** 村庄传送锚点结构的命名空间路径 */
    private static final String TP_ANCHOR_STRUCTURE_PATH = "youzaiworldcore:villige_teleport_anchor_ruins";

    /** 5 种原版村庄的 town_centers 池 — 村庄 Jigsaw 的生成起点 */
    private static final String[] TOWN_CENTER_POOLS = {
            "village/plains/town_centers",
            "village/desert/town_centers",
            "village/savanna/town_centers",
            "village/snowy/town_centers",
            "village/taiga/town_centers"
    };

    private VillageStructureInjector() {
        // 工具类，禁止实例化
    }

    /**
     * 替换所有原版村庄的 town_centers 池为传送锚点结构。
     *
     * <p>应在 {@code ServerLifecycleEvents.SERVER_STARTING} 阶段调用，
     * 此时 Dynamic Registries 已加载但世界生成尚未开始。</p>
     *
     * @param registryAccess 服务器注册表访问接口
     */
    public static void inject(RegistryAccess registryAccess) {
        YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 开始替换原版村庄 meeting point 为传送锚点...");

        Holder<StructureProcessorList> emptyProcessorList = registryAccess
                .lookupOrThrow(Registries.PROCESSOR_LIST)
                .getOrThrow(EMPTY_PROCESSOR_LIST_KEY);

        LegacySinglePoolElement tpAnchorElement = StructurePoolElement
                .legacy(TP_ANCHOR_STRUCTURE_PATH, emptyProcessorList)
                .apply(StructureTemplatePool.Projection.RIGID);

        int successCount = 0;

        for (String poolPath : TOWN_CENTER_POOLS) {
            Identifier poolId = Identifier.withDefaultNamespace(poolPath);

            StructureTemplatePool pool = registryAccess
                    .lookupOrThrow(Registries.TEMPLATE_POOL)
                    .getOptional(poolId)
                    .orElse(null);

            if (pool == null) {
                YouzaiworldCore.LOGGER.warn("[VillageStructureInjector] 未找到 town_centers 池: {}", poolId);
                continue;
            }

            StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;

            // 用传送锚点元素完全替换池内容
            List<Pair<StructurePoolElement, Integer>> newWeighted = new ArrayList<>();
            newWeighted.add(new Pair<>(tpAnchorElement, 1));
            accessor.setRawTemplates(newWeighted);

            ObjectArrayList<StructurePoolElement> newFlat = new ObjectArrayList<>();
            newFlat.add(tpAnchorElement);
            accessor.setTemplates(newFlat);

            successCount++;
            YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 已替换: {}", poolId);
        }

        YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 完成: 成功替换 {}/{} 个村庄的 meeting point",
                successCount, TOWN_CENTER_POOLS.length);
    }
}
