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
 * 村庄传送锚点结构注入器 — town_centers 替换模式。
 *
 * <p>将原版 5 种村庄的 {@code town_centers} 模板池替换为自定义 meeting point 结构。
 * 每个自定义结构在原版 meeting point 基础上加入了传送锚点石柱，并保留了 Jigsaw 连接块，
 * 确保后续 streets → houses 链条完整，村庄正常生成。</p>
 *
 * <h3>结构 NBT 命名</h3>
 * <ul>
 *   <li>{@code plains_village_teleport_anchor_ruins.nbt} — plains</li>
 *   <li>{@code desert_village_teleport_anchor_ruins.nbt} — desert</li>
 *   <li>{@code savanna_village_teleport_anchor_ruins.nbt} — savanna</li>
 *   <li>{@code snowy_village_teleport_anchor_ruins.nbt} — snowy</li>
 *   <li>{@code taiga_village_teleport_anchor_ruins.nbt} — taiga</li>
 * </ul>
 */
@SuppressWarnings("null")
public final class VillageStructureInjector {

    private static final ResourceKey<StructureProcessorList> EMPTY_PROCESSOR_LIST_KEY =
            ResourceKey.create(Registries.PROCESSOR_LIST,
                    Identifier.fromNamespaceAndPath("minecraft", "empty"));

    private static final String STRUCTURE_PREFIX = "youzaiworldcore:";

    /** 村庄类型 → 结构 NBT 名称 映射 */
    private static final String[] TOWN_CENTER_ENTRIES = {
            "plains_village_teleport_anchor_ruins",
            "desert_village_teleport_anchor_ruins",
            "savanna_village_teleport_anchor_ruins",
            "snowy_village_teleport_anchor_ruins",
            "taiga_village_teleport_anchor_ruins",
    };

    /** 结构名到村庄池路径的映射（通过提取前缀） */
    private static String toPoolPath(String structureName) {
        String prefix = structureName.split("_")[0];
        return "village/" + prefix + "/town_centers";
    }

    private VillageStructureInjector() {}

    /**
     * 替换所有原版村庄的 town_centers 池为含传送锚点的自定义 meeting point。
     *
     * @param registryAccess 服务器注册表访问接口
     */
    public static void inject(RegistryAccess registryAccess) {
        YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 开始替换原版村庄 town_centers...");

        Holder<StructureProcessorList> emptyProcessorList = registryAccess
                .lookupOrThrow(Registries.PROCESSOR_LIST)
                .getOrThrow(EMPTY_PROCESSOR_LIST_KEY);

        int successCount = 0;

        for (String structureName : TOWN_CENTER_ENTRIES) {
            String poolPath = toPoolPath(structureName);
            Identifier poolId = Identifier.withDefaultNamespace(poolPath);

            StructureTemplatePool pool = registryAccess
                    .lookupOrThrow(Registries.TEMPLATE_POOL)
                    .getOptional(poolId)
                    .orElse(null);

            if (pool == null) {
                YouzaiworldCore.LOGGER.warn("[VillageStructureInjector] 未找到: {}", poolId);
                continue;
            }

            LegacySinglePoolElement element = StructurePoolElement
                    .legacy(STRUCTURE_PREFIX + structureName, emptyProcessorList)
                    .apply(StructureTemplatePool.Projection.RIGID);

            StructureTemplatePoolAccessor accessor = (StructureTemplatePoolAccessor) pool;

            List<Pair<StructurePoolElement, Integer>> weighted = new ArrayList<>();
            weighted.add(new Pair<>(element, 1));
            accessor.setRawTemplates(weighted);

            ObjectArrayList<StructurePoolElement> flat = new ObjectArrayList<>();
            flat.add(element);
            accessor.setTemplates(flat);

            successCount++;
            YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 已替换: {}", poolId);
        }

        YouzaiworldCore.LOGGER.info("[VillageStructureInjector] 完成: 替换 {}/{} 个村庄 town_centers",
                successCount, TOWN_CENTER_ENTRIES.length);
    }
}
