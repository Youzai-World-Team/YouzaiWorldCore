package top.csituka.youzaiworldcore.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 单个传送锚点的数据记录。
 *
 * @param pos       锚点方块的世界坐标
 * @param dimension 锚点所在的维度
 * @param name      锚点的显示名称
 */
public record TeleportAnchorData(BlockPos pos, ResourceKey<Level> dimension, String name) {

    public static final Codec<TeleportAnchorData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlockPos.CODEC.fieldOf("pos").forGetter(TeleportAnchorData::pos),
                    ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(TeleportAnchorData::dimension),
                    Codec.STRING.fieldOf("name").forGetter(TeleportAnchorData::name)
            ).apply(instance, TeleportAnchorData::new)
    );
}
