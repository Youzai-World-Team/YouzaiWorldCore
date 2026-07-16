package top.csituka.youzaiworldcore.entity.animation_subtitle;

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
 * 动画字幕实体类型注册。
 * <p>
 * 参考 {@code ModSeatEntities} 的注册模式，
 * 使用 {@link Registry#register} 配合 {@link ResourceKey}。
 * </p>
 */
@SuppressWarnings("null")
public class ModAnimationSubtitleEntities {

    /**
     * 动画字幕实体类型。
     * <ul>
     *   <li>尺寸为 (0.0f, 0.0f) — 字幕不需要碰撞箱</li>
     *   <li>不保存到世界文件（可配置，目前默认保存）</li>
     *   <li>不可通过指令 / 刷怪蛋召唤（{@code noSummon()} — 由命令生成）</li>
     *   <li>归类为 {@link MobCategory#MISC}</li>
     *   <li>追踪距离 64 格，确保客户端能看到远处字幕</li>
     *   <li>更新间隔为 1 tick（动画需要每帧更新）</li>
     * </ul>
     */
    public static final EntityType<AnimationSubtitleEntity> ANIMATION_SUBTITLE = register(
            "animation_subtitle",
            EntityType.Builder.<AnimationSubtitleEntity>of(AnimationSubtitleEntity::new, MobCategory.MISC)
                    .sized(0.0f, 0.0f)
                    .noSummon()
                    .clientTrackingRange(64)
                    .updateInterval(1)
    );

    /**
     * 注册实体类型到 {@link BuiltInRegistries#ENTITY_TYPE}。
     */
    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(
            String name, EntityType.Builder<T> builder
    ) {
        Identifier id = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, name);
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        EntityType<T> type = builder.build(key);
        DebugLogger.info("ModAnimationSubtitleEntities", "Registering entity: " + id);
        return (EntityType<T>) Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
    }

    /**
     * 初始化方法——触发静态字段加载以完成注册。
     * 需在 {@link YouzaiworldCore#onInitialize()} 中调用。
     */
    public static void initialize() {
        DebugLogger.entering("ModAnimationSubtitleEntities", "initialize");
        DebugLogger.info("ModAnimationSubtitleEntities", "动画字幕实体已注册: animation_subtitle");
        DebugLogger.exiting("ModAnimationSubtitleEntities", "initialize");
    }
}
