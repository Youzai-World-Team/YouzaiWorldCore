package top.csituka.youzaiworldcore.mixin.afk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.afk.AfkManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** AFK 保护直接拦截玩家伤害，包含饥饿和虚空，不添加或移除药水效果。 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerAfkDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$preventAfkDamage(ServerLevel level, DamageSource source,
            float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (AfkManager.isProtected(player)) {
            DebugLogger.trace("AfkDamage", "已拦截 %s 的 AFK 期间伤害: %s",
                    player.getName().getString(), source);
            cir.setReturnValue(false);
        }
    }
}
