package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.client.screen.widget.CheckboxButton;
import top.csituka.youzaiworldcore.client.screen.widget.DropdownButton;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.mail.AttachmentType;
import top.csituka.youzaiworldcore.mail.MailType;
import top.csituka.youzaiworldcore.mail.TargetSpec;
import top.csituka.youzaiworldcore.network.AttachmentData;
import top.csituka.youzaiworldcore.network.MailAdminEditPayload;
import top.csituka.youzaiworldcore.network.MailAdminSendPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 邮件发布/编辑界面。
 */
@SuppressWarnings("null")
public class MailComposeScreen extends Screen {

    private final boolean editMode;
    private final UUID editMailId;

    // 控件
    private CheckboxButton cbAll, cbNonAdmin, cbPlayer, cbRole;
    private EditBox playerInput, roleInput;
    private DropdownButton typeDropdown, expireDropdown;
    private EditBox titleInput, bodyInput;
    private CheckboxButton cbItem, cbCmd, cbExp, cbLevel, cbAdv;
    private EditBox cmdInput, expInput, levelInput, advInput;
    private TransparentButton publishBtn;
    private int btnX, btnY; // 按钮区域位置

    // 物品附件
    private static final int ITEM_SLOTS = 10;
    private final ItemStack[] itemSlots = new ItemStack[ITEM_SLOTS];
    private boolean pickingSlot = false;
    private int pickingSlotIndex = -1;

    // 过期选项
    private static final List<String> EXPIRE_OPTIONS = List.of("1天", "7天", "30天", "永久");
    private static final byte[] EXPIRE_VALUES = { 0, 1, 2, 3 };

    // ItemStack slot size
    private static final int SLOT_SIZE = 18;

    public MailComposeScreen() {
        super(Component.translatable("youzaiworldcore.message.gui.mail.compose.title"));
        this.editMode = false;
        this.editMailId = null;
    }

    public MailComposeScreen(boolean editMode, UUID editMailId) {
        super(Component.translatable(
                editMode ? "youzaiworldcore.message.gui.mail.compose.edit_title"
                        : "youzaiworldcore.message.gui.mail.compose.title"));
        this.editMode = editMode;
        this.editMailId = editMailId;
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2 - 160;
        this.cx = cx;
        int y = 35;
        int inputW = 200;

        // === 接收范围 ===
        addRenderableWidget(cbAll = new CheckboxButton(cx, y, 14, 14,
                Component.literal("全体"), false, () -> {
                }));
        addRenderableWidget(cbNonAdmin = new CheckboxButton(cx + 80, y, 14, 14,
                Component.literal("非管理"), false, () -> {
                }));
        addRenderableWidget(cbPlayer = new CheckboxButton(cx + 170, y, 14, 14,
                Component.literal("指定玩家"), false, () -> {
                    playerInput.setVisible(cbPlayer.isChecked());
                }));
        addRenderableWidget(cbRole = new CheckboxButton(cx + 260, y, 14, 14,
                Component.literal("角色组"), false, () -> {
                    roleInput.setVisible(cbRole.isChecked());
                }));
        y += 18;

        playerInput = new EditBox(font, cx + 10, y, inputW, 16, Component.literal(""));
        playerInput.setHint(Component.literal("玩家名, 逗号分隔"));
        playerInput.setVisible(false);
        addRenderableWidget(playerInput);
        y += 20;

        roleInput = new EditBox(font, cx + 10, y, inputW, 16, Component.literal(""));
        roleInput.setHint(Component.literal("权限节点, 逗号分隔"));
        roleInput.setVisible(false);
        addRenderableWidget(roleInput);
        y += 20;

        // === 类型 ===
        typeDropdown = new DropdownButton(cx, y, 120, 120, 20,
                Component.literal("类型"),
                List.of("公告", "通知", "奖励"), 2, false,
                idx -> {
                }, () -> {
                });
        addRenderableWidget(typeDropdown);
        y += 24;

        // === 主题 ===
        titleInput = new EditBox(font, cx + 40, y, inputW + 80, 18, Component.literal(""));
        titleInput.setMaxLength(64);
        addRenderableWidget(titleInput);
        y += 22;

        // === 正文（单行 EditBox，MVP） ===
        bodyInput = new EditBox(font, cx + 40, y, inputW + 80, 18, Component.literal(""));
        bodyInput.setMaxLength(500);
        addRenderableWidget(bodyInput);
        y += 24;

        // === 物品附件 ===
        addRenderableWidget(cbItem = new CheckboxButton(cx, y, 14, 14,
                Component.literal("物品附件(最多10)"), false, () -> {
                }));
        y += 16;

        // 10 个物品槽
        int slotX = cx + 10;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            int sx = slotX + i * (SLOT_SIZE + 2);
            slotRects[i] = new SlotRect(sx, y, SLOT_SIZE, SLOT_SIZE);
        }
        y += SLOT_SIZE + 4;

        // 选取按钮
        TransparentButton pickBtn = new TransparentButton(cx + 10, y, 120, 16,
                Component.literal("[选取物品]"), () -> pickingSlot = !pickingSlot);
        addRenderableWidget(pickBtn);

        // 数量输入
        itemAmountInput = new EditBox(font, cx + 180, y, 40, 16, Component.literal("1"));
        addRenderableWidget(itemAmountInput);
        y += 20;

        // === 指令附件 ===
        addRenderableWidget(cbCmd = new CheckboxButton(cx, y, 14, 14,
                Component.literal("指令附件"), false,
                () -> cmdInput.setVisible(cbCmd.isChecked())));
        cmdInput = new EditBox(font, cx + 10, y + 16, 280, 16, Component.literal(""));
        cmdInput.setHint(Component.literal("用 %player% 代替玩家名称"));
        cmdInput.setVisible(false);
        addRenderableWidget(cmdInput);
        y += (cbCmd.isChecked() ? 34 : 18);

        // === 原版经验值 ===
        addRenderableWidget(cbExp = new CheckboxButton(cx, y, 14, 14,
                Component.literal("原版经验值"), false,
                () -> expInput.setVisible(cbExp.isChecked())));
        expInput = new EditBox(font, cx + 100, y, 60, 16, Component.literal(""));
        expInput.setVisible(false);
        addRenderableWidget(expInput);
        y += 20;

        // === 原版等级 ===
        addRenderableWidget(cbLevel = new CheckboxButton(cx, y, 14, 14,
                Component.literal("增加等级"), false,
                () -> levelInput.setVisible(cbLevel.isChecked())));
        levelInput = new EditBox(font, cx + 100, y, 60, 16, Component.literal(""));
        levelInput.setVisible(false);
        addRenderableWidget(levelInput);
        y += 20;

        // === 冒险经验 ===
        addRenderableWidget(cbAdv = new CheckboxButton(cx, y, 14, 14,
                Component.literal("冒险等级"), false,
                () -> advInput.setVisible(cbAdv.isChecked())));
        advInput = new EditBox(font, cx + 100, y, 60, 16, Component.literal(""));
        advInput.setVisible(false);
        addRenderableWidget(advInput);
        y += 24;

        // === 过期时间 ===
        expireDropdown = new DropdownButton(cx, y, 120, 120, 20,
                Component.literal("过期"),
                EXPIRE_OPTIONS, 2, false, idx -> {
                }, () -> {
                });
        addRenderableWidget(expireDropdown);
        y += 28;

        // === 按钮 ===
        btnX = cx + 40;
        btnY = y + 10;

        publishBtn = new TransparentButton(btnX + 120, btnY, 80, 24,
                Component.literal(editMode ? "保存修改" : "发布"), this::onPublish);
        addRenderableWidget(publishBtn);

        addRenderableWidget(new TransparentButton(btnX + 30, btnY, 80, 24,
                Component.literal("取消"), () -> onClose()));

        // 编辑模式预填
        if (editMode) {
            var data = top.csituka.youzaiworldcore.client.MailClientState.pendingEditData;
            if (data != null && data.mail() != null) {
                var mail = data.mail();
                titleInput.setValue(mail.getTitle() != null ? mail.getTitle() : "");
                bodyInput.setValue(mail.getBody() != null ? mail.getBody() : "");

                // 预填类型
                typeDropdown = new DropdownButton(cx, 35 + 50, 120, 120, 20,
                        Component.literal("类型"),
                        List.of("公告", "通知", "奖励"),
                        switch (mail.getType()) {
                            case ANNOUNCEMENT -> 0;
                            case NOTICE -> 1;
                            case REWARD -> 2;
                        },
                        false, idx -> {
                        }, () -> {
                        });

                // 预填过期
                if (mail.getExpireTime() != null) {
                    long diff = mail.getExpireTime() - System.currentTimeMillis();
                    int expireIdx = 2; // 30天默认
                    if (diff <= 24L * 60 * 60 * 1000)
                        expireIdx = 0;
                    else if (diff <= 7L * 24 * 60 * 60 * 1000)
                        expireIdx = 1;
                    else if (diff <= 30L * 24 * 60 * 60 * 1000)
                        expireIdx = 2;
                    expireDropdown = new DropdownButton(cx, 35 + 220, 120, 120, 20,
                            Component.literal("过期"), EXPIRE_OPTIONS, expireIdx, false, idx -> {
                            }, () -> {
                            });
                }

                // 预填接收范围（从 mail.getTargets()）
                var targets = mail.getTargets();
                if (targets != null) {
                    for (var spec : targets) {
                        switch (spec.scope()) {
                            case TargetSpec.SCOPE_ALL -> {
                                /* checkbox 不可逆，暂时跳过 */ }
                            case TargetSpec.SCOPE_PLAYER -> {
                                playerInput.setValue(String.join(", ", spec.args()));
                                playerInput.setVisible(true);
                            }
                            case TargetSpec.SCOPE_ROLE -> {
                                roleInput.setValue(String.join(", ", spec.args()));
                                roleInput.setVisible(true);
                            }
                        }
                    }
                }

                // 预填附件（CheckboxButton 无 setChecked，仅预填数值输入框）
                var atts = mail.getAttachments();
                if (atts != null) {
                    for (var att : atts) {
                        switch (att.type()) {
                            case ITEM -> {
                                // 客户端反序列化 ItemStack NBT 需 HolderLookup，暂不支持
                            }
                            case COMMAND -> {
                                cmdInput.setValue(att.data() != null ? att.data() : "");
                            }
                            case VANILLA_EXP -> {
                                expInput.setValue(String.valueOf(att.amount()));
                            }
                            case VANILLA_LEVEL -> {
                                levelInput.setValue(String.valueOf(att.amount()));
                            }
                            case ADVENTURE_EXP -> {
                                advInput.setValue(String.valueOf(att.amount()));
                            }
                        }
                    }
                }
            }
            // 清空暂存数据
            top.csituka.youzaiworldcore.client.MailClientState.pendingEditData = null;
        }
    }

    // 物品槽位坐标记录
    private static class SlotRect {
        int x, y;

        SlotRect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
        }
    }

    private final SlotRect[] slotRects = new SlotRect[ITEM_SLOTS];
    private EditBox itemAmountInput;
    private boolean pickMode = false;
    private int cx; // 记录初始 X 偏移供渲染使用

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        extractBackground(g, mx, my, pt);
        super.extractRenderState(g, mx, my, pt);

        int cx = this.cx;
        if (cx == 0)
            cx = width / 2 - 160;

        // 绘制标签
        g.text(font, "类型:", cx, 35 + 50, 0xFFFFFF);
        g.text(font, "主题:", cx, 35 + 50 + 24, 0xFFFFFF);
        g.text(font, "正文:", cx, 35 + 50 + 24 + 22, 0xFFFFFF);

        // 物品槽位
        for (int i = 0; i < ITEM_SLOTS; i++) {
            int sx = slotRects[i].x, sy = slotRects[i].y;
            g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, pickMode && pickingSlotIndex == i ? 0xFF4488FF : 0x44FFFFFF);
            g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x88000000);
            ItemStack stack = itemSlots[i];
            if (stack != null && !stack.isEmpty()) {
                g.text(font,
                        stack.getDisplayName().getString().substring(0,
                                Math.min(2, stack.getDisplayName().getString().length())),
                        sx + 3, sy + 3, 0xFFFFFF);
                g.text(font, "x" + stack.getCount(), sx + 2, sy + 10, 0xAAAAAA);
            }
        }

        // 选取模式：显示物品栏
        if (pickMode && pickingSlotIndex >= 0) {
            int invX = 10, invY = height - 60;
            g.fill(invX - 2, invY - 2, invX + 9 * 20 + 2, invY + 4 * 20 + 2, 0xCC000000);
            var inv = Minecraft.getInstance().player.getInventory();
            for (int i = 0; i < 36; i++) {
                int ix = invX + (i % 9) * 20, iy = invY + (i / 9) * 20;
                g.fill(ix, iy, ix + 18, iy + 18, 0x44FFFFFF);
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty()) {
                    g.text(font, s.getDisplayName().getString().length() > 2 ? ".." : s.getDisplayName().getString(),
                            ix + 2, iy + 3, 0xFFFFFF);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean bl) {
        if (super.mouseClicked(ev, bl))
            return true;
        double mx = ev.x(), my = ev.y();

        // 物品栏选取模式
        if (pickMode && pickingSlotIndex >= 0) {
            int invX = 10, invY = height - 60;
            if (mx >= invX && mx < invX + 9 * 20 && my >= invY && my < invY + 4 * 20) {
                int slot = (int) ((mx - invX) / 20) + (int) ((my - invY) / 20) * 9;
                if (slot >= 0 && slot < 36) {
                    ItemStack stack = Minecraft.getInstance().player.getInventory().getItem(slot).copy();
                    if (!stack.isEmpty()) {
                        int amt = 1;
                        try {
                            amt = Integer.parseInt(itemAmountInput.getValue());
                        } catch (Exception ignored) {
                        }
                        if (amt > 0)
                            stack.setCount(Math.min(amt, stack.getMaxStackSize()));
                        itemSlots[pickingSlotIndex] = stack;
                    }
                }
                pickMode = false;
                return true;
            }
            // 点击其他区域退出选取模式
            pickMode = false;
            for (int i = 0; i < ITEM_SLOTS; i++) {
                if (mx >= slotRects[i].x && mx < slotRects[i].x + SLOT_SIZE && my >= slotRects[i].y
                        && my < slotRects[i].y + SLOT_SIZE) {
                    pickingSlotIndex = i;
                    pickMode = true;
                    return true;
                }
            }
            return true;
        }

        // 物品槽点击
        for (int i = 0; i < ITEM_SLOTS; i++) {
            if (mx >= slotRects[i].x && mx < slotRects[i].x + SLOT_SIZE && my >= slotRects[i].y
                    && my < slotRects[i].y + SLOT_SIZE) {
                pickingSlotIndex = i;
                pickMode = true;
                return true;
            }
        }

        return false;
    }

    protected void onPublish() {
        // 收集接收范围
        List<TargetSpec> targets = new ArrayList<>();
        if (cbAll.isChecked())
            targets.add(TargetSpec.all());
        if (cbNonAdmin.isChecked())
            targets.add(TargetSpec.nonadmin());
        if (cbPlayer.isChecked() && !playerInput.getValue().isBlank()) {
            targets.add(TargetSpec.forPlayers(List.of(playerInput.getValue().split("[,，]"))));
        }
        if (cbRole.isChecked() && !roleInput.getValue().isBlank()) {
            targets.add(TargetSpec.forRoles(List.of(roleInput.getValue().split("[,，]"))));
        }
        if (targets.isEmpty()) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c请至少选择一个接收范围"));
            return;
        }

        String title = titleInput.getValue();
        if (title.isBlank()) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c主题不能为空"));
            return;
        }

        int typeIdx = typeDropdown.getSelectedIndex();
        MailType mailType = switch (typeIdx) {
            case 0 -> MailType.ANNOUNCEMENT;
            case 1 -> MailType.NOTICE;
            default -> MailType.REWARD;
        };

        int expireIdx = expireDropdown.getSelectedIndex();
        if (expireIdx < 0)
            expireIdx = 2;
        byte expireOpt = EXPIRE_VALUES[expireIdx];

        List<AttachmentData> atts = new ArrayList<>();
        if (cbItem.isChecked()) {
            for (ItemStack stack : itemSlots) {
                if (stack != null && !stack.isEmpty()) {
                    atts.add(new AttachmentData(AttachmentType.ITEM, "", stack.getCount(), stack.copy()));
                }
            }
        }
        if (cbCmd.isChecked() && !cmdInput.getValue().isBlank()) {
            atts.add(new AttachmentData(AttachmentType.COMMAND, cmdInput.getValue(), 1, null));
        }
        if (cbExp.isChecked()) {
            try {
                atts.add(new AttachmentData(AttachmentType.VANILLA_EXP, "", Integer.parseInt(expInput.getValue()),
                        null));
            } catch (Exception ignored) {
            }
        }
        if (cbLevel.isChecked()) {
            try {
                atts.add(new AttachmentData(AttachmentType.VANILLA_LEVEL, "", Integer.parseInt(levelInput.getValue()),
                        null));
            } catch (Exception ignored) {
            }
        }
        if (cbAdv.isChecked()) {
            try {
                atts.add(new AttachmentData(AttachmentType.ADVENTURE_EXP, "", Integer.parseInt(advInput.getValue()),
                        null));
            } catch (Exception ignored) {
            }
        }

        if (mailType == MailType.REWARD && atts.isEmpty()) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§c奖励类型邮件至少需要一个附件"));
            return;
        }

        if (editMode && editMailId != null) {
            ClientPlayNetworking.send(new MailAdminEditPayload(editMailId, false, targets, mailType, title,
                    bodyInput.getValue(), expireOpt, atts));
        } else {
            ClientPlayNetworking.send(new MailAdminSendPayload(targets, mailType, title,
                    bodyInput.getValue(), expireOpt, atts));
        }

        onClose();
    }

    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
