package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.screen.LoginScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.PasswordResetScreen;
import top.csituka.youzaiworldcore.client.screen.RegisterScreen;
import top.csituka.youzaiworldcore.client.screen.RegistrationEmailScreen;
import top.csituka.youzaiworldcore.client.screen.block.LargeSignEditScreen;
import top.csituka.youzaiworldcore.client.screen.block.TeleportAnchorNameScreen;
import top.csituka.youzaiworldcore.client.screen.block.TeleportAnchorScreen;
import top.csituka.youzaiworldcore.client.screen.block.WirelessRedstoneChannelScreen;
import top.csituka.youzaiworldcore.client.screen.element.AboutMeMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.element.SettingsMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.SwitchWorldMenuElements;
import top.csituka.youzaiworldcore.client.FunctionToggleClientState;
import top.csituka.youzaiworldcore.client.InPlaceRespawnClientState;
import top.csituka.youzaiworldcore.client.cosmetic.CosmeticClientManager;
import top.csituka.youzaiworldcore.client.hud.AdventureLevelHudRenderer;
import top.csituka.youzaiworldcore.client.render.DamageNumberRenderer;
import top.csituka.youzaiworldcore.client.skill.ClientAttributeData;
import top.csituka.youzaiworldcore.mana.ManaManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Map;
import java.util.HashMap;

@SuppressWarnings("null")
public class ClientNetworking {

    private static final Map<String, MenuElementGroup> MENU_MAP = new HashMap<>();

    static {
        MENU_MAP.put("main", new MainMenuElements());
        MENU_MAP.put("switch_world", new SwitchWorldMenuElements());
        MENU_MAP.put("settings", new SettingsMenuElements());
        MENU_MAP.put("about_me", new AboutMeMenuElements());
    }

    public static void initialize() {
        DebugLogger.entering("ClientNetworking", "initialize");

        ClientPlayNetworking.registerGlobalReceiver(CosmeticReadyPayload.ID, (payload, context) ->
                context.client().execute(() -> CosmeticClientManager.onReady(payload)));
        DebugLogger.info("ClientNetworking", "Registered receiver: CosmeticReadyPayload");

        ClientPlayNetworking.registerGlobalReceiver(CosmeticUploadResultPayload.ID, (payload, context) ->
                context.client().execute(() -> CosmeticClientManager.onUploadResult(payload)));
        DebugLogger.info("ClientNetworking", "Registered receiver: CosmeticUploadResultPayload");

        ClientPlayNetworking.registerGlobalReceiver(CosmeticInfoPayload.ID, (payload, context) ->
                context.client().execute(() -> CosmeticClientManager.onInfo(payload)));
        DebugLogger.info("ClientNetworking", "Registered receiver: CosmeticInfoPayload");

        ClientPlayNetworking.registerGlobalReceiver(CosmeticDataPayload.ID, (payload, context) ->
                context.client().execute(() -> CosmeticClientManager.onData(payload)));
        DebugLogger.info("ClientNetworking", "Registered receiver: CosmeticDataPayload");

        ClientPlayNetworking.registerGlobalReceiver(InPlaceRespawnInfoPayload.ID, (payload, context) ->
                context.client().execute(() -> InPlaceRespawnClientState.updateInfo(
                        payload.enabled(), payload.requiredLevel())));
        DebugLogger.info("ClientNetworking", "Registered receiver: InPlaceRespawnInfoPayload");

        ClientPlayNetworking.registerGlobalReceiver(InPlaceRespawnResultPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.approved()) {
                        if (context.client().player != null && !context.client().player.isAlive()) {
                            context.client().player.respawn();
                        }
                        return;
                    }
                    InPlaceRespawnClientState.applyRejection(
                            payload.reason(), payload.requiredLevel(), payload.currentLevel());
                }));
        DebugLogger.info("ClientNetworking", "Registered receiver: InPlaceRespawnResultPayload");

        ClientPlayNetworking.registerGlobalReceiver(OpenMenuPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "OpenMenuPayload handler");
            Minecraft client = context.client();
            client.execute(() -> {
                // 打开本项目任何屏幕视为玩家活动（服务端 /yzwc open_menu 或管理员远程触发）
                top.csituka.youzaiworldcore.client.afk.AfkInputTracker.markInput();
                MenuElementGroup element = MENU_MAP.get(payload.menuName());
                boolean hasElement = element != null;
                DebugLogger.branch("ClientNetworking", "MENU_MAP contains menuName", hasElement, "menuName=" + payload.menuName());
                if (hasElement) {
                    client.setScreenAndShow(new MenuScreen(element));
                }
            });
            DebugLogger.exiting("ClientNetworking", "OpenMenuPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: OpenMenuPayload");

        // 注册认证界面打开处理器
        ClientPlayNetworking.registerGlobalReceiver(OpenAuthScreenPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "OpenAuthScreenPayload handler");
            Minecraft client = context.client();
            client.execute(() -> {
                // 只有目标界面已经打开时才跳过；登出或管理员重置密码时，必须允许登录界面替换旧的注册界面。
                Screen currentScreen = client.gui.screen();
                boolean isRegisterScreen = currentScreen instanceof RegisterScreen;
                boolean isLoginScreen = currentScreen instanceof LoginScreen;
                boolean isPasswordResetScreen = currentScreen instanceof PasswordResetScreen;
                DebugLogger.branch("ClientNetworking", "currentScreen instanceof RegisterScreen", isRegisterScreen);
                DebugLogger.branch("ClientNetworking", "currentScreen instanceof LoginScreen", isLoginScreen);
                String type = payload.screenType();
                String username = payload.username();
                boolean isRegister = "register".equals(type);
                DebugLogger.branch("ClientNetworking", "screenType == register", isRegister);
                boolean isLogin = "login".equals(type);
                boolean targetScreenAlreadyOpen = (isRegister && isRegisterScreen)
                        || (isLogin && (isLoginScreen || isPasswordResetScreen));
                DebugLogger.branch("ClientNetworking", "target auth screen already open", targetScreenAlreadyOpen);
                if (targetScreenAlreadyOpen) {
                    DebugLogger.exiting("ClientNetworking", "OpenAuthScreenPayload handler (skipped, target screen already open)");
                    return;
                }
                if (isRegister) {
                    client.setScreenAndShow(new RegisterScreen(username));
                } else if (isLogin) {
                    client.setScreenAndShow(new LoginScreen(username));
                }
            });
            DebugLogger.exiting("ClientNetworking", "OpenAuthScreenPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: OpenAuthScreenPayload");

        ClientPlayNetworking.registerGlobalReceiver(RegistrationEmailStatePayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "RegistrationEmailStatePayload handler");
            Minecraft client = context.client();
            client.execute(() -> {
                Screen currentScreen = client.gui.screen();
                if (currentScreen instanceof RegistrationEmailScreen emailScreen
                        && emailScreen.matchesSession(payload.sessionId())) {
                    emailScreen.applyState(payload);
                    return;
                }
                if (payload.state() == RegistrationEmailStatePayload.State.REQUIRED) {
                    String username = client.player == null ? "" : client.player.getScoreboardName();
                    client.setScreenAndShow(new RegistrationEmailScreen(
                            username, payload.sessionId(), payload.expiresInSeconds()));
                } else if (payload.state() == RegistrationEmailStatePayload.State.EXPIRED
                        && currentScreen instanceof RegisterScreen) {
                    String username = client.player == null ? "" : client.player.getScoreboardName();
                    client.setScreenAndShow(new RegisterScreen(username));
                }
            });
            DebugLogger.exiting("ClientNetworking", "RegistrationEmailStatePayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: RegistrationEmailStatePayload");

        ClientPlayNetworking.registerGlobalReceiver(PasswordResetStatePayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "PasswordResetStatePayload handler");
            Minecraft client = context.client();
            client.execute(() -> {
                Screen currentScreen = client.gui.screen();
                if (currentScreen instanceof PasswordResetScreen resetScreen) {
                    resetScreen.applyState(payload);
                }
            });
            DebugLogger.exiting("ClientNetworking", "PasswordResetStatePayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: PasswordResetStatePayload");

        // 注册传送锚点列表处理器
        ClientPlayNetworking.registerGlobalReceiver(TeleportAnchorListPayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "TeleportAnchorListPayload handler");
            context.client().execute(() -> {
                context.client().setScreenAndShow(new TeleportAnchorScreen(
                        payload.points(), payload.currentPos(), payload.currentDim(),
                        payload.entryType(), payload.entryHand()));
            });
            DebugLogger.exiting("ClientNetworking", "TeleportAnchorListPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: TeleportAnchorListPayload");

        // 注册传送锚点命名界面处理器
        ClientPlayNetworking.registerGlobalReceiver(TeleportAnchorOpenNamePayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "TeleportAnchorOpenNamePayload handler");
            context.client().execute(() -> {
                context.client().setScreenAndShow(
                        new TeleportAnchorNameScreen(payload.pos(), payload.dimension()));
            });
            DebugLogger.exiting("ClientNetworking", "TeleportAnchorOpenNamePayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: TeleportAnchorOpenNamePayload");

        // 注册大字牌编辑界面处理器（服务端已校验未涂蜡且玩家有建造权限）
        ClientPlayNetworking.registerGlobalReceiver(LargeSignOpenEditPayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "LargeSignOpenEditPayload handler");
            context.client().execute(() -> {
                context.client().setScreenAndShow(
                        new LargeSignEditScreen(payload.pos(), payload.currentText()));
            });
            DebugLogger.exiting("ClientNetworking", "LargeSignOpenEditPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: LargeSignOpenEditPayload");

        // 注册无线红石频道设置界面处理器（服务端已校验方块实体存在且玩家有建造权限）
        ClientPlayNetworking.registerGlobalReceiver(WirelessRedstoneOpenChannelPayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "WirelessRedstoneOpenChannelPayload handler");
            context.client().execute(() -> {
                context.client().setScreenAndShow(new WirelessRedstoneChannelScreen(
                        payload.pos(), payload.currentChannel(), payload.transmitter()));
            });
            DebugLogger.exiting("ClientNetworking", "WirelessRedstoneOpenChannelPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: WirelessRedstoneOpenChannelPayload");

        // 注册传送石蓄力打断处理器：服务端已停止使用物品，客户端同步停手，避免一直保持蓄力动作
        ClientPlayNetworking.registerGlobalReceiver(TeleportStoneInterruptPayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "TeleportStoneInterruptPayload handler");
            Minecraft client = context.client();
            client.execute(() -> {
                if (client.player != null && client.player.isUsingItem() && client.gameMode != null) {
                    client.gameMode.releaseUsingItem(client.player);
                }
            });
            DebugLogger.exiting("ClientNetworking", "TeleportStoneInterruptPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: TeleportStoneInterruptPayload");

        // 注册魔力同步处理器
        ClientPlayNetworking.registerGlobalReceiver(ManaSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ManaManager.setClientMana(payload.mana());
            });
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: ManaSyncPayload");

        // 注册冒险经验同步处理器
        ClientPlayNetworking.registerGlobalReceiver(LevelExpSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                AdventureLevelHudRenderer.onExpGained(
                        payload.level(),
                        payload.currentExp(),
                        payload.neededExp(),
                        payload.gainedExp(),
                        payload.leveledUp()
                );
            });
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: LevelExpSyncPayload");

        // 注册伤害跳字处理器
        ClientPlayNetworking.registerGlobalReceiver(DamageNumberPayload.ID, (payload, context) ->
                context.client().execute(() -> DamageNumberRenderer.add(
                        payload.x(), payload.y(), payload.z(), payload.entityHeight(), payload.damage())));
        DebugLogger.info("ClientNetworking", "Registered receiver: DamageNumberPayload");

        // 注册属性数据同步处理器
        ClientPlayNetworking.registerGlobalReceiver(AttributeSyncPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                ClientAttributeData.update(payload);
            });
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: AttributeSyncPayload");

        // ======================================================================
        // 邮件系统（Mail）—— 客户端 S2C 接收器
        // ======================================================================

        // 打开发布 GUI（P5 实现）
        ClientPlayNetworking.registerGlobalReceiver(OpenMailComposePayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "OpenMailComposePayload handler");
            context.client().execute(() -> {
                context.client().setScreenAndShow(new top.csituka.youzaiworldcore.client.screen.MailComposeScreen());
            });
            DebugLogger.exiting("ClientNetworking", "OpenMailComposePayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: OpenMailComposePayload");

        // 收件箱列表
        ClientPlayNetworking.registerGlobalReceiver(MailListPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "MailListPayload handler", "entries=" + payload.entries().size());
            context.client().execute(() -> {
                top.csituka.youzaiworldcore.client.MailClientState.currentInbox = new java.util.ArrayList<>(payload.entries());
                // 列表是权威快照，按它重算未读数，避免徽标与列表对不上
                top.csituka.youzaiworldcore.client.MailClientState.recalculateUnreadCount();
                // MailScreen.renderMailContent 已从 MailClientState.currentInbox 读取数据
            });
            DebugLogger.exiting("ClientNetworking", "MailListPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: MailListPayload");

        // 已发送邮件摘要列表
        ClientPlayNetworking.registerGlobalReceiver(MailSentListPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "MailSentListPayload handler", "summaries=" + payload.summaries().size());
            context.client().execute(() -> {
                top.csituka.youzaiworldcore.client.MailClientState.currentSentList = new java.util.ArrayList<>(payload.summaries());
                context.client().setScreenAndShow(new top.csituka.youzaiworldcore.client.screen.MailSentScreen());
            });
            DebugLogger.exiting("ClientNetworking", "MailSentListPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: MailSentListPayload");

        // 邮件更新（新增/移除/编辑预填）
        ClientPlayNetworking.registerGlobalReceiver(MailUpdatePayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "MailUpdatePayload handler", "mode=" + payload.mode());
            context.client().execute(() -> {
                switch (payload.mode()) {
                    case MailUpdatePayload.MODE_UPDATE -> {
                        // 新增或更新收件箱条目
                        var inbox = top.csituka.youzaiworldcore.client.MailClientState.currentInbox;
                        boolean found = false;
                        for (int i = 0; i < inbox.size(); i++) {
                            if (inbox.get(i).mail().getId().equals(payload.mail().getId())) {
                                inbox.set(i, new MailStreamCodecs.MailRefAndMail(payload.ref(), payload.mail()));
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            inbox.add(new MailStreamCodecs.MailRefAndMail(payload.ref(), payload.mail()));
                        }
                    }
                    case MailUpdatePayload.MODE_REMOVE -> {
                        // 从收件箱移除
                        top.csituka.youzaiworldcore.client.MailClientState.currentInbox
                                .removeIf(pair -> pair.mail().getId().equals(payload.removedMailId()));
                    }
                    case MailUpdatePayload.MODE_EDIT_PREFILL -> {
                        // 编辑预填：存储数据后打开 MailComposeScreen 编辑模式
                        DebugLogger.info("ClientNetworking", "Edit prefill received: mailId=%s, canEdit=%s",
                                payload.mail().getId(), payload.canEdit());
                        top.csituka.youzaiworldcore.client.MailClientState.pendingEditData =
                                new MailStreamCodecs.MailRefAndMail(payload.ref(), payload.mail());
                        if (payload.canEdit()) {
                            context.client().setScreenAndShow(
                                    new top.csituka.youzaiworldcore.client.screen.MailComposeScreen(true, payload.mail().getId()));
                        } else {
                            notifyMailResult(context.client(), "已有玩家领取过附件，不可编辑，仅可撤回", false);
                        }
                    }
                }
                // 单条更新同样会改变未读数（如领取即已读），同步重算徽标
                top.csituka.youzaiworldcore.client.MailClientState.recalculateUnreadCount();
            });
            DebugLogger.exiting("ClientNetworking", "MailUpdatePayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: MailUpdatePayload");

        // 操作结果反馈：邮件界面打开时走顶部提示条，否则回落到聊天栏
        ClientPlayNetworking.registerGlobalReceiver(MailOpResultPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "MailOpResultPayload handler", "success=" + payload.success());
            context.client().execute(() ->
                    notifyMailResult(context.client(),
                            payload.reason() != null ? payload.reason() : "", payload.success()));
            DebugLogger.exiting("ClientNetworking", "MailOpResultPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: MailOpResultPayload");

        // 已注册玩家名单（发布页「选取玩家」弹窗）
        ClientPlayNetworking.registerGlobalReceiver(MailPlayerListPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "MailPlayerListPayload handler",
                    "players=" + payload.playerNames().size());
            context.client().execute(() ->
                    top.csituka.youzaiworldcore.client.MailClientState.registeredPlayers =
                            new java.util.ArrayList<>(payload.playerNames()));
            DebugLogger.exiting("ClientNetworking", "MailPlayerListPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: MailPlayerListPayload");

        // 未读数量与发布权限同步
        ClientPlayNetworking.registerGlobalReceiver(MailUnreadCountPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "MailUnreadCountPayload handler", "unread=" + payload.unreadCount());
            context.client().execute(() -> {
                top.csituka.youzaiworldcore.client.MailClientState.unreadCount = payload.unreadCount();
                top.csituka.youzaiworldcore.client.MailClientState.canSend = payload.canSend();
                // MainMenuElements.renderCustomContent 已读取 MailClientState.unreadCount 绘制徽标
            });
            DebugLogger.exiting("ClientNetworking", "MailUnreadCountPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: MailUnreadCountPayload");

        // ======================================================================
        // 老吴贴贴事件（LaowuMeme）—— 客户端 S2C 接收器
        // 服务端权威状态机广播 trigger/stop，客户端只收包驱动渲染与音频。
        // ======================================================================

        ClientPlayNetworking.registerGlobalReceiver(LaowuMemeTriggerPayload.ID, (payload, context) -> {
            context.client().execute(() ->
                    top.csituka.youzaiworldcore.client.laowumeme.LaowuMemeClientState.get()
                            .onTrigger(payload.catAId(), payload.catBId(), payload.soundId(), payload.rollSign()));
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: LaowuMemeTriggerPayload");

        ClientPlayNetworking.registerGlobalReceiver(LaowuMemeStopPayload.ID, (payload, context) -> {
            context.client().execute(() ->
                    top.csituka.youzaiworldcore.client.laowumeme.LaowuMemeClientState.get()
                            .onStop(payload.catAId(), payload.catBId()));
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: LaowuMemeStopPayload");

        ClientPlayNetworking.registerGlobalReceiver(FunctionToggleSyncPayload.TYPE, (payload, context) -> {
            var map = new java.util.HashMap<String, Boolean>();
            map.put("ladder_extend_downward", payload.ladderExtendDownward());
            map.put("tool_info_overlay", payload.toolInfoOverlay());
            map.put("block_animation", payload.blockAnimation());
            map.put("crafting_sound", payload.craftingSound());
            map.put("item_sparkle", payload.itemSparkle());
            FunctionToggleClientState.update(map);
            DebugLogger.debug("ClientNetworking", "收到功能开关同步包");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: FunctionToggleSyncPayload");

        DebugLogger.exiting("ClientNetworking", "initialize");
    }

    public static void sendDecomposePacket() {
        ClientPlayNetworking.send(new DecomposeItemPayload());
    }

    /**
     * 展示一条邮件系统反馈。
     * <p>
     * 邮件界面打开时聊天栏被界面遮住，玩家看不到反馈，因此改用界面顶部的浮动提示条；
     * 界面未打开（例如通过 {@code /yzwc mail} 命令操作）时仍回落到聊天栏。
     * </p>
     *
     * @param client  客户端实例
     * @param message 反馈文本
     * @param success 是否为成功类消息（决定配色）
     */
    private static void notifyMailResult(net.minecraft.client.Minecraft client, String message, boolean success) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (top.csituka.youzaiworldcore.client.screen.MailToast.isMailScreenOpen()) {
            top.csituka.youzaiworldcore.client.screen.MailToast.show(message, success);
            return;
        }
        if (client.player != null) {
            client.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal((success ? "§a" : "§c") + message));
        }
    }
}
