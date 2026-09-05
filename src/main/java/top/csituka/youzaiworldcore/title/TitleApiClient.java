package top.csituka.youzaiworldcore.title;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import top.csituka.youzaiworldcore.api.ApiHttp;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 称号模块专用的 HMAC Api 客户端。调用方必须在异步线程执行。 */
public final class TitleApiClient {
    private static final String MODULE = "TitleApiClient";
    private static final int MAX_SYNC_PLAYERS = 200;

    private TitleApiClient() {
    }

    public record SyncRequest(String username, List<String> permissionTitleIds) {
    }

    public record PlayerSnapshot(
            String username,
            UUID uuid,
            List<String> ownedTitleIds,
            String equippedTitleId) {
    }

    public record Result(
            boolean success,
            String message,
            Map<String, TitleDefinition> definitions,
            Map<String, PlayerSnapshot> players) {
    }

    public static Result sync(List<SyncRequest> requests) {
        JsonArray players = new JsonArray();
        for (SyncRequest request : requests) {
            JsonObject entry = new JsonObject();
            entry.addProperty("username", request.username());
            JsonArray ids = new JsonArray();
            request.permissionTitleIds().forEach(ids::add);
            entry.add("permission_title_ids", ids);
            players.add(entry);
        }
        JsonObject body = new JsonObject();
        body.add("players", players);
        return parse(ApiHttp.request("POST", "/api/game/titles/sync", body.toString()), true);
    }

    /**
     * 同步任意数量的在线玩家。Api 为单次请求设置了 200 人上限，
     * 因此这里按上限分批请求，再合并称号目录和玩家快照。
     */
    public static Result syncBatched(List<SyncRequest> requests) {
        if (requests == null || requests.isEmpty()) return sync(List.of());
        Map<String, TitleDefinition> definitions = new LinkedHashMap<>();
        Map<String, PlayerSnapshot> players = new LinkedHashMap<>();
        for (int start = 0; start < requests.size(); start += MAX_SYNC_PLAYERS) {
            int end = Math.min(requests.size(), start + MAX_SYNC_PLAYERS);
            Result result = sync(requests.subList(start, end));
            if (result == null || !result.success()) {
                return result == null
                        ? new Result(false, ApiHttp.failureMessage(), Map.of(), Map.of())
                        : result;
            }
            if (result.definitions() != null) definitions.putAll(result.definitions());
            if (result.players() != null) players.putAll(result.players());
        }
        return new Result(true, "", definitions, players);
    }

    public static Result equip(String username, String titleId) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        if (titleId == null || titleId.isBlank()) body.add("title_id", com.google.gson.JsonNull.INSTANCE);
        else body.addProperty("title_id", titleId);
        return parse(ApiHttp.request("POST", "/api/game/titles/equip", body.toString()), false);
    }

    private static Result parse(HttpResponse<String> response, boolean listResponse) {
        if (response == null) {
            return new Result(false, ApiHttp.failureMessage(), Map.of(), Map.of());
        }
        JsonObject root = ApiHttp.parse(response.body());
        if (!ApiHttp.successful(response)) {
            return new Result(false, ApiHttp.responseMessage(root), Map.of(), Map.of());
        }
        try {
            Map<String, TitleDefinition> definitions = new LinkedHashMap<>();
            JsonArray titles = root.has("titles") && root.get("titles").isJsonArray()
                    ? root.getAsJsonArray("titles") : new JsonArray();
            for (JsonElement element : titles) {
                if (!element.isJsonObject()) continue;
                TitleDefinition definition = parseDefinition(element.getAsJsonObject());
                if (!definition.id().isBlank()) definitions.put(definition.id(), definition);
            }
            Map<String, PlayerSnapshot> snapshots = new LinkedHashMap<>();
            if (listResponse) {
                JsonArray array = root.has("players") && root.get("players").isJsonArray()
                        ? root.getAsJsonArray("players") : new JsonArray();
                for (JsonElement element : array) addSnapshot(snapshots, element);
            } else {
                addSnapshot(snapshots, root.get("player"));
            }
            return new Result(true, "", definitions, snapshots);
        } catch (RuntimeException e) {
            DebugLogger.exception(MODULE, "parse", e);
            return new Result(false, "Api 称号数据格式无效", Map.of(), Map.of());
        }
    }

    private static TitleDefinition parseDefinition(JsonObject value) {
        return new TitleDefinition(
                string(value, "id"),
                string(value, "display_name"),
                TitleDefinition.RenderType.parse(string(value, "render_type")),
                string(value, "text_content"),
                parseColor(string(value, "text_color")),
                bool(value, "bold"),
                bool(value, "italic"),
                string(value, "texture_key"),
                string(value, "font_id"),
                string(value, "glyph"),
                number(value, "sort_order"));
    }

    private static void addSnapshot(Map<String, PlayerSnapshot> snapshots, JsonElement element) {
        if (element == null || !element.isJsonObject()) return;
        JsonObject value = element.getAsJsonObject();
        String username = string(value, "username");
        if (username.isBlank()) return;
        UUID uuid = null;
        try {
            String rawUuid = string(value, "uuid");
            if (!rawUuid.isBlank()) uuid = UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ignored) {
        }
        List<String> owned = new ArrayList<>();
        JsonArray ids = value.has("owned_title_ids") && value.get("owned_title_ids").isJsonArray()
                ? value.getAsJsonArray("owned_title_ids") : new JsonArray();
        for (JsonElement id : ids) if (id.isJsonPrimitive()) owned.add(id.getAsString());
        snapshots.put(username.toLowerCase(java.util.Locale.ROOT), new PlayerSnapshot(
                username, uuid, List.copyOf(owned), string(value, "equipped_title_id")));
    }

    private static String string(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private static boolean bool(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static int number(JsonObject value, String key) {
        JsonElement element = value.get(key);
        return element == null || element.isJsonNull() ? 0 : element.getAsInt();
    }

    private static int parseColor(String value) {
        try {
            return Integer.parseInt(value.replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return 0xFFFFFF;
        }
    }
}
