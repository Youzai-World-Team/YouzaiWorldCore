package top.csituka.youzaiworldcore.client.screen;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.cosmetic.CosmeticPreviewTextures;
import top.csituka.youzaiworldcore.client.render.YzuiMenuPanel;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.CosmeticPreviewWidget;
import top.csituka.youzaiworldcore.client.screen.widget.MenuCloseButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** Core 设置中的本地皮肤／披风预览；无需世界、服务器连接或外观上传权限。 */
@SuppressWarnings("null")
public final class CosmeticPreviewScreen extends Screen implements YzuiMenuScreen {
    private final Screen parent;
    private CosmeticPreviewTextures preview;
    private CosmeticPreviewWidget model;
    private TransparentButton cloakButton;
    private boolean showCloak = true;
    private int panelX, panelY, panelWidth, panelHeight, statusY;

    public CosmeticPreviewScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (preview == null) preview = new CosmeticPreviewTextures(fallbackSkin());
        panelWidth = Math.max(1, Math.min(620, width - 24));
        panelHeight = Math.max(1, Math.min(416, height - 24));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int innerWidth = Math.max(1, panelWidth - 32);
        int footerY = panelY + panelHeight - 40;
        int controlsY = footerY - 34;
        statusY = controlsY - 20;
        int modelY = panelY + 54;
        int modelHeight = Math.max(1, statusY - modelY - 8);
        if (model == null) model = new CosmeticPreviewWidget(panelX + 16, modelY, innerWidth, modelHeight, this::previewSkin);
        else model.setRectangle(innerWidth, modelHeight, panelX + 16, modelY);
        addRenderableWidget(model);
        addRenderableWidget(new MenuCloseButton(panelX + panelWidth - 40, panelY + 8, this));
        int buttonWidth = Math.max(1, (innerWidth - 16) / 3);
        addRenderableWidget(new TransparentButton(panelX + 16, controlsY, buttonWidth, 26,
                text("front"), () -> model.face(false)));
        addRenderableWidget(new TransparentButton(panelX + 24 + buttonWidth, controlsY, buttonWidth, 26,
                text("back"), () -> model.face(true)));
        cloakButton = new TransparentButton(panelX + 32 + buttonWidth * 2, controlsY, buttonWidth, 26,
                text(showCloak ? "hide_cloak" : "show_cloak"), () -> {
                    showCloak = !showCloak;
                    updateCloakButton();
                });
        addRenderableWidget(cloakButton);
        updateCloakButton();
        addRenderableWidget(new TransparentButton(panelX + 16, footerY, buttonWidth, 28, text("reload"), () -> {
            preview.reload(fallbackSkin());
            updateCloakButton();
        }).setStyle(YzuiTheme.ButtonStyle.FILLED));
        addRenderableWidget(new TransparentButton(panelX + 24 + buttonWidth, footerY, buttonWidth, 28,
                text("folder"), this::openFolder));
        addRenderableWidget(new TransparentButton(panelX + 32 + buttonWidth * 2, footerY, buttonWidth, 28,
                Component.translatable("gui.back"), this::onClose));
    }

    private PlayerSkin fallbackSkin() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? DefaultPlayerSkin.get(client.getUser().getProfileId()) : client.player.getSkin();
    }

    private PlayerSkin previewSkin() {
        PlayerSkin skin = preview.skin();
        return showCloak ? skin : PlayerSkin.insecure(skin.body(), null, null, skin.model());
    }

    private void updateCloakButton() {
        cloakButton.active = preview.hasCloak();
        cloakButton.setMessage(text(showCloak ? "hide_cloak" : "show_cloak"));
    }

    private void openFolder() {
        ModPaths.ensureDir(CosmeticPreviewTextures.directory());
        Blaze3D.openPath(CosmeticPreviewTextures.directory());
        DebugLogger.CLIENT.debug("CosmeticPreviewScreen", "已打开本地皮肤与披风目录");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (usesMenuOverlay()) YzuiMenuPanel.card(g, panelX, panelY, panelWidth, panelHeight);
        else YzuiTheme.card(g, panelX, panelY, panelWidth, panelHeight);
        YzuiTheme.label(g, font, getTitle(), panelX + 16, panelY + 16, panelWidth - 68, YzuiTheme.text(), false);
        YzuiTheme.label(g, font, text("local_notice"), panelX + 16, panelY + 36,
                panelWidth - 32, YzuiTheme.textMuted(), false);
        int statusWidth = Math.max(1, (panelWidth - 40) / 2);
        Component skinStatus = fileStatus(preview.skinFile(), true);
        Component cloakStatus = fileStatus(preview.cloakFile(), false);
        YzuiTheme.label(g, font, skinStatus, panelX + 16, statusY, statusWidth, statusColor(preview.skinFile()), false);
        YzuiTheme.label(g, font, cloakStatus, panelX + 24 + statusWidth, statusY, statusWidth,
                statusColor(preview.cloakFile()), false);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (mouseX >= model.getX() && mouseX < model.getRight() && mouseY >= model.getY() && mouseY < model.getBottom())
            g.setTooltipForNextFrame(font, text("rotate"), mouseX, mouseY);
        if (mouseY >= statusY && mouseY < statusY + font.lineHeight + 4) {
            if (mouseX >= panelX + 16 && mouseX < panelX + 16 + statusWidth)
                g.setTooltipForNextFrame(font, skinStatus, mouseX, mouseY);
            else if (mouseX >= panelX + 24 + statusWidth && mouseX < panelX + panelWidth - 16)
                g.setTooltipForNextFrame(font, cloakStatus, mouseX, mouseY);
        }
    }

    private Component fileStatus(CosmeticPreviewTextures.FilePreview file, boolean skin) {
        return switch (file.status()) {
            case INVALID -> text("invalid", file.fileName());
            case MISSING -> text(skin ? "skin_missing" : "cloak_missing");
            case LOADED -> skin ? text(preview.skinConflict() ? "skin_conflict"
                    : file.fileName().equals("skin_slim.png") ? "skin_slim" : "skin_wide") : text("cloak_loaded");
        };
    }

    private int statusColor(CosmeticPreviewTextures.FilePreview file) {
        return file.status() == CosmeticPreviewTextures.Status.INVALID ? YzuiTheme.error() : YzuiTheme.textMuted();
    }

    private static Component text(String suffix, Object... arguments) {
        return Component.translatable("screen.youzaiworldcore.cosmetic_preview." + suffix, arguments);
    }

    @Override
    public void onClose() {
        GuiAnimationController.setScreenImmediately(Minecraft.getInstance().gui, parent);
    }

    @Override
    public void closeMenu() { onClose(); }

    @Override
    public void removed() {
        if (preview != null) {
            preview.close();
            preview = null;
            model = null;
        }
        super.removed();
    }

    @Override
    public boolean usesMenuOverlay() { return Minecraft.getInstance().level != null; }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return YzuiMenuScreen.handleEscape(this, event) || super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
