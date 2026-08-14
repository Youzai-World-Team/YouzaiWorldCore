package top.csituka.youzaiworldcore.sidebar;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.config.ConfigSection;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 侧边栏定制配置（仿 Styled Sidebars 精简版，计分板驱动，去依赖化重写）。
 * <p>
 * 存放位置：{@code yzwc/server/config/global_settings.json} 的
 * {@code sidebar_module} 分节。只保留<b>单一全局样式</b>（不做样式切换），
 * 支持的功能：
 * <ul>
 *   <li>{@code enabled}：总开关，关闭后向在线玩家发送计分板移除包（保持原版行为）；</li>
 *   <li>{@code update_tick_time}：侧边栏刷新周期（tick，20 = 1 秒）。占位符与
 *       动画帧在该周期内重新求值 / 推进；</li>
 *   <li>{@code title_change}：标题轮播切换所需刷新次数（标题数组多于 1 帧时生效）；</li>
 *   <li>{@code page_change}：分页切换所需刷新次数（使用 {@code pages} 时生效）；</li>
 *   <li>{@code scroll_speed}：滚动模式每多少次刷新推进一行；</li>
 *   <li>{@code scroll_loop}：滚动是否无缝循环（false 时滚到底后回到开头）；</li>
 *   <li>{@code title}：标题帧数组（每元素一帧，单帧则静止）；</li>
 *   <li>{@code lines} / {@code pages}：互斥。lines 为单页行数组；pages 为二维
 *       数组（每页一行数组）。任一页行数超过 14 行时自动进入滚动模式。</li>
 * </ul>
 * 行的四种写法（与 Styled Sidebars 对齐）：
 * <ol>
 *   <li><b>字符串</b>：普通行，如 {@code "<gray> ▪ <yellow>%player:name%"};</li>
 *   <li><b>两元素数组</b>：左右文本（右文本渲染在行右侧），如
 *       {@code ["<green>名字 ", "<yellow>%player:name%"]};</li>
 *   <li><b>对象 + value 数组</b>：逻辑行（一组行），如
 *       {@code {"value": ["行1", "行2"]}};</li>
 *   <li><b>对象 + value + permission</b>：带权限条件的逻辑行，
 *       {@code {"value": [...], "permission": "youzaiworldcore.sidebar.line.admin"}}，
 *       无权限时整组隐藏（LP 未装时回退 OP 2 级）。</li>
 * </ol>
 * 所有文本均支持 {@code <red>} 等 Simplified Text 标签与 {@code %papi%} 占位符
 * （Placeholder API，按玩家分别求值），例如 {@code %server:online%}、
 * {@code %server:tps_colored%}、{@code %player:ping%}、{@code %world:name%}。
 * 文本的实际解析（TextNode）与动画编排由 {@link SidebarManager} 完成，
 * 本类只负责存取原始 JSON。
 * </p>
 */
@SuppressWarnings("null")
public final class SidebarSettings {

    public static final String MODULE = "SidebarSettings";

    /** 默认刷新周期（tick，20 = 1 秒） */
    public static final int DEFAULT_UPDATE_TICK_TIME = 20;

    /** 刷新周期允许范围 [1, 1200]（1 tick 到 1 分钟） */
    private static final int MIN_UPDATE_TICK_TIME = 1;
    private static final int MAX_UPDATE_TICK_TIME = 1200;

    // ===== 默认值（新开服 / 坏文件恢复时写入） =====
    //
    // 默认配置即「全功能测试模板」：把本功能支持的全部能力集中展示，
    // 供新服 / 调试时一眼对照。覆盖点：
    //   - 标题轮播：title 用 3 帧（渐变 / 彩虹 / 渐变），title_change(5) × update_tick_time(20) = 100 tick = 5 秒切一帧；
    //   - 分页：pages 用 3 页，page_change(8) × update_tick_time(20) = 160 tick = 8 秒切一页；
    //   - 滚动：第 2 / 3 页行数超过 14 行触发，scroll_speed(2) 每 40 tick 推进一行，scroll_loop=true 无缝循环；
    //   - 左右文本：第 2 页的 [left, right] 数组行（右文本经 FixedFormat 渲染在行右侧）；
    //   - 逻辑行：第 3 页的 {"value": [...]} 对象行；
    //   - 权限行：第 3 页的 {"value": [...], "permission": ...} 对象行（无权限玩家不可见）；
    //   - 占位符：%server:online% / %server:max_players% / %server:tps_colored% /
    //            %server:mspt_colored% / %server:used_ram% / %server:max_ram% /
    //            %server:time% / %world:name% / %world:time% / %player:name% /
    //            %player:ping% / %player:health% / %player:max_health% /
    //            %player:pos_x% / %player:pos_y% / %player:pos_z%。
    // 注意：不要使用 <gr ...>（渐变简写）或嵌套 <color> 标签，26.2 下渲染不稳定（与 TabList 模板同约束）。

    /** 默认标题：3 帧轮播 */
    private static final String DEFAULT_TITLE_JSON =
            """
            [
              "<gradient:#4adeff:#3d8eff><bold> 悠哉世界 Youzai World </bold></gradient>",
              "<rainbow><bold> 欢迎来到悠哉世界 </bold></rainbow>",
              "<gradient:#ff5555:#55ff55><bold> Youzai World Server </bold></gradient>"
            ]
            """;

    /**
     * 默认分页模板：3 页。
     * <ul>
     *   <li>第 1 页「服务器概览」：12 行静态（不触发滚动）；</li>
     *   <li>第 2 页「性能与滚动」：21 行（触发滚动），含 5 组左右文本行；</li>
     *   <li>第 3 页「逻辑行与权限行」：19 行（触发滚动），含逻辑行与权限行。</li>
     * </ul>
     */
    private static final String DEFAULT_PAGES_JSON =
            """
            [
              [
                "",
                "<gray>» <white>在线人数",
                "<gray> ▪ <yellow>%server:online%</yellow><dark_gray>/</dark_gray><orange>%server:max_players%</orange>",
                "",
                "<gray>» <white>玩家信息",
                "<gray> ▪ <yellow>%player:name%",
                "<gray> ▪ <aqua>%player:pos_x% %player:pos_y% %player:pos_z%",
                "",
                "<gray>» <white>世界信息",
                "<gray> ▪ <yellow>%world:name%",
                "<gray> ▪ <yellow>%world:time%",
                ""
              ],
              [
                "",
                "<gray>» <white>服务器性能",
                "<gray> ▪ TPS: <yellow>%server:tps_colored%",
                "<gray> ▪ MSPT: <yellow>%server:mspt_colored%",
                "<gray> ▪ 内存: <yellow>%server:used_ram%</yellow><dark_gray>/</dark_gray><orange>%server:max_ram%",
                "",
                "<gray>» <white>左右文本示例（右对齐）",
                ["<green>当前时间 ", "<yellow>%server:time%"],
                ["<green>在线人数 ", "<yellow>%server:online%"],
                ["<green>我的名字 ", "<yellow>%player:name%"],
                ["<green>Ping ", "<yellow>%player:ping%"],
                ["<green>血量 ", "<yellow>%player:health%<dark_gray>/</dark_gray>%player:max_health%"],
                "",
                "<gray>» <white>滚动测试（本页超过 14 行）",
                "<gray> ▪ 第 1 行", "<gray> ▪ 第 2 行", "<gray> ▪ 第 3 行",
                "<gray> ▪ 第 4 行", "<gray> ▪ 第 5 行", "<gray> ▪ 第 6 行"
              ],
              [
                "",
                "<gray>» <white>逻辑行测试",
                {
                  "value": [
                    "<green> ▪ 逻辑行第 1 行",
                    "<green> ▪ 逻辑行第 2 行",
                    "<green> ▪ 逻辑行第 3 行"
                  ]
                },
                "",
                "<gray>» <white>权限行测试（无权限不可见）",
                {
                  "value": [
                    "<gold> ▪ 管理员专属行",
                    "<gold> ▪ 需要权限 youzaiworldcore.sidebar.line.admin"
                  ],
                  "permission": "youzaiworldcore.sidebar.line.admin"
                },
                "",
                "<gray>» <white>滚动测试（长列表）",
                "<gray> ▪ A", "<gray> ▪ B", "<gray> ▪ C", "<gray> ▪ D", "<gray> ▪ E",
                "<gray> ▪ F", "<gray> ▪ G", "<gray> ▪ H", "<gray> ▪ I"
              ]
            ]
            """;

    private static boolean enabled = true;
    private static int updateTickTime = DEFAULT_UPDATE_TICK_TIME;
    private static int titleChange = 5;
    private static int pageChange = 8;
    private static int scrollSpeed = 2;
    private static boolean scrollLoop = true;

    /** 标题原始 JSON（null = 未配置，显示为空标题） */
    @Nullable
    private static JsonElement title = null;

    /** 单页行原始 JSON（与 pages 互斥，lines 优先） */
    @Nullable
    private static JsonElement lines = null;

    /** 分页原始 JSON（与 lines 互斥） */
    @Nullable
    private static JsonElement pages = null;

    private SidebarSettings() {
    }

    // ===== 读取 =====

    public static boolean isEnabled() {
        return enabled;
    }

    /** @return 侧边栏刷新周期（tick） */
    public static int getUpdateTickTime() {
        return updateTickTime;
    }

    /** @return 标题轮播切换所需刷新次数 */
    public static int getTitleChange() {
        return titleChange;
    }

    /** @return 分页切换所需刷新次数 */
    public static int getPageChange() {
        return pageChange;
    }

    /** @return 滚动模式每多少次刷新推进一行 */
    public static int getScrollSpeed() {
        return scrollSpeed;
    }

    /** @return 滚动是否无缝循环 */
    public static boolean isScrollLoop() {
        return scrollLoop;
    }

    @Nullable
    public static JsonElement getTitle() {
        return title;
    }

    @Nullable
    public static JsonElement getLines() {
        return lines;
    }

    @Nullable
    public static JsonElement getPages() {
        return pages;
    }

    // ===== 加载 / 保存 =====

    public static void load() {
        DebugLogger.entering(MODULE, "load");
        ConfigSection section = GlobalSettings.section(GlobalSettings.SIDEBAR_MODULE);
        if (section.isEmpty()) {
            // 老服升级：global_settings.json 已存在但没有 sidebar_module 分节 → 补写默认值
            writeDefaults();
            DebugLogger.exiting(MODULE, "load", "created default");
            return;
        }
        enabled = section.getBoolean("enabled", enabled);
        updateTickTime = section.getInt("update_tick_time", updateTickTime,
                MIN_UPDATE_TICK_TIME, MAX_UPDATE_TICK_TIME);
        titleChange = section.getInt("title_change", titleChange,
                MIN_UPDATE_TICK_TIME, MAX_UPDATE_TICK_TIME);
        pageChange = section.getInt("page_change", pageChange,
                MIN_UPDATE_TICK_TIME, MAX_UPDATE_TICK_TIME);
        scrollSpeed = section.getInt("scroll_speed", scrollSpeed,
                MIN_UPDATE_TICK_TIME, MAX_UPDATE_TICK_TIME);
        scrollLoop = section.getBoolean("scroll_loop", scrollLoop);
        title = section.raw().get("title");
        lines = section.raw().get("lines");
        pages = section.raw().get("pages");
        DebugLogger.info(MODULE,
                "已加载侧边栏配置：enabled=%s, updateTickTime=%d, titleChange=%d, pageChange=%d, scrollSpeed=%d, scrollLoop=%s, title=%s, lines=%s, pages=%s",
                enabled, updateTickTime, titleChange, pageChange, scrollSpeed, scrollLoop,
                title == null ? "null" : "set", lines == null ? "null" : "set",
                pages == null ? "null" : "set");
        DebugLogger.exiting(MODULE, "load");
    }

    /** 重置为默认值并写入 {@code sidebar_module} 分节（新开服 / 坏文件恢复用） */
    public static void writeDefaults() {
        enabled = true;
        updateTickTime = DEFAULT_UPDATE_TICK_TIME;
        titleChange = 5;
        pageChange = 8;
        scrollSpeed = 2;
        scrollLoop = true;
        title = JsonParser.parseString(DEFAULT_TITLE_JSON);
        lines = null;
        pages = JsonParser.parseString(DEFAULT_PAGES_JSON);
        save();
    }

    public static void save() {
        ConfigSection section = GlobalSettings.section(GlobalSettings.SIDEBAR_MODULE);
        section.set("enabled", enabled);
        section.set("update_tick_time", updateTickTime);
        section.set("title_change", titleChange);
        section.set("page_change", pageChange);
        section.set("scroll_speed", scrollSpeed);
        section.set("scroll_loop", scrollLoop);
        if (title == null) {
            section.remove("title");
        } else {
            section.set("title", title);
        }
        if (lines == null) {
            section.remove("lines");
        } else {
            section.set("lines", lines);
        }
        if (pages == null) {
            section.remove("pages");
        } else {
            section.set("pages", pages);
        }
        GlobalSettings.save();
    }
}
