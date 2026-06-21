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
import java.util.Set;
import top.csituka.youzaiworldcore.account.AccountManager;
import top.csituka.youzaiworldcore.account.AccountRegisterResult;
import top.csituka.youzaiworldcore.account.LoginResult;
import top.csituka.youzaiworldcore.account.LoginState;
import top.csituka.youzaiworldcore.network.account.LoginResultPayload;
import top.csituka.youzaiworldcore.network.account.OpenLoginScreenPayload;
import top.csituka.youzaiworldcore.network.account.RegisterResultPayload;

/**
 * 账户事件处理器，管理离线服务器的玩家登录/注册全生命周期。
 * <p>
 * 核心机制：</p>
 * <ul>
 *   <li><b>登录大厅维度</b>（Login Hall）— 一个独立的虚空维度，未登录玩家被传送至此
 *       进行操作，在登录/注册完成后才传送到目标维度</li>
 *   <li><b>玩家加入</b> — 立即设置无敌/飞行保护，延迟 1 tick 后传送至登录大厅并打开
 *       对应的登录/注册 GUI</li>
 *   <li><b>玩家离开</b> — 已登录玩家保存状态快照和位置信息以便下次重连恢复；
 *       未登录玩家直接清理状态</li>
 *   <li><b>保护机制</b> — 未登录玩家：不受伤害、不可见背包物品、无法打开主菜单、
 *       持续清除状态效果</li>
 *   <li><b>失败惩罚</b> — 连续 3 次密码错误踢出服务器；连续 5 次阻止登入</li>
 * </ul>
 */
public class AccountEventHandler {

    /** 登录大厅（虚空）维度的 ResourceKey */
    public static final ResourceKey<Level> LOGIN_HALL_DIMENSION = ResourceKey.create(
            Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "login_hall")
    );

    /** 登录大厅传送坐标 (0, 100, 0)，确保玩家不会摔落 */
    public static final BlockPos LOGIN_HALL_POS = new BlockPos(0, 100, 0);

    /** 是否已注册事件回调（防止重复注册） */
    private static boolean initialized = false;

    /**
     * 注册所有事件回调。
     * 包括：玩家加入/离开、重生、服务端 Tick、网络包处理器。
     * 此方法应在 Mod 初始化阶段调用一次。
     */
    public static void register() {
        if (initialized) return;
        initialized = true;

        // 初始化账户管理器（创建存储目录）
        AccountManager.initialize();

        /**
         * 玩家加入游戏事件。
         * 立即设置无敌/飞行以防止延迟期间受伤；
         * 延迟 1 tick 后执行真正加入处理逻辑。
         */
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            // 立即设置无敌/飞行，防止延迟期间受伤
            player.setInvulnerable(true);
            player.getAbilities().flying = true;
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            // 延迟 1 tick 确保玩家已完全初始化（网络连接就绪）
            server.execute(() -> {
                handlePlayerJoin(player);
            });
        });

        /**
         * 玩家断开连接事件。
         * - 已登录玩家：保存完整状态快照（含位置），标记为登出，保留 LoginState
         *   以便下次重连时恢复到下线前的位置
         * - 未登录玩家：直接清理 LoginState，无需保留任何数据
         */
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (AccountManager.isLoggedIn(player)) {
                // 已登录：保存完整状态快照（含下线位置），标记为登出
                // 保留 LoginState 以便下次重连登录时恢复到下线前位置
                AccountManager.snapshotState(player);
                AccountManager.setLoggedIn(player, false);
            } else {
                // 未登录：直接清理，无数据需要保留
                AccountManager.removePlayer(player);
            }
        });

        /**
         * 玩家重生事件。
         * 如果玩家尚未登录（死亡时未登录），重新传送回登录大厅
         * 并延迟打开登录/注册 GUI。
         */
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!AccountManager.isLoggedIn(newPlayer)) {
                teleportToLoginHall(newPlayer);
                String mode = AccountManager.isRegistered(newPlayer.getUUID()) ? "login" : "register";
                var server = newPlayer.level().getServer();
                if (server != null) {
                    scheduleDelayedGuiOpen(server, newPlayer, mode, 3);
                }
            }
        });

        /**
         * 服务端每 Tick 事件。
         * 清理未登录玩家的所有状态效果（保护机制之一）。
         */
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!AccountManager.isLoggedIn(player)) {
                    // 清除所有状态效果（防止未登录时获得/消耗效果）
                    if (!player.getActiveEffects().isEmpty()) {
                        player.removeAllEffects();
                    }
                }
            }
        });

        YouzaiworldCore.LOGGER.info("账户事件处理器已注册");
    }

    /**
     * 处理玩家加入游戏。
     * <p>
     * 逻辑流程：</p>
     * <ol>
     *   <li>获取或创建 LoginState（已存在的不会重新创建）</li>
     *   <li>如果已有快照（断线重连玩家），保留其位置信息不覆盖</li>
     *   <li>否则保存当前状态快照</li>
     *   <li>传送至登录大厅等待操作</li>
     *   <li>延迟 3 tick 打开登录/注册 GUI</li>
     * </ol>
     *
     * @param player 加入游戏的玩家
     */
    private static void handlePlayerJoin(ServerPlayer player) {
        // 获取或创建 LoginState（已存在的不会覆盖已有状态）
        LoginState state = AccountManager.getLoginState(player);

        // 如果已有快照（来自已登录玩家的断线重连），不要覆盖保存的位置信息
        if (!state.hasSnapshot()) {
            // 没有快照（新玩家或已登出玩家），保存当前状态
            AccountManager.snapshotState(player);
        }

        // 传送至登录大厅，让玩家在安全环境中操作
        teleportToLoginHall(player);

        // 使用多 tick 延迟确保客户端有足够时间处理维度切换
        // Minecraft 的 teleportTo 是异步的——仅发送网络包给客户端
        // 对于断线重连玩家，客户端有主世界缓存，延迟不足会显示主世界背景背景
        var server = player.level().getServer();
        if (server != null) {
            String mode = AccountManager.isRegistered(player.getUUID()) ? "login" : "register";
            scheduleDelayedGuiOpen(server, player, mode, 3);
        }
    }

    /**
     * 通过链式 {@code server.execute()} 实现固定 tick 延迟后打开 GUI。
     * <p>
     * 每 tick 检查玩家维度：如果玩家不在登录大厅（例如由于维度传送异步导致的
     * 竞争条件），强制重新传送。保证玩家在 GUI 打开时已经在正确的维度。
     * </p>
     *
     * @param server         MinecraftServer 实例
     * @param player         目标玩家
     * @param mode           界面模式（"login" 或 "register"）
     * @param ticksRemaining 剩余 tick 数，递减至 0 时执行
     */
    private static void scheduleDelayedGuiOpen(
            net.minecraft.server.MinecraftServer server,
            ServerPlayer player,
            String mode,
            int ticksRemaining
    ) {
        if (ticksRemaining <= 0) {
            // 延迟结束，最终检查维度并打开 GUI
            ServerLevel loginHall = server.getLevel(LOGIN_HALL_DIMENSION);
            if (loginHall != null && player.level() != loginHall) {
                // 玩家仍然不在登录大厅，强制重新传送后再打开 GUI
                teleportToLoginHall(player);
            }
            sendOpenLoginScreen(player, mode);
            return;
        }
        server.execute(() -> {
            // 每 tick 检查玩家维度，如果不在登录大厅则强制重新传送
            ServerLevel loginHall = server.getLevel(LOGIN_HALL_DIMENSION);
            if (loginHall != null && player.level() != loginHall) {
                teleportToLoginHall(player);
            }
            scheduleDelayedGuiOpen(server, player, mode, ticksRemaining - 1);
        });
    }

    /**
     * 将玩家传送至登录大厅维度 (0, 100, 0)。
     * 如果登录大厅维度不存在，降级到主世界（理论上不应发生）。
     * 传送后确保玩家无敌且可飞行，防止在登录大厅中摔落或受到虚空伤害。
     *
     * @param player 目标玩家
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
     * 根据玩家是否已注册自动判断模式并发送打开登录/注册界面包。
     *
     * @param player 目标玩家
     */
    public static void sendOpenLoginScreen(ServerPlayer player) {
        boolean registered = AccountManager.isRegistered(player.getUUID());
        sendOpenLoginScreen(player, registered ? "login" : "register");
    }

    /**
     * 发送指定模式的打开登录/注册界面网络包。
     *
     * @param player 目标玩家
     * @param mode   界面模式（"login" 或 "register"）
     */
    public static void sendOpenLoginScreen(ServerPlayer player, String mode) {
        ServerPlayNetworking.send(player, new OpenLoginScreenPayload(mode));
    }

    // ==================== 登录处理 ====================

    /**
     * 处理客户端发来的登录请求。
     * <p>
     * 处理流程：</p>
     * <ol>
     *   <li>检查玩家是否已登录 → 是则返回成功</li>
     *   <li>调用 {@link AccountManager#login} 校验密码</li>
     *   <li>根据校验结果：成功 → 恢复状态、传送、通知客户端；失败 → 根据失败次数
     *       返回不同提示/踢出/阻止</li>
     * </ol>
     *
     * @param player   发起请求的玩家
     * @param password 用户输入的明文密码
     */
    public static void handleLogin(ServerPlayer player, String password) {
        if (AccountManager.isLoggedIn(player)) {
            ServerPlayNetworking.send(player, new LoginResultPayload(0, "您已经登录了！"));
            return;
        }

        LoginResult result = AccountManager.login(player.getUUID(), password);
        switch (result) {
            case SUCCESS -> {
                // 登录成功：标记为已登录，取消保护状态
                AccountManager.setLoggedIn(player, true);
                player.setInvulnerable(false);
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
                player.fallDistance = 0f;

                // 根据是否有保存的位置快照决定传送目标
                LoginState state = AccountManager.getLoginState(player);
                if (state.hasSnapshot()) {
                    // 有保存的位置快照（断线前已登录），恢复到下线前位置
                    state.restorePosition(player);
                    state.clearSnapshot();
                } else {
                    // 无保存位置（已登出玩家），传送到登录大厅位置
                    ServerLevel loginHall = player.level().getServer().getLevel(LOGIN_HALL_DIMENSION);
                    if (loginHall != null) {
                        player.teleportTo(loginHall,
                                LOGIN_HALL_POS.getX() + 0.5,
                                LOGIN_HALL_POS.getY(),
                                LOGIN_HALL_POS.getZ() + 0.5,
                                Set.of(), 0f, 0f, true);
                    }
                }
                ServerPlayNetworking.send(player, new LoginResultPayload(0, "登录成功！"));
            }
            case NOT_REGISTERED -> {
                ServerPlayNetworking.send(player, new LoginResultPayload(4, "该账户尚未注册！"));
            }
            case WRONG_PASSWORD -> {
                ServerPlayNetworking.send(player, new LoginResultPayload(1, "密码错误"));
            }
            case KICK -> {
                // 连续 3 次失败，踢出服务器
                player.connection.disconnect(Component.literal("密码错误过多次，已被踢出服务器"));
            }
            case BLOCKED -> {
                // 连续 5 次失败或已被阻止，断开连接并显示提示
                player.connection.disconnect(Component.literal("由于您过多次数次密码验证失败，请前往QQ交流群联系管理员"));
            }
        }
    }

    /**
     * 处理客户端发来的注册请求。
     * <p>
     * 处理流程：</p>
     * <ol>
     *   <li>检查玩家是否已登录 → 是则返回成功（不重复注册）</li>
     *   <li>校验两次密码是否一致</li>
     *   <li>校验密码是否为空</li>
     *   <li>调用 {@link AccountManager#register} 执行注册</li>
     *   <li>注册成功 → 标记为已登录，取消保护，传送至主世界 (0, 100, 0)</li>
     * </ol>
     *
     * @param player          发起请求的玩家
     * @param password        密码
     * @param confirmPassword 确认密码
     */
    public static void handleRegister(ServerPlayer player, String password, String confirmPassword) {
        if (AccountManager.isLoggedIn(player)) {
            ServerPlayNetworking.send(player, new RegisterResultPayload(0, "您已经登录了！"));
            return;
        }

        if (!password.equals(confirmPassword)) {
            ServerPlayNetworking.send(player, new RegisterResultPayload(3, "两次输入的密码不一致！"));
            return;
        }

        if (password.isEmpty()) {
            ServerPlayNetworking.send(player, new RegisterResultPayload(3, "密码不能为空！"));
            return;
        }

        String playerName = player.getGameProfile().name();
        AccountRegisterResult result = AccountManager.register(player.getUUID(), playerName, password);
        switch (result) {
            case SUCCESS -> {
                // 注册成功：标记为已登录，取消保护状态
                AccountManager.setLoggedIn(player, true);
                player.setInvulnerable(false);
                player.getAbilities().flying = false;
                player.getAbilities().mayfly = false;
                player.onUpdateAbilities();
                player.fallDistance = 0f;
                // 传送至主世界 (0, 100, 0)
                ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
                if (overworld != null) {
                    player.teleportTo(overworld, 0.5, 100, 0.5, java.util.Set.of(), 0f, 0f, true);
                }
                ServerPlayNetworking.send(player, new RegisterResultPayload(0, "注册成功！"));
            }
            case ALREADY_REGISTERED -> {
                ServerPlayNetworking.send(player, new RegisterResultPayload(1, "该账户已经注册了！"));
            }
            case NAME_TAKEN -> {
                ServerPlayNetworking.send(player, new RegisterResultPayload(2, "该玩家代号已被使用！"));
            }
        }
    }

    // ==================== 保护机制 ====================

    /**
     * 判断玩家是否应该被阻止受伤。
     * <p>
     * 条件：玩家在登录大厅维度中且尚未登录。
     * 此方法被混合（Mixin）调用，用于实现"未登录时无敌"的机制。
     * </p>
     *
     * @param player 目标玩家
     * @return true 如果应该阻止该玩家受伤
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
