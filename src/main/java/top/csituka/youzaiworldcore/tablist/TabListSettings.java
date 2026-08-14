package top.csituka.youzaiworldcore.tablist;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * Tab 列表抬头 / 页脚定制配置（仿 Styled Player List 精简版，仅 Header/Footer）。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code tablist_module} 分节。支持的功能：
 * <ul>
 *   <li>{@code enabled}：总开关，关闭后不发送任何抬头 / 页脚包（保持原版行为）；</li>
 *   <li>{@code update_tick_time}：抬头 / 页脚刷新周期（tick，20 = 1 秒）。占位符与动画帧
 *       在该周期内重新求值 / 切换；</li>
 *   <li>{@code list_header} / {@code list_footer}：支持三种写法：
 *     <ol>
 *       <li><b>字符串</b>：单行文本，如 {@code "<gold> 悠哉世界 </gold>"}；</li>
 *       <li><b>字符串数组</b>：多行静态文本（数组的每一项是一行，自动以换行连接）；</li>
 *       <li><b>动画对象</b>：{@code {"change_rate": N, "values": [[...], [...]]}}，
 *           values 的每个元素是一帧（帧内为行数组），每 {@code change_rate} 次刷新切换一帧。</li>
 *     </ol>
 *   </li>
 * </ul>
 * 所有文本均支持 {@code <red>} 等 Simplified Text 标签与 {@code %papi%} 占位符
 * （Placeholder API，始终解析），例如 {@code %server:online%}、{@code %server:tps_colored%}、
 * {@code %player:ping%}。文本的实际解析（TextNode）由 {@link TabListManager} 完成，
 * 本类只负责存取原始 JSON。
 * </p>
 */
@SuppressWarnings("null")
public final class TabListSettings {

    public static final String MODULE = "TabListSettings";

    /** 默认刷新周期（tick，20 = 1 秒） */
    public static final int DEFAULT_UPDATE_TICK_TIME = 20;

    /** 刷新周期允许范围 [1, 1200]（1 tick 到 1 分钟） */
    private static final int MIN_UPDATE_TICK_TIME = 1;
    private static final int MAX_UPDATE_TICK_TIME = 1200;

    // ===== 默认值（新开服 / 坏文件恢复时写入） =====
    //
    // 默认配置即「全功能测试模板」：把本功能支持的全部能力集中展示，
    // 供新服 / 调试时一眼对照。覆盖点：
    //   - 写法：header 用「动画对象」，footer 用「多行字符串数组」（顶层字符串写法见类注释）；
    //   - 标签：仅使用 ChatFormatHelper 已验证的语法 —— <gradient:#..:#..> 渐变 / <rainbow> 彩虹 /
    //           <c:#hex> 颜色 / 颜色枚举 / <bold>/<italic>/<underline>/<strikethrough>;
    //   - 占位符：%server:online% / %server:max_players% / %server:tps_colored% /
    //            %world:name% / %world:time% / %player:name% / %player:displayname% /
    //            %player:ping% / %player:health% / %player:max_health% /
    //            %player:pos_x% / %player:pos_y% / %player:pos_z%。
    // 注意：不要使用 <gr ...>（渐变简写）或嵌套 <color> 标签，26.2 下渲染不稳定；
    // %player:playtime% 依赖 PLAY_TIME 统计，统计为 0 时返回空字符串（不显示），故模板改用坐标占位符。

    /**
     * 默认抬头：动画对象写法，3 帧。
     * 切换周期 = change_rate(5) × update_tick_time(20) = 100 tick = 5 秒。
     */
    private static final String DEFAULT_HEADER_JSON =
            "{\n"
            + "  \"change_rate\": 5,\n"
            + "  \"values\": [\n"
            + "    [\n"
            + "      \"\",\n"
            + "      \"<gradient:#4adeff:#3d8eff><bold> 悠哉世界 Youzai World </bold></gradient>\",\n"
            + "      \"\",\n"
            + "      \"<c:#555555>—— </c><c:#ff5555>%server:online%/%server:max_players%</c><c:#555555> ——</c> <gray>|</gray> <italic>在线玩家</italic>\",\n"
            + "      \"\"\n"
            + "    ],\n"
            + "    [\n"
            + "      \"\",\n"
            + "      \"<rainbow><bold> 欢迎来到悠哉世界 </bold></rainbow>\",\n"
            + "      \"\",\n"
            + "      \"<c:#555555><strikethrough>        </strikethrough></c> <c:#ffaa00>TPS: %server:tps_colored%</c>\",\n"
            + "      \"\"\n"
            + "    ],\n"
            + "    [\n"
            + "      \"\",\n"
            + "      \"<gradient:#ff5555:#55ff55><bold> Youzai World Server </bold></gradient>\",\n"
            + "      \"\",\n"
            + "      \"<gray>世界: <yellow>%world:name%</yellow> <dark_gray>|</dark_gray> 时间: <yellow>%world:time%</yellow></gray>\",\n"
            + "      \"\"\n"
            + "    ]\n"
            + "  ]\n"
            + "}";

    /**
     * 默认页脚：多行字符串数组写法，静态（个人占位符按每个玩家分别求值）。
     */
    private static final String DEFAULT_FOOTER_JSON =
            "[\n"
            + "  \"\",\n"
            + "  \"<c:#555555><strikethrough>                          </strikethrough></c>\",\n"
            + "  \"\",\n"
            + "  \"<gray>名字: <yellow>%player:name%</yellow> <dark_gray>|</dark_gray> 显示名: <gold>%player:displayname%</gold>\",\n"
            + "  \"<gray>Ping: <c:#ffba26>%player:ping%</c> <dark_gray>|</dark_gray> HP: <green>%player:health%/%player:max_health%</green> <dark_gray>|</dark_gray> 坐标: <aqua>%player:pos_x% %player:pos_y% %player:pos_z%</aqua>\",\n"
            + "  \"\"\n"
            + "]";

    private static boolean enabled = true;
    private static int updateTickTime = DEFAULT_UPDATE_TICK_TIME;

    /** 抬头原始 JSON（null = 未配置，显示为空） */
    @Nullable
    private static JsonElement header = null;

    /** 页脚原始 JSON（null = 未配置，显示为空） */
    @Nullable
    private static JsonElement footer = null;

    private TabListSettings() {
    }

    // ===== 读取 =====

    public static boolean isEnabled() {
        return enabled;
    }

    /** @return 抬头 / 页脚刷新周期（tick） */
    public static int getUpdateTickTime() {
        return updateTickTime;
    }

    @Nullable
    public static JsonElement getHeader() {
        return header;
    }

    @Nullable
    public static JsonElement getFooter() {
        return footer;
    }

    // ===== 加载 / 保存 =====

    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.TABLIST_MODULE);
        if (section.isEmpty()) {
            // 老服升级：global_settings.json 已存在但没有 tablist_module 分节 → 补写默认值
            writeDefaults();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }
        enabled = section.getBoolean("enabled", enabled);
        updateTickTime = section.getInt("update_tick_time", updateTickTime,
                MIN_UPDATE_TICK_TIME, MAX_UPDATE_TICK_TIME);
        header = section.raw().get("list_header");
        footer = section.raw().get("list_footer");
        DebugLogger.info(MODULE, "已加载 Tab 列表配置：enabled=%s, updateTickTime=%d, header=%s, footer=%s",
                enabled, updateTickTime, header == null ? "null" : "set", footer == null ? "null" : "set");
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code tablist_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = true;
        updateTickTime = DEFAULT_UPDATE_TICK_TIME;
        header = JsonParser.parseString(DEFAULT_HEADER_JSON);
        footer = JsonParser.parseString(DEFAULT_FOOTER_JSON);
        save();
    }

    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.TABLIST_MODULE);
        section.set("enabled", enabled);
        section.set("update_tick_time", updateTickTime);
        if (header == null) {
            section.remove("list_header");
        } else {
            section.set("list_header", header);
        }
        if (footer == null) {
            section.remove("list_footer");
        } else {
            section.set("list_footer", footer);
        }
        GlobalSettings.save();
    }
}
