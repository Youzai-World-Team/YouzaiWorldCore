package top.csituka.youzaiworldcore.account.mixin;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAuthAccess;
import top.csituka.youzaiworldcore.account.data.RegistrationEmailSessionStore;
import top.csituka.youzaiworldcore.account.util.AuthLocationData;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.api.ApiServiceClient;
import top.csituka.youzaiworldcore.cosmetic.CosmeticManager;
import top.csituka.youzaiworldcore.network.OpenAuthScreenPayload;
import top.csituka.youzaiworldcore.network.RegistrationEmailStatePayload;

import java.util.concurrent.CompletableFuture;
import java.util.Set;

/**
 * ServerPlayer Mixin — 追踪认证状态、位置保存、踢出计时器
 */
@Mixin(ServerPlayer.class)
public abstract class AccountServerPlayerMixin implements PlayerAuthAccess {

    @Unique
    private final ServerPlayer yzwc$player = (ServerPlayer) (Object) this;

    /** 是否已通过认证 */
    @Unique
    private volatile boolean yzwc$authenticated = false;

    /** 是否为特殊玩家（可以跳过认证） */
    @Unique
    private boolean yzwc$canSkipAuth = yzwc$player.getClass() != ServerPlayer.class;

    /** 玩家账户数据 */
    @Unique
    private PlayerAccount yzwc$account = new PlayerAccount(yzwc$player.getScoreboardName());

    /** 玩家的 IP 地址 */
    @Unique
    private String yzwc$ipAddress = null;

    /** Api 游戏会话令牌，仅保存在当前玩家对象内存中。 */
    @Unique
    private String yzwc$sessionToken = null;

    @Unique
    private int yzwc$sessionCheckTimer = 200;

    @Unique
    private boolean yzwc$sessionCheckPending = false;

    /** 认证前玩家的真实位置 */
    @Unique
    private AuthLocationData yzwc$lastLocation = null;

    /** 踢出计时器（tick） */
    @Unique
    private int yzwc$kickTimer = 6000; // 5分钟 @ 20tps = 6000 ticks

    // ===== 公开访问方法（实例方法，供其他 mixin 的静态辅助类调用） =====

    public boolean yzwc$isAuthenticated() {
        return yzwc$authenticated;
    }

    public void yzwc$setAuthenticated(boolean authenticated) {
        this.yzwc$authenticated = authenticated;
        if (authenticated) {
            yzwc$kickTimer = 6000;
        }
    }

    public boolean yzwc$canSkipAuth() {
        return yzwc$canSkipAuth;
    }

    public void yzwc$setCanSkipAuth(boolean canSkip) {
        this.yzwc$canSkipAuth = canSkip;
    }

    public PlayerAccount yzwc$getAccount() {
        return yzwc$account;
    }

    public void yzwc$setAccount(PlayerAccount account) {
        this.yzwc$account = account;
    }

    public String yzwc$getIpAddress() {
        return yzwc$ipAddress;
    }

    public void yzwc$setIpAddress(String ip) {
        this.yzwc$ipAddress = ip;
    }

    public String yzwc$getSessionToken() {
        return yzwc$sessionToken;
    }

    public void yzwc$setSessionToken(String token) {
        this.yzwc$sessionToken = token == null || token.isBlank() ? null : token;
        this.yzwc$sessionCheckTimer = 200;
    }

    public AuthLocationData yzwc$getLastLocation() {
        return yzwc$lastLocation;
    }

    public void yzwc$setLastLocation(AuthLocationData location) {
        this.yzwc$lastLocation = location;
    }

    public int yzwc$getKickTimer() {
        return yzwc$kickTimer;
    }

    public void yzwc$setKickTimer(int timer) {
        this.yzwc$kickTimer = timer;
    }

    // ===== 保存当前位置 =====
    public void yzwc$saveLocation() {
        AuthLocationData loc = new AuthLocationData();
        loc.position = yzwc$player.position();
        loc.dimension = yzwc$player.level().dimension();
        loc.yaw = yzwc$player.getYRot();
        loc.pitch = yzwc$player.getXRot();
        this.yzwc$lastLocation = loc;
        YouzaiworldCore.LOGGER.debug("已保存玩家 {} 的位置: {}", yzwc$player.getScoreboardName(), loc);
    }

    // ===== 发送认证界面打开数据包 =====
    @Unique
    private void sendAuthScreenPacket(String type) {
        if (yzwc$player.connection != null && yzwc$player.connection.isAcceptingMessages()) {
            try {
                ServerPlayNetworking.send(yzwc$player,
                        new OpenAuthScreenPayload(type, yzwc$player.getScoreboardName()));
            } catch (Exception e) {
                YouzaiworldCore.LOGGER.error("发送认证界面数据包失败: {}", e.getMessage());
            }
        }
    }

    @Unique
    private void sendRegistrationEmailScreenPacket(
            RegistrationEmailSessionStore.PendingSession pending) {
        if (yzwc$player.connection != null && yzwc$player.connection.isAcceptingMessages()) {
            try {
                ServerPlayNetworking.send(yzwc$player, RegistrationEmailStatePayload.required(
                        pending.sessionId(), pending.remainingSeconds()));
            } catch (Exception e) {
                YouzaiworldCore.LOGGER.error("发送邮箱注册界面数据包失败: {}", e.getMessage());
            }
        }
    }

    // ===== 玩家 tick — 未认证时倒计时并阻止 tick =====
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void onPlayerTick(CallbackInfo ci) {
        if (yzwc$canSkipAuth) {
            return;
        }

        if (yzwc$authenticated) {
            validateApiSessionIfDue();
            return;
        }

        if (yzwc$kickTimer <= 0) {
            if (yzwc$player.connection != null && yzwc$player.connection.isAcceptingMessages()) {
                yzwc$player.connection.disconnect(
                        Component.translatable("youzaiworldcore.message.account.auth_timeout")
                );
            }
        } else {
            // 每 10 秒发送一次 GUI 打开数据包（聊天提示已由 GUI 替代，不再重复发送）
            if (yzwc$kickTimer % 200 == 0) {
                if (yzwc$account != null && yzwc$account.isRegistered()) {
                    sendAuthScreenPacket("login");
                } else {
                    RegistrationEmailSessionStore.PendingSession pending =
                            RegistrationEmailSessionStore.get(yzwc$player.getUUID());
                    if (pending == null) {
                        sendAuthScreenPacket("register");
                    } else {
                        sendRegistrationEmailScreenPacket(pending);
                    }
                }
            }
            yzwc$kickTimer--;
        }
        ci.cancel();
    }

    @Unique
    private void validateApiSessionIfDue() {
        if (yzwc$sessionToken == null || yzwc$sessionCheckPending || --yzwc$sessionCheckTimer > 0) {
            return;
        }
        yzwc$sessionCheckTimer = 200;
        yzwc$sessionCheckPending = true;
        String token = yzwc$sessionToken;
        MinecraftServer server = yzwc$player.level().getServer();
        if (server == null) {
            yzwc$sessionCheckPending = false;
            return;
        }
        String username = yzwc$player.getScoreboardName();
        CompletableFuture.supplyAsync(() -> ApiServiceClient.validateSession(token, username))
                .whenComplete((result, error) -> {
                    server.execute(() -> {
                        yzwc$sessionCheckPending = false;
                        if (!token.equals(yzwc$sessionToken)) return;
                        if (error != null || result == null
                                || result.state() != ApiServiceClient.SessionValidationState.VALID) {
                            yzwc$saveLocation();
                            if (result != null && result.state() == ApiServiceClient.SessionValidationState.DELETED) {
                                AccountDataStorage.removeRemoteAccount(username);
                                yzwc$account = new PlayerAccount(username);
                            } else if (result != null && result.account() != null) {
                                yzwc$account = result.account();
                                AccountDataStorage.acceptRemoteAccount(result.account(), false);
                            }
                            yzwc$sessionToken = null;
                            yzwc$authenticated = false;
                            CosmeticManager.onDeauthenticated(yzwc$player);
                            var loginHall = server.getLevel(AuthPlayerHelper.LOGIN_HALL_KEY);
                            if (loginHall == null) loginHall = server.overworld();
                            yzwc$player.teleportTo(loginHall,
                                    AuthPlayerHelper.LOGIN_HALL_X, AuthPlayerHelper.LOGIN_HALL_Y,
                                    AuthPlayerHelper.LOGIN_HALL_Z, Set.of(), 0, 0, true);
                            yzwc$player.sendSystemMessage(Component.literal("Api 会话已失效，请重新登录"));
                        }
                    });
                });
    }

    // ===== 未认证时无敌 — 拦截伤害（ServerPlayer.hurtServer 是具体方法） =====
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void onHurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source,
                              float amount,
                              org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (!yzwc$authenticated && !yzwc$canSkipAuth) {
            cir.setReturnValue(false); // 未认证时不受伤害
        }
    }

    // ===== 复活时复制认证状态 =====
    @Inject(method = "restoreFrom", at = @At("RETURN"))
    private void onCopyFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        AccountServerPlayerMixin oldMixin = (AccountServerPlayerMixin) (Object) oldPlayer;
        this.yzwc$authenticated = oldMixin.yzwc$authenticated;
        this.yzwc$canSkipAuth = oldMixin.yzwc$canSkipAuth;
        this.yzwc$account = oldMixin.yzwc$account;
        this.yzwc$ipAddress = oldMixin.yzwc$ipAddress;
        this.yzwc$sessionToken = oldMixin.yzwc$sessionToken;
        this.yzwc$sessionCheckTimer = oldMixin.yzwc$sessionCheckTimer;
        this.yzwc$sessionCheckPending = false;
        this.yzwc$lastLocation = oldMixin.yzwc$lastLocation;
        this.yzwc$kickTimer = oldMixin.yzwc$kickTimer;
    }
}
