package top.csituka.youzaiworldcore.account.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;

/**
 * Entity Mixin — 未认证玩家隐身
 *
 * 注意：不能在此类中使用 AccountServerPlayerMixin 类型的局部变量，
 * 因为 Mixin 的 LVT 转换会在 Entity 继承树中查找该类型，导致崩溃。
 * 改用静态辅助方法 AccountServerPlayerMixin.shouldBlockActions()。
 *
 * 无敌逻辑移到 AccountServerPlayerMixin 中实现（hurtServer 在 Entity 中是 abstract，
 * Mixin 无法注入 abstract 方法）。
 */
@Mixin(Entity.class)
public abstract class AccountEntityMixin {

    /**
     * 未认证玩家对其他玩家隐身
     */
    @Inject(method = "isInvisible", at = @At("RETURN"), cancellable = true)
    private void onIsInvisible(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ServerPlayer player) {
            if (AuthPlayerHelper.shouldBlockActions(player)) {
                cir.setReturnValue(true); // 未认证时隐身
            }
        }
    }
}
