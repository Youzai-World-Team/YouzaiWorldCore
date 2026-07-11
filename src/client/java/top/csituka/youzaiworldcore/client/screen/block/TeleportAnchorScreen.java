package top.csituka.youzaiworldcore.client.screen.block;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.data.TeleportAnchorData;
import top.csituka.youzaiworldcore.network.TeleportAnchorTeleportPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/**
 * 传送锚点选择界面。
 * <p>
 * 显示当前玩家的所有活跃传送点，点击即可传送。
 */
public class TeleportAnchorScreen extends Screen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_PADDING = 12;
    private static final int ITEM_HEIGHT = 22;
    private static final int ITEM_GAP = 4;
    private static final int TITLE_HEIGHT = 30;

    private final List<TeleportAnchorData> points;
    private int panelX;
    private int panelY;
    private int panelHeight;

    public TeleportAnchorScreen(List<TeleportAnchorData> points) {
        super(Component.translatable("screen.youzaiworldcore.teleport_anchor.title"));
        this.points = points;
    }

    @Override
    protected void init() {
        super.init();

        int listHeight = points.size() * (ITEM_HEIGHT + ITEM_GAP) - ITEM_GAP;
        panelHeight = TITLE_HEIGHT + PANEL_PADDING + listHeight + PANEL_PADDING;

        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - panelHeight) / 2;

        int buttonY = panelY + TITLE_HEIGHT + PANEL_PADDING;
        for (int i = 0; i < points.size(); i++) {
            int index = i;
            TeleportAnchorData point = points.get(i);

            String dimDisplay = switch (point.dimension().identifier().getPath()) {
                case "overworld" -> "主世界";
                case "the_nether" -> "下界";
                case "the_end" -> "末地";
                default -> point.dimension().identifier().getPath();
            };
            String label = point.name() + " (" + dimDisplay + " @ "
                    + point.pos().getX() + ", " + point.pos().getY() + ", " + point.pos().getZ() + ")";

            Button button = Button.builder(
                    Component.literal(label),
                    btn -> {
                        ClientPlayNetworking.send(new TeleportAnchorTeleportPayload(index));
                        Minecraft.getInstance().setScreenAndShow(null);
                    }
                )
                .bounds(panelX + PANEL_PADDING, buttonY,
                        PANEL_WIDTH - PANEL_PADDING * 2, ITEM_HEIGHT)
                .build();
            addRenderableWidget(button);
            buttonY += ITEM_HEIGHT + ITEM_GAP;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 标题
        var font = Minecraft.getInstance().font;
        String title = this.getTitle().getString();
        int titleWidth = font.width(title);
        guiGraphics.text(font, title, (this.width - titleWidth) / 2, panelY + 10, 0xFFFFFFFF, false);

        // 渲染子组件（按钮）
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制原版背景
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
