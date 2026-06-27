package top.csituka.youzaiworldcore.account.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.data.PlayerAuthAccess;

import java.util.Set;

/**
 * 玩家认证状态辅助类
 *
 * 通过 PlayerAuthAccess 接口访问 AccountServerPlayerMixin 混合数据，
 * 避免 Mixin 0.8.7 的"禁止外部直接引用 mixin 类"限制。
 */
public class AuthPlayerHelper {

    private static PlayerAuthAccess access(ServerPlayer player) {
        return (PlayerAuthAccess) (Object) player;
    }

    public static boolean isAuthenticated(ServerPlayer player) {
        return access(player).yzwc$isAuthenticated();
    }

    public static boolean canSkipAuth(ServerPlayer player) {
        return access(player).yzwc$canSkipAuth();
    }

    public static boolean shouldBlockActions(ServerPlayer player) {
        PlayerAuthAccess a = access(player);
        return !a.yzwc$isAuthenticated() && !a.yzwc$canSkipAuth();
    }

    public static void setAuthenticated(ServerPlayer player, boolean authenticated) {
        access(player).yzwc$setAuthenticated(authenticated);
    }

    public static void setCanSkipAuth(ServerPlayer player, boolean canSkip) {
        access(player).yzwc$setCanSkipAuth(canSkip);
    }

    public static PlayerAccount getAccount(ServerPlayer player) {
        return access(player).yzwc$getAccount();
    }

    public static void setAccount(ServerPlayer player, PlayerAccount account) {
        access(player).yzwc$setAccount(account);
    }

    public static String getIpAddress(ServerPlayer player) {
        return access(player).yzwc$getIpAddress();
    }

    public static void setIpAddress(ServerPlayer player, String ip) {
        access(player).yzwc$setIpAddress(ip);
    }

    public static AuthLocationData getLastLocation(ServerPlayer player) {
        return access(player).yzwc$getLastLocation();
    }

    public static void setLastLocation(ServerPlayer player, AuthLocationData loc) {
        access(player).yzwc$setLastLocation(loc);
    }

    public static void saveLocation(ServerPlayer player) {
        access(player).yzwc$saveLocation();
    }

    public static void restoreLocation(ServerPlayer player) {
        AuthLocationData loc = getLastLocation(player);
        if (loc == null || loc.position == null) {
            YouzaiworldCore.LOGGER.warn("玩家 {} 没有保存的位置，传送到主世界出生点", player.getScoreboardName());
            teleportToSpawn(player);
            return;
        }

        // 如果位置是虚空坐标，也传送到出生点
        if (isVoidLocation(loc)) {
            YouzaiworldCore.LOGGER.warn("玩家 {} 的位置在虚空中，传送到主世界出生点", player.getScoreboardName());
            teleportToSpawn(player);
            return;
        }

        MinecraftServer server = player.level().getServer();
        if (server == null) return;

        ServerLevel targetWorld = loc.dimension != null
                ? server.getLevel(loc.dimension)
                : server.overworld();
        if (targetWorld == null) targetWorld = server.overworld();

        player.teleportTo(targetWorld, loc.position.x, loc.position.y, loc.position.z, Set.of(), loc.yaw, loc.pitch, true);
        YouzaiworldCore.LOGGER.info("已将玩家 {} 恢复到原位置", player.getScoreboardName());
    }

    private static boolean isVoidLocation(AuthLocationData loc) {
        if (loc.dimension == null) return false;
        String dim = loc.dimension.identifier().toString();
        if (!"minecraft:the_end".equals(dim)) return false;
        return Math.abs(loc.position.x) < 1 && Math.abs(loc.position.y + 60) < 1 && Math.abs(loc.position.z) < 1;
    }

    private static void teleportToSpawn(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        var overworld = server.overworld();
        var spawnPos = overworld.getRespawnData().pos();
        player.teleportTo(overworld, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), Set.of(), player.getYRot(), player.getXRot(), true);
        YouzaiworldCore.LOGGER.info("已将玩家 {} 传送至主世界出生点", player.getScoreboardName());
    }
}
