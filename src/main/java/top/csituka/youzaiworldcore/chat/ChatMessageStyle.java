package top.csituka.youzaiworldcore.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Optional;

/** 聊天、私信、游戏、模组和四类系统事件的统一排版；正文作为独立组件保留。 */
@SuppressWarnings("null")
public final class ChatMessageStyle {
    public static final String PLAYER_HEADER = "chat.youzaiworldcore.player";
    public static final String PRIVATE_HEADER = "chat.youzaiworldcore.private";
    public static final String PRIVATE_SENT_TO = "chat.youzaiworldcore.private.sent_to";
    public static final String GAME_HEADER = "chat.youzaiworldcore.game_header";
    public static final String GAME_MESSAGE = "chat.youzaiworldcore.game";
    public static final String MOD_MESSAGE = "chat.youzaiworldcore.mod";
    public static final int REPEAT_COLOR = 0x2DD4BF;
    public static final int GAME_NAME_COLOR = 0xFF8A00;
    public static final int MOD_NAME_COLOR = 0x5B8CFF;
    private static final int BODY_COLOR = 0xF3EFEF;
    private static final int LEVEL_COLOR = 0xF36B7B;
    private static final int SEPARATOR_COLOR = 0x555555;

    private ChatMessageStyle() {
    }

    /** 玩家代号前显示冒险等级及已佩戴称号；非玩家私信使用 level=0。 */
    public static Component playerHeader(boolean privateMessage, String playerName, int level, Component title) {
        return playerHeader(privateMessage, Component.literal(playerName), level, title);
    }

    /** 玩家名作为组件传递，保留服务端附加的资料悬停。 */
    public static Component playerHeader(boolean privateMessage, Component playerName, int level, Component title) {
        String key = privateMessage ? PRIVATE_HEADER : PLAYER_HEADER;
        String fallback = privateMessage ? "[私信]|%s%s%s%s" : "[聊天]|%s%s%s%s";
        return playerHeader(key, fallback, playerName, level, title,
                privateMessage ? 0xC03AFF : 0xFF3364, privateMessage ? 0xDF61FF : 0xD0529E);
    }

    /** 事件前缀沿用玩家聊天的冒险等级、称号和账户代号结构。 */
    public static Component eventHeader(ChatEventType type, String playerName, int level, Component title) {
        return eventHeader(type, Component.literal(playerName), level, title);
    }

    /** 事件玩家名保留资料悬停，标题、等级和渐变排版与普通聊天一致。 */
    public static Component eventHeader(ChatEventType type, Component playerName, int level, Component title) {
        return playerHeader(type.headerKey(), type.label() + "|%s%s%s%s", playerName, level, title,
                type.gradientStart(), type.gradientEnd());
    }

    /** 事件正文独立保留，避免重新染色或丢失成就及死亡消息的交互。 */
    public static Component event(ChatEventType type, String playerName, int level, Component title, Component body) {
        return event(type, Component.literal(playerName), level, title, body);
    }

    /** 事件正文独立保留，玩家名可携带资料交互。 */
    public static Component event(ChatEventType type, Component playerName, int level, Component title, Component body) {
        return Component.translatable("%s >> %s", eventHeader(type, playerName, level, title), body.copy())
                .withStyle(style -> style.withColor(BODY_COLOR));
    }

    private static Component playerHeader(String key, String fallback, Component playerName, int level,
            Component title, int start, int end) {
        Component levelText = level > 0 ? Component.literal("Lv." + level)
                .withStyle(style -> style.withColor(LEVEL_COLOR)) : Component.empty();
        // 点号是玩家等级后的分隔符，未佩戴称号时仍保留；非玩家私信没有等级。
        Component separator = level > 0 ? Component.literal("·")
                .withStyle(style -> style.withColor(SEPARATOR_COLOR)) : Component.empty();
        Component name = ChatPlayerInfo.colorName(playerName, text -> gradient(text, start, end));
        return Component.translatableWithFallback(key, fallback, levelText, separator, title.copy(), name)
                .withStyle(style -> style.withColor(BODY_COLOR));
    }

    /** /say、/me 等命令使用执行者名称，正文由原版 ChatType 单独传递。 */
    public static Component gameHeader(String from) {
        return gameHeader(Component.literal(sourceName(from)));
    }

    /** 游戏命令的玩家来源保留其资料悬停。 */
    public static Component gameHeader(Component from) {
        return Component.translatableWithFallback(GAME_HEADER, "[游戏]|%s", name(from, GAME_NAME_COLOR))
                .withStyle(style -> style.withColor(BODY_COLOR));
    }

    public static Component game(String from, Component message) {
        return game(Component.literal(sourceName(from)), message);
    }

    /** 游戏消息来源可携带玩家资料，正文样式保持原样。 */
    public static Component game(Component from, Component message) {
        return Component.translatableWithFallback(GAME_MESSAGE, "[游戏]|%s >> %s", name(from, GAME_NAME_COLOR), message.copy())
                .withStyle(style -> style.withColor(BODY_COLOR));
    }

    /** 模组也可直接调用本方法，明确提供发送模组的显示名称。 */
    public static Component mod(String modName, Component message) {
        return Component.translatableWithFallback(MOD_MESSAGE, "[模组]|%s >> %s", name(modName, MOD_NAME_COLOR), message.copy())
                .withStyle(style -> style.withColor(BODY_COLOR));
    }

    /** 发送者的独立私信回执；正文仅包含收件人名称，不重复私信内容。 */
    public static Component privateSent(Component senderHeader, Component recipients) {
        Component receipt = Component.translatableWithFallback(PRIVATE_SENT_TO, "已发送给 %s", recipients.copy());
        return Component.translatable("%s >> %s", senderHeader.copy(), receipt)
                .withStyle(style -> style.withColor(BODY_COLOR));
    }

    /** 通过结构识别发送回执，使它不打断真实私信的连续重复计数。 */
    public static boolean isPrivateReceipt(Component message) {
        if (!(message.getContents() instanceof TranslatableContents contents)) return false;
        Object[] args = contents.getArgs();
        return "%s >> %s".equals(contents.getKey()) && args.length == 2
                && args[0] instanceof Component header
                && header.getContents() instanceof TranslatableContents prefix
                && PRIVATE_HEADER.equals(prefix.getKey())
                && args[1] instanceof Component body
                && body.getContents() instanceof TranslatableContents receipt
                && PRIVATE_SENT_TO.equals(receipt.getKey()) && receipt.getArgs().length == 1;
    }

    /** 命令反馈与普通系统消息在发送入口识别实际发送模组。 */
    public static Component system(Component message, @Nullable CommandSourceStack source) {
        if (isFormatted(message)) return message;
        var mod = ModMessageSource.caller();
        if (mod.isPresent()) return mod(mod.get(), message);
        return game(source == null ? Component.literal("Server") : ChatPlayerInfo.sourceName(source), message);
    }

    /** 单条不显示计数，连续重复时在正文后追加 [!n]。 */
    public static Component repeated(Component message, int count) {
        if (count <= 1) return message;
        return message.copy().append(Component.literal(" [!" + count + "]").withStyle(style -> style.withColor(REPEAT_COLOR)));
    }

    /** 按 Unicode 码点着色，保留输入文字的交互和非颜色样式；前后端共用同一渐变算法。 */
    public static Component gradient(Component message, int start, int end) {
        String text = message.getString();
        int length = text.codePointCount(0, text.length());
        if (length == 0) return message.copy().withStyle(style -> style.withColor(start));

        MutableComponent result = Component.empty().setStyle(message.getStyle());
        int[] index = {0};
        message.visit((style, segment) -> {
            segment.codePoints().forEach(codePoint -> {
                double position = length == 1 ? 0 : (double) index[0]++ / (length - 1);
                result.append(Component.literal(new String(Character.toChars(codePoint)))
                        .setStyle(style.withColor(interpolate(start, end, position))));
            });
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static int interpolate(int start, int end, double position) {
        int red = (int) Math.round(((start >> 16) & 255) * (1 - position) + ((end >> 16) & 255) * position);
        int green = (int) Math.round(((start >> 8) & 255) * (1 - position) + ((end >> 8) & 255) * position);
        int blue = (int) Math.round((start & 255) * (1 - position) + (end & 255) * position);
        return (red << 16) | (green << 8) | blue;
    }

    public static boolean isHeader(Component component) {
        if (!(component.getContents() instanceof TranslatableContents contents)) return false;
        String key = contents.getKey();
        return PLAYER_HEADER.equals(key) || PRIVATE_HEADER.equals(key) || GAME_HEADER.equals(key)
                || ChatEventType.fromHeaderKey(key) != null;
    }

    /** 只识别结构化前缀，不根据正文是否恰好以「[聊天]」等文字开头判断来源。 */
    public static boolean isFormatted(Component component) {
        if (component.getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            if (GAME_MESSAGE.equals(key) || MOD_MESSAGE.equals(key)) return true;
            Object[] args = contents.getArgs();
            return "%s >> %s".equals(key) && args.length == 2
                    && args[0] instanceof Component header && isHeader(header);
        }
        // 原版失败反馈会用空根组件包住原文并添加红色。
        return component.getSiblings().size() == 1
                && component.getContents().equals(Component.empty().getContents())
                && isFormatted(component.getSiblings().getFirst());
    }

    private static String sourceName(String name) {
        return name == null || name.isBlank() ? "Server" : name;
    }

    private static Component name(String value, int color) {
        return name(Component.literal(sourceName(value)), color);
    }

    private static Component name(Component value, int color) {
        return ChatPlayerInfo.colorName(value, text -> text.copy().withStyle(style -> style.withColor(color)));
    }

    public static void logRepeat(int count) {
        DebugLogger.SERVER.debug("ChatMessages", "连续重复消息计数={}", count);
    }
}
