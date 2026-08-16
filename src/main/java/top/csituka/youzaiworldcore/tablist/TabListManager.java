package top.csituka.youzaiworldcore.tablist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.NodeParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tab 列表抬头 / 页脚定制核心（仿 Styled Player List 精简版，仅 Header/Footer）。
 * <p>
 * 实现方式（去依赖化、零 Mixin，全部走 Fabric 事件）：
 * <ol>
 * <li>{@code ServerTickEvents.END_SERVER_TICK}：按玩家错峰求值抬头 / 页脚，
 * 仅在内容变化时发送 {@link ClientboundTabListPacket}；占位符按每个玩家分别求值
 * （支持 {@code %player:ping%} 等个人占位符）；</li>
 * <li>{@code ServerPlayConnectionEvents.JOIN}：玩家加入时立即发送一次当前帧；</li>
 * <li>配置重载（{@code /yzwc reload} 或 {@link #reload()}）时重新解析模板；
 * 功能被关闭时向在线玩家广播一次空抬头 / 页脚，清除客户端残留。</li>
 * </ol>
 * 文本模板（字符串 / 行数组 / 动画对象）由 {@link TabListSettings} 提供原始 JSON，
 * 本类负责解析为预解析的 {@link TextNode} 帧列表（动画帧索引由全局 tick 计算，
 * 无需每玩家状态）。解析失败时降级为空帧并记录 WARN 日志，不中断服务。
 * </p>
 */
@SuppressWarnings({ "null", "UnstableApiUsage" })
public final class TabListManager {

    public static final String MODULE = "TabListManager";

    /** 模板解析器：Simplified Text 标签（&lt;red&gt; 等）+ %papi% 服务端占位符 */
    private static final NodeParser PARSER = NodeParser.builder()
            .simplifiedTextFormat()
            .serverPlaceholders()
            .staticPreParsing()
            .build();

    private static FrameSet header = FrameSet.empty();
    private static FrameSet footer = FrameSet.empty();

    /** 每名玩家上一次发送的 Header/Footer，用于跳过无变化的网络包。 */
    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    /** 上一次的开关状态，用于检测「开启 → 关闭」切换并清除客户端残留 */
    private static boolean lastEnabled = false;

    private TabListManager() {
    }

    // ===== 生命周期 =====

    /** 初始化：加载配置、解析模板、注册 tick 与加入事件（在 onInitialize 中调用） */
    public static void initialize() {
        DebugLogger.entering(MODULE, "initialize");
        reload();

        ServerTickEvents.END_SERVER_TICK.register(TabListManager::onServerTick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!TabListSettings.isEnabled()) {
                return;
            }
            if (handler.getPlayer() instanceof ServerPlayer sp && sp.connection != null) {
                DebugLogger.trace(MODULE, "玩家加入，立即发送 Tab 抬头 / 页脚: %s", sp.getName().getString());
                PlayerState state = STATES.computeIfAbsent(sp.getUUID(), u -> new PlayerState());
                sendTo(sp, server.getTickCount(), state);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                STATES.remove(handler.getPlayer().getUUID()));

        DebugLogger.exiting(MODULE, "initialize");
    }

    /** 重载配置并重解析模板（供 /yzwc reload 调用） */
    public static void reload() {
        TabListSettings.load();
        header = parseFrames(TabListSettings.getHeader(), "list_header");
        footer = parseFrames(TabListSettings.getFooter(), "list_footer");
        for (PlayerState state : STATES.values()) {
            state.dirty = true;
        }
        DebugLogger.info(MODULE, "TabList 抬头 / 页脚已重载：enabled=%s, updateTickTime=%d, headerFrames=%d, footerFrames=%d",
                TabListSettings.isEnabled(), TabListSettings.getUpdateTickTime(),
                header.frames().size(), footer.frames().size());
    }

    // ===== 服务端 tick 调度 =====

    private static void onServerTick(MinecraftServer server) {
        boolean enabled = TabListSettings.isEnabled();
        if (enabled != lastEnabled) {
            DebugLogger.stateChange(MODULE, "TabList", "enabled", lastEnabled, enabled);
            if (!enabled) {
                // 关闭时清除客户端已显示的抬头 / 页脚残留
                clearAll(server);
            }
            lastEnabled = enabled;
        }
        if (!enabled) {
            return;
        }

        int tick = server.getTickCount();
        int rate = TabListSettings.getUpdateTickTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isRefreshTick(player, tick, rate)) {
                continue;
            }
            PlayerState state = STATES.computeIfAbsent(player.getUUID(), u -> new PlayerState());
            sendTo(player, tick, state);
        }
    }

    /** TabList 使用与侧边栏错开的玩家相位；默认相同周期下不会在同一 tick 刷新同一玩家。 */
    private static boolean isRefreshTick(ServerPlayer player, int tick, int rate) {
        int safeRate = Math.max(rate, 1);
        int phaseOffset = Math.max(1, safeRate / 2);
        int playerPhase = Math.floorMod(player.getUUID().hashCode() + phaseOffset, safeRate);
        return Math.floorMod(tick, safeRate) == playerPhase;
    }

    /** 向单个玩家发送当前帧的抬头 / 页脚（内容未变化时跳过发包）。 */
    private static void sendTo(ServerPlayer player, int tick, PlayerState state) {
        try {
            int rate = TabListSettings.getUpdateTickTime();
            var context = ServerPlaceholderContext.of(player).asParserContext();
            Component h = header.getForTick(tick, rate).toComponent(context);
            Component f = footer.getForTick(tick, rate).toComponent(context);

            if (!state.dirty && Objects.equals(state.lastHeader, h) && Objects.equals(state.lastFooter, f)) {
                return;
            }
            player.connection.send(new ClientboundTabListPacket(h, f));
            state.lastHeader = h;
            state.lastFooter = f;
            state.dirty = false;
        } catch (Exception e) {
            DebugLogger.exception(MODULE, "sendTo(" + player.getName().getString() + ")", e);
        }
    }

    /** 向全部在线玩家广播空抬头 / 页脚（功能关闭时清除残留） */
    private static void clearAll(MinecraftServer server) {
        var empty = new ClientboundTabListPacket(Component.empty(), Component.empty());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection != null) {
                player.connection.send(empty);
            }
            PlayerState state = STATES.get(player.getUUID());
            if (state != null) {
                state.lastHeader = Component.empty();
                state.lastFooter = Component.empty();
                state.dirty = false;
            }
        }
        DebugLogger.info(MODULE, "TabList 功能已关闭，已向 %d 名在线玩家清除抬头 / 页脚",
                server.getPlayerList().getPlayers().size());
    }

    // ===== 模板解析 =====

    /**
     * 把配置中的原始 JSON 解析为「帧列表 + 帧切换周期」。
     * <ul>
     * <li>{@code null} / {@code null} 值 → 单个空帧；</li>
     * <li>字符串 → 单帧单行；</li>
     * <li>字符串数组 → 单帧多行（行以换行连接）；</li>
     * <li>{@code {change_rate, values}} → 多帧，每帧为行数组。</li>
     * </ul>
     *
     * @param element 配置原始 JSON（可为 null）
     * @param label   出错时的描述（如 {@code list_header}），用于日志
     */
    private static FrameSet parseFrames(@Nullable JsonElement element, String label) {
        if (element == null || element.isJsonNull()) {
            return FrameSet.empty();
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            int changeRate = readChangeRate(obj, label);
            JsonElement values = obj.get("values");
            if (values == null || !values.isJsonArray()) {
                DebugLogger.warn(MODULE, "TabList 配置错误：%s 的动画对象缺少 values 数组，已回退为空", label);
                return FrameSet.empty();
            }
            List<TextNode> frames = new ArrayList<>();
            for (JsonElement frame : values.getAsJsonArray()) {
                List<String> lines = toStringList(frame, label + ".values[]");
                frames.add(PARSER.parseNode(joinLines(lines)));
            }
            if (frames.isEmpty()) {
                return FrameSet.empty();
            }
            return new FrameSet(frames, changeRate);
        }

        if (element.isJsonArray()) {
            List<String> lines = toStringList(element, label);
            return new FrameSet(List.of(PARSER.parseNode(joinLines(lines))), 1);
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new FrameSet(List.of(PARSER.parseNode(element.getAsString())), 1);
        }

        DebugLogger.warn(MODULE, "TabList 配置错误：%s 类型不受支持（期望字符串 / 字符串数组 / 动画对象），已回退为空",
                label);
        return FrameSet.empty();
    }

    /**
     * 用「换行 + 样式重置」连接多行：{@code \n<r>}。
     * <p>
     * 只换行不重置样式时，上一行末尾未闭合的颜色 / 格式（如删除线）会跨行污染
     * 下一行（MC 的 {@code \n} 不重置样式）。{@code <r>} 为 placeholder-api 的
     * reset 标签，保证每行样式独立——与 Styled Player List 26.2 的
     * legacyJoinBehaviour=false 默认行为一致。
     * </p>
     */
    private static String joinLines(List<String> lines) {
        return String.join("\n<r>", lines);
    }

    /** 读取动画对象的 {@code change_rate}（缺省 / 非法时取 1） */
    private static int readChangeRate(JsonObject obj, String label) {
        JsonElement cr = obj.get("change_rate");
        if (cr != null && cr.isJsonPrimitive() && cr.getAsJsonPrimitive().isNumber()) {
            int value = cr.getAsInt();
            if (value >= 1) {
                return value;
            }
            DebugLogger.warn(MODULE, "TabList 配置错误：%s.change_rate=%d 非法（应 >= 1），已回退为 1", label, value);
        }
        return 1;
    }

    /**
     * 把 JSON 数组转换为字符串行列表；元素不是字符串时跳过并记 WARN。
     * 空数组 / 非法输入时返回单个空行，保证换行语义稳定。
     */
    private static List<String> toStringList(JsonElement element, String label) {
        if (element == null || !element.isJsonArray()) {
            DebugLogger.warn(MODULE, "TabList 配置错误：%s 期望字符串数组，已回退为空行", label);
            return List.of("");
        }
        JsonArray array = element.getAsJsonArray();
        List<String> lines = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                DebugLogger.warn(MODULE, "TabList 配置错误：%s[%d] 必须是字符串，该行已跳过", label, i);
                continue;
            }
            lines.add(item.getAsString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    // ===== 帧集合 =====

    /**
     * 一组模板帧及其切换周期。
     *
     * @param frames     预解析的帧（每帧一个 TextNode，帧内多行已用换行连接）
     * @param changeRate 每多少次刷新切换一帧（至少 1）
     */
    record FrameSet(List<TextNode> frames, int changeRate) {

        static FrameSet empty() {
            return new FrameSet(List.of(TextNode.empty()), 1);
        }

        /** 取指定 tick 对应的帧（全局 tick 计算，无需每玩家状态） */
        TextNode getForTick(int tick, int updateTickTime) {
            if (frames.size() <= 1) {
                return frames.get(0);
            }
            int sendCount = tick / Math.max(updateTickTime, 1);
            return frames.get((sendCount / Math.max(changeRate, 1)) % frames.size());
        }
    }

    /** 每名玩家的 TabList 差量状态。 */
    private static final class PlayerState {
        @Nullable
        private Component lastHeader;
        @Nullable
        private Component lastFooter;
        private boolean dirty = true;
    }
}
