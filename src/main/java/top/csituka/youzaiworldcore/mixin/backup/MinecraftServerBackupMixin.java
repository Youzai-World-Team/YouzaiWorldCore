package top.csituka.youzaiworldcore.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.backup.PlayerDataBackup;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerBackupMixin {
    @Inject(method = "runServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;onServerCrash(Lnet/minecraft/CrashReport;)V"))
    private void youzaiworldcore$markBackupCrash(CallbackInfo ci) {
        PlayerDataBackup.markCrashed((MinecraftServer) (Object) this);
    }

    // Capture after vanilla's final save and before it releases the world lock.
    @Inject(method = "stopServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;close()V"))
    private void youzaiworldcore$backupBeforeUnlock(CallbackInfo ci) {
        PlayerDataBackup.backupOnExit((MinecraftServer) (Object) this, false);
    }

    @WrapOperation(method = "runServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;stopServer()V"))
    private void youzaiworldcore$backupIfFinalSaveFails(MinecraftServer server, Operation<Void> original) {
        try {
            original.call(server);
        } catch (Throwable e) {
            try {
                PlayerDataBackup.backupOnExit(server, true);
            } catch (Throwable backupFailure) {
                if (backupFailure != e) {
                    e.addSuppressed(backupFailure);
                }
            }
            throw e;
        }
    }
}
