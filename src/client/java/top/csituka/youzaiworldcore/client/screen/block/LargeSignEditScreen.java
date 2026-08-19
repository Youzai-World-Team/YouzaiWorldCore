package top.csituka.youzaiworldcore.client.screen.block;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.network.LargeSignSetTextPayload;
import top.csituka.youzaiworldcore.util.LargeSignTextRules;

/**
 * 大字牌编辑界面。
 * <p>
 * 由服务端在玩家右键未涂蜡的大字牌时下发
 * {@link top.csituka.youzaiworldcore.network.LargeSignOpenEditPayload} 打开，
 * 输入框会预填字牌当前内容 —— 因此「再次右键即为修改」。
 * <p>
 * 输入框按 {@link LargeSignTextRules} 实时裁剪：
 * 最多 1 个中文 / 中文标点 / 表情符号，或 2 个英文 / 数字 / 英文符号。
 * 客户端裁剪只是即时反馈，服务端收到 C2S 包后仍会独立复核。
 */
@SuppressWarnings("null")
public class LargeSignEditScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 110;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 20;

    /**
     * 输入框的字符数上限。
     * <p>
     * 这里按 Java {@code char} 计（1 个表情符号占 2 个 char，外加变体选择符），
     * 给得比业务上限宽松，真正的约束交给 {@link LargeSignTextRules#clamp(String)}。
     */
    private static final int EDIT_BOX_MAX_LENGTH = 12;

    private final BlockPos signPos;
    private final String initialText;

    private EditBox textInput;

    /** 防止在 {@code setResponder} 回调里调用 {@code setValue} 造成无限递归。 */
    private boolean clamping;

    public LargeSignEditScreen(BlockPos signPos, String initialText) {
        super(Component.translatable("screen.youzaiworldcore.large_sign_edit.title"));
        this.signPos = signPos;
        this.initialText = LargeSignTextRules.clamp(initialText);
    }

    @Override
    protected void init() {
        super.init();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        textInput = new EditBox(this.font,
                panelX + 12, panelY + 40,
                PANEL_WIDTH - 24, 18,
                Component.translatable("screen.youzaiworldcore.large_sign_edit.hint"));
        textInput.setMaxLength(EDIT_BOX_MAX_LENGTH);
        textInput.setResponder(this::onTextChanged);
        textInput.setValue(initialText);
        textInput.moveCursorToEnd(false);
        textInput.setFocused(true);
        addRenderableWidget(textInput);

        TransparentButton confirmButton = new TransparentButton(
                panelX + (PANEL_WIDTH / 2) - (BUTTON_WIDTH / 2), panelY + 76,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.large_sign_edit.confirm"),
                this::confirm);
        confirmButton.setTextColor(0xFFFFFF);
        addRenderableWidget(confirmButton);
    }

    /**
     * 输入变化时按字牌规则裁剪，超量输入会被直接丢弃。
     *
     * @param value 输入框当前内容
     */
    private void onTextChanged(String value) {
        if (clamping) {
            return;
        }
        String clamped = LargeSignTextRules.clamp(value);
        if (clamped.equals(value)) {
            return;
        }

        clamping = true;
        try {
            textInput.setValue(clamped);
            textInput.moveCursorToEnd(false);
        } finally {
            clamping = false;
        }
    }

    /**
     * 提交文本并关闭界面。空内容表示清空字牌。
     */
    private void confirm() {
        String text = LargeSignTextRules.clamp(textInput.getValue());
        ClientPlayNetworking.send(new LargeSignSetTextPayload(signPos, text));
        Minecraft.getInstance().setScreenAndShow(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int centerX = this.width / 2;

        guiGraphics.centeredText(this.font, this.getTitle(), centerX, panelY + 12, 0xFFFFFFFF);
        guiGraphics.centeredText(this.font,
                Component.translatable("screen.youzaiworldcore.large_sign_edit.rule"),
                centerX, panelY + 26, 0xFFA0A0A0);

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (textInput != null && textInput.isFocused()) {
            if (keyEvent.key() == 257 || keyEvent.key() == 335) { // Enter / 小键盘 Enter
                confirm();
                return true;
            }
            if (textInput.keyPressed(keyEvent)) {
                return true;
            }
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (textInput != null && textInput.isFocused() && textInput.charTyped(characterEvent)) {
            return true;
        }
        return super.charTyped(characterEvent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (textInput != null) {
            textInput.setFocused(
                    mouseButtonEvent.x() >= textInput.getX()
                            && mouseButtonEvent.x() < textInput.getX() + textInput.getWidth()
                            && mouseButtonEvent.y() >= textInput.getY()
                            && mouseButtonEvent.y() < textInput.getY() + textInput.getHeight());
        }
        return super.mouseClicked(mouseButtonEvent, doubleClick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
