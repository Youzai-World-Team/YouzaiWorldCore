package top.csituka.youzaiworldcore.chat;

import eu.pb4.placeholders.api.ParserContext;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.node.DynamicTextNode;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.TagLikeParser;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
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
import top.csituka.youzaiworldcore.skill.PlayerLevelStorage;
import top.csituka.youzaiworldcore.title.TitleManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 玩家聊天、私信及成就、加入、离开、死亡事件的格式化入口。
 * <p>
 * 玩家聊天和私信使用固定的等级、称号、玩家代号前缀，正文保留原文。
 * 系统事件沿用等级、称号与玩家代号前缀；加入、离开和死亡事件支持自定义正文模板。
 * </p>
 * <p>
 * 处理链路（Minecraft 26.3）：
 * <ol>
 *   <li>玩家聊天：{@code ChatDecorator.decorate} 被重定向生成消息内容 →
 *       {@code broadcastChatMessage} 入口只挂上 {@code chat_header} 前缀 →
 *       {@code OutgoingChatMessage.create} 替换为 {@link StyledChatMessage} 发送；</li>
 *   <li>加入/离开：{@code PlayerList} / {@code ServerGamePacketListenerImpl} 的
 *       {@code broadcastSystemMessage} 参数被替换为事件前缀与正文；</li>
 *   <li>死亡：{@code ServerPlayer.die} 中 {@code CombatTracker.getDeathMessage} 被重定向；</li>
 *   <li>私聊：替换 {@code MsgCommand.sendMessage} 的 ChatType 绑定，保留原版投递及过滤；</li>
 *   <li>成就：替换 {@code PlayerAdvancements.lambda$award$0} 中的广播文本。</li>
 * </ol>
 * </p>
 */
@SuppressWarnings({ "null", "UnstableApiUsage" })
public final class ChatFormatHelper {

    public static final String MODULE = "ChatFormat";

    /** 空消息哨兵：未设置前缀时扩展接口的返回值。 */
    public static final Component EMPTY_TEXT = Component.empty();
    public static final String HEADER_ARG = "chat_header";

    /** 前缀与正文分别传递，正文继续由 Minecraft 处理过滤及签名。 */
    public static final ResourceKey<ChatType> MESSAGE_TYPE_ID = ResourceKey.create(
            Registries.CHAT_TYPE, Identifier.fromNamespaceAndPath("youzaiworldcore", "chat_line"));

    /** 系统事件模板变量的动态解析 key（如 ${player} 和 ${level}）。 */
    public static final ParserContext.Key<Function<String, Component>> DYN_KEY =
            DynamicTextNode.key("youzaiworldcore_chat");

    /** 系统事件模板解析器：文本标签、服务端占位符及动态变量。 */
    private static final NodeParser PARSER = NodeParser.builder()
            .simplifiedTextFormat()
            .serverPlaceholders()
            .placeholders(TagLikeParser.PLACEHOLDER_USER, DYN_KEY)
            .staticPreParsing()
            .build();

    /** 当前聊天服务器，供普通玩家消息的出站前缀绑定使用。 */
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
            DebugLogger.SERVER.debug(MODULE, "聊天模板已重新解析: {}", format);
            return node;
        }
        return FORMAT_NODE_CACHE.get(format);
    }

    /** 通用模板应用：把变量注入模板并解析（含 %papi%） */
    private static Component applyFormat(ServerPlayer player, String format, Map<String, Component> variables) {
        var context = ServerPlaceholderContext.of(player);
        return getFormatNode(format).toComponent(
                context.asParserContext().with(DYN_KEY, variables::get));
    }

    private static Map<String, Component> playerVars(ServerPlayer player) {
        Map<String, Component> vars = new HashMap<>();
        vars.put("player", player.getDisplayName());
        vars.put("default", player.getDisplayName());
        vars.put("name", player.getName());
        vars.put("title", TitleManager.getEquippedComponent(player));
        vars.put("tag", vars.get("title"));
        vars.put("level", Component.literal("Lv." + level(player)));
        vars.put("player_name", Component.literal(player.getScoreboardName()));
        return vars;
    }

    // ===== 消息正文 =====

    /** 普通聊天正文原样保留，百分号、标签及占位符均作为普通文字。 */
    public static Component formatMessageContent(ServerPlayer player, String input) {
        return Component.literal(input);
    }

    // ===== 各消息类型 =====

    /** 普通消息：[聊天]|Lv.n·{称号}玩家代号。 */
    public static Component formatChatHeader(ServerPlayer player) {
        return ChatMessageStyle.playerHeader(false, ChatPlayerInfo.name(player), level(player),
                TitleManager.getEquippedComponent(player));
    }

    /** 私信双方均显示实际发送者；非玩家发送者没有冒险等级和称号。 */
    public static Component formatPrivateHeader(net.minecraft.commands.CommandSourceStack source) {
        ServerPlayer sender = source.getPlayer();
        return sender == null
                ? ChatMessageStyle.playerHeader(true, source.getTextName(), 0, Component.empty())
                : ChatMessageStyle.playerHeader(true, ChatPlayerInfo.name(sender), level(sender),
                        TitleManager.getEquippedComponent(sender));
    }

    private static int level(ServerPlayer player) {
        var data = PlayerLevelStorage.get(player.getUUID());
        return data == null ? 1 : data.getLevel();
    }

    public static Component formatJoin(ServerPlayer player) {
        return formatEvent(player, ChatEventType.JOIN, ChatFormatSettings.getJoinedGameFormat(),
                ChatFormatSettings.DEFAULT_JOINED_FORMAT,
                Component.translatableWithFallback("chat.youzaiworldcore.event.join.body", "加入了服务器"), playerVars(player));
    }

    public static Component formatJoinFirstTime(ServerPlayer player) {
        return formatEvent(player, ChatEventType.JOIN, ChatFormatSettings.getJoinedFirstTimeFormat(),
                ChatFormatSettings.DEFAULT_JOINED_FIRST_TIME_FORMAT,
                Component.translatableWithFallback("chat.youzaiworldcore.event.join.first", "首次加入服务器，欢迎！"), playerVars(player));
    }

    /** 改名后加入消息（oldName 为旧名） */
    public static Component formatJoinRenamed(ServerPlayer player, String oldName) {
        Map<String, Component> vars = playerVars(player);
        vars.put("old_name", Component.literal(oldName));
        return formatEvent(player, ChatEventType.JOIN, ChatFormatSettings.getJoinedRenamedFormat(),
                ChatFormatSettings.DEFAULT_JOINED_RENAMED_FORMAT,
                Component.translatableWithFallback("chat.youzaiworldcore.event.join.renamed", "加入了服务器（原名：%s）",
                        vars.get("old_name")), vars);
    }

    public static Component formatLeft(ServerPlayer player) {
        return formatEvent(player, ChatEventType.LEAVE, ChatFormatSettings.getLeftGameFormat(),
                ChatFormatSettings.DEFAULT_LEFT_FORMAT,
                Component.translatableWithFallback("chat.youzaiworldcore.event.leave.body", "退出了服务器"), playerVars(player));
    }

    /** 死亡消息（vanillaMessage 为原版死亡描述） */
    public static Component formatDeath(ServerPlayer player, Component vanillaMessage) {
        Map<String, Component> vars = playerVars(player);
        return formatEvent(player, ChatEventType.DEATH, ChatFormatSettings.getDeathFormat(),
                ChatFormatSettings.DEFAULT_DEATH_FORMAT, vanillaMessage, vars);
    }

    /** 成就、目标和挑战使用独立前缀，名称继续使用原版的颜色及悬停说明。 */
    public static Component formatAdvancement(ServerPlayer player, AdvancementType type, AdvancementHolder holder) {
        DebugLogger.SERVER.debug(MODULE, "成就聊天排版：玩家={}，类型={}", player.getScoreboardName(), type);
        Component name = Advancement.name(holder);
        Component body = switch (type) {
            case TASK -> Component.translatableWithFallback("chat.youzaiworldcore.event.advancement.task", "达成进度 %s", name);
            case GOAL -> Component.translatableWithFallback("chat.youzaiworldcore.event.advancement.goal", "完成目标 %s", name);
            case CHALLENGE -> Component.translatableWithFallback("chat.youzaiworldcore.event.advancement.challenge", "完成挑战 %s", name);
        };
        return event(player, ChatEventType.ADVANCEMENT, body);
    }

    private static Component formatEvent(ServerPlayer player, ChatEventType type, String format,
            String defaultFormat, Component defaultBody, Map<String, Component> variables) {
        variables.put("default_message", defaultBody);
        // 内置模板使用紧凑的本地化正文，玩家信息只在前缀出现；自定义模板继续原样解析。
        Component body = format.equals(defaultFormat) ? defaultBody : applyFormat(player, format, variables);
        return event(player, type, body);
    }

    private static Component event(ServerPlayer player, ChatEventType type, Component body) {
        return ChatMessageStyle.event(type, ChatPlayerInfo.name(player), level(player),
                TitleManager.getEquippedComponent(player), body);
    }

    // ===== 发送辅助 =====

    /** 在广播入口附加来源前缀，正文不放入前缀参数。 */
    public static void modifyForSending(PlayerChatMessage message, ServerPlayer player) {
        try {
            ExtPlayerChatMessage.setArg(message, HEADER_ARG, formatChatHeader(player));
            server = player.level().getServer();
            if (DebugLogger.SERVER.isEnabled(DebugLogger.LEVEL_DEBUG)) {
                DebugLogger.SERVER.trace(MODULE, "已设置玩家聊天前缀：{}", player.getScoreboardName());
            }
        } catch (Exception e) {
            DebugLogger.SERVER.exception(MODULE, "modifyForSending", e);
        }
    }

    /** 用自定义 ChatType 绑定前缀，正文保留独立 content 参数。 */
    public static ChatType.Bound createParameters(Component header) {
        MinecraftServer s = server;
        if (s == null) {
            // 理论不可达：modifyForSending 会在广播前设置 server；此处抛错由调用方兜底走原版
            throw new IllegalStateException("ChatFormatHelper.server is null");
        }
        return ChatType.bind(MESSAGE_TYPE_ID, s.registryAccess(), header);
    }
}
