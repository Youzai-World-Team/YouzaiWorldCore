package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import top.csituka.youzaiworldcore.client.screen.LoginScreen;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.RegisterScreen;
import top.csituka.youzaiworldcore.client.screen.block.TeleportAnchorNameScreen;
import top.csituka.youzaiworldcore.client.screen.block.TeleportAnchorScreen;
import top.csituka.youzaiworldcore.client.screen.element.AboutMeMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MainMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.element.SettingsMenuElements;
import top.csituka.youzaiworldcore.client.screen.element.SwitchWorldMenuElements;
import top.csituka.youzaiworldcore.client.hud.AdventureLevelHudRenderer;
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

        ClientPlayNetworking.registerGlobalReceiver(OpenMenuPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "OpenMenuPayload handler");
            Minecraft client = context.client();
            client.execute(() -> {
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
                // 如果当前已经是认证界面，不重复打开
                Screen currentScreen = client.gui.screen();
                boolean isRegisterScreen = currentScreen instanceof RegisterScreen;
                boolean isLoginScreen = currentScreen instanceof LoginScreen;
                DebugLogger.branch("ClientNetworking", "currentScreen instanceof RegisterScreen", isRegisterScreen);
                DebugLogger.branch("ClientNetworking", "currentScreen instanceof LoginScreen", isLoginScreen);
                if (isRegisterScreen || isLoginScreen) {
                    DebugLogger.exiting("ClientNetworking", "OpenAuthScreenPayload handler (skipped, screen already open)");
                    return;
                }
                String type = payload.screenType();
                String username = payload.username();
                boolean isRegister = "register".equals(type);
                DebugLogger.branch("ClientNetworking", "screenType == register", isRegister);
                if (isRegister) {
                    client.setScreenAndShow(new RegisterScreen(username));
                } else if ("login".equals(type)) {
                    client.setScreenAndShow(new LoginScreen(username));
                }
            });
            DebugLogger.exiting("ClientNetworking", "OpenAuthScreenPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: OpenAuthScreenPayload");

        // 注册传送锚点列表处理器
        ClientPlayNetworking.registerGlobalReceiver(TeleportAnchorListPayload.TYPE, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "TeleportAnchorListPayload handler");
            context.client().execute(() -> {
                context.client().setScreenAndShow(new TeleportAnchorScreen(payload.points(), payload.currentPos(), payload.currentDim()));
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
                // MailScreen.extractRenderState 已从 MailClientState.currentInbox 读取数据
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
                            context.client().player.sendSystemMessage(
                                    net.minecraft.network.chat.Component.literal("§c已有玩家领取过附件，不可编辑，仅可撤回"));
                        }
                    }
                }
            });
            DebugLogger.exiting("ClientNetworking", "MailUpdatePayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: MailUpdatePayload");

        // 操作结果反馈
        ClientPlayNetworking.registerGlobalReceiver(MailOpResultPayload.ID, (payload, context) -> {
            DebugLogger.entering("ClientNetworking", "MailOpResultPayload handler", "success=" + payload.success());
            context.client().execute(() -> {
                var player = context.client().player;
                if (player != null) {
                    var color = payload.success() ? "§a" : "§c";
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(color + (payload.reason() != null ? payload.reason() : "")));
                }
            });
            DebugLogger.exiting("ClientNetworking", "MailOpResultPayload handler");
        });
        DebugLogger.info("ClientNetworking", "Registered receiver: MailOpResultPayload");

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

        DebugLogger.exiting("ClientNetworking", "initialize");
    }

    public static void sendDecomposePacket() {
        ClientPlayNetworking.send(new DecomposeItemPayload());
    }
}
