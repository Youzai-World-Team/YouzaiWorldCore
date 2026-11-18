package top.csituka.youzaiworldcore.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 聊天、私信、游戏和模组消息的统一排版；正文作为独立组件保留。 */
@SuppressWarnings("null")
public final class ChatMessageStyle {
    public static final String PLAYER_HEADER = "chat.youzaiworldcore.player";
    public static final String PRIVATE_HEADER = "chat.youzaiworldcore.private";
    public static final String GAME_HEADER = "chat.youzaiworldcore.game_header";
    public static final String GAME_MESSAGE = "chat.youzaiworldcore.game";
    public static final String MOD_MESSAGE = "chat.youzaiworldcore.mod";

    private ChatMessageStyle() {
    }

    /** 玩家代号前显示冒险等级及已佩戴称号；非玩家私信使用 level=0。 */
    public static Component playerHeader(boolean privateMessage, String playerName, int level, Component title) {
        String key = privateMessage ? PRIVATE_HEADER : PLAYER_HEADER;
        String fallback = privateMessage ? "[私信]|%s%s%s" : "[聊天]|%s%s%s";
        Component levelText = level > 0 ? Component.literal("Lv." + level + "·") : Component.empty();
        return Component.translatableWithFallback(key, fallback, levelText, title.copy(), playerName)
                .withStyle(ChatFormatting.WHITE);
    }

    /** /say、/me 等命令使用执行者名称，正文由原版 ChatType 单独传递。 */
    public static Component gameHeader(String from) {
        return Component.translatableWithFallback(GAME_HEADER, "[游戏]|%s", sourceName(from))
                .withStyle(ChatFormatting.WHITE);
    }

    public static Component game(String from, Component message) {
        return Component.translatableWithFallback(GAME_MESSAGE, "[游戏]|%s >> %s", sourceName(from), message.copy())
                .withStyle(ChatFormatting.WHITE);
    }

    /** 模组也可直接调用本方法，明确提供发送模组的显示名称。 */
    public static Component mod(String modName, Component message) {
        return Component.translatableWithFallback(MOD_MESSAGE, "[模组]|%s >> %s", sourceName(modName), message.copy())
                .withStyle(ChatFormatting.WHITE);
    }

    /** 命令反馈与普通系统消息在发送入口识别实际发送模组。 */
    public static Component system(Component message, @Nullable CommandSourceStack source) {
        if (isFormatted(message)) return message;
        var mod = ModMessageSource.caller();
        if (mod.isPresent()) return mod(mod.get(), message);
        return game(source == null ? "Server" : source.getTextName(), message);
    }

    /** 单条不显示计数，连续重复时在正文后追加 [!n]。 */
    public static Component repeated(Component message, int count) {
        if (count <= 1) return message;
        return message.copy().append(Component.literal(" [!" + count + "]").withStyle(ChatFormatting.GRAY));
    }

    public static boolean isHeader(Component component) {
        if (!(component.getContents() instanceof TranslatableContents contents)) return false;
        String key = contents.getKey();
        return PLAYER_HEADER.equals(key) || PRIVATE_HEADER.equals(key) || GAME_HEADER.equals(key);
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

    public static void logRepeat(int count) {
        DebugLogger.debug("ChatMessages", "连续重复消息计数=%d", count);
    }
}
