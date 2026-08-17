package top.csituka.youzaiworldcore.client.screen;

import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.resource.CustomFontResourcePack;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.screen.widget.YzuiStyleOverride;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 首次启动欢迎导览。
 * <p>
 * 导览依次展示欢迎页、YZUI 选择、MCsans 字体选择和完成页。选择只保存在本屏幕内，
 * 最后点击“关闭”时才写入 {@code yzwc/client/global_settings.json} 的
 * {@code core_module} 分节并返回打开导览前的屏幕。
 * </p>
 */
@SuppressWarnings("null")
public final class WelcomeGuideScreen extends Screen {

    private static final String MODULE = "WelcomeGuideScreen";
    private static final int PANEL_MAX_WIDTH = 700;
    private static final int PANEL_MAX_HEIGHT = 440;
    private static final int PANEL_MARGIN = 12;
    private static final int PANEL_RADIUS = 8;
    private static final int CARD_GAP = 10;
    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 24;
    private static final int TEXT_COLOR = 0xFFF4F4F4;
    private static final int MUTED_TEXT_COLOR = 0xFFC8C8C8;
    private static final int ACCENT_COLOR = 0xFF78D89A;

    private static final FontDescription.Resource VANILLA_PREVIEW_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "vanilla_preview"));
    private static final FontDescription.Resource MCSANS_PREVIEW_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "mcsans_preview"));

    private final Screen parentScreen;
    private Page page = Page.WELCOME;
    private boolean yzuiEnabled;
    private boolean customFontEnabled;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int pageTitleY;
    private int bodyY;
    private int bodyMaxLines;

    /**
     * @param parentScreen 打开导览前的屏幕，完成导览后原样返回
     */
    public WelcomeGuideScreen(Screen parentScreen) {
        super(Component.translatable("screen.youzaiworldcore.welcome_guide.title"));
        this.parentScreen = parentScreen;
        this.yzuiEnabled = ClientExternalSettings.isYzuiEnabled();
        this.customFontEnabled = ClientExternalSettings.isCustomFontEnabled();
        DebugLogger.entering(MODULE, "constructor",
                "yzui=" + yzuiEnabled + ", customFont=" + customFontEnabled);
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        calculateLayout();

        int navY = panelY + panelHeight - BUTTON_HEIGHT - 14;
        switch (page) {
            case WELCOME -> addCenteredButton(navY,
                    Component.translatable("screen.youzaiworldcore.welcome_guide.start"),
                    () -> changePage(Page.YZUI));
            case YZUI -> {
                addChoiceCards(PreviewKind.VANILLA_UI, PreviewKind.YZUI, navY);
                addNavigationButtons(navY, Page.WELCOME, Page.FONT);
            }
            case FONT -> {
                addChoiceCards(PreviewKind.VANILLA_FONT, PreviewKind.MCSANS_FONT, navY);
                addNavigationButtons(navY, Page.YZUI, Page.COMPLETE);
            }
            case COMPLETE -> addCenteredButton(navY,
                    Component.translatable("screen.youzaiworldcore.welcome_guide.close"),
                    this::finishGuide);
        }
    }

    private void calculateLayout() {
        panelWidth = Math.max(1, Math.min(PANEL_MAX_WIDTH, width - PANEL_MARGIN * 2));
        panelHeight = Math.max(1, Math.min(PANEL_MAX_HEIGHT, height - PANEL_MARGIN * 2));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        pageTitleY = panelY + 42;
        bodyY = pageTitleY + 18;
        int availableBodyHeight = Math.max(10, panelHeight - 130);
        bodyMaxLines = Math.max(1, Math.min(3, availableBodyHeight / 10));
    }

    private void addCenteredButton(int y, Component message, Runnable action) {
        TransparentButton button = new TransparentButton(
                panelX + (panelWidth - BUTTON_WIDTH) / 2, y,
                BUTTON_WIDTH, BUTTON_HEIGHT, message, action);
        button.setTextColor(0x000000);
        addRenderableWidget(button);
    }

    private void addNavigationButtons(int y, Page previous, Page next) {
        int centerX = panelX + panelWidth / 2;
        TransparentButton backButton = new TransparentButton(
                centerX - BUTTON_WIDTH - 5, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.welcome_guide.back"),
                () -> changePage(previous));
        backButton.setTextColor(0x000000);
        addRenderableWidget(backButton);

        TransparentButton nextButton = new TransparentButton(
                centerX + 5, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.welcome_guide.next"),
                () -> changePage(next));
        nextButton.setTextColor(0x000000);
        addRenderableWidget(nextButton);
    }

    private void addChoiceCards(PreviewKind leftKind, PreviewKind rightKind, int navY) {
        Component body = pageBody();
        int bodyLines = Math.min(bodyMaxLines, font.split(body, panelWidth - 48).size());
        int cardsTop = bodyY + bodyLines * 10 + 9;
        int cardsBottom = navY - 10;
        int cardsHeight = Math.max(48, cardsBottom - cardsTop);
        int cardsWidth = panelWidth - 40;

        if (cardsWidth < 250 && cardsHeight >= 106) {
            int cardHeight = (cardsHeight - CARD_GAP) / 2;
            addPreviewCard(panelX + 20, cardsTop, cardsWidth, cardHeight, leftKind);
            addPreviewCard(panelX + 20, cardsTop + cardHeight + CARD_GAP,
                    cardsWidth, cardHeight, rightKind);
            return;
        }

        int cardWidth = (cardsWidth - CARD_GAP) / 2;
        addPreviewCard(panelX + 20, cardsTop, cardWidth, cardsHeight, leftKind);
        addPreviewCard(panelX + 20 + cardWidth + CARD_GAP, cardsTop,
                cardWidth, cardsHeight, rightKind);
    }

    private void addPreviewCard(int x, int y, int cardWidth, int cardHeight, PreviewKind kind) {
        BooleanSupplier selected = switch (kind) {
            case VANILLA_UI -> () -> !yzuiEnabled;
            case YZUI -> () -> yzuiEnabled;
            case VANILLA_FONT -> () -> !customFontEnabled;
            case MCSANS_FONT -> () -> customFontEnabled;
        };
        Runnable action = switch (kind) {
            case VANILLA_UI -> () -> selectYzui(false);
            case YZUI -> () -> selectYzui(true);
            case VANILLA_FONT -> () -> selectCustomFont(false);
            case MCSANS_FONT -> () -> selectCustomFont(true);
        };
        Button previewButton = null;
        PreviewSlider previewSlider = null;
        if (kind == PreviewKind.VANILLA_UI || kind == PreviewKind.YZUI) {
            boolean useYzuiStyle = kind == PreviewKind.YZUI;
            int innerX = x + 8;
            int innerWidth = Math.max(1, cardWidth - 16);
            int labelCount = Math.min(2, font.split(kind.label(), innerWidth).size());
            int previewTop = y + 12 + labelCount * 10;
            int previewBottom = y + cardHeight - 9;
            int previewHeight = Math.max(1, previewBottom - previewTop);
            int controlsX = innerX + Math.min(10, Math.max(2, innerWidth / 12));
            int controlsWidth = Math.max(1, innerWidth - (controlsX - innerX) * 2);
            int controlsGap = previewHeight >= 46 ? 8 : 3;
            int controlsHeight = Math.max(1, Math.min(20, (previewHeight - controlsGap - 8) / 2));
            int controlsTotalHeight = controlsHeight * 2 + controlsGap;
            int controlsY = previewTop + Math.max(1, (previewHeight - controlsTotalHeight) / 2);

            previewButton = Button.builder(
                    Component.translatable("screen.youzaiworldcore.welcome_guide.preview_button"),
                    button -> action.run())
                    .bounds(controlsX, controlsY, controlsWidth, controlsHeight)
                    .build();
            previewSlider = new PreviewSlider(
                    controlsX, controlsY + controlsHeight + controlsGap,
                    controlsWidth, controlsHeight, action);

            int styleOverride = useYzuiStyle
                    ? YzuiStyleOverride.STYLE_YZUI
                    : YzuiStyleOverride.STYLE_VANILLA;
            YzuiStyleOverride.set(previewButton, styleOverride);
            YzuiStyleOverride.set(previewSlider, styleOverride);

            // 子控件参与输入和无障碍朗读，由预览卡负责按正确层级渲染。
            addWidget(previewButton);
            addWidget(previewSlider);
        }

        addRenderableWidget(new PreviewOptionWidget(
                x, y, cardWidth, cardHeight, kind.label(), selected, action, kind,
                previewButton, previewSlider));
    }

    private void selectYzui(boolean enabled) {
        DebugLogger.stateChange(MODULE, "guide_selection", "yzui_enabled", yzuiEnabled, enabled);
        yzuiEnabled = enabled;
    }

    private void selectCustomFont(boolean enabled) {
        DebugLogger.stateChange(MODULE, "guide_selection", "custom_font_enabled", customFontEnabled, enabled);
        customFontEnabled = enabled;
    }

    private void changePage(Page target) {
        DebugLogger.stateChange(MODULE, "guide", "page", page, target);
        page = target;
        rebuildWidgets();
    }

    private void finishGuide() {
        DebugLogger.entering(MODULE, "finishGuide",
                "yzui=" + yzuiEnabled + ", customFont=" + customFontEnabled);
        ClientExternalSettings.completeWelcomeGuide(yzuiEnabled, customFontEnabled);
        Minecraft client = Minecraft.getInstance();
        client.gui.setScreen(parentScreen);
        CustomFontResourcePack.setEnabled(customFontEnabled);
        DebugLogger.exiting(MODULE, "finishGuide");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        RoundedRect.fillWithBorder(graphics, panelX, panelY, panelWidth, panelHeight,
                PANEL_RADIUS, PANEL_RADIUS - 1, 0x70FFFFFF, 0xD0181B20);

        drawCentered(graphics, title, panelY + 14, TEXT_COLOR);
        Component progress = Component.translatable(
                "screen.youzaiworldcore.welcome_guide.progress", page.index, Page.values().length);
        graphics.text(font, progress,
                panelX + panelWidth - 14 - font.width(progress), panelY + 15,
                MUTED_TEXT_COLOR, false);

        drawCentered(graphics, page.title(), pageTitleY, TEXT_COLOR);
        drawCenteredWrapped(graphics, pageBody(), bodyY, panelWidth - 48, bodyMaxLines, MUTED_TEXT_COLOR);

        if (page == Page.WELCOME) {
            drawWelcomeContent(graphics);
        } else if (page == Page.COMPLETE) {
            drawCompleteContent(graphics);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawWelcomeContent(GuiGraphicsExtractor graphics) {
        int centerY = bodyY + Math.max(38, (panelHeight - 150) / 2);
        RoundedRect.fill(graphics, panelX + panelWidth / 2 - 38, centerY - 22,
                76, 44, 8, 0x8078D89A);
        Component brand = Component.literal("YZWC");
        drawCentered(graphics, brand, centerY - 4, 0xFFFFFFFF);
    }

    private void drawCompleteContent(GuiGraphicsExtractor graphics) {
        int summaryWidth = Math.min(360, panelWidth - 48);
        int summaryX = panelX + (panelWidth - summaryWidth) / 2;
        int summaryY = bodyY + 38;
        RoundedRect.fill(graphics, summaryX, summaryY, summaryWidth, 62, 6, 0x35FFFFFF);

        Component yzuiSummary = Component.translatable(
                "screen.youzaiworldcore.welcome_guide.summary_yzui",
                settingState(yzuiEnabled));
        Component fontSummary = Component.translatable(
                "screen.youzaiworldcore.welcome_guide.summary_font",
                settingState(customFontEnabled));
        graphics.text(font, yzuiSummary, summaryX + 12, summaryY + 15, TEXT_COLOR, false);
        graphics.text(font, fontSummary, summaryX + 12, summaryY + 38, TEXT_COLOR, false);
    }

    private Component settingState(boolean enabled) {
        String suffix = enabled ? "enabled" : "disabled";
        return Component.translatable("screen.youzaiworldcore.welcome_guide." + suffix)
                .withStyle(style -> style.withColor(enabled ? 0x78D89A : 0xB8B8B8));
    }

    private Component pageBody() {
        return Component.translatable(page.bodyKey);
    }

    private void drawCentered(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        graphics.text(font, text, (width - font.width(text)) / 2, y, color, false);
    }

    private void drawCenteredWrapped(GuiGraphicsExtractor graphics, Component text,
            int y, int maxWidth, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.text(font, line, (width - font.width(line)) / 2, y + i * 10, color, false);
        }
    }

    private void renderPreviewCard(GuiGraphicsExtractor graphics, PreviewOptionWidget widget,
            int mouseX, int mouseY, float partialTick) {
        boolean selected = widget.selected.getAsBoolean();
        boolean childHighlighted = widget.previewButton != null
                && (widget.previewButton.isHoveredOrFocused()
                || widget.previewSlider.isHoveredOrFocused());
        boolean highlighted = widget.isHoveredOrFocused() || childHighlighted;
        int border = selected ? ACCENT_COLOR : (highlighted ? 0xC0FFFFFF : 0x50FFFFFF);
        int fill = selected ? 0xC8FFFFFF : (highlighted ? 0x3CFFFFFF : 0x26FFFFFF);
        RoundedRect.fillWithBorder(graphics, widget.getX(), widget.getY(),
                widget.getWidth(), widget.getHeight(), 7, 6, border, fill);

        int innerX = widget.getX() + 8;
        int innerWidth = widget.getWidth() - 16;
        List<FormattedCharSequence> labelLines = font.split(widget.getMessage(), innerWidth);
        int labelCount = Math.min(2, labelLines.size());
        int labelColor = selected ? 0xFF202020 : TEXT_COLOR;
        for (int i = 0; i < labelCount; i++) {
            FormattedCharSequence line = labelLines.get(i);
            graphics.text(font, line,
                    widget.getX() + (widget.getWidth() - font.width(line)) / 2,
                    widget.getY() + 8 + i * 10, labelColor, false);
        }

        int previewTop = widget.getY() + 12 + labelCount * 10;
        int previewBottom = widget.getY() + widget.getHeight() - 9;
        if (previewBottom - previewTop < 24) {
            return;
        }

        switch (widget.kind) {
            case VANILLA_UI, YZUI -> drawUiPreview(graphics, innerX, previewTop, innerWidth,
                    previewBottom - previewTop, widget, mouseX, mouseY, partialTick);
            case VANILLA_FONT -> drawFontPreview(graphics, innerX, previewTop, innerWidth,
                    previewBottom - previewTop, VANILLA_PREVIEW_FONT);
            case MCSANS_FONT -> drawFontPreview(graphics, innerX, previewTop, innerWidth,
                    previewBottom - previewTop, MCSANS_PREVIEW_FONT);
        }
    }

    private void drawUiPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
            PreviewOptionWidget widget, int mouseX, int mouseY, float partialTick) {
        // 两侧使用相同的预览容器，仅由真实按钮和滑块呈现原版/YZUI差异。
        RoundedRect.fill(graphics, x, y, w, h, 5, 0xD0202020);
        widget.previewButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        widget.previewSlider.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawFontPreview(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
            FontDescription.Resource previewFont) {
        RoundedRect.fill(graphics, x, y, w, h, 5, 0xB0181818);
        Component sample = Component.translatable("screen.youzaiworldcore.welcome_guide.font_sample")
                .withStyle(style -> style.withFont(previewFont));
        Component emojiSample = Component.translatable(
                "screen.youzaiworldcore.welcome_guide.font_emoji_sample")
                .withStyle(style -> style.withFont(previewFont));
        int lineGap = 4;
        int totalHeight = font.lineHeight * 2 + lineGap;
        int firstLineY = y + Math.max(3, (h - totalHeight) / 2);
        graphics.enableScissor(x + 3, y + 2, x + w - 3, y + h - 2);
        graphics.text(font, sample, x + Math.max(5, (w - font.width(sample)) / 2),
                firstLineY, 0xFFFFFFFF, false);
        graphics.text(font, emojiSample, x + Math.max(5, (w - font.width(emojiSample)) / 2),
                firstLineY + font.lineHeight + lineGap, 0xFFFFFFFF, false);
        graphics.disableScissor();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x35000000);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum Page {
        WELCOME(1, "welcome_title", "welcome_body"),
        YZUI(2, "yzui_title", "yzui_body"),
        FONT(3, "font_title", "font_body"),
        COMPLETE(4, "complete_title", "complete_body");

        private final int index;
        private final String titleKey;
        private final String bodyKey;

        Page(int index, String titleKey, String bodyKey) {
            this.index = index;
            this.titleKey = "screen.youzaiworldcore.welcome_guide." + titleKey;
            this.bodyKey = "screen.youzaiworldcore.welcome_guide." + bodyKey;
        }

        private Component title() {
            return Component.translatable(titleKey);
        }
    }

    private enum PreviewKind {
        VANILLA_UI("vanilla_ui"),
        YZUI("yzui"),
        VANILLA_FONT("vanilla_font"),
        MCSANS_FONT("mcsans_font");

        private final String labelKey;

        PreviewKind(String labelKey) {
            this.labelKey = "screen.youzaiworldcore.welcome_guide." + labelKey;
        }

        private Component label() {
            return Component.translatable(labelKey);
        }
    }

    private final class PreviewOptionWidget extends AbstractButton {

        private final BooleanSupplier selected;
        private final Runnable onPress;
        private final PreviewKind kind;
        private final Button previewButton;
        private final PreviewSlider previewSlider;

        private PreviewOptionWidget(int x, int y, int width, int height, Component message,
                BooleanSupplier selected, Runnable onPress, PreviewKind kind,
                Button previewButton, PreviewSlider previewSlider) {
            super(x, y, width, height, message);
            this.selected = selected;
            this.onPress = onPress;
            this.kind = kind;
            this.previewButton = previewButton;
            this.previewSlider = previewSlider;
        }

        @Override
        protected void extractContents(
                GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            renderPreviewCard(graphics, this, mouseX, mouseY, partialTick);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            onPress.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
            this.defaultButtonNarrationText(narrationElementOutput);
        }
    }

    /** 使用原版滑块交互，并允许预览卡强制指定原版或 YZUI 渲染。 */
    private final class PreviewSlider extends AbstractSliderButton {

        private final Runnable onChange;

        private PreviewSlider(int x, int y, int width, int height,
                Runnable onChange) {
            super(x, y, width, height, Component.empty(), 0.5D);
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(Math.round(value * 100.0D) + "%"));
        }

        @Override
        protected void applyValue() {
            onChange.run();
        }

    }
}
