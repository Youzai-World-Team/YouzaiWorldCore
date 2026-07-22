package top.csituka.youzaiworldcore.pet;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Optional;

/**
 * 宠物模式控制器 — 根据全局注册表中的 PetMode 决定狼的行为。
 * <p>
 * 由 Mixin 层和事件层共同调用，避免重复引用全局注册表。
 * </p>
 */
public final class PetModeController {

    private static final String MODULE = "PetModeController";

    private PetModeController() {
    }

    /**
     * 获取狼当前的 PetMode。
     *
     * @param wolf 狼实体
     * @return PetMode，如果该狼不是宠物则返回 null
     */
    @Nullable
    public static PetMode getMode(@NotNull Wolf wolf) {
        if (wolf.level().isClientSide()) {
            return null;
        }
        ServerLevel level = (ServerLevel) wolf.level();
        PetGlobalState state;
        try {
            state = PetGlobalState.get(level.getServer());
        } catch (IllegalStateException e) {
            return null;
        }
        Optional<PetEntry> optEntry = state.findByEntityUUID(wolf.getUUID());
        return optEntry.map(PetEntry::mode).orElse(null);
    }

    /**
     * 判断在指定模式下，狼是否应该攻击某个目标。
     * <p>
     * 此方法用于 {@code wantsToAttack} 的通用拦截（OwnerHurtByTargetGoal / OwnerHurtTargetGoal 均会调用）。
     * 仅适用于那些不能区分"谁发起的攻击"的场景。
     * 对于 GUARD 和 HUNTING 模式的精细控制，需结合具体的 Goal Mixin。
     * </p>
     *
     * @param mode  宠物模式（非 null 表示是宠物）
     * @param forceDefault 是否强制放行（绕过模式检查）
     * @return true 如果应该允许攻击
     */
    public static boolean shouldAllowAttack(@Nullable PetMode mode, boolean forceDefault) {
        if (forceDefault) {
            return true;
        }
        if (mode == null) {
            return true; // 非宠物，放行原版行为
        }
        switch (mode) {
            case COMPANIONSHIP:
                return false; // 陪伴模式：绝不攻击
            case GUARD:
            case HUNTING:
            case ATTACK:
                return true; // 这些模式允许攻击（精细控制在 Goal Mixin 中处理）
        }
        return true;
    }

    /**
     * 判断在指定模式下，是否允许狼响应 {@link net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal}
     *（即主人攻击某个生物时，狼也跟着攻击）。
     * <p>
     * ATTACK 和 HUNTING 模式允许，COMPANIONSHIP 和 GUARD 不允许。
     * HUNTING 模式下仅当目标位于主人附近 10 格内才触发（精度在 Goal Mixin 中实现）。
     * </p>
     */
    public static boolean shouldAllowOwnerHurtTarget(@Nullable PetMode mode) {
        if (mode == null) return true; // 非宠物，放行
        return mode == PetMode.ATTACK || mode == PetMode.HUNTING;
    }

    /**
     * 判断在指定模式下，是否允许狼响应 {@link net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal}
     *（即主人被某个生物攻击时，狼去反击）。
     * <p>
     * ATTACK 和 GUARD 模式允许，COMPANIONSHIP 和 HUNTING 不允许。
     * HUNTING 模式不关心主人被谁攻击，只关心主人攻击了谁。
     * </p>
     */
    public static boolean shouldAllowOwnerHurtByTarget(@Nullable PetMode mode) {
        if (mode == null) return true; // 非宠物，放行
        return mode == PetMode.ATTACK || mode == PetMode.GUARD;
    }

    /**
     * 判断在指定模式下，是否允许狼响应 {@link net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal}
     *（即狼自己被攻击时，去反击）。
     * <p>
     * COMPANIONSHIP 不允许，其他模式允许。
     * HUNTING/GUARD 模式下，狼仍可自卫反击（不包含在 10 格检查中，这是即时反击）。
     * </p>
     */
    public static boolean shouldAllowHurtByTarget(@Nullable PetMode mode) {
        if (mode == null) return true; // 非宠物，放行
        return mode != PetMode.COMPANIONSHIP;
    }
}
