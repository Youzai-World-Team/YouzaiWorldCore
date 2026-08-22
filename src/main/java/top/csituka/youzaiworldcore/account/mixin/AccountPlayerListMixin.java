package top.csituka.youzaiworldcore.account.mixin;

import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import top.csituka.youzaiworldcore.api.ApiServiceClient;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.data.PlayerAuthAccess;
import top.csituka.youzaiworldcore.account.data.RegistrationEmailSessionStore;
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
@SuppressWarnings("null")
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
        RegistrationEmailSessionStore.clear(player.getUUID());
        YouzaiworldCore.LOGGER.info("玩家 {} 正在加入服务器...", username);

        PlayerAccount account = AccountDataStorage.ensureRemoteAccount(username, player.getUUID());
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

        // 每次加入服务器都必须重新输入密码。
    }

    /**
     * 玩家完全加入后
     */
    @Inject(method = "placeNewPlayer", at = @At("RETURN"))
    private void onPlayerPostJoin(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (AuthPlayerHelper.canSkipAuth(player)) return;

        teleportToVoid(player);

        PlayerAccount account = AuthPlayerHelper.getAccount(player);
        ((PlayerAuthAccess) (Object) player).yzwc$setSessionToken(null);
        if (account != null && account.isRegistered()) {
            player.sendSystemMessage(Component.translatable("youzaiworldcore.message.account.auth_header"));
            player.sendSystemMessage(Component.translatable("youzaiworldcore.message.account.prompt_login"));
        } else {
            player.sendSystemMessage(Component.translatable("youzaiworldcore.message.account.auth_header"));
            player.sendSystemMessage(Component.translatable("youzaiworldcore.message.account.prompt_register"));
        }
    }

    /**
     * 玩家断开连接时
     */
    @Inject(method = "remove", at = @At("HEAD"))
    private void onPlayerLeave(ServerPlayer player, CallbackInfo ci) {
        if (AuthPlayerHelper.canSkipAuth(player)) return;

        PlayerAuthAccess authPlayer = (PlayerAuthAccess) (Object) player;
        RegistrationEmailSessionStore.clear(player.getUUID());
        ApiServiceClient.deleteSession(authPlayer.yzwc$getSessionToken());
        authPlayer.yzwc$setSessionToken(null);
        PlayerAccount account = AuthPlayerHelper.getAccount(player);

        if (account == null) return;
        AuthLocationData loc;
        if (AuthPlayerHelper.isAuthenticated(player)) {
            loc = new AuthLocationData();
            loc.position = player.position();
            loc.dimension = player.level().dimension();
            loc.yaw = player.getYRot();
            loc.pitch = player.getXRot();
        } else {
            loc = AuthPlayerHelper.getLastLocation(player);
        }
        if (AuthPlayerHelper.isAuthenticated(player)) {
            if (loc != null && loc.position != null && !AuthPlayerHelper.isVoidLocation(loc)) {
                account.lastPositionJson = loc.toJson();
            }
            account.lastIp = AuthPlayerHelper.getIpAddress(player);
            account.lastAuthenticatedDate = ZonedDateTime.now();
            AccountDataStorage.updateForDisconnect(account);
        } else if (loc != null && loc.position != null && !AuthPlayerHelper.isVoidLocation(loc)) {
            account.lastPositionJson = loc.toJson();
            AccountDataStorage.updatePosition(account);
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
    private void teleportToVoid(ServerPlayer player) {
        ResourceKey<Level> loginHallKey = AuthPlayerHelper.LOGIN_HALL_KEY;
        ServerLevel loginHall = server.getLevel(loginHallKey);
        if (loginHall == null) loginHall = server.overworld();
        player.teleportTo(loginHall, AuthPlayerHelper.LOGIN_HALL_X, AuthPlayerHelper.LOGIN_HALL_Y,
                AuthPlayerHelper.LOGIN_HALL_Z, Set.of(), 0, 0, true);
    }
}
