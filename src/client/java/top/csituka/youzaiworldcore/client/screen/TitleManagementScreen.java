package top.csituka.youzaiworldcore.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.render.YzuiMenuPanel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.screen.widget.MenuCloseButton;
import top.csituka.youzaiworldcore.client.title.TitleClientState;
import top.csituka.youzaiworldcore.title.TitleDefinition;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.List;

/** Shift+F 主菜单中的玩家称号管理页。 */
@SuppressWarnings("null")
public final class TitleManagementScreen extends Screen implements YzuiMenuScreen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 420;
    private static final int LIST_TOP = 96;
    private static final int ROW_HEIGHT = 28;
    private static final int ROW_STRIDE = 34;
    private static final int FOOTER_BOTTOM = 52;
    private static final int NAVIGATION_BUTTON_WIDTH = 28;
    private static final int PAGE_LABEL_WIDTH = 60;
    private static final int NAVIGATION_GAP = 6;
    private final Screen parent;
    private int page;
    private int observedRevision = -1;

    public TitleManagementScreen(Screen parent) {
        super(Component.translatable("screen.youzaiworldcore.title_management.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        TitleClientState.requestRefresh();
        rebuildTitleWidgets();
        DebugLogger.info("TitleManagementScreen", "已打开文字称号管理页");
    }

    @Override
    public void tick() {
        super.tick();
        if (observedRevision != TitleClientState.revision())
            rebuildTitleWidgets();
    }

    private void rebuildTitleWidgets() {
        clearWidgets();
        observedRevision = TitleClientState.revision();
        List<TitleDefinition> titles = TitleClientState.ownedDefinitions();
        int pageSize = pageSize();
        int pageCount = Math.max(1, (titles.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pageCount - 1));
        int panelWidth = panelWidth();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight()) / 2;
        int buttonWidth = panelWidth - 48;
        int start = page * pageSize;
        int end = Math.min(titles.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            TitleDefinition title = titles.get(i);
            boolean equipped = title.id().equals(TitleClientState.equippedTitleId());
            Component label = equipped
                    ? Component.literal("✓ ").append(title.asComponent())
                    : title.asComponent();
            TransparentButton button = new TransparentButton(
                    panelX + 24,
                    panelY + listTop() + (i - start) * rowStride(),
                    buttonWidth,
                    rowHeight(),
                    label,
                    () -> TitleClientState.equip(title.id()));
            button.setTextLeftAligned(true);
            button.setTextInsets(12, 12);
            button.setTooltip(Tooltip.create(label));
            button.setTextColor(equipped ? YzuiTheme::primary : YzuiTheme::text);
            button.active = !TitleClientState.loading();
            addRenderableWidget(button);
        }

        // 不佩戴操作固定在底部，不受列表长度和翻页影响。
        int navigationWidth = pageCount > 1 ? navigationWidth() : 0;
        boolean unequipped = TitleClientState.equippedTitleId().isBlank();
        Component noneLabel = Component.translatable("screen.youzaiworldcore.title_management.none");
        if (unequipped) noneLabel = Component.literal("✓ ").append(noneLabel);
        TransparentButton none = new TransparentButton(
                panelX + 24, panelY + footerY(),
                buttonWidth - (navigationWidth == 0 ? 0 : navigationWidth + 12), ROW_HEIGHT,
                noneLabel,
                () -> TitleClientState.equip(""));
        none.setStyle(unequipped ? YzuiTheme.ButtonStyle.TONAL : YzuiTheme.ButtonStyle.TEXT);
        none.setTooltip(Tooltip.create(noneLabel));
        none.active = !TitleClientState.loading();
        addRenderableWidget(none);

        if (pageCount > 1) {
            TransparentButton previous = new TransparentButton(
                    panelX + panelWidth - 24 - navigationWidth, panelY + footerY(),
                    NAVIGATION_BUTTON_WIDTH, ROW_HEIGHT,
                    Component.translatable("gui.recipebook.previous_page"), () -> {
                        page--;
                        rebuildTitleWidgets();
                    }).setLineIcon(MenuIcon.CHEVRON_LEFT);
            previous.active = page > 0;
            addRenderableWidget(previous);
            TransparentButton next = new TransparentButton(
                    panelX + panelWidth - 24 - NAVIGATION_BUTTON_WIDTH, panelY + footerY(),
                    NAVIGATION_BUTTON_WIDTH, ROW_HEIGHT,
                    Component.translatable("gui.recipebook.next_page"), () -> {
                        page++;
                        rebuildTitleWidgets();
                    }).setLineIcon(MenuIcon.CHEVRON_RIGHT);
            next.active = page < pageCount - 1;
            addRenderableWidget(next);
        }

        Component refreshLabel = Component.translatable("screen.youzaiworldcore.title_management.refresh");
        TransparentButton refresh = new TransparentButton(panelX + panelWidth - 116, panelY + 12, 68, 24,
                refreshLabel, TitleClientState::requestRefresh).setStyle(YzuiTheme.ButtonStyle.TEXT);
        refresh.setTooltip(Tooltip.create(refreshLabel));
        refresh.active = !TitleClientState.loading();
        addRenderableWidget(refresh);
        addRenderableWidget(new MenuCloseButton(panelX + panelWidth - 40, panelY + 10, this));
        addRenderableWidget(new TransparentButton(panelX + 12, panelY + 10, 28, 28,
                Component.translatable("gui.back"), () -> Minecraft.getInstance().setScreenAndShow(parent))
                .setLineIcon(MenuIcon.CHEVRON_LEFT)
                .setStyle(YzuiTheme.ButtonStyle.TEXT));
        DebugLogger.debug("TitleManagementScreen", "称号列表：数量=%d，页码=%d/%d，每页=%d",
                titles.size(), page + 1, pageCount, pageSize);
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, width - 24);
    }

    private int panelHeight() {
        return Math.min(PANEL_HEIGHT, height - 24);
    }

    private int pageSize() {
        int available = footerY() - 6 - listTop();
        return Math.max(1, (available + rowStride() - rowHeight()) / rowStride());
    }

    private boolean compact() {
        return panelHeight() < 200;
    }

    private int listTop() {
        return compact() ? 64 : LIST_TOP;
    }

    private int rowHeight() {
        return compact() ? 24 : ROW_HEIGHT;
    }

    private int rowStride() {
        return compact() ? 30 : ROW_STRIDE;
    }

    private int footerY() {
        return panelHeight() - FOOTER_BOTTOM;
    }

    private int navigationWidth() {
        return NAVIGATION_BUTTON_WIDTH * 2 + PAGE_LABEL_WIDTH + NAVIGATION_GAP * 2;
    }

    private int pageCount() {
        int pageSize = pageSize();
        return Math.max(1, (TitleClientState.ownedDefinitions().size() + pageSize - 1) / pageSize);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {

        int panelWidth = panelWidth();
        int panelHeight = panelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        YzuiMenuPanel.card(graphics, panelX, panelY, panelWidth, panelHeight);
        Component title = Component.translatable("screen.youzaiworldcore.title_management.title");
        YzuiTheme.label(graphics, font, title, panelX + 48, panelY + 18, panelWidth - 176, YzuiTheme.text(), false);
        Component current = Component.translatable("screen.youzaiworldcore.title_management.current",
                currentTitleLabel());
        if (compact()) {
            YzuiTheme.label(graphics, font, current, panelX + 24, panelY + 44,
                    panelWidth - 48, YzuiTheme.textMuted(), false);
        } else {
            YzuiTheme.field(graphics, panelX + 24, panelY + 44, panelWidth - 48, 28, false, true, 1f);
            YzuiTheme.label(graphics, font, current, panelX + 36, panelY + 53,
                    panelWidth - 72, YzuiTheme.text(), false);
            Component owned = Component.translatable("screen.youzaiworldcore.title_management.owned")
                    .append(Component.literal(" (" + TitleClientState.ownedDefinitions().size() + ")"));
            YzuiTheme.label(graphics, font, owned, panelX + 24, panelY + 80,
                    panelWidth - 48, YzuiTheme.textMuted(), false);
        }
        if (TitleClientState.loading()) {
            Component loading = Component.translatable("screen.youzaiworldcore.title_management.loading");
            graphics.text(font, loading, panelX + 24, panelY + panelHeight - 16, YzuiTheme.warning(), false);
        } else if (!TitleClientState.message().isBlank()) {
            String message = font.plainSubstrByWidth(TitleClientState.message(), panelWidth - 48);
            graphics.text(font, message, panelX + 24, panelY + panelHeight - 16, YzuiTheme.textMuted(), false);
        } else if (TitleClientState.ownedDefinitions().isEmpty()) {
            Component empty = Component.translatable("screen.youzaiworldcore.title_management.empty");
            YzuiTheme.label(graphics, font, empty, panelX + 24,
                    panelY + listTop() + Math.max(0, (footerY() - listTop() - font.lineHeight) / 2),
                    panelWidth - 48, YzuiTheme.textMuted(), true);
        }
        int pageCount = pageCount();
        if (pageCount > 1) {
            Component pageLabel = Component.literal((page + 1) + " / " + pageCount);
            int labelX = panelX + panelWidth - 24 - navigationWidth() + NAVIGATION_BUTTON_WIDTH + NAVIGATION_GAP;
            YzuiTheme.label(graphics, font, pageLabel, labelX,
                    panelY + footerY() + (ROW_HEIGHT - font.lineHeight) / 2,
                    PAGE_LABEL_WIDTH, YzuiTheme.textMuted(), true);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private Component currentTitleLabel() {
        String id = TitleClientState.equippedTitleId();
        if (id.isBlank())
            return Component.translatable("screen.youzaiworldcore.title_management.none");
        return TitleClientState.ownedDefinitions().stream()
                .filter(title -> title.id().equals(id))
                .findFirst()
                .map(TitleDefinition::asComponent)
                .orElse(Component.literal("{" + id + "}"));
    }

    @Override
    public void onClose() {
        top.csituka.youzaiworldcore.client.animation.GuiAnimationController.setScreenImmediately(
                Minecraft.getInstance().gui, parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
