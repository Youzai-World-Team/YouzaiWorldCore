package top.csituka.youzaiworldcore.client.chat;

/** 聊天栏的两套配色，对应 {@code /yzwc chat_style <name>} 的名称。 */
public enum ChatStyleMode {
    PLAIN("plain"),
    GRADIENT("gradient");

    private final String commandName;

    ChatStyleMode(String commandName) {
        this.commandName = commandName;
    }

    /** 命令使用的稳定名称。 */
    public String commandName() {
        return commandName;
    }
}
