package top.csituka.youzaiworldcore.highlightitem;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 高亮物品功能客户端装配（纯指令控制，无 GUI）。
 * <p>
 * 负责：加载配置、向原版控制设置注册 2 个键位（F10 切换高亮 / 比较模式默认未绑定）、注册客户端命令。
 * 所有设置通过 {@code /yzwc settings highlight_item} 指令完成；悬停物品本身永不着色。
 */
@SuppressWarnings("null")
public class HighlightItemClient {
    public static final KeyMapping TOGGLE_BIND;
    public static final KeyMapping COMPARATOR_BIND;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category
            .register(Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "highlight"));

    static {
        TOGGLE_BIND = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.youzaiworldcore.highlight.toggle",
                InputConstants.KEY_F10, CATEGORY));
        COMPARATOR_BIND = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.youzaiworldcore.highlight.comparator",
                InputConstants.UNKNOWN.getValue(), CATEGORY));
    }

    public static void initialize() {
        DebugLogger.entering("HighlightItem", "initialize");

        try {
            HighlightItem.configurator = new Configurator();
        } catch (Exception e) {
            DebugLogger.exception("HighlightItem", "初始化配置失败", e);
        }

        // 静态装配时经 Fabric 注册一次，原版负责显示、改键、重置和保存。
        DebugLogger.info("HighlightItem", "高亮键位已注册到原版控制设置：切换高亮默认 F10，比较模式默认未绑定 (category=%s)", CATEGORY);

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
            DebugLogger.branch("HighlightItem", "可配置按键切换高亮", true);
            HighlightItem.configurator.updateToggle(player, Configurator.NotificationContext.ON_SCREEN);
        }
        if (COMPARATOR_BIND.consumeClick()) {
            DebugLogger.branch("HighlightItem", "可配置按键切换比较模式", true);
            HighlightItem.configurator.changeMode(player, Configurator.NotificationContext.ON_SCREEN);
        }
    }
}
