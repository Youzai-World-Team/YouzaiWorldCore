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
 * <p>参考 Waystones mod 的村庄路标生成机制，在服务器启动时将传送锚点废墟结构
 * 直接注入到原版村庄的 Template Pool 中，使传送锚点作为村庄的随机建筑之一自然生成。</p>
 *
 * <h3>注入的村庄类型</h3>
 * <ul>
 *   <li>plains (平原) — 通用传送锚点</li>
 *   <li>desert (沙漠) — 通用传送锚点（后续可替换为沙漠主题变体）</li>
 *   <li>savanna (热带草原) — 通用传送锚点</li>
 *   <li>snowy (雪原) — 通用传送锚点</li>
 *   <li>taiga (针叶林) — 通用传送锚点</li>
 * </ul>
 *
 * <h3>生成权重</h3>
 * 权重设为 1，与原版村庄房屋（典型权重 2-5）公平竞争，
 * 使传送锚点有适中的概率出现在村庄中。
 */
@SuppressWarnings("null")
public final class VillageStructureInjector {

    /** minecraft:empty 处理器列表的 ResourceKey */
    private static final ResourceKey<StructureProcessorList> EMPTY_PROCESSOR_LIST_KEY =
            ResourceKey.create(Registries.PROCESSOR_LIST,
                    Identifier.fromNamespaceAndPath("minecraft", "empty"));

    /** 村庄传送锚点结构的命名空间路径 */
    private static final String TP_ANCHOR_STRUCTURE_PATH = "youzaiworldcore:villige_teleport_anchor_ruins";

    /** 在村庄池中的生成权重 */
    private static final int GENERATION_WEIGHT = 1;

    /** 需要注入的 5 种原版村庄 houses 池 */
    private static final String[] VILLAGE_POOLS = {
            "village/plains/houses",
            "village/desert/houses",
            "village/savanna/houses",
            "village/snowy/houses",
            "village/taiga/houses"
    };

    private VillageStructureInjector() {
        // 工具类，禁止实例化
    }

    /**
     * 向所有原版村庄模板池中注入传送锚点结构元素。
     *
     * <p>应在 {@code ServerLifecycleEvents.SERVER_STARTING} 阶段调用，
     * 此时 Dynamic Registries 已加载但世界生成尚未开始。</p>
     *
     * @param registryAccess 服务器注册表访问接口
     */
    public static void inject(RegistryAccess registryAccess) {
        YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 开始向原版村庄注入传送锚点结构...");

        // 获取 minecraft:empty 处理器列表
        Holder<StructureProcessorList> emptyProcessorList = registryAccess
                .lookupOrThrow(Registries.PROCESSOR_LIST)
                .getOrThrow(EMPTY_PROCESSOR_LIST_KEY);

        // 创建传送锚点结构模板元素
        // 使用 LegacySinglePoolElement 兼容原版村庄的 jigsaw 放置逻辑
        LegacySinglePoolElement tpAnchorElement = StructurePoolElement
                .legacy(TP_ANCHOR_STRUCTURE_PATH, emptyProcessorList)
                .apply(StructureTemplatePool.Projection.RIGID);

        int successCount = 0;

        for (String villagePoolPath : VILLAGE_POOLS) {
            Identifier poolId = Identifier.withDefaultNamespace(villagePoolPath);

            StructureTemplatePool pool = registryAccess
                    .lookupOrThrow(Registries.TEMPLATE_POOL)
                    .getOptional(poolId)
                    .orElse(null);

            if (pool == null) {
                YouzaiworldCore.LOGGER.warn("[VillageStructureInjector] 未找到村庄池: {}", poolId);
                continue;
            }

            StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;

            // 防御性拷贝 — 某些情况下模板池可能是不可变列表（例如被数据包覆写）
            List<Pair<StructurePoolElement, Integer>> weightedPieces =
                    new ArrayList<>(accessor.getRawTemplates());
            weightedPieces.add(new Pair<>(tpAnchorElement, GENERATION_WEIGHT));
            accessor.setRawTemplates(weightedPieces);

            ObjectArrayList<StructurePoolElement> flatPieces =
                    new ObjectArrayList<>(accessor.getTemplates());
            for (int i = 0; i < GENERATION_WEIGHT; i++) {
                flatPieces.add(tpAnchorElement);
            }
            accessor.setTemplates(flatPieces);

            successCount++;
            YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 已注入: {} (weight={})",
                    poolId, GENERATION_WEIGHT);
        }

        YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 完成: 成功注入 {}/{} 个村庄池",
                successCount, VILLAGE_POOLS.length);
    }
}
