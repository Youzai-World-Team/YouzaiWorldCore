package top.csituka.youzaiworldcore.tablist;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发送固定的 Tab 列表抬头和页脚，抬头动画与服务端 tick 同步，数据按玩家求值。
 */
@SuppressWarnings({ "null", "UnstableApiUsage" })
public final class TabListManager {

    public static final String MODULE = "TabListManager";

    private static final int REFRESH_TICKS = 2;
    private static final int HEADER_FRAME_TICKS = 10;
    private static final NodeParser PARSER = NodeParser.builder()
            .simplifiedTextFormat()
            .serverPlaceholders()
            .staticPreParsing()
            .build();

    private static final String ONLINE_PLAYERS =
            "<color:#555555><strikethrough>        </strikethrough>[ </color><color:#FF5555>%server:online%</color>"
                    + "<color:#666667>/</color><color:#FF5555>%server:max_players%</color>"
                    + "<color:#555555> ]<strikethrough>        </strikethrough></color>";
    private static final List<TextNode> HEADER_FRAMES =
            List.of("0", "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8").stream()
                    .map(phase -> parseLines("", "<rb:1:1:" + phase + "><bold> 悠哉世界 - YouzaiWorld</bold></rb> ⛏ ",
                            "", ONLINE_PLAYERS))
                    .toList();
    private static final String FOOTER_DIVIDER =
            "<color:#555555>          <strikethrough>                          </strikethrough>          </color>";
    private static final TextNode FOOTER = parseLines(
            FOOTER_DIVIDER,
            "<color:#55aaff><bold>交流群号</bold></color> <dark_gray>|</dark_gray> <color:#aaddff>895689642            </color>",
            "<color:#ffff55><bold>游玩地址</bold></color> <dark_gray>|</dark_gray> <color:#ffffaa>play.mcyzw.top</color>",
            "<color:#ffff55><bold>游玩版本</bold></color> <dark_gray>|</dark_gray> <color:#ffffaa>fabric 26.3       </color>",
            FOOTER_DIVIDER,
            "",
            "<gray>TPS: %server:tps_colored% <dark_gray>|</dark_gray> <gray>延迟: <color:#ffba26>%player:ping%</color>ms",
            "");

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    private TabListManager() {
    }

    /** 注册进服初始化、断开清理和按玩家错峰刷新的事件。 */
    public static void initialize() {
        DebugLogger.SERVER.entering(MODULE, "initialize");

        ServerTickEvents.END_SERVER_TICK.register(TabListManager::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.getPlayer() instanceof ServerPlayer player && player.connection != null) {
                DebugLogger.SERVER.trace(MODULE, "玩家加入，立即发送 Tab 抬头 / 页脚: {}", player.getName().getString());
                sendTo(player, server.getTickCount(), STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState()));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                STATES.remove(handler.getPlayer().getUUID()));

        DebugLogger.SERVER.exiting(MODULE, "initialize");
    }

    private static void onServerTick(MinecraftServer server) {
        int tick = server.getTickCount();
        int refreshPhase = Math.floorMod(tick, REFRESH_TICKS);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // 与侧边栏错开一个 tick，分散同一玩家的占位符求值和发包。
            int playerPhase = Math.floorMod(player.getUUID().hashCode() + 1, REFRESH_TICKS);
            if (refreshPhase == playerPhase) {
                sendTo(player, tick, STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState()));
            }
        }
    }

    private static void sendTo(ServerPlayer player, int tick, PlayerState state) {
        try {
            int frame = Math.floorMod(tick / HEADER_FRAME_TICKS, HEADER_FRAMES.size());
            var context = ServerPlaceholderContext.of(player).asParserContext();
            Component header = HEADER_FRAMES.get(frame).toComponent(context);
            Component footer = FOOTER.toComponent(context);
            if (header.equals(state.lastHeader) && footer.equals(state.lastFooter)) {
                return;
            }
            player.connection.send(new ClientboundTabListPacket(header, footer));
            state.lastHeader = header;
            state.lastFooter = footer;
        } catch (Exception e) {
            DebugLogger.SERVER.exception(MODULE, "sendTo(" + player.getName().getString() + ")", e);
        }
    }

    /** 换行时重置格式，避免颜色和删除线延续到下一行。 */
    private static TextNode parseLines(String... lines) {
        return PARSER.parseNode(String.join("\n<r>", lines));
    }

    private static final class PlayerState {
        @Nullable
        private Component lastHeader;
        @Nullable
        private Component lastFooter;
    }
}
