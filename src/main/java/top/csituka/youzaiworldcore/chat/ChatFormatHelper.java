package top.csituka.youzaiworldcore.chat;

import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.node.DynamicTextNode;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.TagLikeParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import top.csituka.youzaiworldcore.config.ChatFormatSettings;
import top.csituka.youzaiworldcore.title.TitleManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 聊天消息格式化核心（仿 Styled Chat 精简版）。
 * <p>
 * 支持的消息类型：玩家聊天、加入（含首次/改名）、离开、死亡、私聊（发出/收到）。
 * 所有模板均支持 {@code <tag>} 简化文本标签与 {@code %papi%} 占位符（始终解析）。
 * </p>
 * <p>
 * 处理链路（与 26.2 原版聊天管线对应）：
 * <ol>
 *   <li>玩家聊天：{@code ChatDecorator.decorate} 被重定向生成消息内容 →
 *       {@code broadcastChatMessage} 入口把模板组合结果挂到 {@code override} →
 *       {@code OutgoingChatMessage.create} 替换为 {@link StyledChatMessage} 发送；</li>
 *   <li>加入/离开：{@code PlayerList} / {@code ServerGamePacketListenerImpl} 的
 *       {@code broadcastSystemMessage} 参数被替换为模板结果；</li>
 *   <li>死亡：{@code ServerPlayer.die} 中 {@code CombatTracker.getDeathMessage} 被重定向；</li>
 *   <li>私聊：{@code MsgCommand.sendMessage} 中发送者/接收者各自的 ChatType 绑定被替换。</li>
 * </ol>
 * </p>
 */
@SuppressWarnings({ "null", "UnstableApiUsage" })
public final class ChatFormatHelper {

    public static final String MODULE = "ChatFormat";

    /** 空消息哨兵：未设置 override 时 {@link ExtPlayerChatMessage#youzaiworldcore_getArg} 的返回值 */
    public static final Component EMPTY_TEXT = Component.empty();

    /** 自定义 ChatType：translation_key 为 {@code %s}，客户端只透传第一个参数（sender=override） */
    public static final ResourceKey<ChatType> MESSAGE_TYPE_ID = ResourceKey.create(
            Registries.CHAT_TYPE, Identifier.fromNamespaceAndPath("youzaiworldcore", "generic_hack"));

    /** 模板变量 {@code ${...}} 的动态解析 key（{@code ${player}} / {@code ${message}} 等） */
    public static final ParserContext.Key<Function<String, Component>> DYN_KEY =
            DynamicTextNode.key("youzaiworldcore_chat");

    /** 模板 / 消息共用的解析器：Simplified Text 标签 + %papi% 服务端占位符 + ${...} 动态变量 */
    private static final NodeParser PARSER = NodeParser.builder()
            .simplifiedTextFormat()
            .serverPlaceholders()
            .placeholders(TagLikeParser.PLACEHOLDER_USER, DYN_KEY)
            .staticPreParsing()
            .build();

    /** 最后一次处理聊天消息的服务器（供发送阶段绑定 ChatType 用），随 {@link #modifyForSending} 刷新 */
    @Nullable
    public static MinecraftServer server = null;

    /** 模板 TextNode 缓存（配置变更后失效重解析，避免每条消息重复解析模板） */
    private static final Map<String, TextNode> FORMAT_NODE_CACHE = new HashMap<>();
    private static final Map<String, String> FORMAT_SOURCE_CACHE = new HashMap<>();

    private ChatFormatHelper() {
    }

    // ===== 模板解析 =====

    private static TextNode getFormatNode(String format) {
        String cached = FORMAT_SOURCE_CACHE.get(format);
        if (cached == null) {
            TextNode node = PARSER.parseNode(format);
            FORMAT_SOURCE_CACHE.put(format, format);
            FORMAT_NODE_CACHE.put(format, node);
            DebugLogger.info(MODULE, "聊天模板已重新解析: %s", format);
            return node;
        }
        return FORMAT_NODE_CACHE.get(format);
    }

    /**
     * 解析单条消息文本（不含玩家名）：{@code <tag>} → {@code %papi%}。
     * 返回的 TextNode 在 {@code toComponent(context)} 时才求值占位符。
     */
    public static TextNode parseMessageText(ServerPlayer player, String input) {
        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
            DebugLogger.trace(MODULE, "parseMessageText player={} input={}", player.getName().getString(), input);
        }
        return PARSER.parseNode(TextNode.of(input));
    }

    /** 通用模板应用：把变量注入模板并解析（含 %papi%） */
    private static Component applyFormat(ServerPlayer player, String format, Map<String, Component> variables) {
        var context = ServerPlaceholderContext.of(player);
        return getFormatNode(format).toComponent(
                context.asParserContext().with(DYN_KEY, variables::get));
    }

    /** 通用模板应用（基于 CommandSourceStack 上下文，用于非玩家发送者场景） */
    private static Component applyFormat(net.minecraft.commands.CommandSourceStack source, String format,
                                         Map<String, Component> variables) {
        var context = ServerPlaceholderContext.of(source);
        return getFormatNode(format).toComponent(
                context.asParserContext().with(DYN_KEY, variables::get));
    }

    private static Map<String, Component> playerVars(ServerPlayer player) {
        Map<String, Component> vars = new HashMap<>(5);
        vars.put("player", player.getDisplayName());
        vars.put("default", player.getDisplayName());
        vars.put("name", player.getName());
        vars.put("title", TitleManager.getEquippedComponent(player));
        return vars;
    }

    // ===== 消息内容（chat 的 ${message} 部分） =====

    /** 消息内容格式化（供 {@code ChatDecorator.decorate} 重定向，作为 unsignedContent） */
    public static Component formatMessageContent(ServerPlayer player, String input) {
        if (!ChatFormatSettings.isEnabled()) {
            return Component.literal(input);
        }
        return parseMessageText(player, input).toComponent(ServerPlaceholderContext.of(player).asParserContext());
    }

    // ===== 各消息类型 =====

    /** 按配置模板组合完整聊天消息（含玩家名），返回 {@code override} */
    public static Component formatChat(ServerPlayer player, PlayerChatMessage message) {
        var context = ServerPlaceholderContext.of(player);
        var papiContext = context.asParserContext();
        Component displayName = player.getDisplayName();
        Component messageContent = message.decoratedContent();

        var value = getFormatNode(ChatFormatSettings.getChatFormat()).toComponent(
                papiContext.with(DYN_KEY, Map.of(
                        "player", displayName,
                        "default", displayName,
                        "name", player.getName(),
                        "title", TitleManager.getEquippedComponent(player),
                        "message", messageContent
                )::get));

        if (DebugLogger.isEnabled(DebugLogger.LEVEL_DETAILED)) {
            DebugLogger.debug(MODULE, "formatChat player={} -> {}", player.getName().getString(), value.getString());
        }
        return value;
    }

    /** 加入消息 */
    public static Component formatJoin(ServerPlayer player) {
        return applyFormat(player, ChatFormatSettings.getJoinedGameFormat(), playerVars(player));
    }

    /** 首次加入消息 */
    public static Component formatJoinFirstTime(ServerPlayer player) {
        return applyFormat(player, ChatFormatSettings.getJoinedFirstTimeFormat(), playerVars(player));
    }

    /** 改名后加入消息（oldName 为旧名） */
    public static Component formatJoinRenamed(ServerPlayer player, String oldName) {
        Map<String, Component> vars = playerVars(player);
        vars.put("old_name", Component.literal(oldName));
        return applyFormat(player, ChatFormatSettings.getJoinedRenamedFormat(), vars);
    }

    /** 离开消息 */
    public static Component formatLeft(ServerPlayer player) {
        return applyFormat(player, ChatFormatSettings.getLeftGameFormat(), playerVars(player));
    }

    /** 死亡消息（vanillaMessage 为原版死亡描述） */
    public static Component formatDeath(ServerPlayer player, Component vanillaMessage) {
        Map<String, Component> vars = playerVars(player);
        vars.put("default_message", vanillaMessage);
        return applyFormat(player, ChatFormatSettings.getDeathFormat(), vars);
    }

    /** 私聊发出消息（发送者视角） */
    public static Component formatPrivateMessageSent(net.minecraft.commands.CommandSourceStack source,
                                                     Component sender, Component receiver, Component message) {
        return applyFormat(source, ChatFormatSettings.getPrivateMessageSentFormat(), Map.of(
                "sender", sender, "receiver", receiver, "message", message));
    }

    /** 私聊收到消息（接收者视角） */
    public static Component formatPrivateMessageReceived(net.minecraft.commands.CommandSourceStack source,
                                                         Component sender, Component receiver, Component message) {
        return applyFormat(source, ChatFormatSettings.getPrivateMessageReceivedFormat(), Map.of(
                "sender", sender, "receiver", receiver, "message", message));
    }

    // ===== 发送辅助 =====

    /** 在广播入口为消息附加 override 参数，供发送阶段替换 */
    public static void modifyForSending(PlayerChatMessage message, ServerPlayer player) {
        try {
            ExtPlayerChatMessage.setArg(message, "override", formatChat(player, message));
            server = player.level().getServer();
            if (DebugLogger.isEnabled(DebugLogger.LEVEL_DEBUG)) {
                DebugLogger.trace(MODULE, "modifyForSending player={} override set", player.getName().getString());
            }
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "modifyForSending", e);
        }
    }

    /** 样式化聊天关闭时，仅在原版发送者名称右侧追加称号。 */
    public static void modifyVanillaTitleForSending(PlayerChatMessage message, ServerPlayer player) {
        Component title = TitleManager.getEquippedComponent(player);
        if (title.getString().isBlank()) return;
        try {
            Component sender = player.getDisplayName().copy()
                    .append(Component.literal(" "))
                    .append(title);
            Component override = Component.translatable("chat.type.text", sender, message.decoratedContent());
            ExtPlayerChatMessage.setArg(message, "override", override);
            server = player.level().getServer();
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "modifyVanillaTitleForSending", e);
        }
    }

    /** 用自定义 ChatType 绑定完整消息（客户端显示即为 override） */
    public static ChatType.Bound createParameters(Component override) {
        MinecraftServer s = server;
        if (s == null) {
            // 理论不可达：modifyForSending 会在广播前设置 server；此处抛错由调用方兜底走原版
            throw new IllegalStateException("ChatFormatHelper.server is null");
        }
        return ChatType.bind(MESSAGE_TYPE_ID, s.registryAccess(), override);
    }
}
