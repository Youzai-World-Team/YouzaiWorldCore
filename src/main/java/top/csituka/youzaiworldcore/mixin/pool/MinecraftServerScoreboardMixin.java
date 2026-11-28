package top.csituka.youzaiworldcore.mixin.pool;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.dimensionalinventories.PoolScoreboards;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerScoreboardMixin implements PoolScoreboards.ServerAccess {
    @Unique
    private final PoolScoreboards youzaiworldcore$scoreboards = new PoolScoreboards();

    @Override
    public PoolScoreboards youzaiworldcore$getPoolScoreboards() {
        return youzaiworldcore$scoreboards;
    }

    @Inject(method = "saveAllChunks", at = @At("HEAD"))
    private void youzaiworldcore$savePoolScoreboards(boolean silent, boolean flush, boolean force,
            CallbackInfoReturnable<Boolean> cir) {
        youzaiworldcore$scoreboards.save((MinecraftServer) (Object) this);
    }
}
