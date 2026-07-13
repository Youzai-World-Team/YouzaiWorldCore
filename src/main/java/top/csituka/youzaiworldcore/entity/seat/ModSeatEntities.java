package top.csituka.youzaiworldcore.entity.seat;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 自定义实体类型注册——主要注册座椅实体（{@link SeatEntity}）。
 * <p>
 * 注册模式参照 {@code ModBlocks} / {@code ModBlockEntities}，
 * 使用 {@link Registry#register} 配合 {@link ResourceKey} 进行注册。
 */
public class ModSeatEntities {

    /**
     * 座椅实体的 {@link EntityType} 实例。
     * <ul>
     *   <li>尺寸为 (0.0f, 0.0f) —— 无碰撞箱</li>
     *   <li>不保存到世界文件（{@code noSave()}）</li>
     *   <li>不可通过指令 / 刷怪蛋召唤（{@code noSummon()}）</li>
     *   <li>归类为 {@link MobCategory#MISC}</li>
     *   <li>追踪距离 10 格以确保客户端能正确同步乘客状态</li>
     * </ul>
     */
    public static final EntityType<SeatEntity> SEAT = register(
            "seat",
            EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.0f, 0.0f)          // 无碰撞箱
                    .noSave()                     // 不保存到世界 NBT
                    .noSummon()                   // 不可召唤
                    .clientTrackingRange(10)       // 追踪距离 10 格
                    .updateInterval(Integer.MAX_VALUE) // 不需要位置更新（座椅固定）
    );

    /**
     * 注册实体类型到 {@link BuiltInRegistries#ENTITY_TYPE}。
     * <p>
     * {@code build()} 接受 {@code ResourceKey<EntityType<?>>}，
     * 此处直接传递已声明的通配符 key 即可，无需泛型转型。
     */
    @SuppressWarnings("unchecked")
    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
            String name, EntityType.Builder<T> builder
    ) {
        Identifier id = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        EntityType<T> type = builder.build(key);
        DebugLogger.info("ModSeatEntities", "Registering entity: " + id);
        return (EntityType<T>) Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }

    /**
     * 初始化方法——仅用于触发静态字段的类加载，从而完成注册。
     * 需在 {@link YouzaiworldCore#onInitialize()} 中调用。
     */
    public static void initialize() {
        DebugLogger.entering("ModSeatEntities", "initialize");
        DebugLogger.info("ModSeatEntities", "座椅实体已注册: seat");
        DebugLogger.exiting("ModSeatEntities", "initialize");
    }
}
