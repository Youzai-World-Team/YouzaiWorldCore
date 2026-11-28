package top.csituka.youzaiworldcore.mixin.compat;

import eu.pb4.trinkets.impl.data.EntitySlotLoader;
import eu.pb4.trinkets.impl.slots.SlotGroupImpl;
import eu.pb4.trinkets.impl.slots.SlotTypeImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 槽位分配合并完成后移除玩家的精妙背包槽，保留 Core 的 chest/backpack。 */
@Mixin(value = EntitySlotLoader.class, remap = false)
public abstract class TrinketEntitySlotLoaderMixin {
    @Unique
    private static final String youzaiworldcore$DUPLICATE_BACKPACK_SLOT = "chest/back";

    @Shadow @Final
    private Map<EntityType<?>, Map<String, SlotGroupImpl>> groups;

    @Shadow @Final
    private Map<EntityType<?>, Map<String, SlotTypeImpl>> entitySlots;

    @Shadow @Final
    private Map<String, SlotTypeImpl> slots;

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("RETURN"),
            remap = false)
    private void youzaiworldcore$removeDuplicateBackpackSlot(
            Map<String, Map<String, Set<String>>> assignments, ResourceManager manager,
            ProfilerFiller profiler, CallbackInfo ci) {
        if (!FabricLoader.getInstance().isModLoaded("sophisticatedbackpacks")) {
            return;
        }
        Map<String, SlotGroupImpl> playerGroups = this.groups.get(EntityTypes.PLAYER);
        SlotGroupImpl chest = playerGroups == null ? null : playerGroups.get("chest");
        if (chest == null || !chest.slotsImpl().containsKey("back")) {
            return;
        }

        // 同步载荷与饰品库存都读取分配后的分组，不能只过滤资源或修改图标。
        Map<String, SlotTypeImpl> remaining = new LinkedHashMap<>(chest.slotsImpl());
        remaining.remove("back");
        if (remaining.isEmpty()) {
            playerGroups.remove("chest");
        } else {
            playerGroups.put("chest", new SlotGroupImpl(chest.name(), chest.slotId(), chest.order(), remaining));
        }
        Map<String, SlotTypeImpl> playerSlots = this.entitySlots.get(EntityTypes.PLAYER);
        if (playerSlots != null) {
            playerSlots.remove(youzaiworldcore$DUPLICATE_BACKPACK_SLOT);
        }
        if (this.entitySlots.values().stream()
                .noneMatch(entity -> entity.containsKey(youzaiworldcore$DUPLICATE_BACKPACK_SLOT))) {
            this.slots.remove(youzaiworldcore$DUPLICATE_BACKPACK_SLOT);
        }
    }
}
