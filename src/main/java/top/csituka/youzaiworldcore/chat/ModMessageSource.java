package top.csituka.youzaiworldcore.chat;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/** 按发送调用栈和 Fabric 模组目录识别消息来源，类归属只查询一次。 */
public final class ModMessageSource {
    private static final String CORE_PACKAGE = "top.csituka.youzaiworldcore.";
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static final Set<String> TRANSPORT_CLASSES = Set.of(
            "net.minecraft.server.network.ServerCommonPacketListenerImpl",
            "net.minecraft.server.network.ServerGamePacketListenerImpl",
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.server.players.PlayerList",
            "net.minecraft.commands.CommandSourceStack",
            "net.minecraft.world.entity.player.Player",
            "net.minecraft.client.player.LocalPlayer",
            "net.minecraft.client.gui.components.ChatComponent",
            "net.minecraft.client.multiplayer.chat.ChatListener",
            "net.minecraft.client.multiplayer.ClientPacketListener");
    private static final Map<String, Optional<String>> OWNERS = new ConcurrentHashMap<>();
    private static final ClassValue<Map<String, String>> MIXIN_SOURCES = new ClassValue<>() {
        @Override
        protected Map<String, String> computeValue(Class<?> type) {
            Map<String, String> sources = new HashMap<>();
            try {
                for (var method : type.getDeclaredMethods()) {
                    @SuppressWarnings("null")
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    if (merged != null)
                        sources.put(method.getName(), merged.mixin());
                }
            } catch (RuntimeException | LinkageError error) {
                DebugLogger.SERVER.exceptionSummary("ChatMessages", "resolveMixinMessageSource", error.getMessage());
            }
            return Map.copyOf(sources);
        }
    };

    private ModMessageSource() {
    }

    public static Optional<String> caller() {
        return WALKER.walk(frames -> {
            var iterator = frames.iterator();
            while (iterator.hasNext()) {
                var frame = iterator.next();
                String name = frame.getClassName();
                String method = frame.getMethodName();
                if (name.startsWith(CORE_PACKAGE + "chat.")
                        || name.startsWith(CORE_PACKAGE + "client.chat.") || isFormattingHook(method))
                    continue;
                if (name.startsWith(CORE_PACKAGE) || method.contains("youzaiworldcore$") || method.contains("yzwc$")) {
                    return Optional.of("YouzaiWorldCore");
                }
                if (name.startsWith("net.minecraft.")
                        && (!TRANSPORT_CLASSES.contains(name) || method.contains("$"))) {
                    String mixin = MIXIN_SOURCES.get(frame.getDeclaringClass()).get(method);
                    if (mixin != null) {
                        Optional<String> owner = owner(mixin);
                        if (owner.isPresent())
                            return owner;
                    }
                }
                if (TRANSPORT_CLASSES.contains(name) || isLibrary(name))
                    continue;
                // 原版业务代码才是发送者时，不把外层模组调度器误识别成消息来源。
                if (name.startsWith("net.minecraft."))
                    return Optional.empty();
                Optional<String> owner = owner(frame.getDeclaringClass().getNestHost().getName());
                if (owner.isPresent())
                    return owner;
            }
            return Optional.empty();
        });
    }

    private static Optional<String> owner(String className) {
        if (className.startsWith(CORE_PACKAGE))
            return Optional.of("YouzaiWorldCore");
        return OWNERS.computeIfAbsent(className, name -> {
            String resource = name.replace('.', '/') + ".class";
            try {
                for (var mod : FabricLoader.getInstance().getAllMods()) {
                    String id = mod.getMetadata().getId();
                    if (id.equals("minecraft") || id.equals("fabricloader") || id.startsWith("fabric-"))
                        continue;
                    if (mod.findPath(resource).isPresent()) {
                        String displayName = mod.getMetadata().getName();
                        DebugLogger.SERVER.debug("ChatMessages", "已识别消息类 {} 的模组：{}", name, id);
                        return Optional.of(displayName == null || displayName.isBlank() ? id : displayName);
                    }
                }
            } catch (RuntimeException error) {
                DebugLogger.SERVER.exceptionSummary("ChatMessages", "resolveMessageMod", error.getMessage());
            }
            return Optional.empty();
        });
    }

    private static boolean isFormattingHook(String method) {
        return method.contains("$formatSystemPacket") || method.contains("$formatCommandOutput")
                || method.contains("$replaceAdminOutput") || method.contains("$formatClientMessage")
                || method.contains("$formatTellRawMessage");
    }

    private static boolean isLibrary(String name) {
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
                || name.startsWith("sun.") || name.startsWith("net.fabricmc.")
                || name.startsWith("com.mojang.brigadier.") || name.startsWith("org.spongepowered.")
                || name.startsWith("com.llamalad7.");
    }
}
