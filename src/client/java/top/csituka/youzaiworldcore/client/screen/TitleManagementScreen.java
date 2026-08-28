package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.title.TitleClientState;
import top.csituka.youzaiworldcore.title.TitleDefinition;

import java.util.List;

/** Shift+F 主菜单中的玩家称号管理页。 */
@SuppressWarnings("null")
public final class TitleManagementScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 300;
    private static final int LIST_TOP = 62;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_STRIDE = 29;
    private static final int PAGINATION_BOTTOM = 50;
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
    }

    @Override
    public void tick() {
        super.tick();
        if (observedRevision != TitleClientState.revision()) rebuildTitleWidgets();
    }

    private void rebuildTitleWidgets() {
        clearWidgets();
        observedRevision = TitleClientState.revision();
        List<TitleDefinition> titles = TitleClientState.ownedDefinitions();
        int pageSize = pageSize();
        int pageCount = Math.max(1, (titles.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pageCount - 1));
        int panelX = (width - Math.min(PANEL_WIDTH, width - 24)) / 2;
        int panelY = (height - Math.min(PANEL_HEIGHT, height - 24)) / 2;
        int panelWidth = Math.min(PANEL_WIDTH, width - 24);
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
                    panelY + LIST_TOP + (i - start) * ROW_STRIDE,
                    buttonWidth,
                    ROW_HEIGHT,
                    label,
                    () -> TitleClientState.equip(title.id()));
            button.setTextColor(equipped ? 0xFF106B35 : 0xFF202020);
            button.active = !TitleClientState.loading();
            addRenderableWidget(button);
        }

        TransparentButton none = new TransparentButton(
                panelX + 24, panelY + LIST_TOP + pageSize * ROW_STRIDE,
                buttonWidth, ROW_HEIGHT,
                Component.translatable("screen.youzaiworldcore.title_management.none"),
                () -> TitleClientState.equip(""));
        none.setTextColor(TitleClientState.equippedTitleId().isBlank() ? 0xFF106B35 : 0xFF202020);
        none.active = !TitleClientState.loading();
        addRenderableWidget(none);

        if (pageCount > 1) {
            TransparentButton previous = new TransparentButton(
                    panelX + 24, panelY + panelHeight() - PAGINATION_BOTTOM, 70, 20,
                    Component.literal("←"), () -> { page--; rebuildTitleWidgets(); });
            previous.active = page > 0;
            addRenderableWidget(previous);
            TransparentButton next = new TransparentButton(
                    panelX + panelWidth - 94, panelY + panelHeight() - PAGINATION_BOTTOM, 70, 20,
                    Component.literal("→"), () -> { page++; rebuildTitleWidgets(); });
            next.active = page < pageCount - 1;
            addRenderableWidget(next);
        }

        TransparentButton close = new TransparentButton(
                panelX + panelWidth - 42, panelY + 12, 26, 20,
                Component.literal("×"), this::onClose);
        close.setTextColor(0xFFFFFFFF);
        close.setBackgroundVisible(false);
        addRenderableWidget(close);
    }

    private int panelHeight() {
        return Math.min(PANEL_HEIGHT, height - 24);
    }

    private int pageSize() {
        int paginationY = panelHeight() - PAGINATION_BOTTOM;
        return Math.max(1, (paginationY - LIST_TOP - ROW_HEIGHT) / ROW_STRIDE);
    }

    private int pageCount() {
        int pageSize = pageSize();
        return Math.max(1, (TitleClientState.ownedDefinitions().size() + pageSize - 1) / pageSize);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        int panelWidth = Math.min(PANEL_WIDTH, width - 24);
        int panelHeight = panelHeight();
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;
        RoundedRect.fillWithBorder(graphics, panelX, panelY, panelWidth, panelHeight,
                8, 1, 0x60FFFFFF, 0xE6171717);
        Component title = Component.translatable("screen.youzaiworldcore.title_management.title");
        graphics.text(font, title, panelX + 24, panelY + 18, 0xFFFFFFFF, false);
        Component current = Component.translatable("screen.youzaiworldcore.title_management.current",
                currentTitleLabel());
        graphics.enableScissor(panelX + 24, panelY + 35, panelX + panelWidth - 24, panelY + 51);
        graphics.text(font, current, panelX + 24, panelY + 39, 0xFFB8B8B8, false);
        graphics.disableScissor();
        if (TitleClientState.loading()) {
            Component loading = Component.translatable("screen.youzaiworldcore.title_management.loading");
            graphics.text(font, loading, panelX + 24, panelY + panelHeight - 16, 0xFFFFAA00, false);
        } else if (!TitleClientState.message().isBlank()) {
            String message = font.plainSubstrByWidth(TitleClientState.message(), panelWidth - 48);
            graphics.text(font, message, panelX + 24, panelY + panelHeight - 16, 0xFFAAAAAA, false);
        } else if (TitleClientState.ownedDefinitions().isEmpty()) {
            Component empty = Component.translatable("screen.youzaiworldcore.title_management.empty");
            graphics.text(font, empty, panelX + (panelWidth - font.width(empty)) / 2,
                    panelY + panelHeight / 2, 0xFFAAAAAA, false);
        }
        int pageCount = pageCount();
        if (pageCount > 1) {
            Component pageLabel = Component.literal((page + 1) + " / " + pageCount);
            graphics.text(font, pageLabel, panelX + (panelWidth - font.width(pageLabel)) / 2,
                    panelY + panelHeight - PAGINATION_BOTTOM + 6, 0xFFB8B8B8, false);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private Component currentTitleLabel() {
        String id = TitleClientState.equippedTitleId();
        if (id.isBlank()) return Component.translatable("screen.youzaiworldcore.title_management.none");
        return TitleClientState.ownedDefinitions().stream()
                .filter(title -> title.id().equals(id))
                .findFirst()
                .map(TitleDefinition::asComponent)
                .orElse(Component.literal(id));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
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
