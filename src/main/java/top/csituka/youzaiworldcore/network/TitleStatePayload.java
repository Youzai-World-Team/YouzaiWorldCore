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

    // 编解码两端必须使用相同上限；否则截断读取后会把后续字段当成错误类型解析。
    private static final int MAX_DEFINITIONS = 512;
    private static final int MAX_OWNED_TITLE_IDS = 512;
    private static final int MAX_EQUIPPED_PLAYERS = 1024;
    private static final int MAX_TITLE_ID_LENGTH = 64;
    private static final int MAX_FONT_ID_LENGTH = 128;

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
        int definitionCount = Math.min(MAX_DEFINITIONS, payload.definitions().size());
        buf.writeVarInt(definitionCount);
        for (int i = 0; i < definitionCount; i++) {
            TitleDefinition title = payload.definitions().get(i);
            buf.writeUtf(limitUtf(title.id(), MAX_TITLE_ID_LENGTH), MAX_TITLE_ID_LENGTH);
            buf.writeUtf(limitUtf(title.displayName(), 64), 64);
            buf.writeEnum(title.renderType());
            buf.writeUtf(limitUtf(title.textContent(), 128), 128);
            buf.writeInt(title.textColor());
            buf.writeBoolean(title.bold());
            buf.writeBoolean(title.italic());
            buf.writeUtf(limitUtf(title.textureKey(), 128), 128);
            buf.writeUtf(limitUtf(title.fontId(), MAX_FONT_ID_LENGTH), MAX_FONT_ID_LENGTH);
            buf.writeUtf(limitUtf(title.glyph(), 16), 16);
            buf.writeVarInt(title.sortOrder());
        }
        int ownedCount = Math.min(MAX_OWNED_TITLE_IDS, payload.ownedTitleIds().size());
        buf.writeVarInt(ownedCount);
        for (int i = 0; i < ownedCount; i++)
            buf.writeUtf(limitUtf(payload.ownedTitleIds().get(i), MAX_TITLE_ID_LENGTH), MAX_TITLE_ID_LENGTH);
        buf.writeUtf(limitUtf(payload.equippedTitleId(), MAX_TITLE_ID_LENGTH), MAX_TITLE_ID_LENGTH);
        int equippedCount = Math.min(MAX_EQUIPPED_PLAYERS, payload.equippedPlayers().size());
        buf.writeVarInt(equippedCount);
        for (int i = 0; i < equippedCount; i++) {
            EquippedPlayer entry = payload.equippedPlayers().get(i);
            buf.writeUUID(entry.playerUuid());
            buf.writeUtf(limitUtf(entry.titleId(), MAX_TITLE_ID_LENGTH), MAX_TITLE_ID_LENGTH);
        }
        buf.writeUtf(limitUtf(payload.message(), 256), 256);
    }

    private static TitleStatePayload decode(RegistryFriendlyByteBuf buf) {
        int titleCount = Math.min(MAX_DEFINITIONS, Math.max(0, buf.readVarInt()));
        List<TitleDefinition> definitions = new ArrayList<>(titleCount);
        for (int i = 0; i < titleCount; i++) {
            definitions.add(new TitleDefinition(
                    buf.readUtf(MAX_TITLE_ID_LENGTH), buf.readUtf(64), buf.readEnum(TitleDefinition.RenderType.class),
                    buf.readUtf(128), buf.readInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readUtf(128), buf.readUtf(128), buf.readUtf(16), buf.readVarInt()));
        }
        int ownedCount = Math.min(MAX_OWNED_TITLE_IDS, Math.max(0, buf.readVarInt()));
        List<String> owned = new ArrayList<>(ownedCount);
        for (int i = 0; i < ownedCount; i++)
            owned.add(buf.readUtf(MAX_TITLE_ID_LENGTH));
        String equipped = buf.readUtf(MAX_TITLE_ID_LENGTH);
        int playerCount = Math.min(MAX_EQUIPPED_PLAYERS, Math.max(0, buf.readVarInt()));
        List<EquippedPlayer> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++)
            players.add(new EquippedPlayer(buf.readUUID(), buf.readUtf(MAX_TITLE_ID_LENGTH)));
        return new TitleStatePayload(definitions, owned, equipped, players, buf.readUtf(256));
    }

    private static String limitUtf(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value == null ? "" : value;
        return value.substring(0, maxLength);
    }

    @SuppressWarnings("null")
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
