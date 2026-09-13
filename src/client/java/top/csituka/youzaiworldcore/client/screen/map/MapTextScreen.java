package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import top.csituka.youzaiworldcore.client.map.MapTexts;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import net.minecraft.client.gui.screens.Screen;
import java.util.function.Consumer;

/** 地图标签、分组名称等短文本输入；取消操作不会修改数据。 */
public final class MapTextScreen extends MapScreen {
    private String value;
    private final int limit;
    private final Consumer<String> accepted;
    private final java.util.function.Predicate<String> valid;
    public MapTextScreen(Screen parent, String title, String initial, int limit, Consumer<String> accepted) {
        this(parent, title, initial, limit, value -> true, accepted);
    }
    /** 带值域校验的输入；不合法的值留在本页供玩家修改。 */
    public MapTextScreen(Screen parent, String title, String initial, int limit, java.util.function.Predicate<String> valid, Consumer<String> accepted) {
        super(parent, title); value = initial; this.limit = limit; this.accepted = accepted; this.valid = valid;
    }
    @Override protected void init() {
        super.init();
        var input = field(panelX + 18, panelY + 65, panelWidth - 36, "text", value, limit, text -> value = text);
        setInitialFocus(input);
        button(panelX + 18, panelY + panelHeight - 34, (panelWidth - 42) / 2, MapTexts.text("save"), () -> {
            if (value.isBlank() || !valid.test(value.strip())) { error = MapTexts.text("invalid_text"); return; }
            accepted.accept(value.strip()); onClose();
        }).setStyle(YzuiTheme.ButtonStyle.FILLED);
        button(panelX + panelWidth / 2 + 3, panelY + panelHeight - 34, (panelWidth - 42) / 2, MapTexts.text("cancel"), this::onClose);
    }
    @Override protected void content(GuiGraphicsExtractor g, int x, int y, float delta) {
        label(g, MapTexts.text("text"), panelX + 18, panelY + 48, panelWidth - 36);
    }
}
