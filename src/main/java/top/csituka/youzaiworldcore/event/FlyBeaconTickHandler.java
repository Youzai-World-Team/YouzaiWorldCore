package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.block.entity.FlyBeaconBlockEntity;
import top.csituka.youzaiworldcore.item.tool.VoidStaffItem;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FlyBeaconTickHandler implements ServerTickEvents.StartTick {

    private static final FlyBeaconTickHandler INSTANCE = new FlyBeaconTickHandler();
    private static final int BEACON_RADIUS = 10;
    private static final int CHECK_INTERVAL = 10;
    private static final Set<UUID> beaconFlyingPlayers = new HashSet<>();
    private static int tickCounter = 0;

    private FlyBeaconTickHandler() {
    }

    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        Set<BlockPos> activeBeacons = FlyBeaconBlockEntity.getActiveBeacons();

        Set<UUID> currentAffected = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }

            UUID playerId = player.getUUID();

            boolean inRange = false;
            for (BlockPos beaconPos : activeBeacons) {
                double dx = player.getX() - (beaconPos.getX() + 0.5);
                double dz = player.getZ() - (beaconPos.getZ() + 0.5);
                double horizontalDist = Math.sqrt(dx * dx + dz * dz);

                if (horizontalDist <= BEACON_RADIUS && player.getY() >= beaconPos.getY()) {
                    inRange = true;
                    break;
                }
            }

            if (inRange) {
                currentAffected.add(playerId);
                if (!beaconFlyingPlayers.contains(playerId)) {
                    if (!VoidStaffItem.isFlying(playerId)) {
                        VoidStaffItem.enableFlight(player);
                    }
                }
            } else {
                if (beaconFlyingPlayers.contains(playerId) && !VoidStaffItem.isFlying(playerId)) {
                    VoidStaffItem.disableFlight(player);
                }
            }
        }

        beaconFlyingPlayers.clear();
        beaconFlyingPlayers.addAll(currentAffected);
    }

    public static boolean isBeaconFlying(UUID playerId) {
        return !beaconFlyingPlayers.contains(playerId);
    }

    public static void removePlayer(UUID playerId) {
        beaconFlyingPlayers.remove(playerId);
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);
    }
}
