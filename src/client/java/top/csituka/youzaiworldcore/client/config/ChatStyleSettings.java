package top.csituka.youzaiworldcore.client.config;

import top.csituka.youzaiworldcore.client.chat.ChatStyleMode;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.Objects;

/**
 * 本机聊天配色，保存于 {@code yzwc/client/global_settings.json} 的
 * {@code chat_style_module}。
 */
public final class ChatStyleSettings {
    private static final String MODULE = "ChatStyleSettings";
    private static final ChatStyleMode DEFAULT_MODE = ChatStyleMode.GRADIENT;

    private static ChatStyleMode mode = DEFAULT_MODE;

    private ChatStyleSettings() {
    }

    /** 当前客户端使用的配色，首次安装默认渐变点缀。 */
    public static ChatStyleMode getMode() {
        return mode;
    }

    /** 加载本机配色；非法选项由统一配置层隔离坏文件并报告错误。 */
    @SuppressWarnings("null")
    public static void load() {
        DebugLogger.CLIENT.entering(MODULE, "load");
        ConfigSection section = ClientGlobalSettings.section(ClientGlobalSettings.CHAT_STYLE_MODULE);
        if (section.isEmpty()) {
            writeDefaults();
        } else {
            mode = section.getEnum("style", DEFAULT_MODE, ChatStyleMode.class);
        }
        DebugLogger.CLIENT.info(MODULE, "聊天配色已加载：{}", mode.commandName());
        DebugLogger.CLIENT.exiting(MODULE, "load");
    }

    /** 修改并保存本机配色，聊天记录刷新由客户端命令负责。 */
    public static void setMode(ChatStyleMode value) {
        ChatStyleMode previous = mode;
        mode = Objects.requireNonNull(value);
        save();
        DebugLogger.CLIENT.stateChange(MODULE, "本机", "chatStyle", previous, mode);
    }

    /** 重置为渐变点缀并写入统一客户端配置。 */
    public static void writeDefaults() {
        mode = DEFAULT_MODE;
        save();
    }

    /** 保存配色，其他模块的配置由统一存放层保留。 */
    public static void save() {
        ClientGlobalSettings.section(ClientGlobalSettings.CHAT_STYLE_MODULE).set("style", mode.commandName());
        ClientGlobalSettings.save();
    }
}
