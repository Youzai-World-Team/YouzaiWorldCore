package top.csituka.youzaiworldcore.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import eu.pb4.trinkets.impl.data.EntitySlotLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 跳过精妙背包随模组提供的玩家槽位分配，保留 Core 的 chest/backpack。 */
@Mixin(value = EntitySlotLoader.class, remap = false)
public abstract class TrinketEntitySlotLoaderMixin {
    @Unique
    private static final Identifier youzaiworldcore$BACKPACK_ASSIGNMENT =
            Identifier.fromNamespaceAndPath("trinkets", "entities/sophisticatedbackpacks.json");

    @ModifyExpressionValue(method = "prepare(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Map;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResourceStacks(Ljava/lang/String;Lnet/minecraft/server/packs/resources/ResourceManager$Selector;)Ljava/util/Map;"),
            remap = false)
    private Map<Identifier, List<Resource>> youzaiworldcore$skipDuplicateBackpackSlot(
            Map<Identifier, List<Resource>> assignments) {
        List<Resource> stack = assignments.get(youzaiworldcore$BACKPACK_ASSIGNMENT);
        if (stack == null) {
            return assignments;
        }
        // Trinkets 会合并同路径的所有数据包资源，仅过滤精妙背包自身的数据包。
        List<Resource> remaining = stack.stream()
                .filter(resource -> !"sophisticatedbackpacks".equals(resource.sourcePackId()))
                .toList();
        if (remaining.size() == stack.size()) {
            return assignments;
        }
        Map<Identifier, List<Resource>> filtered = new LinkedHashMap<>(assignments);
        if (remaining.isEmpty()) {
            filtered.remove(youzaiworldcore$BACKPACK_ASSIGNMENT);
        } else {
            filtered.put(youzaiworldcore$BACKPACK_ASSIGNMENT, remaining);
        }
        return filtered;
    }
}
