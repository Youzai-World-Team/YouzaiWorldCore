package top.csituka.youzaiworldcore.config;

import java.util.Objects;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 聊天消息格式化配置（仿 Styled Chat 精简版）。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code chat_format_module} 分节。
 * </p>
 * <p>
 * 支持的功能：
 * <ul>
 *   <li>消息模板：玩家聊天 / 加入 / 离开 / 死亡 / 私聊，均支持 {@code ${player}}、
 *       {@code ${message}}、{@code ${default_message}}、{@code ${sender}}、
 *       {@code ${receiver}} 等变量，以及 {@code <red>} 等 Simplified Text 标签
 *       和 {@code %papi%} 占位符（Placeholder API，<b>始终解析</b>，无开关）；</li>
 *   <li>模板颜色<b>强制生效</b>：无视客户端「聊天颜色」设置，对所有玩家一律发送
 *       模板渲染结果。</li>
 * </ul>
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class ChatFormatSettings {

    public static final String MODULE = "ChatFormatSettings";

    /** 默认聊天消息模板（全功能示例：颜色枚举 / 渐变 / hex / 彩虹 / 格式标签 / 模板变量 / %papi%） */
    public static final String DEFAULT_CHAT_FORMAT =
            "<dark_gray>[</dark_gray><gradient:#ff5555:#55ff55>${player}</gradient><dark_gray>]</dark_gray> "
            + "<gold>»</gold> <c:#55ffff>${message}</c> "
            + "<gray>|</gray> <rainbow>%player:name%</rainbow> "
            + "<gray>|</gray> <bold>%player:displayname%</bold> "
            + "<gray>|</gray> <aqua>%player:pos_x% %player:pos_y% %player:pos_z%</aqua> "
            + "<gray>|</gray> <underline>HP %player:health%/%player:max_health%</underline>";
    /** 默认加入消息模板（原版翻译键） */
    public static final String DEFAULT_JOINED_FORMAT = "<yellow><lang:multiplayer.player.joined:'${player}'></yellow>";
    /** 默认首次加入消息模板 */
    public static final String DEFAULT_JOINED_FIRST_TIME_FORMAT = "<yellow><lang:multiplayer.player.joined:'${player}'></yellow>";
    /** 默认改名后加入消息模板 */
    public static final String DEFAULT_JOINED_RENAMED_FORMAT = "<yellow><lang:multiplayer.player.joined.renamed:'${player}':'${old_name}'></yellow>";
    /** 默认离开消息模板 */
    public static final String DEFAULT_LEFT_FORMAT = "<yellow><lang:multiplayer.player.left:'${player}'></yellow>";
    /** 默认死亡消息模板（透传原版死亡消息） */
    public static final String DEFAULT_DEATH_FORMAT = "${default_message}";
    /** 默认私聊发出消息模板（发送者视角） */
    public static final String DEFAULT_PRIVATE_MESSAGE_SENT_FORMAT =
            "<gray><italic><lang:commands.message.display.outgoing:'${receiver}':'${message}'>";
    /** 默认私聊收到消息模板（接收者视角） */
    public static final String DEFAULT_PRIVATE_MESSAGE_RECEIVED_FORMAT =
            "<gray><italic><lang:commands.message.display.incoming:'${sender}':'${message}'>";

    private static boolean enabled = true;
    private static String chatFormat = DEFAULT_CHAT_FORMAT;
    private static String joinedGameFormat = DEFAULT_JOINED_FORMAT;
    private static String joinedFirstTimeFormat = DEFAULT_JOINED_FIRST_TIME_FORMAT;
    private static String joinedRenamedFormat = DEFAULT_JOINED_RENAMED_FORMAT;
    private static String leftGameFormat = DEFAULT_LEFT_FORMAT;
    private static String deathFormat = DEFAULT_DEATH_FORMAT;
    private static String privateMessageSentFormat = DEFAULT_PRIVATE_MESSAGE_SENT_FORMAT;
    private static String privateMessageReceivedFormat = DEFAULT_PRIVATE_MESSAGE_RECEIVED_FORMAT;

    private ChatFormatSettings() {
    }

    public static boolean isEnabled() { return enabled; }
    public static String getChatFormat() { return chatFormat; }
    public static String getJoinedGameFormat() { return joinedGameFormat; }
    public static String getJoinedFirstTimeFormat() { return joinedFirstTimeFormat; }
    public static String getJoinedRenamedFormat() { return joinedRenamedFormat; }
    public static String getLeftGameFormat() { return leftGameFormat; }
    public static String getDeathFormat() { return deathFormat; }
    public static String getPrivateMessageSentFormat() { return privateMessageSentFormat; }
    public static String getPrivateMessageReceivedFormat() { return privateMessageReceivedFormat; }

    public static void setEnabled(boolean v) { if (enabled != v) { enabled = v; save(); } }
    public static void setChatFormat(String v) { if (!Objects.equals(chatFormat, v)) { chatFormat = v; save(); } }
    public static void setJoinedGameFormat(String v) { if (!Objects.equals(joinedGameFormat, v)) { joinedGameFormat = v; save(); } }
    public static void setJoinedFirstTimeFormat(String v) { if (!Objects.equals(joinedFirstTimeFormat, v)) { joinedFirstTimeFormat = v; save(); } }
    public static void setJoinedRenamedFormat(String v) { if (!Objects.equals(joinedRenamedFormat, v)) { joinedRenamedFormat = v; save(); } }
    public static void setLeftGameFormat(String v) { if (!Objects.equals(leftGameFormat, v)) { leftGameFormat = v; save(); } }
    public static void setDeathFormat(String v) { if (!Objects.equals(deathFormat, v)) { deathFormat = v; save(); } }
    public static void setPrivateMessageSentFormat(String v) { if (!Objects.equals(privateMessageSentFormat, v)) { privateMessageSentFormat = v; save(); } }
    public static void setPrivateMessageReceivedFormat(String v) { if (!Objects.equals(privateMessageReceivedFormat, v)) { privateMessageReceivedFormat = v; save(); } }

    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.CHAT_FORMAT_MODULE);
        if (section.isEmpty()) {
            save();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }
        enabled = section.getBoolean("enabled", enabled);
        chatFormat = section.getString("chat_format", chatFormat);
        joinedGameFormat = section.getString("joined_game_format", joinedGameFormat);
        joinedFirstTimeFormat = section.getString("joined_first_time_format", joinedFirstTimeFormat);
        joinedRenamedFormat = section.getString("joined_renamed_format", joinedRenamedFormat);
        leftGameFormat = section.getString("left_game_format", leftGameFormat);
        deathFormat = section.getString("death_format", deathFormat);
        privateMessageSentFormat = section.getString("private_message_sent_format", privateMessageSentFormat);
        privateMessageReceivedFormat = section.getString("private_message_received_format", privateMessageReceivedFormat);
        DebugLogger.info(MODULE, "已加载聊天格式化配置：enabled=%s", enabled);
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code chat_format_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = true;
        chatFormat = DEFAULT_CHAT_FORMAT;
        joinedGameFormat = DEFAULT_JOINED_FORMAT;
        joinedFirstTimeFormat = DEFAULT_JOINED_FIRST_TIME_FORMAT;
        joinedRenamedFormat = DEFAULT_JOINED_RENAMED_FORMAT;
        leftGameFormat = DEFAULT_LEFT_FORMAT;
        deathFormat = DEFAULT_DEATH_FORMAT;
        privateMessageSentFormat = DEFAULT_PRIVATE_MESSAGE_SENT_FORMAT;
        privateMessageReceivedFormat = DEFAULT_PRIVATE_MESSAGE_RECEIVED_FORMAT;
        save();
    }

    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.CHAT_FORMAT_MODULE);
        section.set("enabled", enabled);
        section.set("chat_format", chatFormat);
        section.set("joined_game_format", joinedGameFormat);
        section.set("joined_first_time_format", joinedFirstTimeFormat);
        section.set("joined_renamed_format", joinedRenamedFormat);
        section.set("left_game_format", leftGameFormat);
        section.set("death_format", deathFormat);
        section.set("private_message_sent_format", privateMessageSentFormat);
        section.set("private_message_received_format", privateMessageReceivedFormat);
        GlobalSettings.save();
    }
}
