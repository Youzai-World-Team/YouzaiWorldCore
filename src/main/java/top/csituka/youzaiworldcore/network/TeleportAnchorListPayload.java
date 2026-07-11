package top.csituka.youzaiworldcore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 数据包：服务端将玩家的活跃传送点列表发送给客户端，用于打开传送 GUI。
 *
 * @param points          玩家的活跃传送点列表
 * @param currentPos      当前右键的锚点位置（用于禁用传送按钮），null 表示无限制
 * @param currentDim      当前右键的锚点维度
 */
public record TeleportAnchorListPayload(List<TeleportAnchorData> points,
                                         @Nullable BlockPos currentPos,
                                         @Nullable ResourceKey<Level> currentDim) implements CustomPacketPayload {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "teleport_anchor_list");

    public static final Type<TeleportAnchorListPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TeleportAnchorListPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TeleportAnchorListPayload decode(RegistryFriendlyByteBuf buf) {
                    CompoundTag tag = buf.readNbt();
                    List<TeleportAnchorData> points = new ArrayList<>();
                    BlockPos currentPos = null;
                    ResourceKey<Level> currentDim = null;
                    if (tag != null && tag.contains("points")) {
                        ListTag list = tag.getListOrEmpty("points");
                        for (int i = 0; i < list.size(); i++) {
                            CompoundTag entry = list.getCompoundOrEmpty(i);
                            BlockPos pos = BlockPos.of(entry.getLongOr("pos", 0L));
                            String dimStr = entry.getStringOr("dimension", "minecraft:overworld");
                            Identifier dimId = Identifier.parse(dimStr);
                            ResourceKey<Level> dimension = ResourceKey.create(
                                    net.minecraft.core.registries.Registries.DIMENSION, dimId);
                            String name = entry.getStringOr("name", "传送点");
                            points.add(new TeleportAnchorData(pos, dimension, name));
                        }
                        if (tag.contains("currentPos")) {
                            currentPos = BlockPos.of(tag.getLongOr("currentPos", 0L));
                        }
                        if (tag.contains("currentDim")) {
                            String dimStr = tag.getStringOr("currentDim", "");
                            if (!dimStr.isEmpty()) {
                                currentDim = ResourceKey.create(
                                        net.minecraft.core.registries.Registries.DIMENSION,
                                        Identifier.parse(dimStr));
                            }
                        }
                    }
                    return new TeleportAnchorListPayload(points, currentPos, currentDim);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, TeleportAnchorListPayload payload) {
                    CompoundTag tag = new CompoundTag();
                    ListTag list = new ListTag();
                    for (TeleportAnchorData point : payload.points()) {
                        CompoundTag entry = new CompoundTag();
                        entry.putLong("pos", point.pos().asLong());
                        entry.putString("dimension", point.dimension().identifier().toString());
                        entry.putString("name", point.name());
                        list.add(entry);
                    }
                    tag.put("points", list);
                    if (payload.currentPos() != null) {
                        tag.putLong("currentPos", payload.currentPos().asLong());
                    }
                    if (payload.currentDim() != null) {
                        tag.putString("currentDim", payload.currentDim().identifier().toString());
                    }
                    buf.writeNbt(tag);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
