package top.csituka.youzaiworldcore.client.screen.element;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.TextureTileButton;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolTeleportPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 切换世界菜单 — 宫格磁贴布局
 *
 * 按钮排列分为 4 行，呈宫格布局，按钮大小类似于 Windows 10 开始菜单磁贴，
 * 尺寸支持 1*1、1*2、2*1、2*2，使用贴图填充按钮（带圆角），
 * 所有行两侧对齐，间距合理，参考主菜单(MainMenuElements)的写法。
 *
 * <p>前 7 个按钮（生存世界/王城/玩法/创造/建筑/指令区/教程世界）已集成维度池传送系统：
 * 点击时发送 {@link WorldPoolTeleportPayload} 到服务端进行维度池传送。
 * 其余按钮（下界/末地/主世界/登录大厅）保留旧有的聊天提示行为。</p>
 *
 * 5列布局：
 * ┌─────────────┬──────────┬──────────┬──────────┐
 * │ survival_world (2×2)   │ kingdom  │ gameplay │ creative │
 * │                       │ (1×2)    │ (1×2)    │ (1×1)    │
 * ├──────┬──────┤          │          ├──────────┤
 * │      │      │          │          │ building │
 * │      │      │          │          │ (1×1)    │
 * ├──────┼──────┤          ├──────────┴──────────┤
 * │nether│ end  │ command_zone (1×2)│ tutorials_world (2×2) │
 * │(1×1) │(1×1) │          │                       │
 * ├──────┼──────┤          │                       │
 * │over- │login │          │                       │
 * │world │_hall │          │                       │
 * │(1×1) │(1×1) │          │                       │
 * └──────┴──────┴──────────┴───────────────────────┘
 */
public class SwitchWorldMenuElements implements MenuElementGroup {

    // ========== 维度池映射（按钮标识 -> 维度池 ID） ==========
    private static final Map<String, String> POOL_MAP = Map.of(
            "survival",   "youzaiworldcore_survival_world_pool",
            "kingdom",    "youzaiworldcore_main_city_pool",
            "gameplay",   "youzaiworldcore_gameplay_pool",
            "creative",   "youzaiworldcore_creation_pool",
            "building",   "youzaiworldcore_building_pool",
            "command",    "youzaiworldcore_commands_pool",
            "marketplace","youzaiworldcore_tutorial_world_pool"
    );

    // ========== 贴图标识符 ==========
    // 以下贴图均放在 textures/gui/ 下，有些尚未放入，但照样引用
    /** 生存世界（2*2） */
    private static final Identifier SURVIVAL_WORLD_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/survival_world.png");
    /** 王城（1*2） */
    private static final Identifier KINGDOM_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/kingdom.png");
    /** 玩法（1*2） */
    private static final Identifier GAMEPLAY_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/gameplay.png");
    /** 创造（1*1） */
    private static final Identifier CREATIVE_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/creative.png");
    /** 建筑（1*1） */
    private static final Identifier BUILDING_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/building.png");
    /** 下界（1*1） */
    private static final Identifier THE_NETHER_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/the_nether.png");
    /** 末地（1*1） */
    private static final Identifier THE_END_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/the_end.png");
    /** 指令区（1*2） */
    private static final Identifier COMMAND_ZONE_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/command_zone.png");
    /** 教程世界（2*2） */
    private static final Identifier TUTORIALS_WORLD_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/tutorials_world.png");
    /** 主世界（1*1） */
    private static final Identifier OVERWORLD_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/overworld.png");
    /** 登录大厅（1*1） */
    private static final Identifier LOGIN_HALL_TEXTURE =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/login_hall.png");

    // ========== 布局常量 ==========
    private static final int GAP = 4;
    private static final int GRID_COLS = 5;
    private static final int MAX_TILE_SIZE = 45;
    private static final int MIN_TILE_SIZE = 24;

    @Override
    public String getTitleText() {
        return I18n.get("youzaiworldcore.message.gui.title_switch_world");
    }

    @Override
    public String getSubtitleText() {
        Minecraft client = Minecraft.getInstance();
        String worldId = I18n.get("youzaiworldcore.message.gui.unknown");
        if (client.level != null) {
            worldId = client.level.dimension().identifier().toString();
        }
        return I18n.get("youzaiworldcore.message.gui.subtitle_switch_world", worldId);
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    /**
     * 根据屏幕高度计算最佳磁贴尺寸
     */
    private int calcTileSize(int screenHeight) {
        int gridStartY = screenHeight / 2 - 85;
        int availableHeight = screenHeight - gridStartY - 20;
        int tile = (availableHeight - 3 * GAP) / 4;
        return Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, tile));
    }

    @Override
    public List<AbstractWidget> createButtons(MenuScreen screen, int screenWidth, int screenHeight, float scale, float alpha) {
        List<AbstractWidget> buttons = new ArrayList<>();

        int centerX = screenWidth / 2;

        // 动态磁贴尺寸
        int tile = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, (int) (calcTileSize(screenHeight) * scale)));
        int gap = (int) (GAP * scale);
        int tile2 = tile * 2 + gap; // 跨两列的宽度 / 两行的高度

        // 5列网格总宽度
        int totalGridWidth = tile * GRID_COLS + gap * (GRID_COLS - 1);
        int gridStartX = centerX - totalGridWidth / 2;

        // 各列左边界
        int c0 = gridStartX;
        int c1 = gridStartX + tile + gap;
        int c2 = gridStartX + 2 * (tile + gap);
        int c3 = gridStartX + 3 * (tile + gap);
        int c4 = gridStartX + 4 * (tile + gap);

        // 网格顶部起始 Y
        int gridTop = screenHeight / 2 - 85;
        if (gridTop - tile2 < 20) {
            gridTop = 20 + tile2;
        }
        int gridBottom = gridTop + 3 * (tile + gap) + tile + gap * 2;
        if (gridBottom > screenHeight - 10) {
            gridTop = screenHeight - 10 - (3 * (tile + gap) + tile + gap * 2);
            if (gridTop < 20) gridTop = 20;
        }

        int row0Y = gridTop;
        int row1Y = row0Y + tile + gap;
        int row2Y = row0Y + 2 * (tile + gap);
        int row3Y = row0Y + 3 * (tile + gap);

        // ================================================================
        // ROW 0（第一行）：
        //   [survival_world 2x2]  [kingdom 1x2]  [gameplay 1x2]  [creative 1x1]
        //   列 0-1: survival_world（2x2，跨行 0-1）
        //   列 2  : kingdom（1x2，跨行 0-1）
        //   列 3  : gameplay（1x2，跨行 0-1）
        //   列 4  : creative（1x1，仅行 0）
        // ================================================================
        /* 生存世界 【survival_world.png】 */
        TextureTileButton survivalBtn = new TextureTileButton(
                c0, row0Y, tile2, tile2,
                SURVIVAL_WORLD_TEXTURE,
                () -> requestPoolTeleport(screen, "survival")
        );
        survivalBtn.setExternalAlpha(alpha);
        buttons.add(survivalBtn);

        /* 王城 【kingdom.png】 */
        TextureTileButton kingdomBtn = new TextureTileButton(
                c2, row0Y, tile, tile2,
                KINGDOM_TEXTURE,
                () -> requestPoolTeleport(screen, "kingdom")
        );
        kingdomBtn.setExternalAlpha(alpha);
        buttons.add(kingdomBtn);

        /* 玩法 【gameplay.png】 */
        TextureTileButton gameplayBtn = new TextureTileButton(
                c3, row0Y, tile, tile2,
                GAMEPLAY_TEXTURE,
                () -> requestPoolTeleport(screen, "gameplay")
        );
        gameplayBtn.setExternalAlpha(alpha);
        buttons.add(gameplayBtn);

        /* 创造 【creative.png】 */
        TextureTileButton creativeBtn = new TextureTileButton(
                c4, row0Y, tile, tile,
                CREATIVE_TEXTURE,
                () -> requestPoolTeleport(screen, "creative")
        );
        creativeBtn.setExternalAlpha(alpha);
        buttons.add(creativeBtn);

        // ================================================================
        // ROW 1（第二行）：
        //   [survival_world 续]  [kingdom 续]  [gameplay 续]  [building 1x1]
        //   列 4  : building（1x1，仅行 1，在 creative 下方）
        // ================================================================
        /* 建筑 【building.png】 */
        TextureTileButton buildingBtn = new TextureTileButton(
                c4, row1Y, tile, tile,
                BUILDING_TEXTURE,
                () -> requestPoolTeleport(screen, "building")
        );
        buildingBtn.setExternalAlpha(alpha);
        buttons.add(buildingBtn);

        // ================================================================
        // ROW 2（第三行）：
        //   [the_nether 1x1]  [the_end 1x1]  [command_zone 1x2]  [tutorials_world 2x2]
        //   列 0  : the_nether（1x1，位于 survival_world 正下方左侧）
        //   列 1  : the_end（1x1，紧邻 the_nether 右侧）
        //   列 2  : command_zone（1x2，位于 kingdom 正下方，跨行 2-3）
        //   列 3-4: tutorials_world（2x2，位于 gameplay+building 下方，跨行 2-3）
        //   注意：tutorials_world 先添加，确保行 2-3 不互相遮挡
        // ================================================================
        /* 教程世界 【tutorials_world.png】（2*2，先添加） */
        TextureTileButton tutorialsWorldBtn = new TextureTileButton(
                c3, row2Y, tile2, tile2,
                TUTORIALS_WORLD_TEXTURE,
                () -> requestPoolTeleport(screen, "marketplace")
        );
        tutorialsWorldBtn.setExternalAlpha(alpha);
        buttons.add(tutorialsWorldBtn);

        /* 下界 【the_nether.png】 */
        TextureTileButton theNetherBtn = new TextureTileButton(
                c0, row2Y, tile, tile,
                THE_NETHER_TEXTURE,
                () -> showTeleportDialog(screen, "nether")
        );
        theNetherBtn.setExternalAlpha(alpha);
        buttons.add(theNetherBtn);

        /* 末地 【the_end.png】 */
        TextureTileButton theEndBtn = new TextureTileButton(
                c1, row2Y, tile, tile,
                THE_END_TEXTURE,
                () -> showTeleportDialog(screen, "end")
        );
        theEndBtn.setExternalAlpha(alpha);
        buttons.add(theEndBtn);

        /* 指令区 【command_zone.png】（1*2，跨行 2-3） */
        TextureTileButton commandZoneBtn = new TextureTileButton(
                c2, row2Y, tile, tile2,
                COMMAND_ZONE_TEXTURE,
                () -> requestPoolTeleport(screen, "command")
        );
        commandZoneBtn.setExternalAlpha(alpha);
        buttons.add(commandZoneBtn);

        // ================================================================
        // ROW 3（第四行）：
        //   [overworld 1x1]  [login_hall 1x1]  [command_zone 续]  [tutorials_world 续]
        //   列 0  : overworld（1x1）
        //   列 1  : login_hall（1x1）
        //   列 2  : command_zone 续行
        //   列 3-4: tutorials_world 续行
        // ================================================================
        /* 主世界 【overworld.png】 */
        TextureTileButton overworldBtn = new TextureTileButton(
                c0, row3Y, tile, tile,
                OVERWORLD_TEXTURE,
                () -> showTeleportDialog(screen, "overworld")
        );
        overworldBtn.setExternalAlpha(alpha);
        buttons.add(overworldBtn);

        /* 登录大厅 【login_hall.png】 */
        TextureTileButton loginHallBtn = new TextureTileButton(
                c1, row3Y, tile, tile,
                LOGIN_HALL_TEXTURE,
                () -> showTeleportDialog(screen, "login")
        );
        loginHallBtn.setExternalAlpha(alpha);
        buttons.add(loginHallBtn);

        return buttons;
    }

    /**
     * 发送维度池传送请求（7 个维度池按钮使用此方法）。
     * <p>
     * 通过 Fabric 网络包 {@link WorldPoolTeleportPayload} 向服务端发送目标池 ID，
     * 服务端接收后由 {@link top.csituka.youzaiworldcore.dimensionalinventories.DimensionPoolManager}
     * 执行完整的池切换流程。
     */
    private void requestPoolTeleport(MenuScreen screen, String buttonId) {
        String poolId = POOL_MAP.get(buttonId);
        if (poolId == null) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(
                        Component.literal("§c未知的维度池按钮: " + buttonId));
            }
            return;
        }

        ConfirmationDialog dialog = new ConfirmationDialog(
                I18n.get("youzaiworldcore.message.gui.confirm_teleport_title"),
                new String[]{I18n.get("youzaiworldcore.message.gui.confirm_teleport_msg1"),
                        I18n.get("youzaiworldcore.message.gui.confirm_teleport_msg2")},
                () -> {
                    ClientPlayNetworking.send(new WorldPoolTeleportPayload(poolId));
                    Minecraft.getInstance().gui.setScreen(null);
                },
                null
        );
        screen.showDialog(dialog);
    }

    /**
     * 显示传送确认对话框（非维度池按钮使用，仅输出聊天提示）。
     * 保留给下界/末地/主世界/登录大厅等旧按钮。
     */
    private void showTeleportDialog(MenuScreen screen, String worldId) {
        ConfirmationDialog dialog = new ConfirmationDialog(
                I18n.get("youzaiworldcore.message.gui.confirm_teleport_title"),
                new String[]{I18n.get("youzaiworldcore.message.gui.confirm_teleport_msg1"), I18n.get("youzaiworldcore.message.gui.confirm_teleport_msg2")},
                () -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.connection.sendCommand("say 传送" + worldId);
                    }
                    Minecraft.getInstance().gui.setScreen(null);
                },
                null
        );
        screen.showDialog(dialog);
    }
}