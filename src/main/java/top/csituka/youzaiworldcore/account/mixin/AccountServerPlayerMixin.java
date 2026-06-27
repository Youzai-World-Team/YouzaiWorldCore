package top.csituka.youzaiworldcore.account.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.data.PlayerAuthAccess;
import top.csituka.youzaiworldcore.account.util.AuthLocationData;

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

    // ===== 玩家 tick — 未认证时倒计时并阻止 tick =====
    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void onPlayerTick(CallbackInfo ci) {
        if (yzwc$authenticated || yzwc$canSkipAuth) {
            return;
        }

        if (yzwc$kickTimer <= 0) {
            if (yzwc$player.connection != null && yzwc$player.connection.isAcceptingMessages()) {
                yzwc$player.connection.disconnect(
                        Component.translatable("youzaiworldcore.message.account.auth_timeout")
                );
            }
        } else {
            // 每 10 秒发送一次提示
            if (yzwc$kickTimer % 200 == 0) {
                if (yzwc$account != null && yzwc$account.isRegistered()) {
                    yzwc$player.sendSystemMessage(
                            Component.translatable("youzaiworldcore.message.account.prompt_login"));
                } else {
                    yzwc$player.sendSystemMessage(
                            Component.translatable("youzaiworldcore.message.account.prompt_register"));
                }
            }
            yzwc$kickTimer--;
        }
        ci.cancel();
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
        this.yzwc$lastLocation = oldMixin.yzwc$lastLocation;
        this.yzwc$kickTimer = oldMixin.yzwc$kickTimer;
    }
}
