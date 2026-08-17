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

    /** 恢复计数器 */
    private static int recoverTickCounter = 0;
    /** 同步计数器 */
    private static int syncTickCounter = 0;
    private static final Map<UUID, Integer> LAST_SYNCED_MANA = new HashMap<>();

    /** 每 2 tick 恢复 1 点魔力 */
    private static final int MANA_RECOVER_INTERVAL = 2;
    /** 每 5 tick 同步一次到客户端 */
    private static final int MANA_SYNC_INTERVAL = 5;

    private ManaTickHandler() {
    }

    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        // 魔力恢复（每 2 tick 一次）
        recoverTickCounter++;
        if (recoverTickCounter >= MANA_RECOVER_INTERVAL) {
            recoverTickCounter = 0;
            ManaManager.getInstance().onServerTick();
        }

        // 同步魔力到客户端（每 5 tick 一次）
        syncTickCounter++;
        if (syncTickCounter >= MANA_SYNC_INTERVAL) {
            syncTickCounter = 0;
            syncManaToClients(server);
        }
    }

    private void syncManaToClients(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int mana = ManaManager.getInstance().getMana(player.getUUID());
            Integer previous = LAST_SYNCED_MANA.put(player.getUUID(), mana);
            if (previous != null && previous == mana) {
                continue;
            }
            ServerPlayNetworking.send(player, new ManaSyncPayload(mana));
        }
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);
        ManaManager.registerEvents();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                LAST_SYNCED_MANA.remove(handler.getPlayer().getUUID()));
        DebugLogger.info("ManaTickHandler", "魔力恢复 Tick 处理器已注册");
    }
}
