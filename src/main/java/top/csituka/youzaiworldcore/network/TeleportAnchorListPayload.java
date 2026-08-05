package top.csituka.youzaiworldcore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C 数据包：服务端将玩家的活跃传送点列表发送给客户端，用于打开传送 GUI。
 *
 * @param points       玩家的活跃传送点列表
 * @param currentPos   当前右键的锚点位置（用于禁用传送按钮），null 表示无限制
 * @param currentDim   当前右键的锚点维度
 * @param entryType    本次列表由哪种入口打开（{@link EntryType#ANCHOR} / {@link EntryType#STONE} /
 *                     {@link EntryType#SCROLL}），决定传送结算要扣哪种资源
 * @param entryHand    本次列表由传送石/卷轴打开时，玩家握持该物品的那只手；
 *                     {@code null} 表示走的是传送锚点方块入口
 *                     （{@link EntryType#ANCHOR}，不消耗物品）
 */
@SuppressWarnings("null")
public record TeleportAnchorListPayload(List<TeleportAnchorData> points,
                                         @Nullable BlockPos currentPos,
                                         @Nullable ResourceKey<Level> currentDim,
                                         EntryType entryType,
                                         @Nullable InteractionHand entryHand) implements CustomPacketPayload {

    /**
     * 列表入口类型。
     * <p>
     * 决定传送处理器（{@code ModNetworking}）拿到该标记后要走哪条结算路径：
     * <ul>
     *   <li>{@link #ANCHOR}：传送锚点方块入口，无资源消耗，仅扣经验</li>
     *   <li>{@link #STONE}：传送石入口，扣传送石耐久 + 60 秒物品冷却</li>
     *   <li>{@link #SCROLL}：传送卷轴入口，扣 1 张卷轴 + 120 秒物品冷却，不扣耐久与经验</li>
     * </ul>
     */
    public enum EntryType {
        /** 由传送锚点方块打开，无消耗。 */
        ANCHOR,
        /** 由传送石打开，扣耐久 + 60 秒冷却。 */
        STONE,
        /** 由传送卷轴打开，扣 1 张卷轴 + 120 秒冷却。 */
        SCROLL;

        /**
         * 从字节反序列化（用于 NBT 流编解码）。
         *
         * @param b 0=ANCHOR, 1=STONE, 2=SCROLL；越界视为 ANCHOR 兜底
         */
        public static EntryType fromByte(byte b) {
            EntryType[] vals = values();
            int idx = Byte.toUnsignedInt(b);
            return idx < vals.length ? vals[idx] : ANCHOR;
        }

        /** 序列化到字节。 */
        public byte toByte() {
            return (byte) ordinal();
        }
    }

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
                    EntryType entryType = EntryType.ANCHOR;
                    InteractionHand entryHand = null;
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
                            String poolId = entry.getStringOr("poolId", "");
                            if (poolId.isEmpty()) poolId = null;
                            points.add(new TeleportAnchorData(pos, dimension, name, poolId));
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
                        if (tag.contains("entryType")) {
                            entryType = EntryType.fromByte(tag.getByteOr("entryType", (byte) 0));
                        }
                        if (tag.contains("entryHand")) {
                            entryHand = tag.getBooleanOr("entryHand", false)
                                    ? InteractionHand.OFF_HAND
                                    : InteractionHand.MAIN_HAND;
                        }
                    }
                    return new TeleportAnchorListPayload(points, currentPos, currentDim, entryType, entryHand);
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
                        if (point.poolId() != null) {
                            entry.putString("poolId", point.poolId());
                        }
                        list.add(entry);
                    }
                    tag.put("points", list);
                    if (payload.currentPos() != null) {
                        tag.putLong("currentPos", payload.currentPos().asLong());
                    }
                    if (payload.currentDim() != null) {
                        tag.putString("currentDim", payload.currentDim().identifier().toString());
                    }
                    // 入口类型：ANCHOR/STONE/SCROLL，用单字节编码（ordinal）
                    tag.putByte("entryType", payload.entryType().toByte());
                    if (payload.entryHand() != null) {
                        // 只需要区分主手 / 副手，用一个布尔位表示：true = 副手
                        tag.putBoolean("entryHand", payload.entryHand() == InteractionHand.OFF_HAND);
                    }
                    buf.writeNbt(tag);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
