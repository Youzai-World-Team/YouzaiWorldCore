"""在临时目录运行实际 AFK 状态机、配置和输入 Mixin 的独立回归，不构建模组。"""

import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "top/csituka/youzaiworldcore"
SOURCES = [
    f"src/main/java/{PACKAGE}/afk/AfkActivityState.java",
    f"src/main/java/{PACKAGE}/config/AfkConfig.java",
    f"src/client/java/{PACKAGE}/client/afk/AfkInputTracker.java",
    f"src/client/java/{PACKAGE}/mixin/client/afk/AfkKeyboardHandlerMixin.java",
    f"src/client/java/{PACKAGE}/mixin/client/afk/AfkMouseHandlerMixin.java",
    f"src/main/java/{PACKAGE}/mixin/afk/ServerGamePacketListenerImplMixin.java",
]

STUBS = {
    "org/spongepowered/asm/mixin/Mixin.java": """
package org.spongepowered.asm.mixin;
public @interface Mixin { Class<?>[] value(); }
""",
    "org/spongepowered/asm/mixin/Shadow.java": """
package org.spongepowered.asm.mixin;
public @interface Shadow {}
""",
    "org/spongepowered/asm/mixin/injection/At.java": """
package org.spongepowered.asm.mixin.injection;
public @interface At { String value(); }
""",
    "org/spongepowered/asm/mixin/injection/Inject.java": """
package org.spongepowered.asm.mixin.injection;
public @interface Inject { String[] method(); At at(); }
""",
    "org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java": """
package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfo {}
""",
    "com/mojang/blaze3d/platform/InputConstants.java": """
package com.mojang.blaze3d.platform;
// 26.3 改用 SDL：按键 action 为 1 按下 / 0 释放 / -1 重复，键值为 SDL 扫描码。
public class InputConstants { public static final int PRESS=1, RELEASE=0, REPEAT=-1, KEY_ESCAPE=41; }
""",
    "net/minecraft/client/input/KeyEvent.java": """
package net.minecraft.client.input;
public record KeyEvent(int key) {}
""",
    "net/minecraft/client/input/MouseButtonInfo.java": """
package net.minecraft.client.input;
public record MouseButtonInfo(int button) {}
""",
    "net/minecraft/client/KeyboardHandler.java": """
package net.minecraft.client;
public class KeyboardHandler {}
""",
    "net/minecraft/client/MouseHandler.java": """
package net.minecraft.client;
public class MouseHandler { public boolean grabbed = true; public boolean isMouseGrabbed() { return grabbed; } }
""",
    "net/minecraft/client/gui/screens/Screen.java": """
package net.minecraft.client.gui.screens;
public class Screen {}
""",
    "net/minecraft/client/gui/screens/ChatScreen.java": """
package net.minecraft.client.gui.screens;
public class ChatScreen extends Screen {}
""",
    "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java": """
package net.minecraft.client.gui.screens.inventory;
public class AbstractContainerScreen extends net.minecraft.client.gui.screens.Screen {}
""",
    "net/minecraft/client/gui/screens/inventory/InventoryScreen.java": """
package net.minecraft.client.gui.screens.inventory;
public class InventoryScreen extends AbstractContainerScreen {}
""",
    "net/minecraft/client/Minecraft.java": """
package net.minecraft.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    public static Minecraft getInstance() { return INSTANCE; }
    public Object player = new Object();
    public final MouseHandler mouseHandler = new MouseHandler();
    public final Gui gui = new Gui();
    public final Options options = new Options();
    public final Window window = new Window();
    public Window getWindow() { return window; }
    public Object getConnection() { return player; }
    public static class Window {
        public boolean focused = true;
        public long handle() { return 1L; }
        public boolean isFocused() { return focused; }
    }
    public static class Gui { public Screen current; public Screen screen() { return current; } }
    public static class Key {
        public int key;
        Key(int key) { this.key = key; }
        public boolean matches(KeyEvent e) { return key == e.key(); }
    }
    public static class Options {
        public Key keyInventory=new Key(69), keyChat=new Key(84), keyCommand=new Key(47),
            keyScreenshot=new Key(291), keyTogglePerspective=new Key(294);
    }
}
""",
    "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking.java": """
package net.fabricmc.fabric.api.client.networking.v1;
import java.util.ArrayList;
import java.util.List;
import top.csituka.youzaiworldcore.network.AfkHeartbeatPayload;
public class ClientPlayNetworking {
    public static boolean supported = true;
    public static final List<AfkHeartbeatPayload> SENT = new ArrayList<>();
    public static boolean canSend(Object id) { return supported; }
    public static void send(AfkHeartbeatPayload payload) { SENT.add(payload); }
}
""",
    f"{PACKAGE}/network/AfkHeartbeatPayload.java": """
package top.csituka.youzaiworldcore.network;
public record AfkHeartbeatPayload(int idleTicks, long inputSequence) { public static final Object ID = new Object(); }
""",
    f"{PACKAGE}/util/DebugLogger.java": """
package top.csituka.youzaiworldcore.util;
public class DebugLogger {
    public static void trace(String module, String format, Object... args) {}
    public static void info(String module, String format, Object... args) {}
    public static void warn(String module, String format, Object... args) {}
    public static void entering(String module, String method, Object... args) {}
    public static void exiting(String module, String method, Object... args) {}
    public static void stateChange(String module, String name, String key, Object before, Object after) {}
}
""",
    f"{PACKAGE}/config/GlobalSettings.java": """
package top.csituka.youzaiworldcore.config;
public class GlobalSettings {
    public static final String AFK_MODULE = "afk_module";
    public static final ConfigSection SECTION = new ConfigSection();
    public static ConfigSection section(String name) { return SECTION; }
    public static void save() {}
}
""",
    f"{PACKAGE}/config/ConfigSection.java": """
package top.csituka.youzaiworldcore.config;
import java.util.HashMap;
public class ConfigSection {
    public final HashMap<String,Object> values = new HashMap<>();
    public boolean isEmpty() { return values.isEmpty(); }
    public void set(String key, Object value) { values.put(key, value); }
    public boolean getBoolean(String key, boolean fallback) { return (boolean)values.getOrDefault(key, fallback); }
    public String getString(String key, String fallback) { return (String)values.getOrDefault(key, fallback); }
    public int getInt(String key, int fallback, int min, int max) {
        int value = (int)values.getOrDefault(key, fallback);
        if (value < min || value > max) fail(key, "range");
        return value;
    }
    public <T extends Enum<T>> T getEnum(String key, T fallback, Class<T> type) {
        Object value = values.getOrDefault(key, fallback);
        return value instanceof String ? Enum.valueOf(type, (String)value) : type.cast(value);
    }
    public void fail(String key, String reason) { throw new IllegalArgumentException(key + ": " + reason); }
}
""",
}

STUBS.update({
    "net/minecraft/network/protocol/Packet.java": """
package net.minecraft.network.protocol;
public interface Packet<T> { void handle(T listener); }
""",
    "net/minecraft/network/protocol/PacketUtils.java": """
package net.minecraft.network.protocol;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.level.ServerLevel;
public final class PacketUtils {
    public static final class Requeued extends RuntimeException {}
    public static void ensureRunningOnSameThread(Packet<ServerGamePacketListener> packet,
            ServerGamePacketListener listener, ServerLevel level) {
        var server = level.getServer();
        if (!server.mainThread) {
            server.packets.add(() -> packet.handle(listener));
            throw new Requeued();
        }
    }
}
""",
    "net/minecraft/network/protocol/game/ServerGamePacketListener.java": """
package net.minecraft.network.protocol.game;
public interface ServerGamePacketListener {
    void handleChat(ServerboundChatPacket packet);
    void handleChatCommand(ServerboundChatCommandPacket packet);
    void handleSignedChatCommand(ServerboundChatCommandSignedPacket packet);
}
""",
    "net/minecraft/server/MinecraftServer.java": """
package net.minecraft.server;
import java.util.*;
public class MinecraftServer {
    public boolean mainThread, runningTask;
    public final Queue<Runnable> packets = new ArrayDeque<>(), tasks = new ArrayDeque<>();
    public int getTickCount() { return 100; }
    // 仿原版 ReentrantBlockableEventLoop：主线程正在执行任务时，嵌套 execute 仍排队。
    public void execute(Runnable action) {
        if (!mainThread || runningTask) tasks.add(action); else action.run();
    }
    public void drainPackets() {
        mainThread = true; runningTask = true;
        while (!packets.isEmpty()) packets.remove().run();
        runningTask = false;
    }
    public void drainTasks() {
        mainThread = true; runningTask = true;
        while (!tasks.isEmpty()) tasks.remove().run();
        runningTask = false;
    }
}
""",
    "net/minecraft/server/level/ServerLevel.java": """
package net.minecraft.server.level;
import net.minecraft.server.MinecraftServer;
public record ServerLevel(MinecraftServer server) { public MinecraftServer getServer() { return server; } }
""",
    "net/minecraft/server/level/ServerPlayer.java": """
package net.minecraft.server.level;
import top.csituka.youzaiworldcore.afk.AfkActivityState;
public class ServerPlayer {
    public final AfkActivityState activity = new AfkActivityState(0);
    public final ServerLevel level;
    public boolean disconnected;
    public int explicitCalls;
    public ServerPlayer(net.minecraft.server.MinecraftServer server) { level = new ServerLevel(server); }
    public ServerLevel level() { return level; }
    public boolean hasDisconnected() { return disconnected; }
    public Name getName() { return new Name(); }
    public static class Name { public String getString() { return "TestPlayer"; } }
}
""",
    f"{PACKAGE}/afk/AfkManager.java": """
package top.csituka.youzaiworldcore.afk;
import net.minecraft.server.level.ServerPlayer;
public final class AfkManager {
    public static void onHeartbeat(ServerPlayer player, long now, int idle, long sequence) {
        if (!player.disconnected) player.activity.heartbeat(now,idle,sequence);
    }
    public static void onChatActivity(ServerPlayer player, long now) {
        player.explicitCalls++; player.activity.explicitActivity(now);
    }
}
""",
    "net/minecraft/server/network/ServerGamePacketListenerImpl.java": """
package net.minecraft.server.network;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.csituka.youzaiworldcore.mixin.afk.ServerGamePacketListenerImplMixin;
public class ServerGamePacketListenerImpl extends ServerGamePacketListenerImplMixin implements ServerGamePacketListener {
    public ServerGamePacketListenerImpl(net.minecraft.server.level.ServerPlayer player) { this.player=player; }
    private void invoke(String suffix, Packet<ServerGamePacketListener> packet, boolean toggle) {
        try {
            var method = java.util.Arrays.stream(ServerGamePacketListenerImplMixin.class.getDeclaredMethods())
                .filter(m -> m.getName().endsWith(suffix)).findFirst().orElseThrow();
            method.setAccessible(true); method.invoke(this,packet,new CallbackInfo());
        } catch (java.lang.reflect.InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new RuntimeException(error.getCause());
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
        // 仿 tryHandleChat：原版仍通过 execute 执行真正的聊天/命令。
        player.level().getServer().execute(() -> { if(toggle) player.activity.enter(100,true); });
    }
    public void handleChat(ServerboundChatPacket packet) { invoke("$onChat",packet,false); }
    public void handleChatCommand(ServerboundChatCommandPacket packet) { invoke("$onCommand",packet,packet.toggle()); }
    public void handleSignedChatCommand(ServerboundChatCommandSignedPacket packet) { invoke("$onSignedCommand",packet,packet.toggle()); }
}
""",
})
for packet, handler in [("ServerboundChatPacket", "handleChat"),
                        ("ServerboundChatCommandPacket", "handleChatCommand"),
                        ("ServerboundChatCommandSignedPacket", "handleSignedChatCommand")]:
    STUBS[f"net/minecraft/network/protocol/game/{packet}.java"] = f"""
package net.minecraft.network.protocol.game;
public record {packet}(boolean toggle) implements net.minecraft.network.protocol.Packet<ServerGamePacketListener> {{
    public void handle(ServerGamePacketListener listener) {{ listener.{handler}(this); }}
}}
"""

CHECK = r"""
package top.csituka.youzaiworldcore.afk;

import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.input.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.*;
import top.csituka.youzaiworldcore.network.AfkHeartbeatPayload;
import top.csituka.youzaiworldcore.client.afk.AfkInputTracker;
import top.csituka.youzaiworldcore.config.*;
import top.csituka.youzaiworldcore.mixin.client.afk.*;
import static top.csituka.youzaiworldcore.afk.AfkActivityState.DetectMode.*;

public class AfkRegression {
    static int checks;
    static void require(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }
    static void rejected(Runnable call, String reason) {
        boolean failed = false;
        try { call.run(); } catch (IllegalArgumentException e) { failed = true; }
        require(failed, reason);
    }
    static void packetOrder() {
        var directServer = new MinecraftServer();
        var directPlayer = new ServerPlayer(directServer);
        directPlayer.activity.heartbeat(90,0,6);
        directServer.mainThread = true; directServer.runningTask = true;
        AfkHeartbeatReceiver.receive(new AfkHeartbeatPayload(0,7), new AfkHeartbeatReceiver.Context(directPlayer));
        directPlayer.activity.explicitActivity(100);
        directPlayer.activity.enter(100,true);
        directServer.drainTasks();
        require(!directPlayer.activity.shouldExit(BOTH,120), "主线程收到心跳必须立即更新，不能另排 execute 延后到进入 AFK 之后");
        for (boolean signed : new boolean[]{false,true}) {
            var server = new MinecraftServer();
            var player = new ServerPlayer(server);
            var listener = new ServerGamePacketListenerImpl(player);
            player.activity.heartbeat(90,0,6);
            // 客户端先发包含本次操作序号的心跳，再发 /yzwc afk。
            server.packets.add(() -> AfkHeartbeatReceiver.receive(new AfkHeartbeatPayload(0,7),
                    new AfkHeartbeatReceiver.Context(player)));
            Packet<ServerGamePacketListener> packet = signed
                    ? new ServerboundChatCommandSignedPacket(true) : new ServerboundChatCommandPacket(true);
            boolean requeued = false;
            try { packet.handle(listener); } catch (PacketUtils.Requeued expected) { requeued = true; }
            require(requeued, "命令不能从网络线程越过先收到的心跳");
            require(player.explicitCalls == 0, "网络线程不得写活动状态");
            server.drainPackets(); server.drainTasks();
            require(player.explicitCalls == 1, "主线程只记录一次命令活动");
            require(!player.activity.shouldExit(BOTH,120), "自己的命令心跳及任务队列不能取消手动 AFK");
            AfkHeartbeatReceiver.receive(new AfkHeartbeatPayload(20,7), new AfkHeartbeatReceiver.Context(player));
            require(!player.activity.shouldExit(BOTH,120), "后续相同序号心跳保持 AFK");
            AfkHeartbeatReceiver.receive(new AfkHeartbeatPayload(0,8), new AfkHeartbeatReceiver.Context(player));
            require(player.activity.shouldExit(BOTH,120), "真正的新操作仍恢复 AFK");
        }
        var server = new MinecraftServer();
        var player = new ServerPlayer(server);
        var listener = new ServerGamePacketListenerImpl(player);
        player.activity.enter(100,true);
        try { new ServerboundChatPacket(false).handle(listener); } catch (PacketUtils.Requeued expected) {}
        server.drainPackets(); server.drainTasks();
        require(player.explicitCalls == 1 && player.activity.shouldExit(BOTH,100), "普通聊天仍恢复 AFK");
    }
    static void stateMachine() {
        // 心跳先于命令到达，手动切换快照包含命令自身的输入和聊天事件。
        var state = new AfkActivityState(0);
        state.heartbeat(100, 0, 7);
        state.explicitActivity(100);
        state.enter(100, true);
        for (long now : new long[]{101, 120, 140, 160}) {
            state.heartbeat(now, 0, 7); // 极端网络抖动：旧序号错误地携带零空闲也不能唤醒。
            state.serverActivity(now); // 水流、矿车等被动移动。
            require(!state.shouldExit(BOTH, now), "旧心跳或被动移动不应取消手动 AFK");
        }
        state.heartbeat(160, 0, 8);
        require(state.shouldExit(BOTH, 160), "真正的新输入应恢复，不能设置一刀切宽限期");
        state.exit(160);
        require(!state.shouldEnter(BOTH, 175, 600), "恢复后不能立即重新 AFK");

        // 自动 AFK 期间持续被动移动，保活但无新输入；不应反复切换或重置踢出计时。
        state = new AfkActivityState(0);
        state.heartbeat(0, 0, 0);
        for (int now=20; now<=600; now+=20) {
            state.heartbeat(now, now, 0);
            state.serverActivity(now);
        }
        require(state.shouldEnter(BOTH, 600, 600), "移动中的空闲客户端仍应进入 AFK");
        state.enter(600, false);
        for (int now=620; now<=1200; now+=20) {
            state.heartbeat(now, now, 0);
            state.serverActivity(now);
            require(!state.shouldExit(BOTH, now), "自动 AFK 不能被被动移动打断");
        }
        require(!state.shouldKick(1199, 600) && state.shouldKick(1200, 600), "踢出从进入时刻计时");
        require(!state.shouldKick(2000, 0), "关闭踢出");

        // SERVER 模式下站着聊天也会延长空闲期限。
        state = new AfkActivityState(0);
        state.explicitActivity(590);
        require(!state.shouldEnter(SERVER, 600, 600), "聊天必须参与进入判定");
        require(state.shouldEnter(SERVER, 1190, 600), "聊天后重新计算完整阈值");
        state.enter(1190, true);
        state.explicitActivity(1190);
        require(state.shouldExit(SERVER, 1190), "同一 tick 的后续命令也能恢复");
        state.exit(1190);
        require(!state.shouldEnter(SERVER, 1200, 600), "手动退出也必须重新计时");

        state = new AfkActivityState(0);
        require(!state.shouldEnter(CLIENT, 10000, 600), "CLIENT 无心跳时不能自动判定");
        state.enter(10000, true);
        state.serverActivity(10001);
        require(state.shouldExit(CLIENT, 10001), "无模组客户端可以走动退出手动 AFK");
        state = new AfkActivityState(0);
        state.heartbeat(100, 0, 1);
        state.enter(100, true);
        state.serverActivity(120);
        require(!state.shouldExit(BOTH, 120), "有效客户端忽略服务端移动");
        require(!state.shouldExit(BOTH, 201), "心跳超时不能重放先前被忽略的移动");
        state.serverActivity(202);
        require(state.shouldExit(BOTH, 202), "心跳超时后新的服务端活动可恢复");
        state = new AfkActivityState(0);
        state.enter(600, true);
        state.heartbeat(601, 500, 50);
        require(!state.shouldExit(BOTH, 601), "首次接入心跳只建立基线");
        state.heartbeat(620, 0, 51);
        require(state.shouldExit(BOTH, 620), "首次心跳之后的新输入可以恢复");
        state = new AfkActivityState(0);
        state.heartbeat(100, 0, -1);
        state.heartbeat(100, -1, 1);
        require(!state.clientAlive(100), "非法心跳不应激活客户端通道");
        state.heartbeat(100, 0, 9);
        state.heartbeat(200, 0, 8);
        require(!state.clientAlive(201), "过期序号不能续期心跳");
        state.rebase(600);
        require(!state.shouldEnter(SERVER, 610, 600), "切换模式或重新启用后重新计时");

        state = new AfkActivityState(0);
        state.heartbeat(600, 0, 1);
        require(state.shouldEnter(SERVER, 600, 600), "SERVER 的进入不依赖客户端通道");
        state.enter(600, false);
        state.heartbeat(620, 0, 2);
        require(!state.shouldExit(SERVER, 620), "SERVER 的退出同样不依赖客户端通道");
        state.explicitActivity(620);
        require(state.shouldExit(SERVER, 620), "SERVER 仍接受聊天和命令");
    }

    static Method method(Class<?> type, String suffix) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().endsWith(suffix)) { m.setAccessible(true); return m; }
        }
        throw new AssertionError(suffix);
    }
    static void field(Object target, String name, Object value) throws Exception {
        Class<?> type = target == null ? AfkInputTracker.class : target.getClass();
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    static long sequence() throws Exception {
        var field = AfkInputTracker.class.getDeclaredField("inputSequence");
        field.setAccessible(true);
        return field.getLong(null);
    }
    static void input() throws Exception {
        var mc = Minecraft.getInstance();
        var keyboard = new AfkKeyboardHandlerMixin();
        var mouse = new AfkMouseHandlerMixin();
        var key = method(keyboard.getClass(), "$onKeyPress");
        var button = method(mouse.getClass(), "$onButton");
        var move = method(mouse.getClass(), "$onMove");
        AfkInputTracker.reset();
        key.invoke(keyboard, 1L, 1, new KeyEvent(26), null);
        long first = sequence();
        for (int i=0; i<100; i++) key.invoke(keyboard, 1L, -1, new KeyEvent(26), null);
        key.invoke(keyboard, 1L, 0, new KeyEvent(26), null);
        require(first == 1 && sequence() == first, "长按和松开不能续期输入");
        mc.gui.current = new ChatScreen();
        key.invoke(keyboard, 1L, 1, new KeyEvent(4), null);
        key.invoke(keyboard, 1L, 1, new KeyEvent(40), null);
        require(sequence() == first, "聊天打字和回车按键本身不算发送");
        int sent = ClientPlayNetworking.SENT.size();
        AfkInputTracker.onChatSent();
        require(sequence() == first+1 && ClientPlayNetworking.SENT.size() == sent+1,
                "发送命令必须立即发送包含本次输入序号的心跳");
        require(ClientPlayNetworking.SENT.getLast().inputSequence() == sequence(), "发送正确序号");
        mc.gui.current = null;
        long boundary = sequence();
        key.invoke(keyboard, 1L, 0, new KeyEvent(40), null);
        field(mouse, "ignoreFirstMove", true);
        move.invoke(mouse, 1L, 100D, 100D, 4D, 3D, null);
        require(sequence() == boundary, "发送命令后的回车释放和鼠标重新捕获必须忽略");
        field(mouse, "ignoreFirstMove", false);
        move.invoke(mouse, 1L, 100D, 100D, 0D, 0D, null);
        require(sequence() == boundary, "捕获鼠标时没有相对位移不算移动");
        move.invoke(mouse, 1L, 100D, 100D, 4D, 3D, null);
        require(sequence() == boundary+1, "真正移动鼠标可以恢复");
        boundary = sequence();
        button.invoke(mouse, 1L, new MouseButtonInfo(1), 0, null);
        require(sequence() == boundary, "鼠标释放不算新输入");
        button.invoke(mouse, 1L, new MouseButtonInfo(1), 1, null);
        require(sequence() == boundary+1, "鼠标按下算操作");
        boundary = sequence();
        mc.gui.current = new InventoryScreen();
        move.invoke(mouse, 1L, 200D, 200D, 4D, 3D, null);
        require(sequence() == boundary, "界面内悬停不算活动");
        mc.gui.current = new Screen();
        key.invoke(keyboard, 1L, 1, new KeyEvent(26), null);
        button.invoke(mouse, 1L, new MouseButtonInfo(1), 1, null);
        require(sequence() == boundary, "游戏外界面操作不算活动");
        mc.gui.current = null;
        mc.options.keyInventory.key = 12;
        key.invoke(keyboard, 1L, 1, new KeyEvent(12), null);
        require(sequence() == boundary, "改键后的物品栏键也要排除");
        key.invoke(keyboard, 1L, 1, new KeyEvent(8), null);
        require(sequence() == boundary+1, "原物品栏键改作其他用途后可以计入操作");
        boundary = sequence();
        mc.window.focused = false;
        key.invoke(keyboard, 1L, 1, new KeyEvent(26), null);
        require(sequence() == boundary, "失焦不计入输入");
        mc.window.focused = true;
        key.invoke(keyboard, 2L, 1, new KeyEvent(26), null);
        require(sequence() == boundary, "其他窗口事件不计入输入");
        sent = ClientPlayNetworking.SENT.size();
        ClientPlayNetworking.supported = false;
        for (int i=0; i<20; i++) AfkInputTracker.onClientTick(mc);
        require(ClientPlayNetworking.SENT.size() == sent, "不向不支持通道的服务端发送心跳");
        ClientPlayNetworking.supported = true;
        field(null, "lastInputNanos", System.nanoTime() - ((long)Integer.MAX_VALUE+100) * 50_000_000L);
        for (int i=0; i<20; i++) AfkInputTracker.onClientTick(mc);
        require(ClientPlayNetworking.SENT.getLast().idleTicks() == Integer.MAX_VALUE, "超长空闲饱和而非溢出归零");
        AfkInputTracker.reset();
        require(sequence() == 0, "重连必须重置输入序号");
    }

    static void config() {
        AfkConfig.writeDefaults();
        require(AfkConfig.isEnabled() && AfkConfig.getDetectMode() == BOTH, "默认开关和模式");
        require(AfkConfig.getPrefix().equals("[AFK] ") && AfkConfig.isNametagPrefixEnabled(), "默认名字牌前缀");
        AfkConfig.setPrefix("[离开] ");
        AfkConfig.setNametagPrefixEnabled(false);
        AfkConfig.load();
        require(AfkConfig.getPrefix().equals("[离开] ") && !AfkConfig.isNametagPrefixEnabled(), "显示配置保存和重载");
        rejected(() -> AfkConfig.setPrefix("x".repeat(65)), "拒绝过长前缀");
        rejected(() -> AfkConfig.setPrefix("a\nb"), "拒绝换行前缀");
        AfkConfig.setEnabled(false);
        GlobalSettings.SECTION.values.remove("enabled");
        AfkConfig.load();
        require(AfkConfig.isEnabled(), "重载时缺失的键恢复默认，不沿用旧内存值");
        GlobalSettings.SECTION.values.clear();
        AfkConfig.load();
        require(AfkConfig.isNametagPrefixEnabled() && AfkConfig.getPrefix().equals("[AFK] "), "空分节重建默认值");
    }

    public static void main(String[] args) throws Exception {
        stateMachine();
        packetOrder();
        input();
        config();
        System.out.println("AFK 回归通过：" + checks + " 项断言（实际状态机、配置、输入追踪及 Mixin 方法）");
    }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, help="JDK 25+ 路径；未指定则从 PATH 查找")
    args = parser.parse_args()
    javac = str(args.java_home / "bin/javac") if args.java_home else shutil.which("javac")
    java = str(args.java_home / "bin/java") if args.java_home else shutil.which("java")
    if not javac or not java:
        parser.error("需要 JDK 25+，可传 --java-home")
    with tempfile.TemporaryDirectory(prefix="yzwc-afk-check-") as temporary:
        directory = Path(temporary)
        networking = (ROOT / f"src/main/java/{PACKAGE}/network/ModNetworking.java").read_text()
        registration = networking.index("ServerPlayNetworking.registerGlobalReceiver(AfkHeartbeatPayload.ID")
        opening = networking.index("-> {", registration) + len("-> {")
        depth, closing = 1, opening
        while depth:
            depth += (networking[closing] == "{") - (networking[closing] == "}")
            closing += 1
        receiver = """package top.csituka.youzaiworldcore.afk;
import net.minecraft.server.level.ServerPlayer;
import top.csituka.youzaiworldcore.network.AfkHeartbeatPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;
public final class AfkHeartbeatReceiver {
    public record Context(ServerPlayer player) {}
    public static void receive(AfkHeartbeatPayload payload, Context context) {
""" + networking[opening:closing-1] + "\n    }\n}"
        STUBS[f"{PACKAGE}/afk/AfkHeartbeatReceiver.java"] = receiver
        for name, source in STUBS.items():
            target = directory / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(source, encoding="utf-8")
        harness = directory / PACKAGE / "afk/AfkRegression.java"
        harness.parent.mkdir(parents=True, exist_ok=True)
        harness.write_text(CHECK, encoding="utf-8")
        classes = directory / "classes"
        classes.mkdir()
        subprocess.run([javac, "--release", "25", "-encoding", "UTF-8", "-proc:none",
                        "-sourcepath", str(directory), "-d", str(classes),
                        *[str(ROOT / path) for path in SOURCES], str(harness)], check=True)
        subprocess.run([java, "-cp", str(classes),
                        "top.csituka.youzaiworldcore.afk.AfkRegression"], check=True)


if __name__ == "__main__":
    main()
