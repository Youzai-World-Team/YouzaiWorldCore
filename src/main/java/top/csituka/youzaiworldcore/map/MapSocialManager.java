package top.csituka.youzaiworldcore.map;

import com.google.gson.JsonArray;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.UserSettings;
import top.csituka.youzaiworldcore.network.MapSocialPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 服务端权威的位置共享名单；存于玩家 user_settings 的 map_module 分节，请求仅在本次开服有效。 */
public final class MapSocialManager {
    private static final Map<UUID, Rules> RULES = new HashMap<>();
    private static final Map<UUID, Integer> LAST = new HashMap<>();
    private static final class Rules {
        boolean blacklist;
        final Set<UUID> allowed = new LinkedHashSet<>(), blocked = new LinkedHashSet<>(), incoming = new LinkedHashSet<>();
    }
    private MapSocialManager() { }
    private static Rules rules(UUID id) {
        return RULES.computeIfAbsent(id, key -> {
            var section = UserSettings.section(key, GlobalSettings.MAP_MODULE); var rules = new Rules();
            rules.blacklist = section.getBoolean("position_blacklist_mode", true);
            for (String name : List.of("position_allowed", "position_blocked")) {
                var entries = section.getStringList(name, List.of());
                if (entries.size() > 256) section.fail(name, "共享名单不可超过 256 人");
                var target = name.equals("position_allowed") ? rules.allowed : rules.blocked;
                for (String entry : entries) {
                    try { if (!target.add(UUID.fromString(entry))) section.fail(name, "共享名单身份重复"); }
                    catch (IllegalArgumentException invalid) { section.fail(name, "玩家身份必须为 UUID"); }
                }
            }
            return rules;
        });
    }
    /** 每个接收者分别判断，禁止把被拒绝的位置先发送给客户端再隐藏。 */
    public static boolean visibleTo(UUID source, UUID receiver) {
        if (source.equals(receiver)) return true;
        var value = rules(source);
        return !value.blocked.contains(receiver) && (value.blacklist || value.allowed.contains(receiver));
    }
    private static void save(UUID id, Rules rules) {
        var section = UserSettings.section(id, GlobalSettings.MAP_MODULE);
        section.set("position_blacklist_mode", rules.blacklist);
        JsonArray allowed = new JsonArray(), blocked = new JsonArray();
        rules.allowed.forEach(value -> allowed.add(value.toString())); rules.blocked.forEach(value -> blocked.add(value.toString()));
        section.set("position_allowed", allowed); section.set("position_blocked", blocked); UserSettings.save(id);
    }
    /** 经地图模块认证后执行；不会信任客户端随包携带的名单。 */
    public static void handle(ServerPlayer player, MapSocialPayload payload) {
        var server = player.level().getServer(); if (server == null) return;
        int tick = server.getTickCount();
        if (tick - LAST.getOrDefault(player.getUUID(), -1000) < 4) return;
        LAST.put(player.getUUID(), tick);
        var value = rules(player.getUUID()); String result = "saved"; boolean changed = false;
        UUID target = payload.target();
        switch (payload.action()) {
            case FETCH -> { send(player, ""); return; }
            case STATE -> { return; }
            case MODE -> { value.blacklist = payload.blacklist(); changed = true; }
            case REQUEST -> {
                var other = server.getPlayerList().getPlayer(target);
                if (other == null || target.equals(player.getUUID()) || !MapServerManager.socialEligible(other)) { send(player, "denied"); return; }
                var recipient = rules(target);
                if (recipient.blocked.contains(player.getUUID()) || recipient.incoming.size() >= 32) { send(player, "denied"); return; }
                if (recipient.incoming.add(player.getUUID())) send(other, "share_request");
                result = "request_sent";
            }
            case ALLOW, ACCEPT -> {
                if (payload.action() == MapSocialPayload.Action.ACCEPT && !value.incoming.contains(target)) { send(player, "denied"); return; }
                if (payload.action() == MapSocialPayload.Action.ALLOW && (server.getPlayerList().getPlayer(target) == null || target.equals(player.getUUID()))) { send(player, "denied"); return; }
                if (value.allowed.size() >= 256 && !value.allowed.contains(target)) { send(player, "limit"); return; }
                value.incoming.remove(target); value.blocked.remove(target); value.allowed.add(target); changed = true;
            }
            case REMOVE -> { value.allowed.remove(target); changed = true; }
            case REJECT -> value.incoming.remove(target);
            case BLOCK -> {
                if (target.equals(player.getUUID()) || value.blocked.size() >= 256 && !value.blocked.contains(target)) { send(player, "limit"); return; }
                value.incoming.remove(target); value.allowed.remove(target); value.blocked.add(target); changed = true;
            }
            case UNBLOCK -> { value.blocked.remove(target); changed = true; }
        }
        if (changed) save(player.getUUID(), value);
        DebugLogger.info("MapSocial", "位置共享操作：%s / %s", player.getUUID(), payload.action());
        send(player, result);
    }
    private static void send(ServerPlayer player, String result) {
        if (!ServerPlayNetworking.canSend(player, MapSocialPayload.ID)) return;
        var value = rules(player.getUUID());
        ServerPlayNetworking.send(player, new MapSocialPayload(MapSocialPayload.Action.STATE, player.getUUID(), value.blacklist,
                List.copyOf(value.allowed), List.copyOf(value.blocked), List.copyOf(value.incoming), result));
    }
    /** 停服时清理请求与缓存，防止跨存档串用。 */
    public static void clear() { RULES.clear(); LAST.clear(); }
}
