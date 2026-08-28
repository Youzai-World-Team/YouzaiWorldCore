package top.csituka.youzaiworldcore.client.account;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.FriendlyByteBuf;
import top.csituka.youzaiworldcore.account.OnlineUuidLoginManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import io.netty.channel.ChannelFutureListener;

/** 客户端登录阶段 Mojang 会话证明；不向服务器发送访问令牌。 */
public final class OnlineUuidLoginClient {
    private static final String MODULE = "OnlineUuidLoginClient";
    private static final int MAX_TEXT_LENGTH = 128;
    private static final int TIMEOUT_SECONDS = 12;

    private OnlineUuidLoginClient() {
    }

    /** 注册登录查询响应器。 */
    @SuppressWarnings("null")
    public static void initialize() {
        ClientLoginNetworking.registerGlobalReceiver(
                OnlineUuidLoginManager.CHANNEL, OnlineUuidLoginClient::handleQuery);
        DebugLogger.info(MODULE, "已注册 Mojang UUID 登录查询响应器");
    }

    private static CompletableFuture<FriendlyByteBuf> handleQuery(
            Minecraft client,
            ClientHandshakePacketListenerImpl listener,
            FriendlyByteBuf buf,
            Consumer<ChannelFutureListener> callbacksConsumer) {
        String challenge;
        try {
            challenge = buf.readUtf(MAX_TEXT_LENGTH);
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(null);
        }
        if (challenge.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        User user = client.getUser();
        UUID profileId = user.getProfileId();
        String profileName = user.getName();
        if (profileId == null || profileName == null || profileName.isBlank() || isOfflineUser(user)) {
            DebugLogger.debug(MODULE, "当前启动器会话不是正版账户，登录查询回退离线 UUID");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                MinecraftSessionService sessionService = client.services().sessionService();
                sessionService.joinServer(profileId, user.getAccessToken(), challenge);
                FriendlyByteBuf response = FriendlyByteBufs.create();
                response.writeUtf(challenge, MAX_TEXT_LENGTH);
                response.writeUUID(profileId);
                response.writeUtf(profileName, 64);
                DebugLogger.debug(MODULE, "已完成 Mojang 登录会话证明: %s", profileName);
                return response;
            } catch (Exception e) {
                DebugLogger.debug(MODULE, "Mojang 登录会话证明失败：%s", e.getClass().getSimpleName());
                return null;
            }
        }).completeOnTimeout(null, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static boolean isOfflineUser(User user) {
        String token = user.getAccessToken();
        return token == null || token.isBlank() || "0".equals(token)
                || "FabricMC".equalsIgnoreCase(token);
    }
}
