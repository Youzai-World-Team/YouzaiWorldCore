package top.csituka.youzaiworldcore.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.MailCheckboxButton;
import top.csituka.youzaiworldcore.itemborder.ItemBorderRenderer;
import top.csituka.youzaiworldcore.mail.AttachmentType;
import top.csituka.youzaiworldcore.mail.Mail;
import top.csituka.youzaiworldcore.mail.MailAttachment;
import top.csituka.youzaiworldcore.mail.MailType;
import top.csituka.youzaiworldcore.mail.TargetSpec;
import top.csituka.youzaiworldcore.network.AttachmentData;
import top.csituka.youzaiworldcore.network.MailAdminEditPayload;
import top.csituka.youzaiworldcore.network.MailAdminSendPayload;
import top.csituka.youzaiworldcore.network.MailPlayerListRequestPayload;
import top.csituka.youzaiworldcore.network.MailSentListRequestPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 管理员邮件发布与编辑界面。
 * <p>
 * 接收范围为「全体成员 / 指定玩家」二选一；指定玩家通过 {@link MailPlayerPicker} 弹窗从
 * 账户系统的已注册名单中勾选，输入框只读展示结果。
 * </p>
 */
@SuppressWarnings("null")
public class MailComposeScreen extends MailBaseScreen {

    private static final String MODULE = "MailComposeScreen";
    private static final int ITEM_SLOTS = 10;
    private static final int SLOT_SIZE = 18;
    private static final List<String> TYPE_OPTIONS = List.of("公告", "通知", "奖励");
    private static final List<String> EXPIRE_OPTIONS = List.of("1天", "7天", "30天", "永久");
    private static final byte[] EXPIRE_VALUES = {0, 1, 2, 3};
    /** 下拉框宽度：收窄后把省下的横向空间让给主题输入框 */
    private static final int DROPDOWN_WIDTH = 62;

    private final boolean editMode;
    private final UUID editMailId;
    private Mail editSource;

    private MailCheckboxButton cbAll;
    private MailCheckboxButton cbPlayer;
    private MailCheckboxButton cbItem;
    private MailCheckboxButton cbCommand;
    private MailCheckboxButton cbVanillaExp;
    private MailCheckboxButton cbVanillaLevel;
    private MailCheckboxButton cbAdventureExp;
    private MailCheckboxButton cbAdventureLevel;

    private EditBox titleInput;
    private EditBox commandInput;
    private EditBox vanillaExpInput;
    private EditBox vanillaLevelInput;
    private EditBox adventureExpInput;
    private EditBox adventureLevelInput;
    private EditBox itemAmountInput;
    private MultiLineEditBox bodyInput;
    private DropdownButton typeDropdown;
    private DropdownButton expireDropdown;

    /**
     * 「选取玩家」弹窗的搜索输入框。
     * <p>刻意不加入组件树：只作为文本模型承接键盘事件（保留输入法 / 粘贴 / 光标等原版能力），
     * 显示由 {@link MailPlayerPicker} 自绘，从而不与表单组件抢渲染层级和焦点。</p>
     */
    private EditBox playerSearchInput;

    private final MailPlayerPicker playerPicker = new MailPlayerPicker();
    /** 当前选中的指定玩家代号 */
    private final List<String> selectedPlayers = new ArrayList<>();
    /** 旧版接收范围（全体非管理 / 角色组）：界面已不提供，但编辑时原样保留，避免静默丢数据 */
    private final List<TargetSpec> legacyTargets = new ArrayList<>();

    private final ItemStack[] itemSlots = new ItemStack[ITEM_SLOTS];
    private final MailUi.Rect[] slotRects = new MailUi.Rect[ITEM_SLOTS];
    private int selectedItemSlot;
    private boolean inventoryPickerOpen;

    private MailUi.Rect pageRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect formRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect cancelRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect publishRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect pickItemRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect pickerRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect pickPlayersRect = new MailUi.Rect(0, 0, 0, 0);
    private MailUi.Rect playerFieldRect = new MailUi.Rect(0, 0, 0, 0);

    private String validationMessage = "";
    private boolean finished;
    private boolean cancelPacketSent;
    /** 是否已完成首次初始化（窗口尺寸变化会再次触发 init，此时要保留已填内容） */
    private boolean initialized;

    public MailComposeScreen() {
        this(false, null);
    }

    public MailComposeScreen(boolean editMode, UUID editMailId) {
        super(Component.translatable(editMode
                ? "youzaiworldcore.message.gui.mail.compose.edit_title"
                : "youzaiworldcore.message.gui.mail.compose.title"));
        this.editMode = editMode;
        this.editMailId = editMailId;
        Arrays.fill(itemSlots, ItemStack.EMPTY);
    }

    @Override
    protected void init() {
        super.init();
        pageRect = MailUi.centeredPage(620, 360);
        formRect = new MailUi.Rect(pageRect.x() + 22, pageRect.y() + 50,
                pageRect.width() - 44, pageRect.height() - 68);
        if (editMode && editSource == null && MailClientState.pendingEditData != null) {
            editSource = MailClientState.pendingEditData.mail();
            MailClientState.pendingEditData = null;
        }

        // 窗口尺寸变化会再次触发 init：此时从现有控件回捞已填内容，避免表单被清空
        boolean keepAllChecked = initialized && cbAll.isChecked();
        boolean keepPlayerChecked = initialized && cbPlayer.isChecked();
        Prefill prefill = initialized ? capturePrefill() : createPrefill(editSource);

        createScopeWidgets(prefill);
        createMainFields(prefill);
        createAttachmentWidgets(prefill);
        if (initialized) {
            cbAll.setChecked(keepAllChecked);
            cbPlayer.setChecked(keepPlayerChecked);
        } else {
            // 首次进入才拉取已注册玩家名单，打开弹窗时即可直接展示
            ClientPlayNetworking.send(new MailPlayerListRequestPayload());
        }
        initialized = true;
        syncAttachmentState();

        DebugLogger.info(MODULE, "已打开邮件%s页面", editMode ? "编辑" : "发布");
    }

    /** 把当前表单内容打包成预填数据，用于 init 重建控件时原样恢复。 */
    private Prefill capturePrefill() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : itemSlots) {
            if (stack != null && !stack.isEmpty()) {
                items.add(stack.copy());
            }
        }
        return new Prefill(cbAll.isChecked(), List.copyOf(selectedPlayers), List.copyOf(legacyTargets),
                typeDropdown.getSelectedIndex(), expireDropdown.getSelectedIndex(),
                titleInput.getValue(), bodyInput.getValue(), items,
                cbCommand.isChecked() ? commandInput.getValue() : "",
                cbVanillaExp.isChecked() ? vanillaExpInput.getValue() : "",
                cbVanillaLevel.isChecked() ? vanillaLevelInput.getValue() : "",
                cbAdventureExp.isChecked() ? adventureExpInput.getValue() : "",
                cbAdventureLevel.isChecked() ? adventureLevelInput.getValue() : "");
    }

    private void createScopeWidgets(Prefill prefill) {
        int x = formRect.x() + 12;
        int checkboxY = formRect.y() + 22;
        cbAll = addRenderableWidget(new MailCheckboxButton(x, checkboxY, 72,
                Component.literal("全体成员"), prefill.all(), () -> onScopeToggled(true)));
        cbPlayer = addRenderableWidget(new MailCheckboxButton(x + 92, checkboxY, 72,
                Component.literal("指定玩家"), prefill.playersEnabled(), () -> onScopeToggled(false)));

        selectedPlayers.clear();
        selectedPlayers.addAll(prefill.players());
        legacyTargets.clear();
        legacyTargets.addAll(prefill.legacyTargets());

        playerSearchInput = new EditBox(font, 0, 0, 100, 16, Component.literal(""));
        playerSearchInput.setMaxLength(32);
    }

    private void createMainFields(Prefill prefill) {
        int rowY = formRect.y() + 70;
        typeDropdown = new DropdownButton(formRect.x() + 40, rowY, DROPDOWN_WIDTH, DROPDOWN_WIDTH, 18,
                Component.literal(""), TYPE_OPTIONS, prefill.typeIndex(), false,
                ignored -> syncAttachmentState(), () -> {
                });
        addRenderableWidget(typeDropdown);
        expireDropdown = new DropdownButton(formRect.x() + 168, rowY, DROPDOWN_WIDTH, DROPDOWN_WIDTH, 18,
                Component.literal(""), EXPIRE_OPTIONS, prefill.expireIndex(), false,
                ignored -> {
                }, () -> {
                });
        addRenderableWidget(expireDropdown);

        int titleX = formRect.x() + 268;
        titleInput = createEditBox(titleX, rowY, Math.max(80, formRect.right() - titleX - 12), 18,
                "请输入邮件主题", 64);
        titleInput.setValue(prefill.title());
        addRenderableWidget(titleInput);

        int bodyY = formRect.y() + 106;
        bodyInput = MultiLineEditBox.builder()
                .setX(formRect.x() + 12)
                .setY(bodyY)
                .setPlaceholder(Component.literal("请输入邮件正文..."))
                .setTextColor(0xFFE6E6E6)
                .setTextShadow(false)
                .setCursorColor(0xFFFFFFFF)
                .setShowBackground(false)
                .setShowDecorations(false)
                .build(font, formRect.width() - 24, 45, Component.literal("邮件正文"));
        bodyInput.setCharacterLimit(500);
        bodyInput.setValue(prefill.body());
        addRenderableWidget(bodyInput);
    }

    private void createAttachmentWidgets(Prefill prefill) {
        int x = formRect.x() + 12;
        int itemY = formRect.y() + 180;
        cbItem = addRenderableWidget(new MailCheckboxButton(x, itemY, 104,
                Component.literal("物品附件"), prefill.itemEnabled(), this::syncAttachmentState));

        int slotsX = x + 88;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            slotRects[i] = new MailUi.Rect(slotsX + i * (SLOT_SIZE + 2), itemY - 2, SLOT_SIZE, SLOT_SIZE);
        }
        copyItems(prefill.items());

        int amountX = slotsX + ITEM_SLOTS * (SLOT_SIZE + 2) + 25;
        itemAmountInput = createEditBox(amountX, itemY - 1, 34, 18, "1", 3);
        itemAmountInput.setValue(itemSlots[selectedItemSlot].isEmpty()
                ? "1" : String.valueOf(itemSlots[selectedItemSlot].getCount()));
        itemAmountInput.setResponder(this::updateSelectedItemAmount);
        addRenderableWidget(itemAmountInput);
        pickItemRect = new MailUi.Rect(formRect.right() - 92, itemY - 3, 80, 21);

        int commandY = formRect.y() + 205;
        cbCommand = addRenderableWidget(new MailCheckboxButton(x, commandY, 94,
                Component.literal("指令附件"), prefill.commandEnabled(), this::syncAttachmentState));
        commandInput = createEditBox(x + 100, commandY - 1, formRect.width() - 112, 18,
                "用 %player% 代替玩家名称", 240);
        commandInput.setValue(prefill.command());
        addRenderableWidget(commandInput);

        // ===== 经验 / 等级：原版与本项目冒险体系各两项，可同时勾选 =====
        int numericY = formRect.y() + 231;
        cbVanillaExp = addRenderableWidget(new MailCheckboxButton(x, numericY, 66,
                Component.literal("原版经验"), prefill.vanillaExpEnabled(), this::syncAttachmentState));
        vanillaExpInput = createEditBox(x + 70, numericY - 1, 38, 18, "0", 9);
        vanillaExpInput.setValue(prefill.vanillaExpValue());
        addRenderableWidget(vanillaExpInput);

        cbVanillaLevel = addRenderableWidget(new MailCheckboxButton(x + 120, numericY, 66,
                Component.literal("原版等级"), prefill.vanillaLevelEnabled(), this::syncAttachmentState));
        vanillaLevelInput = createEditBox(x + 190, numericY - 1, 38, 18, "0", 9);
        vanillaLevelInput.setValue(prefill.vanillaLevelValue());
        addRenderableWidget(vanillaLevelInput);

        cbAdventureExp = addRenderableWidget(new MailCheckboxButton(x + 240, numericY, 66,
                Component.literal("冒险经验"), prefill.adventureExpEnabled(), this::syncAttachmentState));
        adventureExpInput = createEditBox(x + 310, numericY - 1, 38, 18, "0", 9);
        adventureExpInput.setValue(prefill.adventureExpValue());
        addRenderableWidget(adventureExpInput);

        cbAdventureLevel = addRenderableWidget(new MailCheckboxButton(x + 360, numericY, 66,
                Component.literal("冒险等级"), prefill.adventureLevelEnabled(), this::syncAttachmentState));
        adventureLevelInput = createEditBox(x + 430, numericY - 1, 38, 18, "0", 9);
        adventureLevelInput.setValue(prefill.adventureLevelValue());
        addRenderableWidget(adventureLevelInput);
    }

    private EditBox createEditBox(int x, int y, int width, int height, String hint, int maxLength) {
        EditBox input = new EditBox(font, x, y, Math.max(20, width), height, Component.literal(""));
        input.setBordered(false);
        input.setTextColor(0xFFE6E6E6);
        input.setTextColorUneditable(0xFF888888);
        input.setTextShadow(false);
        input.setHint(Component.literal(hint));
        input.setMaxLength(maxLength);
        return input;
    }

    // ===== 渲染 =====

    @Override
    protected void renderMailContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MailUi.drawPage(graphics, pageRect);
        renderHeader(graphics, mouseX, mouseY);
        renderFormBackground(graphics, mouseX, mouseY);
        renderWidgets(graphics, mouseX, mouseY, partialTick);

        typeDropdown.renderPopup(graphics, mouseX, mouseY, partialTick);
        expireDropdown.renderPopup(graphics, mouseX, mouseY, partialTick);
        if (inventoryPickerOpen) {
            renderInventoryPicker(graphics, mouseX, mouseY);
        }
        if (playerPicker.isOpen()) {
            playerPicker.setQuery(playerSearchInput.getValue());
            playerPicker.render(graphics, font, mouseX, mouseY,
                    MailViewport.DESIGN_WIDTH, MailViewport.DESIGN_HEIGHT);
        }
    }

    private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int titleX = pageRect.x() + 24;
        int titleY = pageRect.y() + 18;
        graphics.text(font, "✎", titleX, titleY + 2, MailUi.TEXT_PRIMARY, false);
        graphics.pose().pushMatrix();
        graphics.pose().scale(1.35f, 1.35f);
        String heading = editMode ? "编辑邮件" : "发布邮件";
        graphics.text(font, heading, (int) ((titleX + 22) / 1.35f), (int) (titleY / 1.35f),
                MailUi.TEXT_PRIMARY, false);
        graphics.pose().popMatrix();

        publishRect = new MailUi.Rect(pageRect.right() - 60, pageRect.y() + 16, 38, 22);
        cancelRect = new MailUi.Rect(publishRect.x() - 50, publishRect.y(), 42, 22);
        MailUi.button(graphics, font, cancelRect, "取消", 0xFF9A9A9A, 0xFF111111,
                cancelRect.contains(mouseX, mouseY), true);
        MailUi.button(graphics, font, publishRect, editMode ? "保存" : "发布", 0xFF9A9A9A, 0xFF111111,
                publishRect.contains(mouseX, mouseY), true);

        if (!validationMessage.isBlank()) {
            int maxWidth = Math.max(20, cancelRect.x() - titleX - 130);
            String message = MailUi.ellipsize(font, validationMessage, maxWidth);
            graphics.text(font, message, titleX + 128, titleY + 4, MailUi.RED, false);
        }
    }

    private void renderFormBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        MailUi.roundedRect(graphics, formRect.x(), formRect.y(), formRect.width(), formRect.height(), 5,
                MailUi.PANEL_BACKGROUND);
        int x = formRect.x() + 12;
        graphics.text(font, "接收范围（二选一）", x, formRect.y() + 9, MailUi.TEXT_PRIMARY, false);
        if (!legacyTargets.isEmpty()) {
            String hint = "含 " + legacyTargets.size() + " 项旧版范围，保存时原样保留";
            graphics.text(font, hint, formRect.right() - font.width(hint) - 12, formRect.y() + 9,
                    MailUi.TEXT_MUTED, false);
        }

        // ===== 指定玩家：只读展示 + 选取按钮 =====
        if (cbPlayer.isChecked()) {
            int fieldY = formRect.y() + 42;
            graphics.text(font, "玩家", formRect.x() + 12, fieldY + 4, MailUi.TEXT_SECONDARY, false);
            pickPlayersRect = new MailUi.Rect(formRect.right() - 84, fieldY - 2, 72, 20);
            String counter = "已选 " + selectedPlayers.size() + " 人";
            int counterWidth = font.width(counter);
            playerFieldRect = new MailUi.Rect(formRect.x() + 42, fieldY,
                    pickPlayersRect.x() - counterWidth - 20 - (formRect.x() + 42), 16);
            drawInputBackground(graphics, playerFieldRect.x(), playerFieldRect.y(),
                    playerFieldRect.width(), playerFieldRect.height(), true);
            String display = selectedPlayers.isEmpty() ? "尚未选取玩家" : String.join(", ", selectedPlayers);
            graphics.text(font, MailUi.ellipsize(font, display, playerFieldRect.width() - 8),
                    playerFieldRect.x() + 4, playerFieldRect.y() + 4,
                    selectedPlayers.isEmpty() ? MailUi.TEXT_MUTED : 0xFFE6E6E6, false);
            graphics.text(font, counter, playerFieldRect.right() + 8, playerFieldRect.y() + 4,
                    MailUi.TEXT_SECONDARY, false);
            MailUi.button(graphics, font, pickPlayersRect, "选取玩家", 0xFF858585, 0xFF111111,
                    pickPlayersRect.contains(mouseX, mouseY), true);
        } else {
            pickPlayersRect = new MailUi.Rect(0, 0, 0, 0);
            playerFieldRect = new MailUi.Rect(0, 0, 0, 0);
            graphics.text(font, "邮件将发送给全部已注册玩家", formRect.x() + 42, formRect.y() + 46,
                    MailUi.TEXT_MUTED, false);
        }

        graphics.fill(formRect.x() + 12, formRect.y() + 63, formRect.right() - 12,
                formRect.y() + 64, MailUi.DIVIDER);
        graphics.text(font, "类型", formRect.x() + 12, formRect.y() + 76, MailUi.TEXT_PRIMARY, false);
        graphics.text(font, "过期时间", formRect.x() + 108, formRect.y() + 76, MailUi.TEXT_PRIMARY, false);
        graphics.text(font, "主题", formRect.x() + 236, formRect.y() + 76, MailUi.TEXT_PRIMARY, false);
        drawInputBackground(graphics, typeDropdown.getX(), typeDropdown.getY(), typeDropdown.getWidth(), 18, true);
        drawInputBackground(graphics, expireDropdown.getX(), expireDropdown.getY(), expireDropdown.getWidth(), 18, true);
        drawInputBackground(graphics, titleInput.getX(), titleInput.getY(), titleInput.getWidth(),
                titleInput.getHeight(), true);

        graphics.text(font, "文本正文", x, formRect.y() + 94, MailUi.TEXT_PRIMARY, false);
        drawInputBackground(graphics, formRect.x() + 12, formRect.y() + 106,
                formRect.width() - 24, 45, true);
        graphics.fill(formRect.x() + 12, formRect.y() + 159, formRect.right() - 12,
                formRect.y() + 160, MailUi.DIVIDER);
        graphics.text(font, "附加附件", x, formRect.y() + 166, MailUi.TEXT_PRIMARY, false);

        boolean itemEnabled = cbItem.active && cbItem.isChecked();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            MailUi.Rect slot = slotRects[i];
            int border = i == selectedItemSlot && itemEnabled ? MailUi.YELLOW : 0xFF7C7C7C;
            graphics.fill(slot.x(), slot.y(), slot.right(), slot.bottom(), border);
            graphics.fill(slot.x() + 1, slot.y() + 1, slot.right() - 1, slot.bottom() - 1,
                    itemEnabled ? 0xFF3F3F3F : 0xFF4B4B4B);
            ItemStack stack = itemSlots[i];
            if (stack != null && !stack.isEmpty()) {
                graphics.item(stack, slot.x() + 1, slot.y() + 1, i);
                ItemBorderRenderer.renderSlotBorder(graphics, slot.x() + 1, slot.y() + 1, stack);
                graphics.itemDecorations(font, stack, slot.x() + 1, slot.y() + 1);
                if (slot.contains(mouseX, mouseY)) {
                    showItemTooltip(graphics, stack, mouseX, mouseY);
                }
            }
        }
        graphics.text(font, "数量", itemAmountInput.getX() - 25, itemAmountInput.getY() + 5,
                itemEnabled ? MailUi.TEXT_SECONDARY : MailUi.TEXT_MUTED, false);
        drawInputBackground(graphics, itemAmountInput.getX(), itemAmountInput.getY(), itemAmountInput.getWidth(),
                itemAmountInput.getHeight(), itemEnabled);
        MailUi.button(graphics, font, pickItemRect, "从物品栏选取", 0xFF858585, 0xFF111111,
                pickItemRect.contains(mouseX, mouseY), itemEnabled);

        drawInputBackground(graphics, commandInput.getX(), commandInput.getY(), commandInput.getWidth(),
                commandInput.getHeight(), cbCommand.active && cbCommand.isChecked());
        drawInputBackground(graphics, vanillaExpInput.getX(), vanillaExpInput.getY(), vanillaExpInput.getWidth(),
                vanillaExpInput.getHeight(), cbVanillaExp.active && cbVanillaExp.isChecked());
        drawInputBackground(graphics, vanillaLevelInput.getX(), vanillaLevelInput.getY(),
                vanillaLevelInput.getWidth(), vanillaLevelInput.getHeight(),
                cbVanillaLevel.active && cbVanillaLevel.isChecked());
        drawInputBackground(graphics, adventureExpInput.getX(), adventureExpInput.getY(),
                adventureExpInput.getWidth(), adventureExpInput.getHeight(),
                cbAdventureExp.active && cbAdventureExp.isChecked());
        drawInputBackground(graphics, adventureLevelInput.getX(), adventureLevelInput.getY(),
                adventureLevelInput.getWidth(), adventureLevelInput.getHeight(),
                cbAdventureLevel.active && cbAdventureLevel.isChecked());

        if (editMode) {
            String hint = "编辑期间接收者暂不可见，保存或取消后恢复";
            graphics.text(font, hint, formRect.right() - font.width(hint) - 12, formRect.bottom() - 14,
                    MailUi.TEXT_MUTED, false);
        }
    }

    private void drawInputBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                     boolean enabled) {
        MailUi.roundedRect(graphics, x, y, width, height, 2,
                enabled ? MailUi.INPUT_BACKGROUND : 0xFF505050);
    }

    private void renderInventoryPicker(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, MailViewport.DESIGN_WIDTH, MailViewport.DESIGN_HEIGHT, 0x99000000);
        int pickerWidth = 202;
        int pickerHeight = 112;
        pickerRect = new MailUi.Rect((MailViewport.DESIGN_WIDTH - pickerWidth) / 2,
                (MailViewport.DESIGN_HEIGHT - pickerHeight) / 2, pickerWidth, pickerHeight);
        MailUi.roundedRect(graphics, pickerRect.x(), pickerRect.y(), pickerRect.width(), pickerRect.height(), 6,
                0xFF2F2F2F);
        graphics.text(font, "选择要复制到附件槽的物品", pickerRect.x() + 11, pickerRect.y() + 9,
                MailUi.TEXT_PRIMARY, false);
        graphics.text(font, "点击空白处取消", pickerRect.right() - font.width("点击空白处取消") - 11,
                pickerRect.y() + 9, MailUi.TEXT_MUTED, false);

        if (Minecraft.getInstance().player == null) {
            return;
        }
        int gridX = pickerRect.x() + 11;
        int gridY = pickerRect.y() + 28;
        var inventory = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < 36; i++) {
            int slotX = gridX + i % 9 * 20;
            int slotY = gridY + i / 9 * 20;
            MailUi.Rect sourceRect = new MailUi.Rect(slotX, slotY, 18, 18);
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18,
                    sourceRect.contains(mouseX, mouseY) ? 0xFF777777 : 0xFF4B4B4B);
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                graphics.item(stack, slotX + 1, slotY + 1, i + 100);
                ItemBorderRenderer.renderSlotBorder(graphics, slotX + 1, slotY + 1, stack);
                graphics.itemDecorations(font, stack, slotX + 1, slotY + 1);
                if (sourceRect.contains(mouseX, mouseY)) {
                    showItemTooltip(graphics, stack, mouseX, mouseY);
                }
            }
        }
    }

    /**
     * 展示物品提示。
     * <p>提示框在整帧末尾以屏幕坐标绘制，不受当前缩放矩阵影响，
     * 因此这里要把设计坐标换算回屏幕坐标，否则缩放后提示会偏离光标。</p>
     */
    private void showItemTooltip(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY) {
        graphics.setTooltipForNextFrame(font, stack,
                viewport.toScreenX(mouseX), viewport.toScreenY(mouseY));
    }

    // ===== 输入 =====

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        double mouseX = viewport.toDesignX(event.x());
        double mouseY = viewport.toDesignY(event.y());

        if (playerPicker.isOpen()) {
            playerPicker.mouseClicked(mouseX, mouseY);
            return true;
        }
        if (inventoryPickerOpen) {
            handleInventoryPickerClick(mouseX, mouseY);
            return true;
        }
        if (cancelRect.contains(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (publishRect.contains(mouseX, mouseY)) {
            onPublish();
            return true;
        }
        if (cbPlayer.isChecked() && pickPlayersRect.contains(mouseX, mouseY)) {
            openPlayerPicker();
            return true;
        }

        // 物品槽：左键仅切换当前槽位，右键清空；弹窗一律由「从物品栏选取」按钮打开
        for (int i = 0; i < ITEM_SLOTS; i++) {
            if (slotRects[i].contains(mouseX, mouseY) && cbItem.active && cbItem.isChecked()) {
                selectedItemSlot = i;
                if (event.button() == 1) {
                    itemSlots[i] = ItemStack.EMPTY;
                    itemAmountInput.setValue("1");
                } else {
                    ItemStack stack = itemSlots[i];
                    itemAmountInput.setValue(stack.isEmpty() ? "1" : String.valueOf(stack.getCount()));
                }
                return true;
            }
        }
        if (pickItemRect.contains(mouseX, mouseY) && cbItem.active && cbItem.isChecked()) {
            inventoryPickerOpen = true;
            return true;
        }

        if (typeDropdown.isOpen() && !typeDropdown.isPositionInsidePopup(mouseX, mouseY)) {
            typeDropdown.closePopup();
        }
        if (expireDropdown.isOpen() && !expireDropdown.isPositionInsidePopup(mouseX, mouseY)) {
            expireDropdown.closePopup();
        }
        return super.mouseClicked(event, isActuallyClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (playerPicker.isOpen()) {
            return playerPicker.mouseScrolled(viewport.toDesignX(mouseX), viewport.toDesignY(mouseY), scrollY);
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void openPlayerPicker() {
        playerSearchInput.setValue("");
        playerSearchInput.setFocused(true);
        if (MailClientState.registeredPlayers.isEmpty()) {
            // 名单尚未到达（如刚进界面就点开），补发一次请求
            ClientPlayNetworking.send(new MailPlayerListRequestPayload());
        }
        playerPicker.open(selectedPlayers, names -> {
            selectedPlayers.clear();
            selectedPlayers.addAll(names);
            validationMessage = "";
            DebugLogger.info(MODULE, "已选取指定玩家: %d 人", names.size());
        });
    }

    private void handleInventoryPickerClick(double mouseX, double mouseY) {
        if (Minecraft.getInstance().player == null || !pickerRect.contains(mouseX, mouseY)) {
            inventoryPickerOpen = false;
            return;
        }
        int gridX = pickerRect.x() + 11;
        int gridY = pickerRect.y() + 28;
        if (mouseX < gridX || mouseX >= gridX + 180 || mouseY < gridY || mouseY >= gridY + 80) {
            inventoryPickerOpen = false;
            return;
        }
        int column = (int) ((mouseX - gridX) / 20);
        int row = (int) ((mouseY - gridY) / 20);
        int index = row * 9 + column;
        if (column < 0 || column >= 9 || row < 0 || row >= 4 || index >= 36) {
            return;
        }
        ItemStack source = Minecraft.getInstance().player.getInventory().getItem(index);
        if (!source.isEmpty()) {
            ItemStack copy = source.copy();
            copy.setCount(Math.min(parsePositive(itemAmountInput.getValue(), 1), copy.getMaxStackSize()));
            itemSlots[selectedItemSlot] = copy;
            itemAmountInput.setValue(String.valueOf(copy.getCount()));
            DebugLogger.info(MODULE, "已选取物品附件: slot=%d, item=%s, count=%d",
                    selectedItemSlot, copy.getDisplayName().getString(), copy.getCount());
        }
        inventoryPickerOpen = false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (playerPicker.isOpen()) {
            // ESC / 回车由弹窗处理，其余按键转给搜索输入框
            if (playerPicker.keyPressed(event.key())) {
                return true;
            }
            playerSearchInput.keyPressed(event);
            return true;
        }
        if (inventoryPickerOpen && event.key() == 256) {
            inventoryPickerOpen = false;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (playerPicker.isOpen()) {
            playerSearchInput.charTyped(event);
            return true;
        }
        return super.charTyped(event);
    }

    // ===== 状态同步 =====

    /**
     * 接收范围二选一：勾上其中一个即取消另一个；两个都不选时自动选回，
     * 但携带旧版范围（角色组 / 全体非管理）的邮件允许全不选，避免强行覆盖其原有范围。
     */
    private void onScopeToggled(boolean allClicked) {
        MailCheckboxButton clicked = allClicked ? cbAll : cbPlayer;
        MailCheckboxButton other = allClicked ? cbPlayer : cbAll;
        if (clicked.isChecked()) {
            other.setChecked(false);
        } else if (!other.isChecked() && legacyTargets.isEmpty()) {
            clicked.setChecked(true);
        }
        validationMessage = "";
    }

    private void syncAttachmentState() {
        if (typeDropdown == null || cbItem == null) {
            return;
        }
        boolean reward = typeDropdown.getSelectedIndex() == 2;
        cbItem.active = reward;
        cbCommand.active = reward;
        cbVanillaExp.active = reward;
        cbVanillaLevel.active = reward;
        cbAdventureExp.active = reward;
        cbAdventureLevel.active = reward;
        itemAmountInput.setEditable(reward && cbItem.isChecked());
        commandInput.setEditable(reward && cbCommand.isChecked());
        vanillaExpInput.setEditable(reward && cbVanillaExp.isChecked());
        vanillaLevelInput.setEditable(reward && cbVanillaLevel.isChecked());
        adventureExpInput.setEditable(reward && cbAdventureExp.isChecked());
        adventureLevelInput.setEditable(reward && cbAdventureLevel.isChecked());
        if (!reward || !cbItem.isChecked()) {
            inventoryPickerOpen = false;
        }
    }

    private void updateSelectedItemAmount(String value) {
        ItemStack selected = itemSlots[selectedItemSlot];
        if (selected != null && !selected.isEmpty()) {
            selected.setCount(Math.min(parsePositive(value, selected.getCount()), selected.getMaxStackSize()));
        }
    }

    // ===== 提交 =====

    /** 校验表单并向服务端发送发布或编辑数据包。 */
    protected void onPublish() {
        validationMessage = "";
        List<TargetSpec> targets = collectTargets();
        if (targets.isEmpty()) {
            if (validationMessage.isEmpty()) {
                validationMessage = "请选择接收范围";
            }
            return;
        }
        String subject = titleInput.getValue().trim();
        if (subject.isEmpty()) {
            validationMessage = "主题不能为空";
            titleInput.setFocused(true);
            return;
        }

        MailType type = switch (typeDropdown.getSelectedIndex()) {
            case 0 -> MailType.ANNOUNCEMENT;
            case 1 -> MailType.NOTICE;
            default -> MailType.REWARD;
        };
        List<AttachmentData> attachments = collectAttachments(type);
        if (!validationMessage.isEmpty()) {
            return;
        }
        if (type == MailType.REWARD && attachments.isEmpty()) {
            validationMessage = "奖励邮件至少需要一个附件";
            return;
        }

        int expireIndex = Math.max(0, Math.min(EXPIRE_VALUES.length - 1, expireDropdown.getSelectedIndex()));
        byte expire = EXPIRE_VALUES[expireIndex];
        if (editMode && editMailId != null) {
            ClientPlayNetworking.send(new MailAdminEditPayload(editMailId, false, targets, type, subject,
                    bodyInput.getValue(), expire, attachments));
            ClientPlayNetworking.send(new MailSentListRequestPayload());
            DebugLogger.info(MODULE, "已提交邮件编辑: mailId=%s, title=%s", editMailId, subject);
        } else {
            ClientPlayNetworking.send(new MailAdminSendPayload(targets, type, subject,
                    bodyInput.getValue(), expire, attachments));
            DebugLogger.info(MODULE, "已提交邮件发布: title=%s, targets=%d", subject, targets.size());
        }
        finished = true;
        navigateBack();
    }

    private List<TargetSpec> collectTargets() {
        List<TargetSpec> targets = new ArrayList<>(legacyTargets);
        if (cbAll.isChecked()) {
            targets.add(TargetSpec.all());
        }
        if (cbPlayer.isChecked()) {
            if (selectedPlayers.isEmpty()) {
                validationMessage = "请先选取指定玩家";
                return List.of();
            }
            targets.add(TargetSpec.forPlayers(List.copyOf(selectedPlayers)));
        }
        return targets;
    }

    private List<AttachmentData> collectAttachments(MailType type) {
        if (type != MailType.REWARD) {
            return List.of();
        }
        List<AttachmentData> attachments = new ArrayList<>();
        if (cbItem.isChecked()) {
            for (ItemStack stack : itemSlots) {
                if (stack != null && !stack.isEmpty()) {
                    attachments.add(new AttachmentData(AttachmentType.ITEM, "", stack.getCount(), stack.copy()));
                }
            }
        }
        if (cbCommand.isChecked()) {
            String command = commandInput.getValue().trim();
            if (command.isEmpty()) {
                validationMessage = "请填写指令附件";
                return List.of();
            }
            attachments.add(new AttachmentData(AttachmentType.COMMAND, command, 1, null));
        }
        if (cbVanillaExp.isChecked() && !appendNumberAttachment(attachments, AttachmentType.VANILLA_EXP,
                vanillaExpInput.getValue(), "原版经验值")) {
            return List.of();
        }
        if (cbVanillaLevel.isChecked() && !appendNumberAttachment(attachments, AttachmentType.VANILLA_LEVEL,
                vanillaLevelInput.getValue(), "原版等级")) {
            return List.of();
        }
        if (cbAdventureExp.isChecked() && !appendNumberAttachment(attachments, AttachmentType.ADVENTURE_EXP,
                adventureExpInput.getValue(), "冒险经验值")) {
            return List.of();
        }
        if (cbAdventureLevel.isChecked() && !appendNumberAttachment(attachments, AttachmentType.ADVENTURE_LEVEL,
                adventureLevelInput.getValue(), "冒险等级")) {
            return List.of();
        }
        return attachments;
    }

    private boolean appendNumberAttachment(List<AttachmentData> attachments, AttachmentType type,
                                           String value, String label) {
        try {
            int amount = Integer.parseInt(value.trim());
            if (amount <= 0) {
                throw new NumberFormatException();
            }
            attachments.add(new AttachmentData(type, "", amount, null));
            return true;
        } catch (NumberFormatException exception) {
            validationMessage = label + "必须是正整数";
            return false;
        }
    }

    // ===== 预填 =====

    private Prefill createPrefill(Mail mail) {
        if (mail == null) {
            return Prefill.empty();
        }
        boolean all = false;
        List<String> players = new ArrayList<>();
        List<TargetSpec> legacy = new ArrayList<>();
        if (mail.getTargets() != null) {
            for (TargetSpec target : mail.getTargets()) {
                switch (target.scope()) {
                    case TargetSpec.SCOPE_ALL -> all = true;
                    case TargetSpec.SCOPE_PLAYER -> players.addAll(target.args());
                    // 全体非管理 / 角色组：界面已移除，原样保留待回写
                    default -> legacy.add(target);
                }
            }
        }

        List<ItemStack> items = new ArrayList<>();
        String command = "";
        String vanillaExp = "";
        String vanillaLevel = "";
        String adventureExp = "";
        String adventureLevel = "";
        if (mail.getAttachments() != null) {
            for (MailAttachment attachment : mail.getAttachments()) {
                switch (attachment.type()) {
                    case ITEM -> {
                        ItemStack stack = decodeItem(attachment);
                        if (!stack.isEmpty() && items.size() < ITEM_SLOTS) {
                            items.add(stack);
                        }
                    }
                    case COMMAND -> command = attachment.data() == null ? "" : attachment.data();
                    case VANILLA_EXP -> vanillaExp = String.valueOf(attachment.amount());
                    case VANILLA_LEVEL -> vanillaLevel = String.valueOf(attachment.amount());
                    case ADVENTURE_EXP -> adventureExp = String.valueOf(attachment.amount());
                    case ADVENTURE_LEVEL -> adventureLevel = String.valueOf(attachment.amount());
                }
            }
        }
        int typeIndex = switch (mail.getType()) {
            case ANNOUNCEMENT -> 0;
            case NOTICE -> 1;
            case REWARD -> 2;
        };
        return new Prefill(all, players, legacy, typeIndex, expireIndex(mail),
                safe(mail.getTitle()), safe(mail.getBody()), items,
                command, vanillaExp, vanillaLevel, adventureExp, adventureLevel);
    }

    private ItemStack decodeItem(MailAttachment attachment) {
        if (attachment.itemNbt() == null || attachment.itemNbt().isBlank()
                || Minecraft.getInstance().level == null) {
            return ItemStack.EMPTY;
        }
        try {
            var tag = TagParser.parseCompoundFully(attachment.itemNbt());
            var lookup = Minecraft.getInstance().level.registryAccess();
            ItemStack stack = ItemStack.CODEC.parse(
                    lookup.createSerializationContext(NbtOps.INSTANCE), tag)
                    .result().orElse(ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                stack.setCount(Math.min(Math.max(1, attachment.amount()), stack.getMaxStackSize()));
            }
            return stack;
        } catch (Exception exception) {
            DebugLogger.exception(MODULE, "decodeItem", exception);
            return ItemStack.EMPTY;
        }
    }

    private int expireIndex(Mail mail) {
        if (mail.getExpireTime() == null) {
            return 3;
        }
        long remaining = mail.getExpireTime() - System.currentTimeMillis();
        if (remaining <= 86_400_000L) {
            return 0;
        }
        if (remaining <= 7L * 86_400_000L) {
            return 1;
        }
        return 2;
    }

    private void copyItems(List<ItemStack> stacks) {
        Arrays.fill(itemSlots, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(ITEM_SLOTS, stacks.size()); i++) {
            itemSlots[i] = stacks.get(i).copy();
        }
    }

    private static int parsePositive(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    // ===== 关闭 =====

    @Override
    public void onClose() {
        if (playerPicker.isOpen()) {
            playerPicker.close();
            return;
        }
        if (inventoryPickerOpen) {
            inventoryPickerOpen = false;
            return;
        }
        if (editMode && editMailId != null && !finished && !cancelPacketSent) {
            cancelPacketSent = true;
            ClientPlayNetworking.send(new MailAdminEditPayload(editMailId, true, List.of(),
                    MailType.ANNOUNCEMENT, "", "", (byte) 0, List.of()));
            DebugLogger.info(MODULE, "已取消邮件编辑: mailId=%s", editMailId);
        }
        finished = true;
        navigateBack();
    }

    private void navigateBack() {
        MailToast.clear();
        switchTo(editMode ? new MailSentScreen() : new MailScreen());
    }

    /**
     * 表单预填数据。
     *
     * @param legacyTargets 界面已移除但需原样保留的接收范围
     */
    private record Prefill(boolean all, List<String> players, List<TargetSpec> legacyTargets,
                           int typeIndex, int expireIndex, String title, String body,
                           List<ItemStack> items, String command, String vanillaExpValue,
                           String vanillaLevelValue, String adventureExpValue, String adventureLevelValue) {

        static Prefill empty() {
            return new Prefill(true, List.of(), List.of(), 2, 2, "", "", List.of(), "", "", "", "", "");
        }

        boolean playersEnabled() {
            return !players.isEmpty();
        }

        boolean itemEnabled() {
            return !items.isEmpty();
        }

        boolean commandEnabled() {
            return !command.isBlank();
        }

        boolean vanillaExpEnabled() {
            return !vanillaExpValue.isBlank();
        }

        boolean vanillaLevelEnabled() {
            return !vanillaLevelValue.isBlank();
        }

        boolean adventureExpEnabled() {
            return !adventureExpValue.isBlank();
        }

        boolean adventureLevelEnabled() {
            return !adventureLevelValue.isBlank();
        }
    }
}
