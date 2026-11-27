package top.csituka.youzaiworldcore.sidebar;

import eu.pb4.placeholders.api.ServerPlaceholderContext;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.NodeParser;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
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
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过原版计分板数据包显示固定侧边栏，按玩家求值占位符并推进标题动画。
 */
@SuppressWarnings({ "null", "UnstableApiUsage" })
public final class SidebarManager {

    public static final String MODULE = "SidebarManager";
    public static final String OBJECTIVE_NAME = "yzwc_sidebar";

    private static final int REFRESH_TICKS = 2;
    private static final String SLOT_PREFIX = OBJECTIVE_NAME + "_";
    private static final Scoreboard SCOREBOARD = new Scoreboard();

    private static final NodeParser PARSER = NodeParser.builder()
            .simplifiedTextFormat()
            .serverPlaceholders()
            .staticPreParsing()
            .build();
    private static final List<TextNode> TITLE_FRAMES = createTitleFrames();
    private static final String DIVIDER = "<color:#345e54>---------------------------------</color>";
    private static final List<TextNode> ROWS = List.of(
            "<color:#a8e6cf>ヾ(≧▽≦*)o 欢迎回来，</color><gold>%player:name_visual%</gold><color:#a8e6cf>！</color>",
            "<color:#a8e6cf>🌍> 当前维度：</color><color:#6bb39b>%world:name%</color>",
            "<color:#a8e6cf>🕒> </color><color:#6bb39b>%server:time yyyy/MM/dd%</color>"
                    + "<color:#a8e6cf>  |  </color><color:#6bb39b>%server:time HH:mm:ss%</color>",
            DIVIDER,
            "<color:#a8e6cf>👥> 在线玩家：</color><color:#6bb39b>%server:online% / %server:max_players%</color>"
                    + "<color:#a8e6cf> 人</color>",
            "<color:#a8e6cf>⚡> TPS：</color><color:#6bb39b>%server:tps_colored%</color>"
                    + "<color:#a8e6cf>  |  MSPT：</color><color:#6bb39b>%server:mspt_colored%</color>",
            "<color:#a8e6cf>📶> Ping：</color><color:#ffffff>%player:ping_colored%</color><color:#a8e6cf> ms</color>",
            DIVIDER,
            "<color:#6bb39b>play.mcyzw.top</color>"
    ).stream().map(PARSER::parseNode).toList();

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    private SidebarManager() {
    }

    /** 注册进服初始化、断开清理和按玩家错峰刷新的事件。 */
    public static void initialize() {
        DebugLogger.SERVER.entering(MODULE, "initialize");

        ServerTickEvents.END_SERVER_TICK.register(SidebarManager::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.getPlayer() instanceof ServerPlayer player && player.connection != null) {
                DebugLogger.SERVER.trace(MODULE, "玩家加入，立即发送侧边栏: {}", player.getName().getString());
                render(player, STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState()));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                STATES.remove(handler.getPlayer().getUUID()));

        DebugLogger.SERVER.exiting(MODULE, "initialize");
    }

    private static void onServerTick(MinecraftServer server) {
        int refreshPhase = Math.floorMod(server.getTickCount(), REFRESH_TICKS);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int playerPhase = Math.floorMod(player.getUUID().hashCode(), REFRESH_TICKS);
            if (refreshPhase == playerPhase) {
                render(player, STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState()));
            }
        }
    }

    private static void render(ServerPlayer player, PlayerState state) {
        try {
            var context = ServerPlaceholderContext.of(player).asParserContext();
            Component title = TITLE_FRAMES.get(state.titleFrame).toComponent(context);
            state.titleFrame = (state.titleFrame + 1) % TITLE_FRAMES.size();

            if (state.lastTitle == null) {
                Objective objective = newObjective(title);
                player.connection.send(new ClientboundSetObjectivePacket(objective,
                        ClientboundSetObjectivePacket.METHOD_ADD));
                player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
            } else if (!title.equals(state.lastTitle)) {
                player.connection.send(new ClientboundSetObjectivePacket(newObjective(title),
                        ClientboundSetObjectivePacket.METHOD_CHANGE));
            }
            state.lastTitle = title;

            for (int i = 0; i < ROWS.size(); i++) {
                Component row = ROWS.get(i).toComponent(context);
                if (!row.equals(state.lastRows[i])) {
                    player.connection.send(new ClientboundSetScorePacket(SLOT_PREFIX + i, OBJECTIVE_NAME,
                            ROWS.size() - i, Optional.of(row), Optional.of(BlankFormat.INSTANCE)));
                    state.lastRows[i] = row;
                }
            }
        } catch (Exception e) {
            DebugLogger.SERVER.exception(MODULE, "render(" + player.getName().getString() + ")", e);
        }
    }

    private static Objective newObjective(Component title) {
        return new Objective(SCOREBOARD, OBJECTIVE_NAME, ObjectiveCriteria.DUMMY, title,
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
    }

    private static List<TextNode> createTitleFrames() {
        List<String> frames = new ArrayList<>();
        appendTitleFrames(frames, "YouzaiWorld");
        appendTitleFrames(frames, "悠哉世界");
        return frames.stream().map(frame -> PARSER.parseNode("✨> " + frame + " <✨")).toList();
    }

    /** 逐字展开、白色扫光、金色闪亮、退场，然后留四帧空档。 */
    private static void appendTitleFrames(List<String> frames, String title) {
        String mint = "<color:#a8e6cf>%s</color>";
        for (int i = 1; i < title.length(); i++) {
            frames.add(mint.formatted(title.substring(0, i)));
        }

        String gradient = "<gradient:#a8e6cf:#6bb39b>" + title + "</gradient>";
        frames.addAll(Collections.nCopies(3, gradient));
        for (int i = 0; i < title.length(); i++) {
            frames.add(mint.formatted(title.substring(0, i))
                    + "<color:#ffffff>" + title.charAt(i) + "</color>"
                    + mint.formatted(title.substring(i + 1)));
        }
        frames.addAll(Collections.nCopies(3, gradient));
        frames.addAll(Collections.nCopies(2, "<gold>" + title + "</gold>"));
        frames.addAll(Collections.nCopies(3, gradient));

        for (int i = title.length() - 1; i >= 1; i--) {
            frames.add(mint.formatted(title.substring(0, i)));
        }
        frames.addAll(Collections.nCopies(2, mint.formatted(title.substring(0, 1))));
        frames.addAll(Collections.nCopies(4, ""));
    }

    private static final class PlayerState {
        private int titleFrame;
        @Nullable
        private Component lastTitle;
        private final Component[] lastRows = new Component[ROWS.size()];
    }
}
