package top.csituka.youzaiworldcore.config;

import java.util.Objects;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 加入、离开及死亡事件的聊天模板配置。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code chat_format_module} 分节。
 * </p>
 * <p>
 * 支持的功能：
 * <ul>
 *   <li>事件模板支持 {@code ${player}}、{@code ${level}}、{@code ${title}}、
 *       {@code ${default_message}} 等变量，以及 {@code <red>} 等文本标签
 *       和 {@code %papi%} 占位符（Placeholder API，<b>始终解析</b>，无开关）；</li>
 *   <li>模板颜色<b>强制生效</b>：无视客户端「聊天颜色」设置，对所有玩家一律发送
 *       模板渲染结果。</li>
 * </ul>
 * </p>
 */
@SuppressWarnings({ "null", "unused" })
public final class ChatFormatSettings {

    public static final String MODULE = "ChatFormatSettings";

    /** 默认加入消息模板（原版翻译键） */
    public static final String DEFAULT_JOINED_FORMAT = "<yellow><lang:multiplayer.player.joined:'${player}'></yellow>";
    public static final String DEFAULT_JOINED_FIRST_TIME_FORMAT = "<yellow><lang:multiplayer.player.joined:'${player}'></yellow>";
    public static final String DEFAULT_JOINED_RENAMED_FORMAT = "<yellow><lang:multiplayer.player.joined.renamed:'${player}':'${old_name}'></yellow>";
    public static final String DEFAULT_LEFT_FORMAT = "<yellow><lang:multiplayer.player.left:'${player}'></yellow>";
    /** 默认死亡消息模板（透传原版死亡消息） */
    public static final String DEFAULT_DEATH_FORMAT = "${default_message}";

    private static boolean enabled = true;
    private static String joinedGameFormat = DEFAULT_JOINED_FORMAT;
    private static String joinedFirstTimeFormat = DEFAULT_JOINED_FIRST_TIME_FORMAT;
    private static String joinedRenamedFormat = DEFAULT_JOINED_RENAMED_FORMAT;
    private static String leftGameFormat = DEFAULT_LEFT_FORMAT;
    private static String deathFormat = DEFAULT_DEATH_FORMAT;

    private ChatFormatSettings() {
    }

    public static boolean isEnabled() { return enabled; }
    public static String getJoinedGameFormat() { return joinedGameFormat; }
    public static String getJoinedFirstTimeFormat() { return joinedFirstTimeFormat; }
    public static String getJoinedRenamedFormat() { return joinedRenamedFormat; }
    public static String getLeftGameFormat() { return leftGameFormat; }
    public static String getDeathFormat() { return deathFormat; }

    public static void setEnabled(boolean v) { if (enabled != v) { enabled = v; save(); } }
    public static void setJoinedGameFormat(String v) { if (!Objects.equals(joinedGameFormat, v)) { joinedGameFormat = v; save(); } }
    public static void setJoinedFirstTimeFormat(String v) { if (!Objects.equals(joinedFirstTimeFormat, v)) { joinedFirstTimeFormat = v; save(); } }
    public static void setJoinedRenamedFormat(String v) { if (!Objects.equals(joinedRenamedFormat, v)) { joinedRenamedFormat = v; save(); } }
    public static void setLeftGameFormat(String v) { if (!Objects.equals(leftGameFormat, v)) { leftGameFormat = v; save(); } }
    public static void setDeathFormat(String v) { if (!Objects.equals(deathFormat, v)) { deathFormat = v; save(); } }

    public static void load() {
        DebugLogger.SERVER.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.CHAT_FORMAT_MODULE);
        if (section.isEmpty()) {
            save();
            DebugLogger.SERVER.exiting(MODULE, "load", "created default");
            return;
        }
        enabled = section.getBoolean("enabled", enabled);
        joinedGameFormat = section.getString("joined_game_format", joinedGameFormat);
        joinedFirstTimeFormat = section.getString("joined_first_time_format", joinedFirstTimeFormat);
        joinedRenamedFormat = section.getString("joined_renamed_format", joinedRenamedFormat);
        leftGameFormat = section.getString("left_game_format", leftGameFormat);
        deathFormat = section.getString("death_format", deathFormat);
        DebugLogger.SERVER.info(MODULE, "已加载聊天格式化配置：enabled={}", enabled);
        DebugLogger.SERVER.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code chat_format_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = true;
        joinedGameFormat = DEFAULT_JOINED_FORMAT;
        joinedFirstTimeFormat = DEFAULT_JOINED_FIRST_TIME_FORMAT;
        joinedRenamedFormat = DEFAULT_JOINED_RENAMED_FORMAT;
        leftGameFormat = DEFAULT_LEFT_FORMAT;
        deathFormat = DEFAULT_DEATH_FORMAT;
        save();
    }

    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.CHAT_FORMAT_MODULE);
        section.set("enabled", enabled);
        section.set("joined_game_format", joinedGameFormat);
        section.set("joined_first_time_format", joinedFirstTimeFormat);
        section.set("joined_renamed_format", joinedRenamedFormat);
        section.set("left_game_format", leftGameFormat);
        section.set("death_format", deathFormat);
        GlobalSettings.save();
    }
}
