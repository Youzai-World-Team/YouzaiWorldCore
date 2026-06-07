package top.csituka.youzaiworldcore.client.screen.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.screen.MenuScreen;
import top.csituka.youzaiworldcore.client.screen.widget.TextureTileButton;

import java.util.ArrayList;
import java.util.List;

public class MainMenuElements implements MenuElementGroup {

    // Tile texture identifiers
    private static final Identifier SWITCH_WORLDS_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/switch-worlds.png");
    private static final Identifier QUESTIONNAIRE_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/questionnaire_application_and_survey.png");
    private static final Identifier TITLE_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/title.png");
    private static final Identifier EVENTS_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/events.png");
    private static final Identifier ABOUT_ME_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/about-me.png");
    private static final Identifier CHECK_IN_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/check-in.png");
    private static final Identifier TUTORIAL_CENTER_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/tutorial_center.png");
    private static final Identifier SETTINGS_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/settings.png");
    private static final Identifier MAIL_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/mail.png");
    private static final Identifier WEBSITE_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/website.png");
    private static final Identifier REPORT_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/report.png");
    private static final Identifier MANAGEMENT_TEXTURE = Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/management.png");

    // Layout constants
    private static final int GAP = 4;
    private static final int GRID_COLS = 5;
    private static final int MAX_TILE_SIZE = 45;
    private static final int MIN_TILE_SIZE = 24;

    @Override
    public String getTitleText() {
        return "主菜单";
    }

    @Override
    public String getSubtitleText() {
        Minecraft client = Minecraft.getInstance();
        String playerName = client.player != null ? client.player.getName().getString() : "Player";
        return "您好，" + playerName + "！欢迎来到悠哉世界";
    }

    @Override
    public boolean isRoot() {
        return true;
    }

    /**
     * Calculate the best tile size that fits the available screen height.
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

        // Dynamic tile size based on available screen height
        int tile = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, (int) (calcTileSize(screenHeight) * scale)));
        int gap = (int) (GAP * scale);
        int tile2 = tile * 2 + gap; // Size spanning 2 columns + internal gap

        // 5-column grid: total width = 5*tile + 4*gap
        int totalGridWidth = tile * GRID_COLS + gap * (GRID_COLS - 1);
        int gridStartX = centerX - totalGridWidth / 2;

        // Column x positions (left edge of each column)
        int c0 = gridStartX;
        int c1 = gridStartX + tile + gap;
        int c2 = gridStartX + 2 * (tile + gap);
        int c3 = gridStartX + 3 * (tile + gap);
        int c4 = gridStartX + 4 * (tile + gap);

        // Grid starts ~10px below subtitle (subtitle at height/2 - 95)
        int gridTop = screenHeight / 2 - 85;
        // Ensure grid doesn't overflow top
        if (gridTop - tile2 < 20) {
            gridTop = 20 + tile2;
        }
        // Ensure grid doesn't overflow bottom
        int gridBottom = gridTop + 3 * (tile + gap) + tile + gap * 2;
        if (gridBottom > screenHeight - 10) {
            gridTop = screenHeight - 10 - (3 * (tile + gap) + tile + gap * 2);
            if (gridTop < 20) gridTop = 20;
        }

        int row0Y = gridTop;
        int row1Y = row0Y + tile + gap;
        int row2Y = row0Y + 2 * (tile + gap);
        int row3Y = row0Y + 3 * (tile + gap);

        // ============================================================
        // ROW 0:
        //   [switch-worlds 2x2] [questionnaire 2x1] [title 1x1]
        //   Col 0-1: switch-worlds (2x2, spans rows 0-1)
        //   Col 2-3: questionnaire (2x1, row 0 only)
        //   Col 4  : title (1x1, row 0 only)
        // ============================================================
        TextureTileButton switchBtn = new TextureTileButton(
                c0, row0Y, tile2, tile2,
                SWITCH_WORLDS_TEXTURE,
                () -> screen.switchTo(new SwitchWorldMenuElements())
        );
        switchBtn.setExternalAlpha(alpha);
        buttons.add(switchBtn);

        TextureTileButton questBtn = new TextureTileButton(
                c2, row0Y, tile2, tile,
                QUESTIONNAIRE_TEXTURE,
                () -> {}
        );
        questBtn.setExternalAlpha(alpha);
        buttons.add(questBtn);

        TextureTileButton titleBtn = new TextureTileButton(
                c4, row0Y, tile, tile,
                TITLE_TEXTURE,
                () -> {}
        );
        titleBtn.setExternalAlpha(alpha);
        buttons.add(titleBtn);

        // ============================================================
        // ROW 1:
        //   [switch cont.] [events 1x1] [about-me 2x2]
        //   Col 0-1: switch-worlds continues
        //   Col 2  : events (1x1, row 1 only)
        //   Col 3-4: about-me (2x2, spans rows 1-2)
        //
        // IMPORTANT: about-me added FIRST so events renders ON TOP
        // ============================================================
        TextureTileButton aboutMeBtn = new TextureTileButton(
                c3, row1Y, tile2, tile2,
                ABOUT_ME_TEXTURE,
                () -> screen.switchTo(new AboutMeMenuElements())
        );
        aboutMeBtn.setExternalAlpha(alpha);
        buttons.add(aboutMeBtn);

        TextureTileButton eventsBtn = new TextureTileButton(
                c2, row1Y, tile, tile,
                EVENTS_TEXTURE,
                () -> {}
        );
        eventsBtn.setExternalAlpha(alpha);
        buttons.add(eventsBtn);

        // ============================================================
        // ROW 2:
        //   [check-in 1x1] [tutorial 2x1] [about-me cont.]
        //   Col 0  : check-in (1x1, row 2)
        //   Col 1-2: tutorial (2x1, row 2 only)
        //   Col 3-4: about-me continues
        // ============================================================
        TextureTileButton checkInBtn = new TextureTileButton(
                c0, row2Y, tile, tile,
                CHECK_IN_TEXTURE,
                () -> {}
        );
        checkInBtn.setExternalAlpha(alpha);
        buttons.add(checkInBtn);

        TextureTileButton tutorialBtn = new TextureTileButton(
                c1, row2Y, tile2, tile,
                TUTORIAL_CENTER_TEXTURE,
                () -> {}
        );
        tutorialBtn.setExternalAlpha(alpha);
        buttons.add(tutorialBtn);

        // ============================================================
        // ROW 3: [settings] [mail] [website] [report] [management]
        //   5 buttons (1x1 each), one per column
        // ============================================================
        Identifier[] bottomTextures = new Identifier[]{
                SETTINGS_TEXTURE, MAIL_TEXTURE, WEBSITE_TEXTURE, REPORT_TEXTURE, MANAGEMENT_TEXTURE
        };
        int[] colXs = new int[]{c0, c1, c2, c3, c4};

        for (int i = 0; i < 5; i++) {
            TextureTileButton bottomBtn = new TextureTileButton(
                    colXs[i], row3Y, tile, tile,
                    bottomTextures[i],
                    () -> {}
            );
            bottomBtn.setExternalAlpha(alpha);
            buttons.add(bottomBtn);
        }

        return buttons;
    }

    @Override
    public void renderCustomContent(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight, float alpha, float xOffset, int mouseX, int mouseY) {
        // No additional decorations
    }
}