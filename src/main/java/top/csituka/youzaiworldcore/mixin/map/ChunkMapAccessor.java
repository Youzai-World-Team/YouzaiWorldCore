package top.csituka.youzaiworldcore.mixin.map;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 只读访问已可见区块的加载票等级，不创建加载票或触发区块生成。 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Invoker("getVisibleChunkIfPresent")
    ChunkHolder youzaiworldcore$getVisibleChunk(long position);
}
