package top.csituka.youzaiworldcore.client.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.YouzaiworldCore;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;
import top.csituka.youzaiworldcore.client.animation.YzuiMotion;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.element.MenuElementGroup;
import top.csituka.youzaiworldcore.client.screen.element.MenuLayout;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.screen.widget.WidgetFocus;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** Shift+F 菜单：页面控件按视口复用，标题、内容与页脚各自保留边界。 */
@SuppressWarnings("null")
public class MenuScreen extends Screen {
    private static final Identifier VERSION_ICON =
            Identifier.fromNamespaceAndPath(YouzaiworldCore.MOD_ID, "textures/gui/icon.png");
    private final Deque<MenuElementGroup> history = new ArrayDeque<>();
    private final Map<MenuElementGroup, List<AbstractWidget>> pageButtons = new IdentityHashMap<>();
    private final List<AbstractWidget> currentButtons = new ArrayList<>();
    private MenuElementGroup currentGroup;
    private MenuElementGroup targetGroup;
    private boolean transitionReverse;
    private long transitionStartTime;
    private ConfirmationDialog currentDialog;
    private TransparentButton backButton;
    private TransparentButton closeButton;

    public MenuScreen(MenuElementGroup elementGroup) {
        super(Component.translatable("screen.youzaiworldcore.menu.title"));
        currentGroup = elementGroup;
    }

    @Override
    protected void init() {
        super.init();
        pageButtons.clear();
        var layout = new MenuLayout(width, height);
        backButton = new TransparentButton(layout.backX(), 18, MenuLayout.NAVIGATION_SIZE, MenuLayout.NAVIGATION_SIZE,
                Component.translatable("youzaiworldcore.message.gui.back_button"), this::goBack);
        closeButton = new TransparentButton(layout.closeX(), 18, MenuLayout.NAVIGATION_SIZE, MenuLayout.NAVIGATION_SIZE,
                Component.translatable("youzaiworldcore.message.gui.close_button"), this::onClose);
        backButton.setBackgroundVisible(false);
        closeButton.setBackgroundVisible(false);
        activateCurrentGroup();
        if (currentDialog != null) currentDialog.init(width, height);
        DebugLogger.debug("MenuScreen", "菜单布局更新：%d×%d，当前页面 %s", width, height,
                currentGroup.getClass().getSimpleName());
    }

    public void switchTo(MenuElementGroup newGroup) {
        if (targetGroup != null || GuiAnimationController.isExiting(this)) return;
        history.push(currentGroup);
        navigate(newGroup, false);
    }

    public void goBack() {
        if (targetGroup != null || history.isEmpty() || GuiAnimationController.isExiting(this)) return;
        navigate(history.pop(), true);
    }

    private void navigate(MenuElementGroup next, boolean reverse) {
        for (AbstractWidget button : currentButtons) {
            button.setFocused(false);
            if (button instanceof DropdownButton dropdown) dropdown.closePopup();
        }
        DebugLogger.debug("MenuScreen", "菜单切换：%s → %s", currentGroup.getClass().getSimpleName(),
                next.getClass().getSimpleName());
        if (GuiAnimationController.isDisabled()) {
            currentGroup = next;
            activateCurrentGroup();
        } else {
            targetGroup = next;
            transitionReverse = reverse;
            transitionStartTime = System.currentTimeMillis();
        }
    }

    private List<AbstractWidget> buttonsFor(MenuElementGroup group) {
        return pageButtons.computeIfAbsent(group, page -> page.createButtons(this, width, height, 1f, 1f));
    }

    private void activateCurrentGroup() {
        pageButtons.keySet().removeIf(page -> page != currentGroup && !history.contains(page));
        clearWidgets();
        currentButtons.clear();
        currentButtons.addAll(buttonsFor(currentGroup));
        if (!currentGroup.isRoot()) currentButtons.add(backButton);
        currentButtons.add(closeButton);
        for (AbstractWidget button : currentButtons) addWidget(button);
        currentGroup.updateButtons(buttonsFor(currentGroup));
    }

    public void showDialog(ConfirmationDialog dialog) {
        currentDialog = dialog;
        dialog.init(width, height);
        dialog.show();
    }

    public void closeDialog() {
        if (currentDialog != null) currentDialog.hide();
    }

    public boolean hasDialog() {
        return currentDialog != null && currentDialog.isVisible();
    }

    @Override
    public void onClose() {
        startExit(() -> Minecraft.getInstance().setScreenAndShow(null));
    }

    public void startExit(Runnable onComplete) {
        if (!GuiAnimationController.isExiting(this)) onComplete.run();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        float progress = targetGroup == null ? 1f : YzuiMotion.progress(
                System.currentTimeMillis() - transitionStartTime, YzuiMotion.switchDuration(YzuiTheme.visualStyle()));
        if (targetGroup != null && (GuiAnimationController.isDisabled() || progress >= 1f)) {
            currentGroup = targetGroup;
            targetGroup = null;
            activateCurrentGroup();
        }

        boolean interactive = targetGroup == null && !hasDialog() && !GuiAnimationController.isExiting(this)
                && !GuiAnimationController.isBackgroundRendering();
        MenuElementGroup displayedGroup = currentGroup;
        float opacity = 1f, offset = 0f;
        if (targetGroup != null) {
            // MD3 依次淡出、淡入；同一位置的标题、模型和按钮不会互相叠印。
            boolean outgoing = progress < 0.4f;
            displayedGroup = outgoing ? currentGroup : targetGroup;
            float phase = outgoing ? progress / 0.4f : (progress - 0.4f) / 0.6f;
            opacity = outgoing ? 1f - YzuiMotion.accelerate(phase) : YzuiMotion.decelerate(phase);
            float distance = YzuiTheme.frosted() ? 18f : 10f;
            float direction = transitionReverse ? 1f : -1f;
            offset = (outgoing ? direction : -direction) * (1f - opacity) * distance;
        }
        renderPage(g, displayedGroup, opacity, offset, mouseX, mouseY, partialTick, interactive);
        if (!displayedGroup.isRoot()) renderWidget(backButton, g, mouseX, mouseY, partialTick, interactive);
        renderWidget(closeButton, g, mouseX, mouseY, partialTick, interactive);
        renderVersionText(g);

        if (interactive) {
            var layout = new MenuLayout(width, height);
            for (AbstractWidget button : currentButtons) {
                if (button instanceof DropdownButton dropdown) {
                    dropdown.renderPopup(g, mouseX, mouseY, partialTick,
                            MenuLayout.CONTENT_TOP, layout.contentBottom(), 0);
                }
            }
        }
        if (hasDialog()) {
            currentDialog.render(g, width, height);
            currentDialog.renderButtons(g, mouseX, mouseY, partialTick);
        } else if (currentDialog != null) {
            currentDialog = null;
        }
    }

    private void renderPage(GuiGraphicsExtractor g, MenuElementGroup group, float opacity, float offset,
            int mouseX, int mouseY, float partialTick, boolean interactive) {
        var previous = GuiAnimationController.pushContent(new YzuiMotion.Frame(opacity, 1f, 0f));
        try {
            renderTitle(g, group);
            var layout = new MenuLayout(width, height);
            g.enableScissor(layout.shellLeft() + 12, 68, width - layout.shellLeft() - 12, layout.contentBottom());
            g.pose().pushMatrix();
            try {
                g.pose().translate(offset, 0f);
                group.renderCustomBackground(g, width, height, 1f, 0f);
                List<AbstractWidget> buttons = buttonsFor(group);
                group.updateButtons(buttons);
                int localMouseX = (int) Math.floor(mouseX - offset);
                for (AbstractWidget button : buttons) {
                    renderWidget(button, g, localMouseX, mouseY, partialTick, interactive);
                }
                group.renderCustomContent(g, width, height, 1f, 0f,
                        interactive ? localMouseX : width / 2, interactive ? mouseY : height / 2);
            } finally {
                g.pose().popMatrix();
                g.disableScissor();
            }
        } finally {
            GuiAnimationController.restoreContent(previous);
        }
    }

    private void renderWidget(AbstractWidget button, GuiGraphicsExtractor g, int mouseX, int mouseY,
            float partialTick, boolean interactive) {
        boolean focused = button.isFocused();
        if (!interactive) button.setFocused(false);
        try {
            // 原版入口同时维护提示计时和焦点朗读，不再绕过它直接画控件。
            button.extractRenderState(g, interactive ? mouseX : -1, interactive ? mouseY : -1, partialTick);
        } finally {
            if (!interactive) button.setFocused(focused);
        }
    }

    private void renderTitle(GuiGraphicsExtractor g, MenuElementGroup group) {
        var layout = new MenuLayout(width, height);
        YzuiTheme.label(g, font, Component.literal(group.getTitleText()),
                layout.titleX(), 24, layout.titleWidth(), YzuiTheme.text(), false);
        String subtitle = group.getSubtitleText();
        if (subtitle != null) {
            YzuiTheme.label(g, font, Component.literal(subtitle),
                    layout.titleX(), 43, layout.titleWidth(), YzuiTheme.textMuted(), false);
        }
        g.fill(layout.left(640), 61, width - layout.left(640), 62, YzuiTheme.outlineVariant());
    }

    private void renderVersionText(GuiGraphicsExtractor g) {
        String version = FabricLoader.getInstance().getModContainer(YouzaiworldCore.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        int x = new MenuLayout(width, height).shellLeft() + 16, y = height - 17;
        g.pose().pushMatrix();
        g.pose().scale(0.5f, 0.5f);
        int iconSize = font.lineHeight * 2;
        g.blit(RenderPipelines.GUI_TEXTURED, VERSION_ICON, x * 2, y * 2 - font.lineHeight / 2,
                0, 0, iconSize, iconSize, iconSize, iconSize);
        g.text(font, I18n.get("youzaiworldcore.message.gui.version_text", version),
                x * 2 + iconSize + 4, y * 2, YzuiTheme.textMuted(), false);
        g.pose().popMatrix();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (hasDialog()) return currentDialog.keyPressed(event);
        if (GuiAnimationController.isExiting(this) || targetGroup != null) return true;
        currentGroup.updateButtons(buttonsFor(currentGroup));
        for (AbstractWidget button : currentButtons) {
            if (button instanceof DropdownButton dropdown && dropdown.isOpen() && dropdown.keyPressed(event)) return true;
        }
        if (event.key() == 256) { onClose(); return true; }
        return WidgetFocus.keyPressed(event, currentButtons) || super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (GuiAnimationController.isExiting(this) || targetGroup != null || event.button() != 0) return true;
        if (hasDialog()) return currentDialog.mouseClicked(event.x(), event.y());
        currentGroup.updateButtons(buttonsFor(currentGroup));
        for (AbstractWidget button : currentButtons) {
            if (button instanceof DropdownButton dropdown && dropdown.isOpen()) {
                if (!dropdown.mouseClicked(event, isActuallyClick)) dropdown.closePopup();
                return true;
            }
        }
        WidgetFocus.mouseFocus(event.x(), event.y(), currentButtons);
        for (AbstractWidget button : currentButtons) {
            if (button.active && button.visible && button.mouseClicked(event, isActuallyClick)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (hasDialog()) return currentDialog.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (GuiAnimationController.isExiting(this) || targetGroup != null) return true;
        for (AbstractWidget button : currentButtons) {
            if (button instanceof DropdownButton dropdown && dropdown.isOpen()) {
                dropdown.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 背景由共用屏幕入口在内容变换之前绘制。
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
