package top.csituka.youzaiworldcore.client.screen;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.cosmetic.CosmeticClientManager;
import top.csituka.youzaiworldcore.client.cosmetic.CosmeticDraft;
import top.csituka.youzaiworldcore.client.cosmetic.CosmeticPreviewTextures;
import top.csituka.youzaiworldcore.client.render.YzuiMenuPanel;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.CosmeticPreviewWidget;
import top.csituka.youzaiworldcore.client.screen.widget.MenuCloseButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.network.CosmeticDataPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("null")
public final class CosmeticSettingsScreen extends Screen implements YzuiMenuScreen {
    private static final String PREFIX = "screen.youzaiworldcore.cosmetic_settings.";
    private static final List<Component> UPLOAD_GUIDE = List.of(
            text("guide_place"), text("guide_wide"), text("guide_slim"), text("guide_cloak"), text("guide_apply"));
    private final Screen parent;
    private final PlayerSkin fallback;
    private CosmeticDataPayload saved;
    private CosmeticPreviewTextures preview;
    private CosmeticPreviewWidget model;
    private CosmeticDraft draft;
    private boolean editing;
    private boolean uploading;
    private boolean failedToReadFiles;
    private boolean uploadFailed;
    private long retryAtNanos;
    private int panelX, panelY, panelWidth, panelHeight;

    private TransparentButton primary;
    private TransparentButton reload;
    private TransparentButton folder;
    private TransparentButton back;
    private MenuCloseButton close;

    public CosmeticSettingsScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        var client = Minecraft.getInstance();
        fallback = DefaultPlayerSkin.get(client.player == null ? client.getUser().getProfileId() : client.player.getUUID());
    }

    @Override
    protected void init() {
        panelWidth = Math.max(1, Math.min(560, width - 20));
        panelHeight = Math.max(1, Math.min(344, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        int inset = 12;
        int gap = 6;
        int innerWidth = panelWidth - inset * 2;
        int bodyY = panelY + 41;
        int bodyHeight = Math.max(1, panelHeight - 95);
        int modelWidth = Math.max(1, (innerWidth - gap) * 45 / 100);
        if (model == null) {
            model = new CosmeticPreviewWidget(panelX + inset, bodyY, modelWidth, bodyHeight, this::modelSkin);
        } else {
            model.setRectangle(modelWidth, bodyHeight, panelX + inset, bodyY);
        }
        addRenderableWidget(model);
        close = addRenderableWidget(new MenuCloseButton(panelX + panelWidth - 40, panelY + 6, this));

        int footerY = panelY + panelHeight - 34;
        int buttonWidth = Math.max(1, (innerWidth - gap * 3) / 4);
        back = addRenderableWidget(new TransparentButton(panelX + inset, footerY, buttonWidth, 24,
                Component.translatable("gui.back"), this::onClose));
        folder = addRenderableWidget(new TransparentButton(panelX + inset + buttonWidth + gap, footerY,
                buttonWidth, 24, text("folder"), this::openFolder));
        reload = addRenderableWidget(new TransparentButton(panelX + inset + 2 * (buttonWidth + gap), footerY,
                buttonWidth, 24, text("reload"), this::stageLocalFiles));
        primary = addRenderableWidget(new TransparentButton(panelX + inset + 3 * (buttonWidth + gap), footerY,
                buttonWidth, 24, text("change"), this::primaryAction).setStyle(YzuiTheme.ButtonStyle.FILLED));
        tick();
    }

    @Override
    public void tick() {
        CosmeticDataPayload latest = CosmeticClientManager.ownSnapshot();
        if (!editing && latest != saved) {
            saved = latest;
            clearPreview();
            if (saved != null) {
                showSnapshot(saved.skinWide(), saved.skinSlim(), saved.cloak());
            }
        }

        boolean available = CosmeticClientManager.canEditAppearance();
        boolean failed = CosmeticClientManager.ownSnapshotFailed();
        boolean showGuide = isMissingLocalFiles();
        primary.setMessage(text(showGuide ? "get_files_again" : editing ? "apply" : failed ? "retry" : "change"));
        int primaryWidth = editing && !showGuide ? reload.getWidth() : reload.getWidth() * 2 + 6;
        primary.setRectangle(primaryWidth, 24, panelX + panelWidth - 12 - primaryWidth, back.getY());
        primary.active = available && !uploading && (editing
                ? showGuide || draft != null && !previewInvalid()
                        && System.nanoTime() >= retryAtNanos
                : saved != null || failed);
        reload.active = reload.visible = editing && !showGuide && !uploading;
        folder.active = folder.visible = editing && !uploading;
        back.active = !uploading;
        model.active = model.visible = preview != null;
        close.refresh();
    }

    private void primaryAction() {
        if (isMissingLocalFiles()) {
            stageLocalFiles();
        } else if (editing) {
            applyDraft();
        } else if (CosmeticClientManager.ownSnapshotFailed()) {
            CosmeticClientManager.requestOwnSnapshot();
            tick();
        } else if (saved != null) {
            editing = true;
            stageLocalFiles();
        }
    }

    private void stageLocalFiles() {
        draft = null;
        failedToReadFiles = false;
        uploadFailed = false;
        clearPreview();
        try {
            draft = CosmeticDraft.read(CosmeticPreviewTextures.directory(),
                    saved.skinWide(), saved.skinSlim(), saved.cloak());
            if (draft.hasLocalFiles()) {
                showSnapshot(draft.skinWide(), draft.skinSlim(), draft.cloak());
            }
        } catch (IOException | SecurityException e) {
            failedToReadFiles = true;
            DebugLogger.CLIENT.exception("CosmeticSettingsScreen", "读取本地装扮", e);
        }
        tick();
    }

    private boolean isMissingLocalFiles() {
        return editing && draft != null && !draft.hasLocalFiles();
    }

    private void applyDraft() {
        if (!primary.active || draft == null) return;
        uploading = true;
        uploadFailed = false;
        tick();
        CosmeticClientManager.applyAppearance(draft.skinWide(), draft.skinSlim(), draft.cloak()).thenAccept(result -> {
            var client = Minecraft.getInstance();
            if (client.gui.screen() != this) return;
            uploading = false;
            if (result.accepted()) {
                GuiAnimationController.setScreenImmediately(client.gui, null);
                return;
            }
            retryAtNanos = System.nanoTime() + result.retryAfterSeconds() * 1_000_000_000L;
            uploadFailed = true;
            tick();
        });
    }

    private void showSnapshot(byte[] wide, byte[] slim, byte[] cloak) {
        boolean isSlim = slim.length != 0;
        preview = new CosmeticPreviewTextures(fallback, isSlim ? slim : wide, isSlim, cloak);
    }

    private boolean previewInvalid() {
        return preview == null || preview.skinFile().status() == CosmeticPreviewTextures.Status.INVALID
                || preview.cloakFile().status() == CosmeticPreviewTextures.Status.INVALID;
    }

    private PlayerSkin modelSkin() {
        return preview == null ? fallback : preview.skin();
    }

    private void openFolder() {
        ModPaths.ensureDir(CosmeticPreviewTextures.directory());
        Blaze3D.openPath(CosmeticPreviewTextures.directory());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        YzuiMenuPanel.card(g, panelX, panelY, panelWidth, panelHeight);
        int x = panelX + 12;
        int y = panelY + 12;
        if (isMissingLocalFiles()) {
            drawUploadGuide(g, x, y, mouseX, mouseY);
            super.extractRenderState(g, mouseX, mouseY, partialTick);
            return;
        }
        YzuiTheme.label(g, font, title, x, y, panelWidth - 56, YzuiTheme.text(), false);
        YzuiTheme.label(g, font, text(editing ? "draft" : "saved"), x, y + 15,
                panelWidth - 52, YzuiTheme.textMuted(), false);
        g.fill(model.getX(), model.getY(), model.getRight(), model.getBottom(), YzuiTheme.surfaceLow());
        int atlasX = model.getRight() + 6;
        int atlasWidth = panelX + panelWidth - 12 - atlasX;
        var skinAtlas = preview == null ? null : preview.skinAtlas();
        var cloakAtlas = preview == null ? null : preview.cloakAtlas();
        var skinFile = preview == null ? null : preview.skinFile();
        var cloakFile = preview == null ? null : preview.cloakFile();
        if (model.getHeight() < 90) {
            int skinWidth = (atlasWidth - 4) / 2;
            drawAtlas(g, atlasX, model.getY(), skinWidth, model.getHeight(), "skin", skinAtlas, skinFile);
            drawAtlas(g, atlasX + skinWidth + 4, model.getY(), atlasWidth - skinWidth - 4,
                    model.getHeight(), "cloak", cloakAtlas, cloakFile);
        } else {
            int skinHeight = model.getHeight() * 3 / 5;
            drawAtlas(g, atlasX, model.getY(), atlasWidth, skinHeight - 2, "skin", skinAtlas, skinFile);
            drawAtlas(g, atlasX, model.getY() + skinHeight + 2, atlasWidth, model.getHeight() - skinHeight - 2,
                    "cloak", cloakAtlas, cloakFile);
        }
        Component status = status();
        if (status != null) {
            int statusY = panelY + panelHeight - 49;
            boolean error = failedToReadFiles || uploadFailed || CosmeticClientManager.ownSnapshotFailed()
                    || preview != null && previewInvalid();
            YzuiTheme.label(g, font, status, x, statusY, panelWidth - 24,
                    error ? YzuiTheme.error() : YzuiTheme.textMuted(), false);
            if (mouseX >= x && mouseX < panelX + panelWidth - 12 && mouseY >= statusY && mouseY < statusY + 12) {
                g.setTooltipForNextFrame(font, status, mouseX, mouseY);
            }
        }
        if (model.visible && model.isMouseOver(mouseX, mouseY)) {
            g.setTooltipForNextFrame(font, Component.translatable("screen.youzaiworldcore.cosmetic_preview.rotate"),
                    mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void drawUploadGuide(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        int width = panelWidth - 24;
        Component heading = text(CosmeticClientManager.isLocalTestEnvironment() ? "local_test" : "guide_title");
        YzuiTheme.label(g, font, heading, x, y, panelWidth - 56, YzuiTheme.text(), false);
        YzuiTheme.label(g, font, text("no_files"), x, y + 15, width, YzuiTheme.textMuted(), false);
        int lineY = y + 31;
        int step = Math.max(font.lineHeight + 1, Math.min(font.lineHeight + 7,
                (back.getY() - lineY - 4) / UPLOAD_GUIDE.size()));
        for (Component line : UPLOAD_GUIDE) {
            YzuiTheme.label(g, font, line, x, lineY, width, YzuiTheme.text(), false);
            if (font.width(line) > width && mouseX >= x && mouseX < x + width
                    && mouseY >= lineY && mouseY < lineY + font.lineHeight) {
                g.setTooltipForNextFrame(font, line, mouseX, mouseY);
            }
            lineY += step;
        }
    }

    private void drawAtlas(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                           CosmeticPreviewTextures.Atlas atlas, CosmeticPreviewTextures.FilePreview file) {
        if (h < 10 || w <= 0) return;
        YzuiTheme.label(g, font, text(label), x + 4, y, w - 8, YzuiTheme.text(), false);
        int imageY = y + 12;
        int imageHeight = h - 12;
        if (imageHeight <= 0) return;
        g.fill(x, imageY, x + w, imageY + imageHeight, YzuiTheme.surfaceLow());
        if (atlas == null) {
            if (file != null && imageHeight >= font.lineHeight + 4) {
                boolean invalid = file.status() == CosmeticPreviewTextures.Status.INVALID;
                YzuiTheme.label(g, font, text(invalid ? "invalid" : "missing"),
                        x + 5, imageY + (imageHeight - font.lineHeight) / 2, w - 10,
                        invalid ? YzuiTheme.error() : YzuiTheme.textMuted(), false);
            }
            return;
        }
        int textureWidth = Math.max(1, Math.min(w, Math.max(1, (imageHeight - 2) * 64 / atlas.height())));
        int textureHeight = Math.max(1, textureWidth * atlas.height() / 64);
        int textureX = x + (w - textureWidth) / 2;
        int textureY = imageY + (imageHeight - textureHeight) / 2;
        int tile = Math.max(3, textureWidth / 12);
        int light = YzuiTheme.surfaceHigh();
        int dark = YzuiTheme.surface();
        for (int row = 0; row < textureHeight; row += tile) {
            for (int col = 0; col < textureWidth; col += tile) {
                g.fill(textureX + col, textureY + row,
                        textureX + Math.min(textureWidth, col + tile), textureY + Math.min(textureHeight, row + tile),
                        (row / tile + col / tile) % 2 == 0 ? light : dark);
            }
        }
        g.blit(RenderPipelines.GUI_TEXTURED, atlas.texture(), textureX, textureY, 0f, 0f,
                textureWidth, textureHeight, 64, atlas.height(), 64, atlas.height());
    }

    private Component status() {
        boolean localTest = CosmeticClientManager.isLocalTestEnvironment();
        if (uploading) return text("uploading");
        if (uploadFailed) {
            long nanos = retryAtNanos - System.nanoTime();
            return nanos > 0 ? text("cooldown", (nanos + 999_999_999L) / 1_000_000_000L)
                    : text(localTest ? "local_test_error" : "upload_error");
        }
        if (failedToReadFiles) return text("file_error");
        if (editing) {
            if (previewInvalid()) return text("file_error");
            if (draft.widePreferred()) return text("wide_preferred");
            return text(localTest ? "local_test" : "keep_saved");
        }
        if (CosmeticClientManager.ownSnapshotFailed()) return text("load_error");
        if (saved != null) return localTest ? text("local_test") : null;
        return text(CosmeticClientManager.canEditAppearance() ? "loading" : "unavailable");
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    @Override
    public boolean canCloseMenu() {
        return !uploading;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !uploading;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return YzuiMenuScreen.handleEscape(this, event) || super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (uploading) return;
        if (editing) {
            editing = false;
            draft = null;
            uploadFailed = false;
            failedToReadFiles = false;
            saved = null;
            clearPreview();
            tick();
            return;
        }
        GuiAnimationController.setScreenImmediately(Minecraft.getInstance().gui, parent);
    }

    private void clearPreview() {
        if (preview != null) {
            preview.close();
            preview = null;
        }
    }

    @Override
    public void removed() {
        clearPreview();
        saved = null;
        draft = null;
        super.removed();
    }
}
