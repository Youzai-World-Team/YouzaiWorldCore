package top.csituka.youzaiworldcore.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import top.csituka.youzaiworldcore.client.MailClientState;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.MailCheckboxButton;
import top.csituka.youzaiworldcore.mail.AttachmentType;
import top.csituka.youzaiworldcore.mail.Mail;
import top.csituka.youzaiworldcore.mail.MailAttachment;
import top.csituka.youzaiworldcore.mail.MailType;
import top.csituka.youzaiworldcore.mail.TargetSpec;
import top.csituka.youzaiworldcore.network.AttachmentData;
import top.csituka.youzaiworldcore.network.MailAdminEditPayload;
import top.csituka.youzaiworldcore.network.MailAdminSendPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 管理员邮件发布与编辑界面。
 */
@SuppressWarnings("null")
public class MailComposeScreen extends Screen {

    private static final String MODULE = "MailComposeScreen";
    private static final int ITEM_SLOTS = 10;
    private static final int SLOT_SIZE = 18;
    private static final List<String> TYPE_OPTIONS = List.of("公告", "通知", "奖励");
    private static final List<String> EXPIRE_OPTIONS = List.of("1天", "7天", "30天", "永久");
    private static final byte[] EXPIRE_VALUES = {0, 1, 2, 3};

    private final boolean editMode;
    private final UUID editMailId;
    private Mail editSource;

    private MailCheckboxButton cbAll;
    private MailCheckboxButton cbNonAdmin;
    private MailCheckboxButton cbPlayer;
    private MailCheckboxButton cbRole;
    private MailCheckboxButton cbItem;
    private MailCheckboxButton cbCommand;
    private MailCheckboxButton cbExp;
    private MailCheckboxButton cbLevel;
    private MailCheckboxButton cbAdventure;

    private EditBox playerInput;
    private EditBox roleInput;
    private EditBox titleInput;
    private EditBox commandInput;
    private EditBox expInput;
    private EditBox levelInput;
    private EditBox adventureInput;
    private EditBox itemAmountInput;
    private MultiLineEditBox bodyInput;
    private DropdownButton typeDropdown;
    private DropdownButton expireDropdown;

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

    private String validationMessage = "";
    private boolean finished;
    private boolean cancelPacketSent;

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
        pageRect = MailUi.centeredPage(width, height, 620, 360);
        formRect = new MailUi.Rect(pageRect.x() + 22, pageRect.y() + 50,
                pageRect.width() - 44, pageRect.height() - 68);
        if (editMode && editSource == null && MailClientState.pendingEditData != null) {
            editSource = MailClientState.pendingEditData.mail();
            MailClientState.pendingEditData = null;
        }

        Prefill prefill = createPrefill(editSource);
        createScopeWidgets(prefill);
        createMainFields(prefill);
        createAttachmentWidgets(prefill);
        syncTargetState();
        syncAttachmentState();
        DebugLogger.info(MODULE, "已打开邮件%s页面", editMode ? "编辑" : "发布");
    }

    private void createScopeWidgets(Prefill prefill) {
        int x = formRect.x() + 12;
        int checkboxY = formRect.y() + 22;
        cbAll = addRenderableWidget(new MailCheckboxButton(x, checkboxY, 52,
                Component.literal("全体"), prefill.all(), this::syncTargetState));
        cbNonAdmin = addRenderableWidget(new MailCheckboxButton(x + 62, checkboxY, 88,
                Component.literal("全体非管理"), prefill.nonAdmin(), this::syncTargetState));
        cbPlayer = addRenderableWidget(new MailCheckboxButton(x + 162, checkboxY, 78,
                Component.literal("指定玩家"), prefill.playersEnabled(), this::syncTargetState));
        cbRole = addRenderableWidget(new MailCheckboxButton(x + 252, checkboxY, 70,
                Component.literal("角色组"), prefill.rolesEnabled(), this::syncTargetState));

        int inputY = formRect.y() + 42;
        int halfWidth = Math.max(90, (formRect.width() - 90) / 2);
        playerInput = createEditBox(formRect.x() + 42, inputY, halfWidth - 48, 16,
                "Steve, Alex ...", 180);
        playerInput.setValue(prefill.players());
        roleInput = createEditBox(formRect.x() + halfWidth + 48, inputY,
                formRect.width() - halfWidth - 60, 16, "group.admin ...", 180);
        roleInput.setValue(prefill.roles());
        addRenderableWidget(playerInput);
        addRenderableWidget(roleInput);
    }

    private void createMainFields(Prefill prefill) {
        int rowY = formRect.y() + 70;
        typeDropdown = new DropdownButton(formRect.x() + 48, rowY, 92, 92, 18,
                Component.literal(""), TYPE_OPTIONS, prefill.typeIndex(), false,
                ignored -> syncAttachmentState(), () -> {
                });
        addRenderableWidget(typeDropdown);
        expireDropdown = new DropdownButton(formRect.x() + 218, rowY, 92, 92, 18,
                Component.literal(""), EXPIRE_OPTIONS, prefill.expireIndex(), false,
                ignored -> {
                }, () -> {
                });
        addRenderableWidget(expireDropdown);

        int titleX = formRect.x() + 362;
        titleInput = createEditBox(titleX, rowY, Math.max(80, formRect.right() - titleX - 12), 18,
                "暑期活动开启公告", 64);
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

        int numericY = formRect.y() + 231;
        cbExp = addRenderableWidget(new MailCheckboxButton(x, numericY, 70,
                Component.literal("原版经验"), prefill.expEnabled(), this::syncAttachmentState));
        expInput = createEditBox(x + 76, numericY - 1, 42, 18, "0", 9);
        expInput.setValue(prefill.expValue());
        addRenderableWidget(expInput);

        cbLevel = addRenderableWidget(new MailCheckboxButton(x + 140, numericY, 70,
                Component.literal("增加等级"), prefill.levelEnabled(), this::syncAttachmentState));
        levelInput = createEditBox(x + 216, numericY - 1, 42, 18, "0", 9);
        levelInput.setValue(prefill.levelValue());
        addRenderableWidget(levelInput);

        cbAdventure = addRenderableWidget(new MailCheckboxButton(x + 280, numericY, 76,
                Component.literal("冒险经验"), prefill.adventureEnabled(), this::syncAttachmentState));
        adventureInput = createEditBox(x + 362, numericY - 1, 48, 18, "0", 9);
        adventureInput.setValue(prefill.adventureValue());
        addRenderableWidget(adventureInput);
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        MailUi.drawPage(graphics, pageRect);
        renderHeader(graphics, mouseX, mouseY);
        renderFormBackground(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        typeDropdown.renderPopup(graphics, mouseX, mouseY, partialTick);
        expireDropdown.renderPopup(graphics, mouseX, mouseY, partialTick);
        if (inventoryPickerOpen) {
            renderInventoryPicker(graphics, mouseX, mouseY);
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
        graphics.text(font, "接收范围（可多选，取并集）", x, formRect.y() + 9,
                MailUi.TEXT_PRIMARY, false);
        if (playerInput.isVisible()) {
            graphics.text(font, "玩家", formRect.x() + 12, formRect.y() + 46, MailUi.TEXT_SECONDARY, false);
            drawInputBackground(graphics, playerInput.getX(), playerInput.getY(), playerInput.getWidth(),
                    playerInput.getHeight(), true);
        }
        if (roleInput.isVisible()) {
            int labelX = roleInput.getX() - 30;
            graphics.text(font, "角色", labelX, formRect.y() + 46, MailUi.TEXT_SECONDARY, false);
            drawInputBackground(graphics, roleInput.getX(), roleInput.getY(), roleInput.getWidth(),
                    roleInput.getHeight(), true);
        }

        graphics.fill(formRect.x() + 12, formRect.y() + 63, formRect.right() - 12,
                formRect.y() + 64, MailUi.DIVIDER);
        graphics.text(font, "类型", formRect.x() + 12, formRect.y() + 76, MailUi.TEXT_PRIMARY, false);
        graphics.text(font, "过期时间", formRect.x() + 158, formRect.y() + 76, MailUi.TEXT_PRIMARY, false);
        graphics.text(font, "主题", formRect.x() + 324, formRect.y() + 76, MailUi.TEXT_PRIMARY, false);
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
                graphics.itemDecorations(font, stack, slot.x() + 1, slot.y() + 1);
                if (slot.contains(mouseX, mouseY)) {
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
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
        drawInputBackground(graphics, expInput.getX(), expInput.getY(), expInput.getWidth(), expInput.getHeight(),
                cbExp.active && cbExp.isChecked());
        drawInputBackground(graphics, levelInput.getX(), levelInput.getY(), levelInput.getWidth(),
                levelInput.getHeight(), cbLevel.active && cbLevel.isChecked());
        drawInputBackground(graphics, adventureInput.getX(), adventureInput.getY(), adventureInput.getWidth(),
                adventureInput.getHeight(), cbAdventure.active && cbAdventure.isChecked());

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
        graphics.fill(0, 0, width, height, 0x99000000);
        int pickerWidth = 202;
        int pickerHeight = 112;
        pickerRect = new MailUi.Rect((width - pickerWidth) / 2, (height - pickerHeight) / 2,
                pickerWidth, pickerHeight);
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
                graphics.itemDecorations(font, stack, slotX + 1, slotY + 1);
                if (sourceRect.contains(mouseX, mouseY)) {
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        double mouseX = event.x();
        double mouseY = event.y();
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

        for (int i = 0; i < ITEM_SLOTS; i++) {
            if (slotRects[i].contains(mouseX, mouseY) && cbItem.active && cbItem.isChecked()) {
                selectedItemSlot = i;
                if (event.button() == 1) {
                    itemSlots[i] = ItemStack.EMPTY;
                    itemAmountInput.setValue("1");
                } else {
                    ItemStack stack = itemSlots[i];
                    itemAmountInput.setValue(stack.isEmpty() ? "1" : String.valueOf(stack.getCount()));
                    inventoryPickerOpen = true;
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (inventoryPickerOpen && event.key() == 256) {
            inventoryPickerOpen = false;
            return true;
        }
        return super.keyPressed(event);
    }

    private void syncTargetState() {
        if (playerInput != null) {
            playerInput.setVisible(cbPlayer.isChecked());
        }
        if (roleInput != null) {
            roleInput.setVisible(cbRole.isChecked());
        }
    }

    private void syncAttachmentState() {
        if (typeDropdown == null || cbItem == null) {
            return;
        }
        boolean reward = typeDropdown.getSelectedIndex() == 2;
        cbItem.active = reward;
        cbCommand.active = reward;
        cbExp.active = reward;
        cbLevel.active = reward;
        cbAdventure.active = reward;
        itemAmountInput.setEditable(reward && cbItem.isChecked());
        commandInput.setEditable(reward && cbCommand.isChecked());
        expInput.setEditable(reward && cbExp.isChecked());
        levelInput.setEditable(reward && cbLevel.isChecked());
        adventureInput.setEditable(reward && cbAdventure.isChecked());
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

    /** 校验表单并向服务端发送发布或编辑数据包。 */
    protected void onPublish() {
        validationMessage = "";
        List<TargetSpec> targets = collectTargets();
        if (targets.isEmpty()) {
            if (validationMessage.isEmpty()) {
                validationMessage = "请至少选择一个接收范围";
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
            ClientPlayNetworking.send(new top.csituka.youzaiworldcore.network.MailSentListRequestPayload());
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
        List<TargetSpec> targets = new ArrayList<>();
        if (cbAll.isChecked()) {
            targets.add(TargetSpec.all());
        }
        if (cbNonAdmin.isChecked()) {
            targets.add(TargetSpec.nonadmin());
        }
        if (cbPlayer.isChecked()) {
            List<String> players = splitValues(playerInput.getValue());
            if (players.isEmpty()) {
                validationMessage = "请填写指定玩家";
                return List.of();
            }
            targets.add(TargetSpec.forPlayers(players));
        }
        if (cbRole.isChecked()) {
            List<String> roles = splitValues(roleInput.getValue());
            if (roles.isEmpty()) {
                validationMessage = "请填写角色组或权限节点";
                return List.of();
            }
            targets.add(TargetSpec.forRoles(roles));
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
        if (cbExp.isChecked() && !appendNumberAttachment(attachments, AttachmentType.VANILLA_EXP,
                expInput.getValue(), "原版经验值")) {
            return List.of();
        }
        if (cbLevel.isChecked() && !appendNumberAttachment(attachments, AttachmentType.VANILLA_LEVEL,
                levelInput.getValue(), "等级值")) {
            return List.of();
        }
        if (cbAdventure.isChecked() && !appendNumberAttachment(attachments, AttachmentType.ADVENTURE_EXP,
                adventureInput.getValue(), "冒险经验值")) {
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

    private Prefill createPrefill(Mail mail) {
        if (mail == null) {
            return Prefill.empty();
        }
        boolean all = false;
        boolean nonAdmin = false;
        List<String> players = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        if (mail.getTargets() != null) {
            for (TargetSpec target : mail.getTargets()) {
                switch (target.scope()) {
                    case TargetSpec.SCOPE_ALL -> all = true;
                    case TargetSpec.SCOPE_NONADMIN -> nonAdmin = true;
                    case TargetSpec.SCOPE_PLAYER -> players.addAll(target.args());
                    case TargetSpec.SCOPE_ROLE -> roles.addAll(target.args());
                }
            }
        }

        List<ItemStack> items = new ArrayList<>();
        String command = "";
        String exp = "";
        String level = "";
        String adventure = "";
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
                    case VANILLA_EXP -> exp = String.valueOf(attachment.amount());
                    case VANILLA_LEVEL -> level = String.valueOf(attachment.amount());
                    case ADVENTURE_EXP -> adventure = String.valueOf(attachment.amount());
                }
            }
        }
        int typeIndex = switch (mail.getType()) {
            case ANNOUNCEMENT -> 0;
            case NOTICE -> 1;
            case REWARD -> 2;
        };
        return new Prefill(all, nonAdmin, String.join(", ", players), String.join(", ", roles),
                typeIndex, expireIndex(mail), safe(mail.getTitle()), safe(mail.getBody()), items,
                command, exp, level, adventure);
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

    private static List<String> splitValues(String value) {
        return Arrays.stream(value.split("[,，\\s]+"))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .distinct()
                .toList();
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

    @Override
    public void onClose() {
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
        Minecraft.getInstance().setScreenAndShow(editMode ? new MailSentScreen() : new MailScreen());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MailUi.drawBackdrop(graphics, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Prefill(boolean all, boolean nonAdmin, String players, String roles,
                           int typeIndex, int expireIndex, String title, String body,
                           List<ItemStack> items, String command, String expValue,
                           String levelValue, String adventureValue) {

        static Prefill empty() {
            return new Prefill(true, false, "", "", 2, 2, "", "", List.of(), "", "", "", "");
        }

        boolean playersEnabled() {
            return !players.isBlank();
        }

        boolean rolesEnabled() {
            return !roles.isBlank();
        }

        boolean itemEnabled() {
            return !items.isEmpty();
        }

        boolean commandEnabled() {
            return !command.isBlank();
        }

        boolean expEnabled() {
            return !expValue.isBlank();
        }

        boolean levelEnabled() {
            return !levelValue.isBlank();
        }

        boolean adventureEnabled() {
            return !adventureValue.isBlank();
        }
    }
}
