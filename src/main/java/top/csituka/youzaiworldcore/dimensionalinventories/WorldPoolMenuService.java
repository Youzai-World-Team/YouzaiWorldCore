package top.csituka.youzaiworldcore.dimensionalinventories;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.network.WorldPoolMenuRequestPayload;
import top.csituka.youzaiworldcore.network.WorldPoolMenuStatePayload;
import top.csituka.youzaiworldcore.network.WorldPoolTeleportResultPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 世界菜单读取与切换反馈；在服务端主线程调用，沿用维度池权威校验。 */
public final class WorldPoolMenuService {
    private WorldPoolMenuService() { }

    public static void handle(ServerPlayer player, WorldPoolMenuRequestPayload request) {
        if (player.hasDisconnected()) return;
        if (AuthPlayerHelper.shouldBlockActions(player)) {
            ServerPlayNetworking.send(player, new WorldPoolTeleportResultPayload(request.requestId(), false,
                    Component.translatable("screen.youzaiworldcore.world_menu.login_required")));
            return;
        }
        if (request.poolId().isEmpty()) {
            sendState(player, request);
            return;
        }
        AtomicReference<Component> message = new AtomicReference<>(
                Component.translatable("screen.youzaiworldcore.world_menu.failed"));
        boolean success;
        try {
            success = DimensionPoolManager.teleportToPool(player, request.poolId(), message::set);
        } catch (RuntimeException error) {
            DebugLogger.exception("WorldPoolMenu", "切换世界失败", error);
            message.set(Component.translatable("screen.youzaiworldcore.world_menu.failed"));
            success = false;
        }
        if (!player.hasDisconnected()) {
            ServerPlayNetworking.send(player, new WorldPoolTeleportResultPayload(request.requestId(), success, message.get()));
            sendState(player, request);
        }
    }

    private static void sendState(ServerPlayer player, WorldPoolMenuRequestPayload request) {
        var current = DimensionPoolSettings.getPoolByDimension(
                player.level().dimension().identifier().toString())
                .filter(pool -> !DimensionPoolSettings.isHiddenPool(pool.id())).orElse(null);
        List<WorldPoolMenuStatePayload.Entry> entries = DimensionPoolSettings.getAllPools().stream().limit(128)
                .map(pool -> new WorldPoolMenuStatePayload.Entry(pool.id(), pool.displayName(),
                        pool.gameMode().getName(), !pool.isEmpty())).toList();
        ServerPlayNetworking.send(player, new WorldPoolMenuStatePayload(request.requestId(),
                current == null ? "" : current.id(), current == null ? "" : current.displayName(), entries));
        DebugLogger.debug("WorldPoolMenu", "已同步 %d 个公开世界", entries.size());
    }
}
