package top.csituka.youzaiworldcore.client.screen;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TextureTileButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MenuScreen extends Screen {

    private static final float ENTRY_ANIMATION_DURATION = 0.5f;
    private static final float TRANSITION_DURATION = 0.4f;
    private static final int CLOSE_BUTTON_SIZE = 14;
    private static final int TITLE_BUTTON_OFFSET = 90;

    private static final float EXIT_ANIMATION_DURATION = 0.25f;

    private MenuElementGroup currentGroup;
    private MenuElementGroup targetGroup;
    private boolean transitionReverse = false;
    private final Deque<MenuElementGroup> history = new ArrayDeque<>();

    private float entryProgress = 0f;
    private long entryStartTime = 0;

    private float transitionProgress = -1f;
    private long transitionStartTime = 0;

    private float backButtonAlpha = 0f;

    private boolean exiting = false;
    private float exitProgress = 0f;
    private long exitStartTime = 0;
    private Runnable onExitComplete;

    private int mouseX = 0;
    private int mouseY = 0;

    private ConfirmationDialog currentDialog;

    private float dialogAnimProgress = 0f;
    private static final float DIALOG_ANIM_SPEED = 0.08f;

    private List<AbstractWidget> currentButtons = new ArrayList<>();

    public MenuScreen(MenuElementGroup elementGroup) {
        super(Component.translatable("screen.youzaiworldcore.menu.title"));
        this.currentGroup = elementGroup;
    }

    public void switchTo(MenuElementGroup newGroup) {
        if (targetGroup != null) return;
        history.push(currentGroup);
        this.targetGroup = newGroup;
        this.transitionReverse = false;
        this.transitionProgress = 0f;
        this.transitionStartTime = System.currentTimeMillis();
    }

    public void goBack() {
        if (targetGroup != null) return;
        if (history.isEmpty()) return;
        MenuElementGroup previous = history.pop();
        this.targetGroup = previous;
        this.transitionReverse = true;
        this.transitionProgress = 0f;
        this.transitionStartTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        this.entryProgress = 0f;
        this.entryStartTime = System.currentTimeMillis();
        if (currentDialog != null) {
            currentDialog.init(this.width, this.height);
        }
    }

    public void showDialog(ConfirmationDialog dialog) {
        this.currentDialog = dialog;
        dialog.init(this.width, this.height);
        dialog.show();
    }

    public void closeDialog() {
        if (this.currentDialog != null) {
            this.currentDialog.hide();
        }
    }

    public boolean hasDialog() {
        return currentDialog != null && currentDialog.isFullyVisible();
    }

    @Override
    public void onClose() {
        if (exiting) return;
        startExit(() -> Minecraft.getInstance().setScreenAndShow(null));
    }

    @Override
    public void removed() {
        super.removed();
        exiting = false;
        exitProgress = 0f;
        onExitComplete = null;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        if (exiting) return true;
        if (keyEvent.key() == 256) { // GLFW_KEY_ESCAPE
            onClose();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    public void startExit(Runnable onComplete) {
        if (exiting) return;
        this.exiting = true;
        this.exitProgress = 0f;
        this.exitStartTime = System.currentTimeMillis();
        this.onExitComplete = onComplete;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        if (exiting) {
            long elapsed = System.currentTimeMillis() - exitStartTime;
            exitProgress = Math.min(1f, elapsed / (EXIT_ANIMATION_DURATION * 1000f));

            float easedExit = easeInOutCubic(exitProgress);
            int bgAlpha = (int) ((1f - easedExit) * 128);
            guiGraphics.fill(0, 0, this.width, this.height, (bgAlpha << 24));

            renderVersionText(guiGraphics, 1f - easedExit);

            float menuAlpha = 1f - easedExit;

            currentButtons.clear();
            renderSingleGroup(guiGraphics, currentGroup, menuAlpha);
            createCloseButton(menuAlpha);

            for (AbstractWidget button : currentButtons) {
                if (button instanceof TransparentButton tb) {
                    tb.render(guiGraphics, mouseX, mouseY, partialTick);
                } else if (button instanceof CheckboxButton cb) {
                    cb.render(guiGraphics, mouseX, mouseY, partialTick);
                } else if (button instanceof DropdownButton db) {
                    db.render(guiGraphics, mouseX, mouseY, partialTick);
                } else if (button instanceof TextureTileButton ttb) {
                    ttb.render(guiGraphics, mouseX, mouseY, partialTick);
                }
            }

            if (exitProgress >= 1f) {
                Runnable callback = onExitComplete;
                exiting = false;
                exitProgress = 0f;
                onExitComplete = null;
                if (callback != null) {
                    callback.run();
                }
            }
            return;
        }

        if (entryProgress < 1f) {
            long elapsed = System.currentTimeMillis() - entryStartTime;
            entryProgress = Math.min(1f, elapsed / (ENTRY_ANIMATION_DURATION * 1000f));
        }

        float easedEntry = easeOutCubic(entryProgress);
        int bgAlpha = (int) (easedEntry * 128);
        guiGraphics.fill(0, 0, this.width, this.height, (bgAlpha << 24));

        renderVersionText(guiGraphics, easedEntry);

        boolean hasDialog = currentDialog != null && currentDialog.isFullyVisible();
        float targetDialogProgress = hasDialog ? 1f : 0f;
        dialogAnimProgress = lerp(dialogAnimProgress, targetDialogProgress, DIALOG_ANIM_SPEED);

        float menuAlpha = easedEntry * (1f - dialogAnimProgress * 0.7f);
        float menuScale = 1f - dialogAnimProgress * 0.05f;

        currentButtons.clear();

        if (dialogAnimProgress > 0.001f) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(this.width / 2f, this.height / 2f);
            guiGraphics.pose().scale(menuScale, menuScale);
            guiGraphics.pose().translate(-this.width / 2f, -this.height / 2f);
        }

        if (targetGroup != null) {
            renderTransition(guiGraphics, menuAlpha);
        } else {
            renderSingleGroup(guiGraphics, currentGroup, menuAlpha);
        }

        float targetBackAlpha = currentGroup.isRoot() ? 0f : 1f;
        backButtonAlpha = lerp(backButtonAlpha, targetBackAlpha, 0.12f);
        if (backButtonAlpha > 0.01f) {
            createBackButton(menuAlpha);
        }

        createCloseButton(menuAlpha);

        int transformedMouseX = (int) ((mouseX - this.width / 2.0) / menuScale + this.width / 2.0);
        int transformedMouseY = (int) ((mouseY - this.height / 2.0) / menuScale + this.height / 2.0);

        for (AbstractWidget button : currentButtons) {
            if (button instanceof TransparentButton tb) {
                tb.render(guiGraphics, transformedMouseX, transformedMouseY, partialTick);
            } else if (button instanceof CheckboxButton cb) {
                cb.render(guiGraphics, transformedMouseX, transformedMouseY, partialTick);
            } else if (button instanceof DropdownButton db) {
                db.render(guiGraphics, transformedMouseX, transformedMouseY, partialTick);
            } else if (button instanceof TextureTileButton ttb) {
                ttb.render(guiGraphics, transformedMouseX, transformedMouseY, partialTick);
            }
        }

        if (dialogAnimProgress > 0.001f) {
            guiGraphics.pose().popMatrix();
        }

        if (currentDialog != null && currentDialog.isVisible()) {
            currentDialog.render(guiGraphics, this.width, this.height);
            currentDialog.renderButtons(guiGraphics, mouseX, mouseY, partialTick);
        } else if (currentDialog != null && !currentDialog.isVisible()) {
            currentDialog = null;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (currentDialog != null && currentDialog.isFullyVisible()) {
            return currentDialog.mouseClicked(event.x(), event.y());
        }

        double mouseX = event.x();
        double mouseY = event.y();

        float menuScale = 1f - dialogAnimProgress * 0.05f;

        double transformedMouseX = (mouseX - this.width / 2.0) / menuScale + this.width / 2.0;
        double transformedMouseY = (mouseY - this.height / 2.0) / menuScale + this.height / 2.0;

        for (AbstractWidget widget : currentButtons) {
            if (widget instanceof TransparentButton button) {
                if (transformedMouseX >= button.getX() && transformedMouseX < button.getX() + button.getWidth() &&
                    transformedMouseY >= button.getY() && transformedMouseY < button.getY() + button.getHeight()) {
                    button.onClick(event, isActuallyClick);
                    return true;
                }
            } else if (widget instanceof CheckboxButton button) {
                if (transformedMouseX >= button.getX() && transformedMouseX < button.getX() + button.getWidth() &&
                    transformedMouseY >= button.getY() && transformedMouseY < button.getY() + button.getHeight()) {
                    button.onClick(event, isActuallyClick);
                    return true;
                }
            } else if (widget instanceof DropdownButton button) {
                if (transformedMouseX >= button.getX() && transformedMouseX < button.getX() + button.getWidth() &&
                    transformedMouseY >= button.getY() && transformedMouseY < button.getY() + button.getHeight()) {
                    button.onClick(event, isActuallyClick);
                    return true;
                }
            } else if (widget instanceof TextureTileButton button) {
                if (transformedMouseX >= button.getX() && transformedMouseX < button.getX() + button.getWidth() &&
                    transformedMouseY >= button.getY() && transformedMouseY < button.getY() + button.getHeight()) {
                    button.onClick(event, isActuallyClick);
                    return true;
                }
            }
        }
        return false;
    }

    private void renderTransition(GuiGraphicsExtractor guiGraphics, float baseAlpha) {
        if (transitionProgress < 1f) {
            long elapsed = System.currentTimeMillis() - transitionStartTime;
            transitionProgress = Math.min(1f, elapsed / (TRANSITION_DURATION * 1000f));
        }

        float eased = easeInOutCubic(transitionProgress);
        float slideDistance = this.width * 0.12f;

        float direction = transitionReverse ? 1f : -1f;

        float outAlpha = (1f - eased) * baseAlpha;
        float outOffset = direction * eased * slideDistance;

        float inAlpha = eased * baseAlpha;
        float inOffset = -direction * (1f - eased) * slideDistance;

        // Use raw (unmapped) alpha in transition, because outAlpha ranges from baseAlpha→0
        // and inAlpha ranges from 0→baseAlpha — no mapping needed for proper fade.
        renderGroupButtonsRaw(currentGroup, outAlpha, outOffset);
        renderGroupButtonsRaw(targetGroup, inAlpha, inOffset);

        renderTitleRaw(guiGraphics, currentGroup, outAlpha);
        renderTitleRaw(guiGraphics, targetGroup, inAlpha);

        currentGroup.renderCustomContent(guiGraphics, this.width, this.height, outAlpha, outOffset, this.mouseX, this.mouseY);
        targetGroup.renderCustomContent(guiGraphics, this.width, this.height, inAlpha, inOffset, this.mouseX, this.mouseY);

        if (transitionProgress >= 1f) {
            currentGroup = targetGroup;
            targetGroup = null;
            transitionProgress = -1f;
        }
    }

    private void renderSingleGroup(GuiGraphicsExtractor guiGraphics, MenuElementGroup group, float entryAlpha) {
        renderGroupButtons(group, entryAlpha, 0f);
        renderTitle(guiGraphics, group, entryAlpha);
        float adjustedAlpha = 0.5f + 0.5f * entryAlpha;
        group.renderCustomContent(guiGraphics, this.width, this.height, adjustedAlpha, 0f, this.mouseX, this.mouseY);
    }

    private void createCloseButton(float alpha) {
        int baseY = (int) (this.height / 2 - 110);
        int titleY = baseY;

        int closeX = this.width / 2 + TITLE_BUTTON_OFFSET;
        int closeY = titleY + (int) ((this.font.lineHeight * 1.3f - CLOSE_BUTTON_SIZE) / 2f);

        TransparentButton closeBtn = new TransparentButton(
                closeX, closeY, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                Component.translatable("youzaiworldcore.message.gui.close_button"),
                () -> startExit(() -> Minecraft.getInstance().setScreenAndShow(null))
        );
        closeBtn.setBackgroundVisible(false);
        closeBtn.setTextColor(0xFFFFFF);
        closeBtn.setExternalAlpha(0.5f + 0.5f * alpha);
        currentButtons.add(closeBtn);
    }

    private void createBackButton(float alpha) {
        int baseY = (int) (this.height / 2 - 110);
        int titleY = baseY;

        int backX = this.width / 2 - TITLE_BUTTON_OFFSET - CLOSE_BUTTON_SIZE;
        int backY = titleY + (int) ((this.font.lineHeight * 1.3f - CLOSE_BUTTON_SIZE) / 2f);

        TransparentButton backBtn = new TransparentButton(
                backX, backY, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                Component.translatable("youzaiworldcore.message.gui.back_button"),
                this::goBack
        );
        backBtn.setBackgroundVisible(false);
        backBtn.setTextColor(0xFFFFFF);
        backBtn.setExternalAlpha((0.5f + 0.5f * alpha) * backButtonAlpha);
        currentButtons.add(backBtn);
    }

    private void renderGroupButtons(MenuElementGroup group, float alpha, float xOffset) {
        float adjustedAlpha = 0.5f + 0.5f * alpha;
        List<AbstractWidget> buttons = group.createButtons(this, this.width, this.height, 1f, adjustedAlpha);
        for (AbstractWidget button : buttons) {
            button.setX(button.getX() + (int) xOffset);
            currentButtons.add(button);
        }
    }

    private void renderGroupButtonsRaw(MenuElementGroup group, float alpha, float xOffset) {
        List<AbstractWidget> buttons = group.createButtons(this, this.width, this.height, 1f, alpha);
        for (AbstractWidget button : buttons) {
            button.setX(button.getX() + (int) xOffset);
            currentButtons.add(button);
        }
    }

    private void renderTitle(GuiGraphicsExtractor guiGraphics, MenuElementGroup group, float alpha) {
        String titleText = group.getTitleText();
        String subtitleText = group.getSubtitleText();

        // TextureTileButton's overlay maxes at alpha=128, so texture visibility = 0.5 + 0.5*alpha.
        // Map text alpha to the same range so text fades at the same rate as textures.
        float textAlphaMapping = 0.5f + 0.5f * alpha;
        int textAlpha = (int) (textAlphaMapping * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;

        int baseY = (int) (this.height / 2 - 110);

        float titleScale = 1.3f;
        int letterSpacing = 3;
        int titleWidth = calculateTextWidthWithSpacing(titleText, letterSpacing);
        float titleX = (this.width - titleWidth * titleScale) / 2f / titleScale;
        int titleY = baseY;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(titleScale, titleScale);
        drawTextWithSpacing(guiGraphics, this.font, titleText, (int) titleX, (int) (titleY / titleScale), textColor, letterSpacing);
        guiGraphics.pose().popMatrix();

        if (subtitleText != null) {
            int subtitleWidth = this.font.width(subtitleText);
            int subtitleX = (this.width - subtitleWidth) / 2;
            int subtitleY = baseY + 25;

            guiGraphics.text(this.font, subtitleText, subtitleX, subtitleY, textColor, false);
        }
    }

    private void renderTitleRaw(GuiGraphicsExtractor guiGraphics, MenuElementGroup group, float alpha) {
        String titleText = group.getTitleText();
        String subtitleText = group.getSubtitleText();

        // Use original alpha (0-1 range) without mapping, for transition animation
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;

        int baseY = (int) (this.height / 2 - 110);

        float titleScale = 1.3f;
        int letterSpacing = 3;
        int titleWidth = calculateTextWidthWithSpacing(titleText, letterSpacing);
        float titleX = (this.width - titleWidth * titleScale) / 2f / titleScale;
        int titleY = baseY;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(titleScale, titleScale);
        drawTextWithSpacing(guiGraphics, this.font, titleText, (int) titleX, (int) (titleY / titleScale), textColor, letterSpacing);
        guiGraphics.pose().popMatrix();

        if (subtitleText != null) {
            int subtitleWidth = this.font.width(subtitleText);
            int subtitleX = (this.width - subtitleWidth) / 2;
            int subtitleY = baseY + 25;

            guiGraphics.text(this.font, subtitleText, subtitleX, subtitleY, textColor, false);
        }
    }

    private static final Identifier VERSION_ICON = Identifier.fromNamespaceAndPath(
            YouzaiworldCore.MOD_ID, "textures/gui/icon.png");
    
    private void renderVersionText(GuiGraphicsExtractor guiGraphics, float alpha) {
        String version = FabricLoader.getInstance()
                .getModContainer("youzaiworldcore")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        String versionText = I18n.get("youzaiworldcore.message.gui.version_text", version);

        float textAlphaMapping = 0.5f + 0.5f * alpha;
        int textAlpha = (int) (textAlphaMapping * 180);
        int textColor = (textAlpha << 24) | 0xAAAAAA;

        float scale = 0.5f;
        int marginX = 10;
        int marginY = 10;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);

        int scaledMarginX = (int) (marginX / scale);
        int scaledMarginY = (int) (marginY / scale);

        // Draw icon before text; icon is ~4x larger than the scaled text
        int iconSize = this.font.lineHeight * 2;
        int iconY = scaledMarginY + (int) ((this.font.lineHeight - iconSize) / 2f);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, VERSION_ICON,
                scaledMarginX, iconY,
                0, 0,
                iconSize, iconSize, iconSize, iconSize);

        // Draw version text next to the icon
        int textX = scaledMarginX + iconSize + 4;
        guiGraphics.text(this.font, versionText, textX, scaledMarginY, textColor, false);
        guiGraphics.pose().popMatrix();
    }

    private int calculateTextWidthWithSpacing(String text, int letterSpacing) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += this.font.width(String.valueOf(text.charAt(i)));
            if (i < text.length() - 1) {
                width += letterSpacing;
            }
        }
        return width;
    }

    private void drawTextWithSpacing(GuiGraphicsExtractor guiGraphics, net.minecraft.client.gui.Font font, String text, int x, int y, int color, int letterSpacing) {
        int currentX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            guiGraphics.text(font, ch, currentX, y, color, false);
            currentX += font.width(ch) + letterSpacing;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    private float easeInOutCubic(float t) {
        return t < 0.5f
                ? 4f * t * t * t
                : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    private float lerp(float current, float target, float speed) {
        if (Math.abs(current - target) < 0.001f) {
            return target;
        }
        return current + (target - current) * speed;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
