package top.csituka.youzaiworldcore.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager;

/** 在原版开始换维度前保存源池，而非在到达事件中保存。 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPoolTeleportMixin {
    @WrapMethod(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;")
    private ServerPlayer youzaiworldcore$switchPool(TeleportTransition transition, Operation<ServerPlayer> original) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        return DimensionPoolManager.onTeleport(player, transition, () -> original.call(transition));
    }
}
