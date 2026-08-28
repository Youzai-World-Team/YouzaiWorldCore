package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;

/** C2S：装备指定称号；空 ID 表示卸下。 */
public record TitleEquipPayload(String titleId) implements CustomPacketPayload {
    public TitleEquipPayload {
        titleId = titleId == null ? "" : titleId;
    }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "title_equip");
    @SuppressWarnings("null")
    public static final Type<TitleEquipPayload> ID = new Type<>(IDENTIFIER);
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleEquipPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeUtf(payload.titleId(), 64),
            buf -> new TitleEquipPayload(buf.readUtf(64)));

    @SuppressWarnings("null")
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
