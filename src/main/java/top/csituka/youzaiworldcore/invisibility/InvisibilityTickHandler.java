package top.csituka.youzaiworldcore.invisibility;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 隐身状态的 Tick 事件处理器。
 * <p>
 * 每 10 tick 检查隐身玩家是否退出了创造模式，自动关闭隐身。
 */
public class InvisibilityTickHandler implements ServerTickEvents.StartTick {

    private static final InvisibilityTickHandler INSTANCE = new InvisibilityTickHandler();

    private static final int CHECK_INTERVAL = 10;
    private static int tickCounter = 0;

    /** 上一次检查时隐身的玩家 UUID 快照 */
    private static final Set<UUID> lastSnapshot = new HashSet<>();

    private InvisibilityTickHandler() {
    }

    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;

        Set<UUID> current = InvisibilityManager.getInvisiblePlayers();

        for (UUID uuid : current) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            // 如果玩家不再是创造模式，强制关闭隐身
            if (player.gameMode() != GameType.CREATIVE) {
                player.sendSystemMessage(Component.literal(
                        "§c你已退出创造模式，隐身状态已自动关闭"
                ));
                InvisibilityManager.forceDisable(player);
            }
        }

        // 更新快照
        lastSnapshot.clear();
        lastSnapshot.addAll(current);
    }

    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);
    }
}
