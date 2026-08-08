package top.csituka.youzaiworldcore.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

import org.jspecify.annotations.NonNull;

import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 自定义画作物品——每个实例绑定一个固定的 {@link PaintingVariant}，
 * 右键放置时始终放置<b>同一个</b>变体，不会随机化。
 *
 * <p>
 * 26.2 起，{@link Painting} 提供公开构造器
 * {@code Painting(Level, BlockPos, Direction, Holder&lt;PaintingVariant&gt;)}，
 * 直接指定变体即可绕过原版 {@code Painting.create()} 的随机选择逻辑。
 * </p>
 *
 * <p>
 * 本类还维护了一个全局 {@link #VARIANT_ITEM_MAP} 映射，供 Mixin
 * {@code MemePaintingDropMixin} 在画被破坏时查找应掉落的专用物品，
 * 从而实现「破坏后掉落的画还能放同一个画」的闭环体验。
 * </p>
 *
 * @see Painting
 * @see PaintingVariant
 */
@SuppressWarnings("null")
public class MemePaintingItem extends Item {

    /** {@code ResourceKey<PaintingVariant>} → {@code Item}，使 Mixin 可追溯到掉落物。 */
    public static final Map<ResourceKey<PaintingVariant>, Item> VARIANT_ITEM_MAP = new HashMap<>();

    private final ResourceKey<PaintingVariant> variantKey;

    public MemePaintingItem(ResourceKey<PaintingVariant> variantKey, Properties settings) {
        super(settings);
        this.variantKey = variantKey;
        VARIANT_ITEM_MAP.put(variantKey, this);
        DebugLogger.info("MemePaintingItem",
                "注册自定义画物品 variant=%s".formatted(variantKey.identifier()));
    }

    /**
     * 右键使用：在目标面创建一幅固定变体的画。
     *
     * <p>
     * 大致流程：
     * <ol>
     * <li>检查玩家是否可在此处放置 / 目标方块是否可交互</li>
     * <li>从 {@code RegistryAccess} 获取 {@link PaintingVariant} 的 Holder</li>
     * <li>实例化 {@link Painting} 并检查 {@code survives()}</li>
     * <li>服务端添加实体并消耗物品</li>
     * </ol>
     *
     * @param context 右键上下文（含玩家、手持物品、目标方块等）
     * @return 放置成功返回 {@link InteractionResult#SUCCESS}，否则 {@code FAIL}
     */
    @Override
    @NonNull
    public InteractionResult useOn(@NonNull UseOnContext context) {
        DebugLogger.entering("MemePaintingItem", "useOn",
                "variant=%s player=%s".formatted(
                        variantKey.identifier(),
                        context.getPlayer() != null ? context.getPlayer().getName().getString() : "null"));

        BlockPos clickPos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos placePos = clickPos.relative(face);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();

        // ===== 基础校验 =====
        if (player == null) {
            DebugLogger.branch("MemePaintingItem", "player is null", false);
            DebugLogger.exiting("MemePaintingItem", "useOn", "FAIL (no player)");
            return InteractionResult.FAIL;
        }

        // 画只能附着在竖直面（水平方向），顶部/底部不可放置
        if (!face.getAxis().isHorizontal()) {
            DebugLogger.branch("MemePaintingItem", "direction is horizontal", false,
                    "face=" + face);
            DebugLogger.exiting("MemePaintingItem", "useOn", "FAIL (non-horizontal face)");
            return InteractionResult.FAIL;
        }

        // 校验玩家是否可以在此处使用物品（覆盖 canBuild / 冒险模式检查）
        if (!player.mayUseItemAt(placePos, face, stack)) {
            DebugLogger.branch("MemePaintingItem", "player may use item at pos", false,
                    "pos=" + placePos + " face=" + face);
            DebugLogger.exiting("MemePaintingItem", "useOn", "FAIL (mayUseItemAt)");
            return InteractionResult.FAIL;
        }

        // ===== 获取 Holder<PaintingVariant> =====
        RegistryAccess registryAccess = level.registryAccess();
        HolderGetter<PaintingVariant> lookup = registryAccess.lookupOrThrow(Registries.PAINTING_VARIANT);
        Optional<Holder.Reference<PaintingVariant>> holderOpt = lookup.get(variantKey);

        if (holderOpt.isEmpty()) {
            DebugLogger.warn("MemePaintingItem",
                    "painting variant %s 不在注册表中".formatted(variantKey.identifier()));
            DebugLogger.exiting("MemePaintingItem", "useOn", "FAIL (variant not found)");
            return InteractionResult.FAIL;
        }

        Holder<PaintingVariant> holder = holderOpt.get();
        DebugLogger.info("MemePaintingItem", "获取到 painting variant holder: %s (size=%d×%d)",
                variantKey.identifier(),
                holder.value().width(), holder.value().height());

        // ===== 创建 Painting 实体 =====
        Painting painting = new Painting(level, placePos, face, holder);

        // 应用物品栈上的实体组件（自定义名等）
        EntityType.createDefaultStackConfig(level, stack, player).apply(painting);

        if (!painting.survives()) {
            DebugLogger.branch("MemePaintingItem", "painting survives", false,
                    "pos=" + placePos + " dir=" + face);
            DebugLogger.exiting("MemePaintingItem", "useOn", "CONSUME (cannot survive)");
            return InteractionResult.CONSUME;
        }

        // ===== 检查与其他实体碰撞 =====
        AABB boundingBox = painting.getBoundingBox();
        if (!level.getEntities(painting, boundingBox).isEmpty()) {
            DebugLogger.branch("MemePaintingItem", "no entity collision", false,
                    "bbox=" + boundingBox);
            DebugLogger.exiting("MemePaintingItem", "useOn", "FAIL (collision)");
            return InteractionResult.FAIL;
        }

        // ===== 客户端直接 SUCCESS =====
        if (level.isClientSide()) {
            DebugLogger.branch("MemePaintingItem", "is server side", false, "客户端 SUCCESS");
            DebugLogger.exiting("MemePaintingItem", "useOn", "SUCCESS (client)");
            return InteractionResult.SUCCESS;
        }

        // ===== 服务端执行 =====
        painting.playPlacementSound();
        level.gameEvent(player, GameEvent.ENTITY_PLACE, painting.position());

        boolean added = level.addFreshEntity(painting);
        if (!added) {
            DebugLogger.warn("MemePaintingItem", "无法将画实体添加到世界，pos=%s".formatted(placePos));
            DebugLogger.exiting("MemePaintingItem", "useOn", "FAIL (add entity failed)");
            return InteractionResult.FAIL;
        }

        stack.shrink(1);
        player.getInventory().setChanged();

        DebugLogger.info("MemePaintingItem", "画已放置：玩家=%s pos=%s variant=%s dir=%s",
                player.getName().getString(), placePos,
                variantKey.identifier(), face);
        DebugLogger.exiting("MemePaintingItem", "useOn", "SUCCESS");
        return InteractionResult.SUCCESS;
    }
}
