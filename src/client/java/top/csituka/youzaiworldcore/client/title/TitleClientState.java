package top.csituka.youzaiworldcore.client.title;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.network.TitleEquipPayload;
import top.csituka.youzaiworldcore.network.TitleStatePayload;
import top.csituka.youzaiworldcore.network.TitleStateRequestPayload;
import top.csituka.youzaiworldcore.title.TitleDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 客户端只读称号快照。所有权和装备校验始终由服务端与 Api 完成。 */
public final class TitleClientState {
    private static final Map<String, TitleDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<UUID, String> EQUIPPED_PLAYERS = new LinkedHashMap<>();
    private static List<String> ownedTitleIds = List.of();
    private static String equippedTitleId = "";
    private static String message = "";
    private static boolean loading;
    private static int revision;

    private TitleClientState() {
    }

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void apply(TitleStatePayload payload) {
        DEFINITIONS.clear();
        for (TitleDefinition definition : payload.definitions())
            DEFINITIONS.put(definition.id(), definition);
        ownedTitleIds = List.copyOf(payload.ownedTitleIds());
        equippedTitleId = payload.equippedTitleId();
        EQUIPPED_PLAYERS.clear();
        for (TitleStatePayload.EquippedPlayer player : payload.equippedPlayers()) {
            EQUIPPED_PLAYERS.put(player.playerUuid(), player.titleId());
        }
        message = payload.message();
        loading = false;
        revision++;
    }

    public static void requestRefresh() {
        loading = true;
        message = "";
        revision++;
        ClientPlayNetworking.send(new TitleStateRequestPayload());
    }

    public static void equip(String titleId) {
        loading = true;
        message = "";
        revision++;
        ClientPlayNetworking.send(new TitleEquipPayload(titleId));
    }

    @SuppressWarnings("null")
    public static List<TitleDefinition> ownedDefinitions() {
        List<TitleDefinition> result = new ArrayList<>();
        for (String id : ownedTitleIds) {
            TitleDefinition definition = DEFINITIONS.get(id);
            if (definition != null)
                result.add(definition);
        }
        result.sort(Comparator.comparingInt(TitleDefinition::sortOrder).thenComparing(TitleDefinition::id));
        return result;
    }

    public static Component equippedComponent(UUID playerUuid) {
        String id = EQUIPPED_PLAYERS.get(playerUuid);
        TitleDefinition definition = id == null ? null : DEFINITIONS.get(id);
        return definition == null ? Component.empty() : definition.asComponent();
    }

    public static String equippedTitleId() {
        return equippedTitleId;
    }

    public static String message() {
        return message;
    }

    public static boolean loading() {
        return loading;
    }

    public static int revision() {
        return revision;
    }

    private static void clear() {
        DEFINITIONS.clear();
        EQUIPPED_PLAYERS.clear();
        ownedTitleIds = List.of();
        equippedTitleId = "";
        message = "";
        loading = false;
        revision++;
    }
}
