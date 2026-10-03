package top.csituka.youzaiworldcore.mixin.map;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.map.MapServerManager;

/** 26.2 save 返回 true 时区块写入已排队，随后从同一个区块 IO 队列读取快照。 */
@Mixin(ChunkMap.class)
public abstract class MapChunkSaveMixin {
    @Shadow @Final private ServerLevel level;
    @Inject(method = "save", at = @At("RETURN"))
    private void youzaiworldcore$mapSaved(ChunkAccess chunk, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) MapServerManager.chunkSaved(level, chunk.getPos());
    }
}
