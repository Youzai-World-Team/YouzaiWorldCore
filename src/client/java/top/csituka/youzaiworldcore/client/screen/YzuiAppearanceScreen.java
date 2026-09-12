package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.config.YzuiThemeMode;
import top.csituka.youzaiworldcore.client.config.YzuiVisualStyle;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

/** 界面外观设置与实时预览，所有选择仅保存在本机。 */
public final class YzuiAppearanceScreen extends Screen {
    private final Screen parent;
    private int panelX, panelY, panelWidth, panelHeight;

    public YzuiAppearanceScreen(Screen parent) {
        super(Component.translatable("screen.youzaiworldcore.appearance.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(440, width - 32);
        panelHeight = 284;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int gap = 8, inner = panelWidth - 32;
        int themeWidth = (inner - gap) / 2;
        for (YzuiThemeMode mode : YzuiThemeMode.values()) {
            var button = new TransparentButton(panelX + 16 + mode.ordinal() * (themeWidth + gap),
                    panelY + 48, themeWidth, 26, text("theme." + mode.name().toLowerCase(java.util.Locale.ROOT)), () -> {
                        ClientExternalSettings.setYzuiTheme(mode);
                        rebuildWidgets();
                    });
            button.setStyle(ClientExternalSettings.getYzuiTheme() == mode
                    ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
            addRenderableWidget(button);
        }
        for (YzuiVisualStyle style : YzuiVisualStyle.values()) {
            var button = new TransparentButton(panelX + 16, panelY + 95 + style.ordinal() * 30,
                    inner, 25, text("style." + style.name().toLowerCase(java.util.Locale.ROOT)), () -> {
                        ClientExternalSettings.setYzuiVisualStyle(style);
                        rebuildWidgets();
                    });
            button.setStyle(ClientExternalSettings.getYzuiVisualStyle() == style
                    ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
            addRenderableWidget(button);
        }
        addRenderableWidget(new TransparentButton(panelX + panelWidth - 96, panelY + panelHeight - 36,
                80, 24, Component.translatable("gui.done"), this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        YzuiTheme.card(g, panelX, panelY, panelWidth, panelHeight);
        g.text(font, getTitle(), panelX + 16, panelY + 16, YzuiTheme.text(), false);
        g.text(font, text("theme"), panelX + 16, panelY + 34, YzuiTheme.textMuted(), false);
        g.text(font, text("effects"), panelX + 16, panelY + 81, YzuiTheme.textMuted(), false);
        String selected = ClientExternalSettings.getYzuiVisualStyle().name().toLowerCase(java.util.Locale.ROOT);
        int descriptionY = panelY + 188;
        var lines = font.split(text("style." + selected + ".description"), panelWidth - 32);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.text(font, lines.get(i), panelX + 16, descriptionY + i * 11, YzuiTheme.textMuted(), false);
        }
        int[] colors = { YzuiTheme.primary(), YzuiTheme.primaryContainer(), YzuiTheme.secondaryContainer() };
        for (int i = 0; i < colors.length; i++) {
            RoundedRect.fill(g, panelX + 16 + i * 22, panelY + panelHeight - 33, 18, 18, 9, colors[i]);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private static Component text(String suffix) {
        return Component.translatable("screen.youzaiworldcore.appearance." + suffix);
    }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
