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

/**
 * 飞行信标（Fly Beacon）的 Tick 事件处理器。
 * 每游戏刻检查玩家是否处于激活信标的范围内，并自动为玩家开启/关闭飞行能力。
 * 实现 Fabric API 的 ServerTickEvents.StartTick 接口。
 */
public class FlyBeaconTickHandler implements ServerTickEvents.StartTick {

    // 单例实例
    private static final FlyBeaconTickHandler INSTANCE = new FlyBeaconTickHandler();

    // 信标的有效范围半径（水平方向，单位：方块）
    private static final int BEACON_RADIUS = 10;

    // 检查间隔（游戏刻），每 10 刻（0.5 秒）执行一次范围判断，降低性能开销
    private static final int CHECK_INTERVAL = 10;

    // 当前正在通过信标获得飞行能力的玩家 UUID 集合
    private static final Set<UUID> beaconFlyingPlayers = new HashSet<>();

    // 辅助计次器，用于实现间隔检查
    private static int tickCounter = 0;

    // 私有构造，确保单例
    private FlyBeaconTickHandler() {
    }

    /**
     * 每个游戏刻开始时触发（实际按 CHECK_INTERVAL 间隔执行逻辑）。
     *
     * @param server Minecraft 服务器实例
     */
    @Override
    public void onStartTick(@NonNull MinecraftServer server) {
        tickCounter++;
        // 未达到检查间隔则直接返回
        if (tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0; // 重置计数器

        // 获取当前所有已激活信标的方块坐标
        Set<BlockPos> activeBeacons = FlyBeaconBlockEntity.getActiveBeacons();

        // 记录本次 tick 中应被信标赋予飞行的玩家 UUID
        Set<UUID> currentAffected = new HashSet<>();

        // 遍历所有在线玩家
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 创造模式或旁观者模式的玩家不受信标影响
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }

            UUID playerId = player.getUUID();
            boolean inRange = false;

            // 检查玩家是否处于任意激活信标的有效范围内
            for (BlockPos beaconPos : activeBeacons) {
                // 计算玩家与信标中心的水平距离（信标方块中心为 x+0.5, z+0.5）
                double dx = player.getX() - (beaconPos.getX() + 0.5);
                double dz = player.getZ() - (beaconPos.getZ() + 0.5);
                double horizontalDist = Math.sqrt(dx * dx + dz * dz);

                // 水平距离 ≤ 半径 且 玩家 Y 坐标 ≥ 信标 Y 坐标（玩家必须在信标同一高度或上方）
                if (horizontalDist <= BEACON_RADIUS && player.getY() >= beaconPos.getY()) {
                    inRange = true;
                    break;
                }
            }

            if (inRange) {
                // 玩家在信标范围内：记录并开启飞行能力（如果尚未通过信标开启且未通过其他方式飞行）
                currentAffected.add(playerId);
                if (!beaconFlyingPlayers.contains(playerId)) {
                    if (!VoidStaffItem.isFlying(playerId)) {
                        VoidStaffItem.enableFlight(player);
                    }
                }
            } else {
                // 玩家离开信标范围：如果之前通过此信标获得了飞行能力且当前没有其他飞行状态，则关闭飞行
                if (beaconFlyingPlayers.contains(playerId) && !VoidStaffItem.isFlying(playerId)) {
                    VoidStaffItem.disableFlight(player);
                }
            }
        }

        // 更新信标飞行玩家集合为本次检查的结果
        beaconFlyingPlayers.clear();
        beaconFlyingPlayers.addAll(currentAffected);
    }

    /**
     * 判断指定玩家当前是否正在通过信标获得飞行能力。
     * 注意：方法名可能暗示返回 true 表示正在飞行，但实际实现逻辑相反（返回 !contains）。
     * 保留原业务逻辑，使用时请留意。
     *
     * @param playerId 玩家 UUID
     * @return true 表示玩家不在信标飞行集合中（即没有通过信标飞行），false 表示在集合中
     */
    public static boolean isBeaconFlying(UUID playerId) {
        return !beaconFlyingPlayers.contains(playerId);
    }

    /**
     * 从信标飞行玩家集合中移除指定玩家。
     * 通常在玩家退出服务器或重置状态时调用。
     *
     * @param playerId 玩家 UUID
     */
    public static void removePlayer(UUID playerId) {
        beaconFlyingPlayers.remove(playerId);
    }

    /**
     * 向 Fabric 事件总线注册此处理器。
     */
    public static void register() {
        ServerTickEvents.START_SERVER_TICK.register(INSTANCE);
    }
}