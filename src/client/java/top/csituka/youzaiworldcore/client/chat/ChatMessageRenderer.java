package top.csituka.youzaiworldcore.client.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.chat.ChatMessageStyle;
import top.csituka.youzaiworldcore.client.config.ChatStyleSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 按 MiniMessage 色板为结构化聊天组件着色，只改显示副本。
 * 正文不解析标签，交互、字体、加粗、斜体及原始组件内容保留。
 */
@SuppressWarnings("null")
public final class ChatMessageRenderer {
    private static final int LEVEL = 0xF36B7B;
    private static final int TITLE = 0xF47B8A;
    private static final int NAME = 0xF7F7F7;
    private static final int BODY = 0xF3EFEF;
    private static final int SEPARATOR = 0x555555;

    private ChatMessageRenderer() {
    }

    /** 按当前本机设置生成彩色显示组件。 */
    public static Component render(Component message) {
        return render(message, ChatStyleSettings.getMode());
    }

    /** 按指定配色渲染；来源只由结构化组件判断。 */
    public static Component render(Component message, ChatStyleMode mode) {
        if (message.getContents() instanceof TranslatableContents contents) {
            MutableComponent rendered = renderContents(contents, mode);
            if (rendered != null) {
                rendered.setStyle(message.getStyle());
                for (Component sibling : message.getSiblings()) {
                    rendered.append(solid(sibling, BODY));
                }
                DebugLogger.CLIENT.trace("ChatStyle", "聊天显示配色：{}，类型={}", mode.commandName(), contents.getKey());
                return rendered;
            }
        }
        // 原版失败反馈的空根红色包装也要保留其非颜色样式。
        if (message.getContents().equals(Component.empty().getContents())
                && message.getSiblings().size() == 1
                && ChatMessageStyle.isFormatted(message.getSiblings().getFirst())) {
            return MutableComponent.create(message.getContents()).setStyle(message.getStyle())
                    .append(render(message.getSiblings().getFirst(), mode));
        }
        return message;
    }

    @Nullable
    private static MutableComponent renderContents(TranslatableContents contents, ChatStyleMode mode) {
        Object[] args = contents.getArgs();
        switch (contents.getKey()) {
            case ChatMessageStyle.PLAYER_HEADER, ChatMessageStyle.PRIVATE_HEADER -> {
                if (args.length != 4) return null;
                boolean privateMessage = ChatMessageStyle.PRIVATE_HEADER.equals(contents.getKey());
                int start = privateMessage ? 0xC03AFF : 0xFF3364;
                int end = privateMessage ? 0xDF61FF : 0xD0529E;
                return Component.empty()
                        .append(accent(Component.literal(privateMessage ? "[私信]" : "[聊天]"),
                                privateMessage ? 0xF36B7B : 0xE77280, start, end, mode))
                        .append(solid(Component.literal("|"), SEPARATOR))
                        .append(solid(argument(args[0]), LEVEL))
                        .append(solid(argument(args[1]), SEPARATOR))
                        .append(solid(argument(args[2]), TITLE))
                        .append(accent(argument(args[3]), NAME, start, end, mode));
            }
            case ChatMessageStyle.GAME_HEADER -> {
                if (args.length != 1) return null;
                return sourceHeader(false, argument(args[0]), mode);
            }
            case ChatMessageStyle.GAME_MESSAGE, ChatMessageStyle.MOD_MESSAGE -> {
                if (args.length != 2) return null;
                return sourceHeader(ChatMessageStyle.MOD_MESSAGE.equals(contents.getKey()), argument(args[0]), mode)
                        .append(solid(Component.literal(" >> "), SEPARATOR))
                        .append(solid(argument(args[1]), BODY));
            }
            default -> {
                if ("%s >> %s".equals(contents.getKey()) && args.length == 2
                        && args[0] instanceof Component header && ChatMessageStyle.isHeader(header)) {
                    return Component.empty().append(render(header, mode))
                            .append(solid(Component.literal(" >> "), SEPARATOR))
                            .append(solid(argument(args[1]), BODY));
                }
                return null;
            }
        }
    }

    private static MutableComponent sourceHeader(boolean modMessage, Component name, ChatStyleMode mode) {
        return Component.empty()
                .append(accent(Component.literal(modMessage ? "[模组]" : "[游戏]"),
                        modMessage ? 0x007AFF : 0xF47B8A,
                        modMessage ? 0x007AFF : 0xFF8A00, modMessage ? 0xC03AFF : 0xE79D0E, mode))
                .append(solid(Component.literal("|"), SEPARATOR))
                .append(solid(name, NAME));
    }

    private static Component argument(Object value) {
        return value instanceof Component component ? component : Component.literal(String.valueOf(value));
    }

    /** 递归复制组件并覆盖颜色；翻译参数和子组件的旧颜色不能覆盖色板。 */
    private static MutableComponent solid(Component message, int color) {
        ComponentContents contents = message.getContents();
        if (contents instanceof TranslatableContents translated) {
            Object[] args = translated.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component argument) args[i] = solid(argument, color);
            }
            contents = new TranslatableContents(translated.getKey(), translated.getFallback(), args);
        }
        MutableComponent result = MutableComponent.create(contents).setStyle(message.getStyle().withColor(color));
        for (Component sibling : message.getSiblings()) result.append(solid(sibling, color));
        return result;
    }

    private static Component accent(Component message, int plain, int start, int end, ChatStyleMode mode) {
        return mode == ChatStyleMode.PLAIN ? solid(message, plain) : ChatMessageStyle.gradient(message, start, end);
    }
}
