package top.csituka.youzaiworldcore.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayer.RespawnConfig;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolRespawnPoints;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolRespawnCounts;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPoolRespawnPointsMixin implements PoolRespawnPoints.Access, PoolRespawnCounts.Access {
    @Shadow
    private @Nullable RespawnConfig respawnConfig;

    @Unique
    private final PoolRespawnPoints youzaiworldcore$respawnPoints = new PoolRespawnPoints();

    @Unique
    private final PoolRespawnCounts youzaiworldcore$respawnCounts = new PoolRespawnCounts();

    @ModifyExpressionValue(method = {"getRespawnConfig", "addAdditionalSaveData"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/level/ServerPlayer;respawnConfig:Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;",
            opcode = Opcodes.GETFIELD))
    private @Nullable RespawnConfig youzaiworldcore$getPoolRespawnPoint(@Nullable RespawnConfig vanillaPoint) {
        return youzaiworldcore$respawnPoints.get(((ServerPlayer) (Object) this).level().dimension());
    }

    @ModifyExpressionValue(method = "setRespawnPosition", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/level/ServerPlayer;respawnConfig:Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;",
            opcode = Opcodes.GETFIELD))
    private @Nullable RespawnConfig youzaiworldcore$getPreviousPoolPoint(@Nullable RespawnConfig vanillaPoint,
            @Local(argsOnly = true) @Nullable RespawnConfig newPoint) {
        return newPoint == null ? null : youzaiworldcore$respawnPoints.get(newPoint.respawnData().dimension());
    }

    @Inject(method = "setRespawnPosition", at = @At("RETURN"))
    private void youzaiworldcore$setPoolRespawnPoint(@Nullable RespawnConfig point, boolean announce, CallbackInfo ci) {
        youzaiworldcore$respawnPoints.set(((ServerPlayer) (Object) this).level().dimension(), respawnConfig);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void youzaiworldcore$loadPoolRespawnPoints(ValueInput input, CallbackInfo ci) {
        youzaiworldcore$respawnPoints.load(input, respawnConfig);
        youzaiworldcore$respawnCounts.load(input);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void youzaiworldcore$savePoolRespawnPoints(ValueOutput output, CallbackInfo ci) {
        youzaiworldcore$respawnPoints.save(output);
        youzaiworldcore$respawnCounts.save(output);
    }

    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void youzaiworldcore$restorePoolRespawnPoints(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        // 原版遇到失效的床或重生锚时会跳过 copyRespawnPosition，其他池的点仍要复制。
        youzaiworldcore$copyPoolRespawnPoints(oldPlayer);
        youzaiworldcore$respawnCounts.copyFrom(((PoolRespawnCounts.Access) oldPlayer).youzaiworldcore$getPoolRespawnCounts());
    }

    @Inject(method = "copyRespawnPosition", at = @At("HEAD"), cancellable = true)
    private void youzaiworldcore$copyAllPoolRespawnPoints(ServerPlayer oldPlayer, CallbackInfo ci) {
        youzaiworldcore$copyPoolRespawnPoints(oldPlayer);
        ci.cancel();
    }

    @Unique
    private void youzaiworldcore$copyPoolRespawnPoints(ServerPlayer oldPlayer) {
        var old = (ServerPlayerPoolRespawnPointsMixin) (Object) oldPlayer;
        youzaiworldcore$respawnPoints.copyFrom(old.youzaiworldcore$respawnPoints);
        respawnConfig = ((ServerPlayer) (Object) this).getRespawnConfig();
    }

    @Override
    public void youzaiworldcore$clearAllRespawnPoints() {
        youzaiworldcore$respawnPoints.clear();
        respawnConfig = null;
    }

    @Override
    public PoolRespawnCounts youzaiworldcore$getPoolRespawnCounts() {
        return youzaiworldcore$respawnCounts;
    }
}
