package top.csituka.youzaiworldcore.account.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.util.AuthHelper;
import top.csituka.youzaiworldcore.account.util.AuthLocationData;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;

import java.net.SocketAddress;
import java.time.ZonedDateTime;
import java.util.Set;

/**
 * PlayerList Mixin — 处理玩家加入/离开
 *
 * 注意：不能声明 AuthPlayerHelper 类型的局部变量，
 * 改用其静态辅助方法访问。
 */
@Mixin(PlayerList.class)
public abstract class AccountPlayerListMixin {

    @Shadow @Final
    private MinecraftServer server;

    /**
     * 玩家加入前检查
     */
    @Inject(method = "placeNewPlayer", at = @At("HEAD"))
    private void onPlayerPreJoin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        String username = player.getScoreboardName();
        YouzaiworldCore.LOGGER.info("玩家 {} 正在加入服务器...", username);

        AccountDataStorage.getOrCreate(username, player.getUUID());
        PlayerAccount account = AccountDataStorage.get(username);
        AuthPlayerHelper.setAccount(player, account != null ? account : new PlayerAccount(username));
        AuthPlayerHelper.setIpAddress(player, AuthHelper.getIp(connection.getRemoteAddress()));
        AuthPlayerHelper.setCanSkipAuth(player, player.getClass() != ServerPlayer.class);

        // 如果账户中有持久化的位置（来自 logout），恢复它，不覆盖
        if (account != null && account.lastPositionJson != null && !account.lastPositionJson.isBlank()) {
            AuthLocationData persistedLoc = AuthLocationData.fromJson(account.lastPositionJson);
            if (persistedLoc != null) {
                AuthPlayerHelper.setLastLocation(player, persistedLoc);
                YouzaiworldCore.LOGGER.info("已从持久化数据恢复玩家 {} 的位置", username);
            }
        } else {
            // 首次加入或已清除缓存，保存当前位置
            AuthPlayerHelper.saveLocation(player);
        }

        if (!AuthPlayerHelper.canSkipAuth(player) && account != null && account.isRegistered()
                && !account.lastIp.isEmpty()
                && AuthHelper.sameIp(account.lastIp, AuthPlayerHelper.getIpAddress(player))
                && AccountDataStorage.getSessionTimeout() > 0
                && account.lastAuthenticatedDate.plusSeconds(AccountDataStorage.getSessionTimeout()).isAfter(ZonedDateTime.now())) {
            YouzaiworldCore.LOGGER.info("玩家 {} 通过会话自动登录", username);
            AuthPlayerHelper.setAuthenticated(player, true);
            account.lastAuthenticatedDate = ZonedDateTime.now();
            account.lastIp = AuthPlayerHelper.getIpAddress(player);
            AccountDataStorage.update(account);
        }
    }

    /**
     * 玩家完全加入后
     */
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void onPlayerPostJoin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (AuthPlayerHelper.canSkipAuth(player)) return;

        if (AuthPlayerHelper.isAuthenticated(player)) {
            AuthPlayerHelper.restoreLocation(player);
            player.sendSystemMessage(Component.literal("§a会话已恢复，欢迎回来！"));
            return;
        }

        teleportToVoid(player);

        PlayerAccount account = AuthPlayerHelper.getAccount(player);
        if (account != null && account.isRegistered()) {
            player.sendSystemMessage(Component.literal("§e=== 账户认证 ==="));
            player.sendSystemMessage(Component.literal("§e请使用 §6/yzwc account login <密码> §e登录"));
        } else {
            player.sendSystemMessage(Component.literal("§e=== 账户认证 ==="));
            player.sendSystemMessage(Component.literal("§e请使用 §6/yzwc account register <密码> <确认密码> §e注册"));
        }
    }

    /**
     * 玩家断开连接时
     */
    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerLeave(ServerPlayer player, CallbackInfo ci) {
        if (AuthPlayerHelper.canSkipAuth(player)) return;

        PlayerAccount account = AuthPlayerHelper.getAccount(player);

        if (AuthPlayerHelper.isAuthenticated(player)) {
            if (account != null) {
                account.lastAuthenticatedDate = ZonedDateTime.now();
                account.lastIp = AuthPlayerHelper.getIpAddress(player);
                // 保存玩家当前位置（从实体坐标读取，不是从 mixin 缓存，因为玩家可能已经移动过）
                AuthLocationData loc = new AuthLocationData();
                loc.position = player.position();
                loc.dimension = player.level().dimension();
                loc.yaw = player.getYRot();
                loc.pitch = player.getXRot();
                account.lastPositionJson = loc.toJson();
                AccountDataStorage.update(account);
            }
        } else {
            // 未认证玩家断开时，如果 mixin 中有有效位置（来自上次登录或首次加入），持久化保存
            AuthLocationData loc = AuthPlayerHelper.getLastLocation(player);
            if (loc != null && loc.position != null && account != null && !isVoidLocation(loc)) {
                account.lastPositionJson = loc.toJson();
                AccountDataStorage.update(account);
            }
        }
    }

    /**
     * 检查玩家能否加入
     */
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void onCanPlayerLogin(SocketAddress address, NameAndId profile, CallbackInfoReturnable<Component> cir) {
        String username = profile.name();
        YouzaiworldCore.LOGGER.debug("检查玩家 {} 的登录权限", username);
    }

    @Unique
    private static boolean isVoidLocation(AuthLocationData loc) {
        if (loc.dimension == null) return false;
        String dim = loc.dimension.identifier().toString();
        if (!"minecraft:the_end".equals(dim)) return false;
        return Math.abs(loc.position.x) < 1 && Math.abs(loc.position.y + 60) < 1 && Math.abs(loc.position.z) < 1;
    }

    @Unique
    private void teleportToVoid(ServerPlayer player) {
        ServerLevel endWorld = server.getLevel(Level.END);
        if (endWorld == null) endWorld = server.overworld();
        player.teleportTo(endWorld, 0, -60, 0, Set.of(), 0, 0, true);
    }
}
