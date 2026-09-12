package top.csituka.youzaiworldcore.client.screen.options;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import top.csituka.youzaiworldcore.client.screen.YouzaiWorldCoreSettingsScreen;

/** 在统一设置外壳中承载现有 Core 设置内容，输入、异步导入和旁白仍由同一实现处理。 */
final class EmbeddedCoreSettings extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    private final YouzaiWorldCoreSettingsScreen content;
    private final ScreenRectangle bounds;

    EmbeddedCoreSettings(YouzaiWorldCoreSettingsScreen content, int x, int y, int width, int height) {
        this.content = content;
        bounds = new ScreenRectangle(x, y, width, height);
    }

    @Override public List<? extends GuiEventListener> children() { return List.of(content); }
    @Override public ScreenRectangle getRectangle() { return bounds; }
    @Override public boolean isMouseOver(double x, double y) {
        return x >= bounds.left() && x < bounds.right() && y >= bounds.top() && y < bounds.bottom();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isMouseOver(event.x(), event.y())) return false;
        if (!content.mouseClicked(event, doubleClick)) return false;
        setFocused(content);
        setDragging(true);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        setDragging(false);
        return content.mouseReleased(event);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        return content.mouseDragged(event, x, y);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return content.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        // Tab 由外层 Screen 沿整个组件树导航，避免内外两层各移动一次焦点。
        return event.key() != 258 && content.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) { return content.charTyped(event); }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float tick) {
        content.extractRenderState(g, x, y, tick);
    }
    @Override public NarrationPriority narrationPriority() {
        return getFocused() != null ? NarrationPriority.FOCUSED : NarrationPriority.NONE;
    }
    @Override public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, content.getTitle());
        if (content.getFocused() instanceof NarratableEntry entry) entry.updateNarration(output.nest());
    }
}
