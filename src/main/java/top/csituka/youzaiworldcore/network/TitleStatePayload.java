package top.csituka.youzaiworldcore.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.title.TitleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S2C：称号目录、本玩家授权和全部在线玩家的佩戴快照。 */
public record TitleStatePayload(
        List<TitleDefinition> definitions,
        List<String> ownedTitleIds,
        String equippedTitleId,
        List<EquippedPlayer> equippedPlayers,
        String message) implements CustomPacketPayload {

    public record EquippedPlayer(UUID playerUuid, String titleId) {
    }

    public TitleStatePayload {
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
        ownedTitleIds = ownedTitleIds == null ? List.of() : List.copyOf(ownedTitleIds);
        equippedTitleId = equippedTitleId == null ? "" : equippedTitleId;
        equippedPlayers = equippedPlayers == null ? List.of() : List.copyOf(equippedPlayers);
        message = message == null ? "" : message;
    }

    public static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "title_state");
    @SuppressWarnings("null")
    public static final Type<TitleStatePayload> ID = new Type<>(IDENTIFIER);
    public static final StreamCodec<RegistryFriendlyByteBuf, TitleStatePayload> STREAM_CODEC = StreamCodec.of(
            TitleStatePayload::encode,
            TitleStatePayload::decode);

    @SuppressWarnings("null")
    private static void encode(RegistryFriendlyByteBuf buf, TitleStatePayload payload) {
        buf.writeVarInt(payload.definitions().size());
        for (TitleDefinition title : payload.definitions()) {
            buf.writeUtf(title.id(), 64);
            buf.writeUtf(title.displayName(), 64);
            buf.writeEnum(title.renderType());
            buf.writeUtf(title.textContent(), 128);
            buf.writeInt(title.textColor());
            buf.writeBoolean(title.bold());
            buf.writeBoolean(title.italic());
            buf.writeUtf(title.textureKey(), 128);
            buf.writeUtf(title.fontId(), 128);
            buf.writeUtf(title.glyph(), 16);
            buf.writeVarInt(title.sortOrder());
        }
        buf.writeVarInt(payload.ownedTitleIds().size());
        for (String id : payload.ownedTitleIds())
            buf.writeUtf(id, 64);
        buf.writeUtf(payload.equippedTitleId(), 64);
        buf.writeVarInt(payload.equippedPlayers().size());
        for (EquippedPlayer entry : payload.equippedPlayers()) {
            buf.writeUUID(entry.playerUuid());
            buf.writeUtf(entry.titleId(), 64);
        }
        buf.writeUtf(payload.message(), 256);
    }

    private static TitleStatePayload decode(RegistryFriendlyByteBuf buf) {
        int titleCount = Math.min(512, Math.max(0, buf.readVarInt()));
        List<TitleDefinition> definitions = new ArrayList<>(titleCount);
        for (int i = 0; i < titleCount; i++) {
            definitions.add(new TitleDefinition(
                    buf.readUtf(64), buf.readUtf(64), buf.readEnum(TitleDefinition.RenderType.class),
                    buf.readUtf(128), buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readUtf(128), buf.readUtf(128), buf.readUtf(16), buf.readVarInt()));
        }
        int ownedCount = Math.min(512, Math.max(0, buf.readVarInt()));
        List<String> owned = new ArrayList<>(ownedCount);
        for (int i = 0; i < ownedCount; i++)
            owned.add(buf.readUtf(64));
        String equipped = buf.readUtf(64);
        int playerCount = Math.min(1024, Math.max(0, buf.readVarInt()));
        List<EquippedPlayer> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++)
            players.add(new EquippedPlayer(buf.readUUID(), buf.readUtf(64)));
        return new TitleStatePayload(definitions, owned, equipped, players, buf.readUtf(256));
    }

    @SuppressWarnings("null")
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
