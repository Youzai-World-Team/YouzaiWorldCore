package top.csituka.youzaiworldcore.highlightitem;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 高亮物品功能客户端装配（纯指令控制，无 GUI）。
 * <p>
 * 负责：加载配置、注册 2 个键位（F10 切换高亮 / B 切换比较模式）、注册客户端命令。
 * 所有设置通过 {@code /yzwc settings highlight_item} 指令完成；悬停物品本身永不着色。
 */
@SuppressWarnings("null")
public class HighlightItemClient {
    public static final KeyMapping TOGGLE_BIND;
    public static final KeyMapping COMPARATOR_BIND;

    private static final Identifier CATEGORY = Identifier.parse(YouzaiworldCore.MOD_ID + ":highlight");

    static {
        TOGGLE_BIND = new KeyMapping("key.youzaiworldcore.highlight.toggle",
                GLFW.GLFW_KEY_F10, new KeyMapping.Category(CATEGORY));
        COMPARATOR_BIND = new KeyMapping("key.youzaiworldcore.highlight.comparator",
                GLFW.GLFW_KEY_B, new KeyMapping.Category(CATEGORY));
    }

    public static void initialize() {
        DebugLogger.entering("HighlightItem", "initialize");

        // 加载（或生成）配置文件
        try {
            HighlightItem.configurator = new Configurator();
        } catch (Exception e) {
            DebugLogger.exception("HighlightItem", "初始化配置失败", e);
        }

        // 键位已通过静态字段创建（无需额外注册）
        DebugLogger.info("HighlightItem", "键位已创建 (category=%s)", CATEGORY);

        // 注册客户端命令
        new HighLightCommands().register();
        DebugLogger.info("HighlightItem", "客户端命令 /yzwc settings highlight_item 已注册");

        DebugLogger.exiting("HighlightItem", "initialize");
    }

    public static void onClientTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }

        if (TOGGLE_BIND.consumeClick()) {
            DebugLogger.branch("HighlightItem", "键位 F10 切换高亮", true);
            HighlightItem.configurator.updateToggle(player, Configurator.NotificationContext.ON_SCREEN);
        }
        if (COMPARATOR_BIND.consumeClick()) {
            DebugLogger.branch("HighlightItem", "键位 B 切换比较模式", true);
            HighlightItem.configurator.changeMode(player, Configurator.NotificationContext.ON_SCREEN);
        }
    }
}
