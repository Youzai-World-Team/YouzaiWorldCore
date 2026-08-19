package top.csituka.youzaiworldcore.block.entity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.block.ModBlocks;

@SuppressWarnings("null")
public class ModBlockEntities {

    public static final BlockEntityType<DecompositionTableBlockEntity> DECOMPOSITION_TABLE = register(
            "decomposition_table",
            FabricBlockEntityTypeBuilder.create(DecompositionTableBlockEntity::new, ModBlocks.DECOMPOSITION_TABLE).build()
    );

    public static final BlockEntityType<FlyBeaconBlockEntity> FLY_BEACON = register(
            "fly_beacon",
            FabricBlockEntityTypeBuilder.create(FlyBeaconBlockEntity::new, ModBlocks.FLY_BEACON).build()
    );

    public static final BlockEntityType<TeleportAnchorBlockEntity> TELEPORT_ANCHOR = register(
            "teleport_anchor",
            FabricBlockEntityTypeBuilder.create(TeleportAnchorBlockEntity::new, ModBlocks.TP_ANCHOR).build()
    );

    public static final BlockEntityType<DuplicateBlockEntity> DUPLICATE_BLOCK = register(
            "duplicate_block",
            FabricBlockEntityTypeBuilder.create(DuplicateBlockEntity::new, ModBlocks.DUPLICATE_BLOCK).build()
    );

    /**
     * 大字牌方块实体类型：全部 {@link ModBlocks#LARGE_SIGNS} 变体共用同一个类型，
     * 它们的差异只在材质与音效，牌面数据结构完全一致。
     */
    public static final BlockEntityType<LargeSignBlockEntity> LARGE_SIGN = register(
            "large_sign",
            FabricBlockEntityTypeBuilder.create(LargeSignBlockEntity::new, ModBlocks.LARGE_SIGNS).build()
    );

    private static <T extends BlockEntityType<?>> T register(String name, T blockEntityType) {
        ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name));
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key, blockEntityType);
    }

    public static void initialize() {
    }
}
