package top.csituka.youzaiworldcore.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.material.FluidState;
import java.util.HashMap;
import java.util.Map;

/** 由区块文件解析的只读方块视图；不引用活动世界，不加载实体或方块实体。 */
public final class MapSavedChunk implements MapSampler.Source {
    private final SerializableChunkData data;
    private final int minimum, height;
    private final boolean ceiling, sky;
    private final Map<Integer, SerializableChunkData.SectionData> sections = new HashMap<>();
    private final int[] surface = new int[256];
    public MapSavedChunk(SerializableChunkData data, int minimum, int height, boolean ceiling, boolean sky) {
        this.data = data; this.minimum = minimum; this.height = height; this.ceiling = ceiling; this.sky = sky;
        data.sectionData().forEach(section -> sections.put(section.y(), section));
        var pos = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int y = getMaxY();
            while (y >= minimum && getBlockState(pos.set(x, y, z)).isAir()) y--;
            surface[x + z * 16] = y;
        }
    }
    public int getMinY() { return minimum; }
    public int getHeight() { return height; }
    public BlockEntity getBlockEntity(BlockPos pos) { return null; }
    public BlockState getBlockState(BlockPos pos) {
        var section = sections.get(Math.floorDiv(pos.getY(), 16));
        return section == null || section.chunkSection() == null ? Blocks.AIR.defaultBlockState()
                : section.chunkSection().getBlockState(Math.floorMod(pos.getX(), 16), Math.floorMod(pos.getY(), 16), Math.floorMod(pos.getZ(), 16));
    }
    public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    public int surface(int x, int z) { return surface[MapTileKey.pixelIndex(x, z)]; }
    public boolean ceiling() { return ceiling; }
    public Holder<Biome> biome(int x, int y, int z) {
        var section = sections.get(Math.floorDiv(y, 16));
        return section == null || section.chunkSection() == null ? data.containerFactory().defaultBiome()
                : section.chunkSection().getNoiseBiome(Math.floorMod(Math.floorDiv(x, 4), 4), Math.floorMod(Math.floorDiv(y, 4), 4), Math.floorMod(Math.floorDiv(z, 4), 4));
    }
    public int light(BlockPos pos, boolean skylight) {
        var section = sections.get(Math.floorDiv(pos.getY(), 16));
        var light = section == null ? null : skylight ? section.skyLight() : section.blockLight();
        if (light != null) return light.get(Math.floorMod(pos.getX(), 16), Math.floorMod(pos.getY(), 16), Math.floorMod(pos.getZ(), 16));
        return skylight && sky && pos.getY() > surface(pos.getX(), pos.getZ()) ? 15 : 0;
    }
}
