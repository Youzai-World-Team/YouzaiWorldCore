package top.csituka.youzaiworldcore.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.account.AccountManager;
import top.csituka.youzaiworldcore.account.AccountRegisterResult;
import top.csituka.youzaiworldcore.account.LoginResult;
import top.csituka.youzaiworldcore.account.LoginState;
import top.csituka.youzaiworldcore.network.account.LoginResultPayload;
import top.csituka.youzaiworldcore.network.account.OpenLoginScreenPayload;
import top.csituka.youzaiworldcore.network.account.RegisterResultPayload;

/**
 * 账户事件处理器
 * 管理玩家加入、离开、登录/注册处理、保护机制等
 */
public class AccountEventHandler {

    /** 登录大厅（虚空）维度 ID */
    public static final ResourceKey<Level> LOGIN_HALL_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "login_hall")
    );

    /** 传送坐标 (0 100 0) */
    private static final BlockPos LOGIN_HALL_POS = new BlockPos(0, 100, 0);

    private static boolean initialized = false;

    public static void register() {
        if (initialized) return;
        initialized = true;

        // 初始化账户管理器
        AccountManager.initialize();

        // 玩家加入时：立即保护，下一 tick 传送到登录大厅并打开登录/注册界面
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            // 立即设置无敌/飞行，防止延迟期间受伤
            player.setInvulnerable(true);
            player.getAbilities().flying = true;
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            // 延迟一 tick 确保玩家已完全初始化
            server.execute(() -> {
                handlePlayerJoin(player);
            });
        });

        // 玩家离开时：清理状态
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            AccountManager.removePlayer(player);
        });

        // 玩家重生后事件
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            // 如果玩家未登录，重新传送到登录大厅
            if (!AccountManager.isLoggedIn(newPlayer)) {
                teleportToLoginHall(newPlayer);
                sendOpenLoginScreen(newPlayer);
            }
        });

        // 每 tick 清理未登录玩家的状态效果并提供保护
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!AccountManager.isLoggedIn(player)) {
                    // 清除所有状态效果
                    if (!player.getActiveEffects().isEmpty()) {
                        player.removeAllEffects();
                    }
                }
            }
        });

        YouzaiworldCore.LOGGER.info("账户事件处理器已注册");
    }

    /**
     * 处理玩家加入
     * 先传送至登录大厅，再延迟一 tick 打开 GUI，确保客户端已完成维度切换
     */
    private static void handlePlayerJoin(ServerPlayer player) {
        // 如果已经注册，发送登录界面；否则发送注册界面
        boolean registered = AccountManager.isRegistered(player.getUUID());
        String mode = registered ? "login" : "register";

        // 保存玩家当前状态快照
        AccountManager.snapshotState(player);

        // 传送到登录大厅
        teleportToLoginHall(player);

        // 延迟一 tick 发送 GUI 打开请求，确保客户端已完成维度切换
        // 否则传送过程中打开 GUI 会被客户端丢弃
        var server = player.level().getServer();
        if (server != null) {
            server.execute(() -> {
                sendOpenLoginScreen(player, mode);
            });
        }
    }

    /**
     * 传送到登录大厅
     */
    private static void teleportToLoginHall(ServerPlayer player) {
        ServerLevel targetLevel = player.level().getServer().getLevel(LOGIN_HALL_DIMENSION);
        if (targetLevel == null) {
            targetLevel = player.level().getServer().getLevel(Level.OVERWORLD);
        }
        if (targetLevel != null) {
            // 重置坠落距离防止传送到位时摔死
            player.fallDistance = 0f;
            player.teleportTo(targetLevel, 
                    LOGIN_HALL_POS.getX() + 0.5, 
                    LOGIN_HALL_POS.getY(), 
                    LOGIN_HALL_POS.getZ() + 0.5, 
                    java.util.Set.of(), 
                    0f, 0f, true);
            // 使玩家无敌且可飞行，防止在登录大厅中摔落/虚空伤害
            player.setInvulnerable(true);
            player.getAbilities().flying = true;
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    /**
     * 发送打开登录/注册界面（自动判断模式）
     */
    public static void sendOpenLoginScreen(ServerPlayer player) {
        boolean registered = AccountManager.isRegistered(player.getUUID());
        sendOpenLoginScreen(player, registered ? "login" : "register");
    }

    /**
     * 发送打开登录/注册界面（指定模式）
     */
    public static void sendOpenLoginScreen(ServerPlayer player, String mode) {
        ServerPlayNetworking.send(player, new OpenLoginScreenPayload(mode));
    }

    // ==================== 登录处理 ====================

    /**
     * 处理登录请求（服务端）
     */
    public static void handleLogin(ServerPlayer player, String password) {
        if (AccountManager.isLoggedIn(player)) {
            ServerPlayNetworking.send(player, new LoginResultPayload(0, "\u60A8\u5DF2\u7ECF\u767B\u5F55\u4E86\uFF01"));
            return;
        }

        LoginResult result = AccountManager.login(player.getUUID(), password);
        switch (result) {
            case SUCCESS -> {
                AccountManager.setLoggedIn(player, true);
                // 恢复玩家状态和位置（移除无敌/飞行）
                player.setInvulnerable(false);
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
                player.fallDistance = 0f;
                LoginState state = AccountManager.getLoginState(player);
                state.restorePosition(player);
                ServerPlayNetworking.send(player, new LoginResultPayload(0, "\u767B\u5F55\u6210\u529F\uFF01"));
            }
            case NOT_REGISTERED -> {
                ServerPlayNetworking.send(player, new LoginResultPayload(4, "\u8BE5\u8D26\u6237\u5C1A\u672A\u6CE8\u518C\uFF01"));
            }
            case WRONG_PASSWORD -> {
                ServerPlayNetworking.send(player, new LoginResultPayload(1, "\u5BC6\u7801\u9519\u8BEF"));
            }
            case KICK -> {
                // 连续3次失败，踢出
                player.connection.disconnect(Component.literal("\u5BC6\u7801\u9519\u8BEF\u8FC7\u591A\u6B21\uFF0C\u5DF2\u88AB\u8E22\u51FA\u670D\u52A1\u5668"));
            }
            case BLOCKED -> {
                // 连续5次失败或已被阻止
                player.connection.disconnect(Component.literal("\u7531\u4E8E\u60A8\u8FC7\u591A\u6B21\u6570\u6B21\u5BC6\u7801\u9A8C\u8BC1\u5931\u8D25\uFF0C\u8BF7\u524D\u5F80QQ\u4EA4\u6D41\u7FA4\u8054\u7CFB\u7BA1\u7406\u5458"));
            }
        }
    }

    /**
     * 处理注册请求
     */
    public static void handleRegister(ServerPlayer player, String password, String confirmPassword) {
        if (AccountManager.isLoggedIn(player)) {
            ServerPlayNetworking.send(player, new RegisterResultPayload(0, "\u60A8\u5DF2\u7ECF\u767B\u5F55\u4E86\uFF01"));
            return;
        }

        if (!password.equals(confirmPassword)) {
            ServerPlayNetworking.send(player, new RegisterResultPayload(3, "\u4E24\u6B21\u8F93\u5165\u7684\u5BC6\u7801\u4E0D\u4E00\u81F4\uFF01"));
            return;
        }

        if (password.isEmpty()) {
            ServerPlayNetworking.send(player, new RegisterResultPayload(3, "\u5BC6\u7801\u4E0D\u80FD\u4E3A\u7A7A\uFF01"));
            return;
        }

        String playerName = player.getGameProfile().name();
        AccountRegisterResult result = AccountManager.register(player.getUUID(), playerName, password);
        switch (result) {
            case SUCCESS -> {
                AccountManager.setLoggedIn(player, true);
                // 移除无敌/飞行
                player.setInvulnerable(false);
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
                player.fallDistance = 0f;
                // 传送到主世界 0 100 0
                ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
                if (overworld != null) {
                    player.teleportTo(overworld, 0.5, 100, 0.5, java.util.Set.of(), 0f, 0f, true);
                }
                ServerPlayNetworking.send(player, new RegisterResultPayload(0, "\u6CE8\u518C\u6210\u529F\uFF01"));
            }
            case ALREADY_REGISTERED -> {
                ServerPlayNetworking.send(player, new RegisterResultPayload(1, "\u8BE5\u8D26\u6237\u5DF2\u7ECF\u6CE8\u518C\u4E86\uFF01"));
            }
            case NAME_TAKEN -> {
                ServerPlayNetworking.send(player, new RegisterResultPayload(2, "\u8BE5\u73A9\u5BB6\u4EE3\u53F7\u5DF2\u88AB\u4F7F\u7528\uFF01"));
            }
        }
    }

    // ==================== 保护机制 ====================

    /**
     * 玩家是否被阻止受伤（未登录时）
     */
    public static boolean shouldBlockDamage(ServerPlayer player) {
        if (player.level() == null) return false;
        var server = player.level().getServer();
        if (server == null) return false;
        ServerLevel loginHall = server.getLevel(LOGIN_HALL_DIMENSION);
        if (loginHall == null) return false;
        if (player.level() != loginHall) return false;
        return !AccountManager.isLoggedIn(player);
    }
}
