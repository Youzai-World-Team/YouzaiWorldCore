package top.csituka.youzaiworldcore.account;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.fabricmc.fabric.api.networking.v1.LoginPacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.mixin.OnlineUuidLoginAccessor;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录阶段的混合正版身份认证。
 *
 * <p>
 * 服务器保持 {@code online-mode=false} 时，原版会先生成离线 UUID。
 * 本管理器在玩家对象创建前发起一次 Fabric 登录查询：正版客户端用启动器令牌
 * 调用 Mojang {@code joinServer}，服务端再用 {@code hasJoinedServer} 验证。
 * 验证成功后替换登录监听器中的 {@code authenticatedProfile}，因此世界玩家数据、
 * 统计、账户和其他 UUID 索引从创建之初就使用 Mojang UUID。
 * </p>
 *
 * <p>
 * 不支持该查询、离线启动器或 Mojang 服务不可用时，登录继续使用原有离线 UUID。
 * 当 {@code online-mode=true} 时原版已经完成同样的认证，本类不会重复发起查询。
 * </p>
 */
@SuppressWarnings("null")
public final class OnlineUuidLoginManager {
    private static final String MODULE = "OnlineUuidLogin";
    private static final int MAX_TEXT_LENGTH = 128;
    private static final int TIMEOUT_SECONDS = 12;

    /** 登录阶段协议频道；与进入游戏后的外观协议分离。 */
    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "mojang_uuid_login");

    private static final Map<ServerLoginPacketListenerImpl, LoginState> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> VERIFIED_UUIDS = new ConcurrentHashMap<>();
    private static volatile boolean initialized;

    private OnlineUuidLoginManager() {
    }

    /** 注册服务端登录查询处理器。只能在服务端模组初始化阶段调用一次。 */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        ServerLoginConnectionEvents.QUERY_START.register(
                (listener, server, sender, synchronizer) -> startQuery(listener, server, sender, synchronizer));
        ServerLoginNetworking.registerGlobalReceiver(CHANNEL,
                OnlineUuidLoginManager::handleResponse);
        ServerLoginConnectionEvents.DISCONNECT.register((listener, server) -> {
            LoginState state = STATES.remove(listener);
            if (state != null) {
                state.complete();
            }
        });
        DebugLogger.info(MODULE, "已注册混合正版 UUID 登录查询（online-mode=false 时启用）");
    }

    /** 服务端停止时释放本次运行的身份缓存。 */
    public static void shutdown() {
        STATES.forEach((listener, state) -> state.complete());
        STATES.clear();
        VERIFIED_UUIDS.clear();
        DebugLogger.debug(MODULE, "已清理登录阶段 UUID 验证缓存");
    }

    /** 判断某 UUID 是否由本管理器在本次服务器运行中完成 Mojang 验证。 */
    public static boolean isVerifiedUuid(UUID uuid) {
        return uuid != null && VERIFIED_UUIDS.containsKey(uuid);
    }

    /** 判断玩家是否应被视为可信正版身份（纯 online-mode 也由原版保证）。 */
    public static boolean isTrustedOnlineIdentity(MinecraftServer server, UUID uuid) {
        return server != null && (server.usesAuthentication() || isVerifiedUuid(uuid));
    }

    private static void startQuery(
            ServerLoginPacketListenerImpl listener,
            MinecraftServer server,
            LoginPacketSender sender,
            ServerLoginNetworking.LoginSynchronizer synchronizer) {
        if (server.usesAuthentication() || listener.getUserName() == null
                || listener.getUserName().isBlank()) {
            return;
        }

        @SuppressWarnings("unused")
        String challenge = UUID.randomUUID().toString().replace("-", "");
        LoginState state = new LoginState(challenge);
        STATES.put(listener, state);
        FriendlyByteBuf request = FriendlyByteBufs.create();
        request.writeUtf(challenge, MAX_TEXT_LENGTH);
        sender.sendPacket(CHANNEL, request);
        synchronizer.waitFor(state.completion);

        CompletableFuture.delayedExecutor(TIMEOUT_SECONDS, TimeUnit.SECONDS).execute(() -> {
            LoginState current = STATES.get(listener);
            if (current == state && state.complete()) {
                STATES.remove(listener, state);
                DebugLogger.debug(MODULE, "玩家 %s 未在登录查询期限内完成正版验证，回退离线 UUID",
                        listener.getUserName());
            }
        });
        DebugLogger.debug(MODULE, "已向玩家 %s 发起登录阶段 Mojang UUID 验证", listener.getUserName());
    }

    private static void handleResponse(
            MinecraftServer server,
            ServerLoginPacketListenerImpl listener,
            boolean understood,
            FriendlyByteBuf buf,
            ServerLoginNetworking.LoginSynchronizer synchronizer,
            net.fabricmc.fabric.api.networking.v1.PacketSender responseSender) {
        LoginState state = STATES.get(listener);
        if (state == null || state.completion.isDone()) {
            return;
        }
        if (!understood) {
            finishOffline(listener, state, "客户端未安装登录扩展");
            return;
        }

        String challenge;
        UUID claimedUuid;
        String claimedName;
        try {
            challenge = buf.readUtf(MAX_TEXT_LENGTH);
            claimedUuid = buf.readUUID();
            claimedName = buf.readUtf(64);
        } catch (RuntimeException e) {
            finishOffline(listener, state, "登录响应格式无效");
            return;
        }
        if (!state.challenge.equals(challenge)
                || claimedUuid == null
                || claimedName == null
                || claimedName.isBlank()
                || !claimedName.equalsIgnoreCase(listener.getUserName())) {
            finishOffline(listener, state, "登录响应与当前玩家不匹配");
            return;
        }

        CompletableFuture
                .supplyAsync(() -> verifyWithMojang(server, listener.getUserName(), challenge, claimedUuid))
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((profile, error) -> {
                    try {
                        server.execute(() -> {
                            if (state.completion.isDone() || STATES.get(listener) != state) {
                                return;
                            }
                            if (error != null || profile == null) {
                                finishOffline(listener, state,
                                        error == null ? "Mojang 未返回匹配档案" : error.getClass().getSimpleName());
                                return;
                            }
                            ((OnlineUuidLoginAccessor) (Object) listener)
                                    .yzwc$setAuthenticatedProfile(profile);
                            VERIFIED_UUIDS.put(profile.id(), Boolean.TRUE);
                            STATES.remove(listener, state);
                            state.complete();
                            DebugLogger.info(MODULE, "玩家 %s 已在登录阶段切换为 Mojang UUID %s",
                                    profile.name(), profile.id());
                        });
                    } catch (RuntimeException e) {
                        finishOffline(listener, state, "服务端主线程不可用");
                    }
                });
    }

    private static GameProfile verifyWithMojang(
            MinecraftServer server, String username, String challenge, UUID claimedUuid) {
        try {
            var result = server.services().sessionService().hasJoinedServer(username, challenge, null);
            if (result == null || result.profile() == null) {
                return null;
            }
            GameProfile profile = result.profile();
            if (!claimedUuid.equals(profile.id()) || !username.equalsIgnoreCase(profile.name())) {
                return null;
            }
            return profile;
        } catch (Exception e) {
            DebugLogger.debug(MODULE, "Mojang 登录验证失败：%s", e.getClass().getSimpleName());
            return null;
        }
    }

    private static void finishOffline(ServerLoginPacketListenerImpl listener, LoginState state, String reason) {
        if (STATES.remove(listener, state)) {
            state.complete();
            DebugLogger.debug(MODULE, "玩家 %s 使用离线 UUID 继续登录：%s", listener.getUserName(), reason);
        }
    }

    private static final class LoginState {
        private final String challenge;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();

        private LoginState(String challenge) {
            this.challenge = challenge;
        }

        private boolean complete() {
            return completion.complete(null);
        }
    }

}
