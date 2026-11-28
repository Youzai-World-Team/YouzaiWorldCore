package top.csituka.youzaiworldcore.mana;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.network.ManaSyncPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("null")
public class ManaTickHandler implements ServerTickEvents.StartTick {

    private static final ManaTickHandler INSTANCE = new ManaTickHandler();

    private static final Map<UUID, Integer> LAST_SYNCED_MANA = new HashMap<>();

    /** 每 20 tick 恢复 1 点魔力 */
    private static final int MANA_RECOVER_INTERVAL = 20;
    /** 每 5 tick 同步一次到客户端 */
    private static final int MANA_SYNC_INTERVAL = 5;

    private ManaTickHandler() {
    }

    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        if (server.getTickCount() % MANA_RECOVER_INTERVAL == 0) {
            ManaManager.getInstance().recover(server);
        }

        if (server.getTickCount() % MANA_SYNC_INTERVAL == 0) {
            syncManaToClients(server);
        }
    }

    private void syncManaToClients(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int mana = ManaManager.getInstance().getMana(player.level(), player.getUUID());
            Integer previous = LAST_SYNCED_MANA.put(player.getUUID(), mana);
            if (previous != null && previous == mana) {
                continue;
            }
            ServerPlayNetworking.send(player, new ManaSyncPayload(mana));
        }
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_SYNCED_MANA.remove(handler.getPlayer().getUUID()));
        DebugLogger.SERVER.debug("ManaTickHandler", "魔力恢复 Tick 处理器已注册");
    }
}
