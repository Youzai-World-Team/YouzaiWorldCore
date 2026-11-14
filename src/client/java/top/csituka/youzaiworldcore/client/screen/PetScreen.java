package top.csituka.youzaiworldcore.client.screen;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.screen.widget.MenuCloseButton;
import top.csituka.youzaiworldcore.network.PetMenuRequestPayload;
import top.csituka.youzaiworldcore.network.PetMenuStatePayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 首页宠物管理：直接选择宠物、模式和改名，等待服务端结果后刷新。 */
@SuppressWarnings("null")
public final class PetScreen extends MailBaseScreen {
    private static long sequence;
    private long pending, deadline;
    private Object connection;
    private List<PetMenuStatePayload.Entry> pets = List.of();
    private String selected;
    private int offset;
    private Component status = text("loading");
    private EditBox nameInput;
    private MailUi.Rect page, list;
    public PetScreen(Screen parent) { super(text("title"), parent); }

    @Override protected void init() {
        super.init();
        page = MailUi.centeredPage(660, 400, viewport.width(), viewport.height());
        list = new MailUi.Rect(page.x() + 12, page.y() + 50, page.width() - 24, Math.max(40, page.height() - 170));
        var selection = pets.stream().filter(p -> p.id().equals(selected)).findFirst().orElse(null);
        String name = nameInput != null ? nameInput.getValue() : selection == null ? "" : selection.name();
        addRenderableWidget(new MenuCloseButton(page.right() - 40, page.y() + 10, this));
        addRenderableWidget(new TransparentButton(page.right() - 106, page.y() + 12, 58, 24, text("refresh"), () -> request("list", "")));
        addRenderableWidget(new TransparentButton(page.x() + 12, page.y() + 12, 48, 24, text("back"), this::onClose));
        int y = list.bottom() + 8, w = (list.width() - 18) / 4;
        String[] modes = { "companionship", "hunting", "attack", "guard" };
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            var button = addRenderableWidget(new TransparentButton(list.x() + i * (w + 6), y, w, 24, text(mode), () -> request("mode", mode)));
            button.active = selection != null && pending == 0 && !selection.mode().equals(mode);
        }
        nameInput = addRenderableWidget(new EditBox(font, list.x(), y + 32, list.width() - 122, 24, text("name")));
        nameInput.setMaxLength(32); nameInput.setValue(name);
        var rename = addRenderableWidget(new TransparentButton(nameInput.getRight() + 6, y + 32, 52, 24, text("rename"), () -> request("rename", nameInput.getValue())));
        var highlight = addRenderableWidget(new TransparentButton(rename.getRight() + 6, y + 32, 58, 24, text("highlight"), () -> request("highlight", "")));
        rename.active = highlight.active = selection != null && pending == 0;
        if (connection == null) request("list", "");
    }
    private void request(String action, String value) {
        if (pending != 0 || Minecraft.getInstance().getConnection() == null || (!"list".equals(action) && selected == null)) return;
        connection = Minecraft.getInstance().getConnection(); pending = ++sequence; deadline = System.currentTimeMillis() + 15000;
        status = text("loading");
        ClientPlayNetworking.send(new PetMenuRequestPayload(pending, action, selected == null ? "" : selected, value));
        DebugLogger.info("PetMenu", "请求宠物操作：%s", action);
        if (nameInput != null) rebuildWidgets();
    }
    public void receive(PetMenuStatePayload result, Object source) {
        if (source != connection || pending == 0 || pending != result.requestId() || System.currentTimeMillis() >= deadline) return;
        pending = 0; pets = result.pets();
        if (pets.stream().noneMatch(p -> p.id().equals(selected))) { selected = null; nameInput = null; }
        status = result.message().getString().isBlank() ? text(pets.isEmpty() ? "empty" : "ready") : result.message();
        offset = Math.min(offset, Math.max(0, pets.size() - visibleRows()));
        rebuildWidgets();
    }
    private int visibleRows() { return Math.max(1, list.height() / 32); }
    @Override public void tick() {
        super.tick();
        if (pending != 0 && (System.currentTimeMillis() >= deadline || connection != Minecraft.getInstance().getConnection())) {
            pending = 0; status = text("timeout"); rebuildWidgets();
        }
    }
    @Override protected void renderMailContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        MailUi.drawPage(g, page);
        YzuiTheme.label(g, font, getTitle(), page.x() + 68, page.y() + 20, page.width() - 184, MailUi.textPrimary(), false);
        int end = Math.min(pets.size(), offset + visibleRows());
        for (int i = offset; i < end; i++) {
            var pet = pets.get(i); int y = list.y() + (i - offset) * 32;
            g.fill(list.x(), y, list.right(), y + 30, pet.id().equals(selected) ? MailUi.rowSelected() : MailUi.rowAlternate());
            String mode = text(pet.mode()).getString();
            g.text(font, MailUi.ellipsize(font, pet.name(), list.width() - font.width(mode) - 24), list.x() + 8, y + 5, MailUi.textPrimary(), false);
            g.text(font, pet.id(), list.x() + 8, y + 18, MailUi.textMuted(), false);
            g.text(font, mode, list.right() - font.width(mode) - 8, y + 12, MailUi.textSecondary(), false);
        }
        YzuiTheme.label(g, font, status, page.x() + 12, page.bottom() - 20, page.width() - 24, MailUi.textSecondary(), false);
        renderWidgets(g, mx, my, delta);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean actual) {
        if (isExiting()) return true;
        double x = viewport.toDesignX(event.x()), y = viewport.toDesignY(event.y());
        if (pending == 0 && list.contains(x, y)) {
            int index = offset + (int) ((y - list.y()) / 32);
            if (index < pets.size()) { selected = pets.get(index).id(); nameInput = null; rebuildWidgets(); return true; }
        }
        return super.mouseClicked(event, actual);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (!list.contains(viewport.toDesignX(x), viewport.toDesignY(y))) return false;
        offset = Math.clamp(offset - (int) Math.signum(dy), 0, Math.max(0, pets.size() - visibleRows())); return true;
    }
    private static Component text(String key) { return Component.translatable("screen.youzaiworldcore.pets." + key); }
}
