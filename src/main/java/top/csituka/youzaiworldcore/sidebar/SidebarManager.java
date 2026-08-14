package top.csituka.youzaiworldcore.sidebar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.NodeParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.luckperms.LuckPermsHelper;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 侧边栏定制核心（仿 Styled Sidebars 精简版，计分板驱动，去依赖化重写）。
 * <p>
 * 实现方式（零 Mixin、零额外依赖，全部走 Fabric 事件 + 原生计分板数据包）：
 * <ol>
 * <li>{@code ServerTickEvents.END_SERVER_TICK}：每个 {@code update_tick_time} tick
 *     对每个在线玩家重算并发送一次侧边栏（标题 CHANGE + 行 SetScore），
 *     占位符按每个玩家分别求值（支持 {@code %player:ping%} 等个人占位符）；</li>
 * <li>{@code ServerPlayConnectionEvents.JOIN}：玩家加入时立即发送初始帧
 *     （ADD objective + 绑定 SIDEBAR 槽位 + 行）；</li>
 * <li>{@code ServerPlayConnectionEvents.DISCONNECT}：移除玩家动画状态；</li>
 * <li>配置重载（{@code /yzwc reload} 或 {@link #reload()}）时重新解析模板并重置
 *     所有玩家动画计数；功能被关闭时向在线玩家广播计分板移除包清除残留。</li>
 * </ol>
 *
 * <p>计分板数据包工作方式（26.2 反编译验证）：
 * <ul>
 * <li>{@code ClientboundSetObjectivePacket}：ADD 创建 / CHANGE 更新标题 /
 *     REMOVE 移除整个计分板（一次清空全部行）；</li>
 * <li>{@code ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, ...)}：
 *     把该计分板绑定到右侧边栏槽位；</li>
 * <li>{@code ClientboundSetScorePacket(owner, objective, score, display, numberFormat)}：
 *     行文本经 {@code display} 组件承载，右侧文本经 {@code FixedFormat} 承载，
 *     无右侧文本用 {@code BlankFormat} 隐藏分数；分数<b>高者显示在上</b>，
 *     故第 0 行赋最高分；</li>
 * <li>{@code ClientboundResetScorePacket(owner, objective)}：移除单行（用于行数
 *     减少时清理多余槽位）。</li>
 * </ul>
 * </p>
 *
 * <p>行槽位固定为 {@value #WINDOW_SIZE} 个（owner 形如 {@code yzwc_sidebar_0}），
 * 每帧按需覆写；上一帧行数多于当前帧时对超出槽位补发 ResetScore。
 * 动画状态（滚动位置 / 页码 / 标题帧计数）为每玩家独立（JOIN 时间不同步），
 * 与 Styled Sidebars 的每玩家实例行为一致。</p>
 *
 * <p>文本模板（标题 / 行 / 页）由 {@link SidebarSettings} 提供原始 JSON，
 * 本类解析为预解析的 {@link TextNode}。解析失败时降级为空帧并记录 WARN 日志，
 * 不中断服务。</p>
 */
@SuppressWarnings({ "null", "UnstableApiUsage" })
public final class SidebarManager {

    public static final String MODULE = "SidebarManager";

    /** 模板解析器：Simplified Text 标签（&lt;red&gt; 等）+ %papi% 服务端占位符 */
    private static final NodeParser PARSER = NodeParser.builder()
            .simplifiedTextFormat()
            .serverPlaceholders()
            .staticPreParsing()
            .build();

    /** 计分板目标名（原版限制 ≤ 16 字符，全玩家共用同一目标） */
    public static final String OBJECTIVE_NAME = "yzwc_sidebar";

    /** 侧边栏可见行窗口（原版上限 15，留 1 行余量） */
    private static final int WINDOW_SIZE = 14;

    /** 行槽位 owner 前缀，槽位 i 的 owner = {@code SLOT_PREFIX + i} */
    private static final String SLOT_PREFIX = OBJECTIVE_NAME + "_";

    /**
     * 共享的计分板实例（仅作为 {@link Objective} 构造参数，不参与真实计分板数据）。
     * 渲染全部发生在服务端主线程，单线程访问安全。
     */
    private static final Scoreboard SCOREBOARD = new Scoreboard();

    // ===== 预解析模板（reload 时重建） =====

    private static List<TextNode> titles = List.of(TextNode.empty());
    @Nullable
    private static List<ParsedLine> lines = null;
    @Nullable
    private static List<List<ParsedLine>> pages = null;

    /** 每玩家动画状态（JOIN 创建、DISCONNECT 移除） */
    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    /** 上一次的开关状态，用于检测「开启 → 关闭」切换并清除客户端残留 */
    private static boolean lastEnabled = false;

    private SidebarManager() {
    }

    // ===== 生命周期 =====

    /** 初始化：加载配置、解析模板、注册 tick 与连接事件（在 onInitialize 中调用） */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");
        reload();

        ServerTickEvents.END_SERVER_TICK.register(SidebarManager::onServerTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!SidebarSettings.isEnabled()) {
                return;
            }
            if (handler.getPlayer() instanceof ServerPlayer sp && sp.connection != null) {
                DebugLogger.trace(MODULE, "玩家加入，立即发送侧边栏: %s", sp.getName().getString());
                PlayerState state = STATES.computeIfAbsent(sp.getUUID(), u -> new PlayerState());
                render(sp, state);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            // 断开后客户端侧计分板随连接销毁，无需补发 REMOVE
            STATES.remove(handler.getPlayer().getUUID());
        });

        DebugLogger.exiting(MODULE, "initialize");
    }

    /** 重载配置并重解析模板（供 /yzwc reload 调用） */
    public static void reload() {
        SidebarSettings.load();
        parseTemplates();
        // 重置动画计数：新模板从头开始（行清理由各玩家 lastRows 兜底，不重置）
        for (PlayerState state : STATES.values()) {
            state.pos = 0;
            state.page = 0;
            state.title = 0;
        }
        DebugLogger.info(MODULE,
                "侧边栏已重载：enabled=%s, updateTickTime=%d, titleFrames=%d, lines=%s, pages=%d",
                SidebarSettings.isEnabled(), SidebarSettings.getUpdateTickTime(),
                titles.size(), lines == null ? "null" : String.valueOf(lines.size()),
                pages == null ? 0 : pages.size());
    }

    // ===== 服务端 tick 调度 =====

    private static void onServerTick(MinecraftServer server) {
        boolean enabled = SidebarSettings.isEnabled();
        if (enabled != lastEnabled) {
            DebugLogger.stateChange(MODULE, "Sidebar", "enabled", lastEnabled, enabled);
            if (!enabled) {
                // 关闭时清除客户端已显示的侧边栏残留
                clearAll(server);
            }
            lastEnabled = enabled;
        }
        if (!enabled) {
            return;
        }

        int tick = server.getTickCount();
        int rate = SidebarSettings.getUpdateTickTime();
        if (tick % rate != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerState state = STATES.computeIfAbsent(player.getUUID(), u -> new PlayerState());
            render(player, state);
        }
    }

    // ===== 渲染 =====

    /**
     * 向单个玩家发送当前帧的侧边栏。
     * <ol>
     * <li>标题：按 {@code title} 计数轮播取帧，ADD 时建目标 + 绑定 SIDEBAR 槽位，
     *     之后每次 CHANGE 更新标题（保证标题内占位符刷新）；</li>
     * <li>行：{@code lines} 或 {@code pages}（按 {@code page} 计数分页），
     *     权限过滤后按玩家求值，超过窗口大小进入滚动模式；</li>
     * <li>清理：上一帧行数多于当前帧时对超出槽位补发 ResetScore。</li>
     * </ol>
     */
    private static void render(ServerPlayer player, PlayerState state) {
        try {
            var ctx = ServerPlaceholderContext.of(player).asParserContext();

            // ===== 标题帧（每次刷新推进计数） =====
            int titleChange = SidebarSettings.getTitleChange();
            int titleIndex = (state.title / titleChange) % titles.size();
            state.title++;
            Component titleComp = titles.get(titleIndex).toComponent(ctx);

            Objective objective = newObjective(titleComp);
            if (!state.added) {
                player.connection.send(new ClientboundSetObjectivePacket(objective,
                        ClientboundSetObjectivePacket.METHOD_ADD));
                player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
                state.added = true;
            } else {
                player.connection.send(new ClientboundSetObjectivePacket(objective,
                        ClientboundSetObjectivePacket.METHOD_CHANGE));
            }

            // ===== 行：lines 或 pages 分页 =====
            List<ParsedLine> source = currentLines(state);

            // 权限过滤 + 占位符求值
            List<Pair<Component, Component>> visible = new ArrayList<>();
            for (ParsedLine line : source) {
                if (!hasPermission(player, line.permission)) {
                    continue;
                }
                for (Pair<TextNode, TextNode> pair : line.values) {
                    visible.add(new Pair<>(pair.getFirst().toComponent(ctx), pair.getSecond().toComponent(ctx)));
                }
            }

            // ===== 滚动窗口 =====
            List<Pair<Component, Component>> window = applyScroll(state, visible);

            // ===== 发送行（分数高者在上，第 0 行赋最高分） =====
            int rows = window.size();
            for (int i = 0; i < rows; i++) {
                Pair<Component, Component> pair = window.get(i);
                Component left = pair.getFirst();
                Component right = pair.getSecond();
                NumberFormat numberFormat = (right == null || right.getString().isEmpty())
                        ? BlankFormat.INSTANCE
                        : new FixedFormat(right);
                player.connection.send(new ClientboundSetScorePacket(SLOT_PREFIX + i, OBJECTIVE_NAME,
                        rows - i, Optional.of(left), Optional.of(numberFormat)));
            }

            // ===== 清理上一帧多余行 =====
            for (int i = rows; i < state.lastRows; i++) {
                player.connection.send(new ClientboundResetScorePacket(SLOT_PREFIX + i, OBJECTIVE_NAME));
            }
            state.lastRows = rows;
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "render(" + player.getName().getString() + ")", e);
        }
    }

    /** 取当前应显示的行源：{@code lines} 优先，否则按 {@code page} 计数分页 */
    private static List<ParsedLine> currentLines(PlayerState state) {
        if (lines != null) {
            return lines;
        }
        if (pages != null && !pages.isEmpty()) {
            int pageChange = SidebarSettings.getPageChange();
            int pageIndex = (state.page / pageChange) % pages.size();
            state.page++;
            return pages.get(pageIndex);
        }
        return List.of();
    }

    /**
     * 滚动窗口处理（与 Styled Sidebars 的 CustomSidebar 逻辑一致）：
     * <ul>
     * <li>行数 ≤ 窗口 → 原样返回；</li>
     * <li>循环模式：列表拼接两份，从 {@code index} 截固定窗口，滚完回开头（无缝）；</li>
     * <li>非循环模式：滚到底后重置回开头。</li>
     * </ul>
     */
    private static List<Pair<Component, Component>> applyScroll(PlayerState state,
                                                                List<Pair<Component, Component>> visible) {
        if (visible.size() <= WINDOW_SIZE) {
            return visible;
        }
        state.pos++;
        int scrollSpeed = SidebarSettings.getScrollSpeed();
        int index = state.pos / scrollSpeed;

        if (SidebarSettings.isScrollLoop()) {
            if (index >= visible.size()) {
                state.pos = 0;
                index = 0;
            }
            List<Pair<Component, Component>> doubled = new ArrayList<>(visible.size() * 2);
            doubled.addAll(visible);
            doubled.addAll(visible);
            return doubled.subList(index, index + WINDOW_SIZE);
        } else {
            if (index + WINDOW_SIZE > visible.size()) {
                state.pos = 0;
                index = 0;
            }
            return visible.subList(index, Math.min(index + WINDOW_SIZE, visible.size()));
        }
    }

    /** 行级权限检查：无权限要求 → 放行；LP 已装 → LP 节点；未装 → 回退 OP 2 级 */
    private static boolean hasPermission(ServerPlayer player, @Nullable String permission) {
        if (permission == null || permission.isBlank()) {
            return true;
        }
        if (LuckPermsHelper.isLuckPermsLoaded()) {
            return LuckPermsHelper.checkLuckPermsOnly(player.getUUID(), permission);
        }
        return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
    }

    /** 构造计分板目标（displayAutoUpdate=false 防止原版自动刷新标题） */
    private static Objective newObjective(Component title) {
        return new Objective(SCOREBOARD, OBJECTIVE_NAME, ObjectiveCriteria.DUMMY, title,
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
    }

    // ===== 清除 =====

    /** 向单个玩家发送计分板移除包（功能关闭 / 重载重建前清理） */
    private static void clearPlayer(ServerPlayer player, PlayerState state) {
        if (state == null || !state.added) {
            return;
        }
        player.connection.send(new ClientboundSetObjectivePacket(newObjective(Component.empty()),
                ClientboundSetObjectivePacket.METHOD_REMOVE));
        state.added = false;
        state.lastRows = 0;
    }

    /** 向全部在线玩家广播计分板移除包（功能关闭时清除残留） */
    private static void clearAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearPlayer(player, STATES.get(player.getUUID()));
        }
        DebugLogger.info(MODULE, "侧边栏功能已关闭，已向 %d 名在线玩家清除计分板",
                server.getPlayerList().getPlayers().size());
    }

    // ===== 模板解析 =====

    /** 从 {@link SidebarSettings} 原始 JSON 重建预解析模板（标题 / 行 / 页） */
    private static void parseTemplates() {
        titles = parseTitle(SidebarSettings.getTitle());
        lines = null;
        pages = null;
        JsonElement linesElement = SidebarSettings.getLines();
        JsonElement pagesElement = SidebarSettings.getPages();
        if (linesElement != null && linesElement.isJsonArray()) {
            lines = parseLines(linesElement.getAsJsonArray());
        } else if (pagesElement != null && pagesElement.isJsonArray()) {
            pages = parsePages(pagesElement.getAsJsonArray());
        } else {
            lines = List.of();
        }
    }

    /**
     * 解析标题：期望字符串数组，每元素一帧。非法时降级为单帧空标题。
     */
    private static List<TextNode> parseTitle(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of(TextNode.empty());
        }
        if (!element.isJsonArray()) {
            DebugLogger.warn(MODULE, "标题配置错误：期望字符串数组 [ ... ]，实际是 %s，已回退为空标题",
                    describe(element));
            return List.of(TextNode.empty());
        }
        JsonArray array = element.getAsJsonArray();
        List<TextNode> frames = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                DebugLogger.warn(MODULE, "标题配置错误：title[%d] 必须是字符串，该帧已跳过", i);
                continue;
            }
            frames.add(PARSER.parseNode(item.getAsString()));
        }
        if (frames.isEmpty()) {
            frames.add(TextNode.empty());
        }
        return frames;
    }

    /** 解析单页行数组（pages 模式每页一行） */
    private static List<ParsedLine> parseLines(JsonArray array) {
        List<ParsedLine> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            ParsedLine line = parseLine(array.get(i), "lines[" + i + "]");
            if (line != null) {
                result.add(line);
            }
        }
        return result;
    }

    /** 解析分页：期望二维数组，每页为行数组 */
    private static List<List<ParsedLine>> parsePages(JsonArray array) {
        List<List<ParsedLine>> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement page = array.get(i);
            if (page == null || !page.isJsonArray()) {
                DebugLogger.warn(MODULE, "分页配置错误：pages[%d] 必须是行数组，该页已跳过", i);
                continue;
            }
            result.add(parseLines(page.getAsJsonArray()));
        }
        return result;
    }

    /**
     * 解析单行定义，支持四种写法：
     * <ol>
     * <li>字符串 → 普通行；</li>
     * <li>两元素数组 → 左右文本；</li>
     * <li>{@code {"value": [...]}} → 逻辑行（多行组）；</li>
     * <li>{@code {"value": [...], "permission": "..."}} → 带权限的逻辑行。</li>
     * </ol>
     * 解析失败返回 {@code null}（由调用方跳过）并记 WARN。
     */
    @Nullable
    private static ParsedLine parseLine(@Nullable JsonElement element, String label) {
        if (element == null || element.isJsonNull()) {
            DebugLogger.warn(MODULE, "行配置错误：%s 为 null，该行已跳过", label);
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new ParsedLine(List.of(toPair(element.getAsString(), "")), null);
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() >= 2) {
                return new ParsedLine(List.of(toPair(array.get(0).getAsString(), array.get(1).getAsString())), null);
            }
            if (array.size() == 1) {
                return new ParsedLine(List.of(toPair("", array.get(0).getAsString())), null);
            }
            return new ParsedLine(List.of(toPair("", "")), null);
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            String permission = null;
            JsonElement perm = obj.get("permission");
            if (perm != null && perm.isJsonPrimitive() && perm.getAsJsonPrimitive().isString()) {
                permission = perm.getAsString();
            }

            JsonElement values = obj.get("value");
            if (values == null) {
                values = obj.get("values");
            }
            if (values == null || values.isJsonNull()) {
                DebugLogger.warn(MODULE, "行配置错误：%s 的 value 缺失，该行已跳过", label);
                return null;
            }
            List<Pair<TextNode, TextNode>> list = new ArrayList<>();
            if (values.isJsonArray()) {
                JsonArray valueArray = values.getAsJsonArray();
                for (int i = 0; i < valueArray.size(); i++) {
                    JsonElement item = valueArray.get(i);
                    if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                        list.add(toPair(item.getAsString(), ""));
                    } else if (item.isJsonArray() && item.getAsJsonArray().size() >= 2) {
                        JsonArray pairArr = item.getAsJsonArray();
                        list.add(toPair(pairArr.get(0).getAsString(), pairArr.get(1).getAsString()));
                    } else {
                        DebugLogger.warn(MODULE, "行配置错误：%s.value[%d] 必须是字符串或两元素数组，该项已跳过",
                                label, i);
                    }
                }
            } else if (values.isJsonObject()) {
                // Map 形式：{"左文本": "右文本", ...}
                for (Map.Entry<String, JsonElement> entry : values.getAsJsonObject().entrySet()) {
                    list.add(toPair(entry.getKey(), entry.getValue().getAsString()));
                }
            } else if (values.isJsonPrimitive() && values.getAsJsonPrimitive().isString()) {
                list.add(toPair(values.getAsString(), ""));
            } else {
                DebugLogger.warn(MODULE, "行配置错误：%s.value 类型不受支持，该行已跳过", label);
                return null;
            }
            if (list.isEmpty()) {
                DebugLogger.warn(MODULE, "行配置错误：%s.value 解析后为空，该行已跳过", label);
                return null;
            }
            return new ParsedLine(list, permission);
        }
        DebugLogger.warn(MODULE, "行配置错误：%s 类型不受支持（期望字符串 / 数组 / 对象），该行已跳过", label);
        return null;
    }

    private static Pair<TextNode, TextNode> toPair(String left, String right) {
        return new Pair<>(PARSER.parseNode(left), PARSER.parseNode(right));
    }

    /** 日志用：描述 JSON 元素类型 */
    private static String describe(JsonElement element) {
        return element == null ? "null"
                : element.isJsonObject() ? "{...}"
                : element.isJsonArray() ? "[...]"
                : element.isJsonPrimitive() ? element.toString() : "?";
    }

    // ===== 数据结构 =====

    /**
     * 预解析行：一组左右文本对 + 可选权限节点。
     *
     * @param values     左右文本对列表（每对渲染为一行）
     * @param permission 可选权限节点；为空 / null 时总是显示
     */
    record ParsedLine(List<Pair<TextNode, TextNode>> values, @Nullable String permission) {
    }

    /** 每玩家动画状态（JOIN 创建、DISCONNECT 移除，ConcurrentHashMap 保证并发安全） */
    static final class PlayerState {
        /** 是否已发送 ADD（此后走 CHANGE 更新标题） */
        boolean added = false;
        /** 滚动位置计数（每次刷新推进，除以 scrollSpeed 得行偏移） */
        int pos = 0;
        /** 分页计数（每次刷新推进，除以 pageChange 得页码） */
        int page = 0;
        /** 标题帧计数（每次刷新推进，除以 titleChange 得帧号） */
        int title = 0;
        /** 上一帧发送的行数（用于清理多余槽位） */
        int lastRows = 0;
    }
}
