package top.csituka.youzaiworldcore.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Mixin Accessor for {@link StructureTemplatePool}.
 * Exposes the private {@code rawTemplates} (weighted list) and
 * {@code templates} (flattened list) fields so that structure elements
 * can be dynamically injected into vanilla village template pools at runtime.
 *
 * <p>This approach avoids overwriting data pack JSONs and is the
 * same pattern used by Waystones for village waystone integration.</p>
 */
@SuppressWarnings("null")
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {

    /**
     * @return the weighted list of (element, weight) pairs
     */
    @Accessor("rawTemplates")
    List<Pair<StructurePoolElement, Integer>> getRawTemplates();

    @Accessor("rawTemplates")
    @Mutable
    void setRawTemplates(List<Pair<StructurePoolElement, Integer>> rawTemplates);

    /**
     * @return the flattened, frequency-expanded list of elements
     */
    @Accessor("templates")
    ObjectArrayList<StructurePoolElement> getTemplates();

    @Accessor("templates")
    @Mutable
    void setTemplates(ObjectArrayList<StructurePoolElement> templates);
}
