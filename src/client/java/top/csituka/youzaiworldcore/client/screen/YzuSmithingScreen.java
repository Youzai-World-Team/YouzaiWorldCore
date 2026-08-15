package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CyclingSlotBackground;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 锻造台屏幕（替换原版 {@code SmithingScreen}）。
 * <p>
 * 26.2 中原版锻造台屏继承 {@link ItemCombinerScreen}（含菜单监听器生命周期），
 * 本类沿用同一基类，将背景纹理替换为 YZUI 白底圆角面板，并保留全部原版交互与视觉：
 * <ul>
 *   <li><b>装甲架预览</b>：右侧 40×60 区域实时渲染结果槽装备（含装甲分层与
 *       非装甲物品左手持有逻辑，复制原版 {@code updateArmorStandPreview}）；</li>
 *   <li><b>空槽位图标循环</b>：三个 {@link CyclingSlotBackground} 展示模板/基底/附加
 *       的空槽位提示图标（模板图标随模板类型切换）；</li>
 *   <li><b>错误图标</b>：配方不可用时复用原版 {@code container/smithing/error} sprite；</li>
 *   <li><b>槽位交互</b>：点击、Shift 快捷移动、双击收集、拖拽分发全继承原版。</li>
 * </ul>
 * <p>
 * <h3>槽位布局（沿用原版，相对面板左上角）</h3>
 * <table>
 *   <tr><th>槽位</th><th>坐标</th><th>说明</th></tr>
 *   <tr><td>模板</td><td>(8, 48)</td><td>锻造模板</td></tr>
 *   <tr><td>基底</td><td>(26, 48)</td><td>装备</td></tr>
 *   <tr><td>附加</td><td>(44, 48)</td><td>升级材料</td></tr>
 *   <tr><td>结果</td><td>(98, 48)</td><td>合成结果</td></tr>
 *   <tr><td>玩家背包</td><td>起点 (8, 84)</td><td>36 格</td></tr>
 * </table>
 * 面板尺寸 176×166；装甲架预览区 (121, 20)-(161, 80)。
 * <p>
 * 主题色：<b>锻造台暗木棕</b> —— 标题 {@code 0xFF7A5C3E}、强调条 {@code 0xB0A8854F}、
 * 槽位 {@code 0x40FFFFFF / 悬停 0x60FFFFFF}（统一白色系派生，与
 * {@link YzuContainerScreen}/{@link YzuShulkerBoxScreen} 同设计语言）。
 * <p>
 * 开关机制：{@code YzuContainerScreenSwitchMixin} 在 {@code Gui.setScreen}
 * 拦截原版 {@code SmithingScreen}，仅当 {@code ClientExternalSettings.isYzuiEnabled()}
 * 为 {@code true} 时替换为本类。零新增纹理（纯色矩形 + 原版物品图标/sprite）。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuSmithingScreen extends ItemCombinerScreen<SmithingMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuSmithingScreen");

    // ========== YZUI 统一设计常量（与 YzuContainerScreen 一致） ==========

    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_RADIUS = 6;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3;

    private static final int LABEL_COLOR = 0xCC404040;

    // ========== 关闭按钮 ==========

    private static final int CLOSE_SIZE = 14;
    private static final int CLOSE_RADIUS = 4;
    private static final int CLOSE_MARGIN = 6;
    private static final int CLOSE_TOP = 2;
    private static final int CLOSE_BG = 0x40FFFFFF;
    private static final int CLOSE_BG_HOVER = 0x80FFFFFF;
    private static final int CLOSE_ICON = 0xCC404040;
    private static final int CLOSE_ICON_HOVER = 0xFF000000;
    private static final String CLOSE_GLYPH = "\u00d7"; // ×

    // ========== 标题区 ==========

    private static final int TITLE_ICON_GAP = 4;
    private static final float ICON_SCALE = 0.75f;
    private static final int ICON_SIZE = 12;

    // ========== 锻造台主题（暗木棕） ==========

    private static final int TITLE_COLOR = 0xFF7A5C3E;
    private static final int SLOT_COLOR = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR = 0x60FFFFFF;
    private static final int ACCENT_BAR_COLOR = 0xB0A8854F;
    private static final ItemStack ICON = new ItemStack(Items.SMITHING_TABLE);

    // ========== 装饰符号（→，自定义贴图，复用铁砧箭头） ==========

    /** → 装饰贴图（anvil_arrow.png 22×15，与铁砧/工作台共用；位于 textures/gui/sprites，atlas id 无前缀） */
    private static final Identifier ARROW_SPRITE = Identifier.fromNamespaceAndPath("youzaiworldcore", "anvil_arrow");
    /** "→" 绘制位置（相对面板，附加槽与结果槽之间） */
    private static final int ARROW_X = 68, ARROW_Y = 49, ARROW_W = 22, ARROW_H = 15;

    // ========== 原版资源（复用，零新增纹理） ==========

    private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/smithing/error");
    private static final Identifier EMPTY_SLOT_SMITHING_TEMPLATE_ARMOR_TRIM =
            Identifier.withDefaultNamespace("container/slot/smithing_template_armor_trim");
    private static final Identifier EMPTY_SLOT_SMITHING_TEMPLATE_NETHERITE_UPGRADE =
            Identifier.withDefaultNamespace("container/slot/smithing_template_netherite_upgrade");
    private static final List<Identifier> EMPTY_SLOT_SMITHING_TEMPLATES =
            List.of(EMPTY_SLOT_SMITHING_TEMPLATE_ARMOR_TRIM, EMPTY_SLOT_SMITHING_TEMPLATE_NETHERITE_UPGRADE);

    // ========== 装甲架预览常量（复制原版数值） ==========

    private static final Vector3f ARMOR_STAND_TRANSLATION = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Quaternionf ARMOR_STAND_ANGLE = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, 3.1415927F);
    /** 预览区（相对面板）：(121,20)-(161,80)，即结果槽右侧 40×60 */
    private static final int ARMOR_STAND_LEFT = 121;
    private static final int ARMOR_STAND_TOP = 20;
    private static final int ARMOR_STAND_RIGHT = 161;
    private static final int ARMOR_STAND_BOTTOM = 80;
    /** 错误图标位置（相对面板，复制原版） */
    private static final int ERROR_ICON_X = 65;
    private static final int ERROR_ICON_Y = 46;
    private static final int ERROR_ICON_WIDTH = 28;
    private static final int ERROR_ICON_HEIGHT = 21;

    // ========== 锻造台特有 ==========

    private final CyclingSlotBackground templateIcon;
    private final CyclingSlotBackground baseIcon;
    private final CyclingSlotBackground additionalIcon;
    private final ArmorStandRenderState armorStandPreview;

    // ========== 构造 ==========

    public YzuSmithingScreen(SmithingMenu menu, Inventory playerInventory, Component title) {
        // 沿用原版 ItemCombinerScreen 构造（menuResource 仅作占位，背景由 YZUI 面板取代）
        super(menu, playerInventory, title,
                Identifier.withDefaultNamespace("textures/gui/container/smithing.png"));
        this.templateIcon = new CyclingSlotBackground(0);
        this.baseIcon = new CyclingSlotBackground(1);
        this.additionalIcon = new CyclingSlotBackground(2);
        this.armorStandPreview = new ArmorStandRenderState();
        this.titleLabelX = 44;
        this.titleLabelY = 15;

        this.armorStandPreview.entityType = EntityTypes.ARMOR_STAND;
        this.armorStandPreview.showBasePlate = false;
        this.armorStandPreview.showArms = true;
        this.armorStandPreview.xRot = 25.0F;
        this.armorStandPreview.bodyRot = 210.0F;

        DebugLogger.info("YzuSmithingScreen", "创建 YZUI 锻造台屏幕: title=%s menuType=%s",
                title.getString(), menu.getType());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuSmithingScreen.init() — leftPos={} topPos={} image={}x{} title={}",
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.title.getString());
    }

    @Override
    protected void subInit() {
        this.updateArmorStandPreview(this.menu.getSlot(3).getItem());
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawMainPanel(g);
        drawTitle(g);
        drawInventoryLabel(g);
        drawCloseButton(g, mouseX, mouseY);

        // 空槽位图标循环（原版在 extractBackground 绘制，此处移到面板之上、槽位之下）
        this.templateIcon.extractRenderState(this.menu, g, partialTick, this.leftPos, this.topPos);
        this.baseIcon.extractRenderState(this.menu, g, partialTick, this.leftPos, this.topPos);
        this.additionalIcon.extractRenderState(this.menu, g, partialTick, this.leftPos, this.topPos);

        drawArmorStandPreview(g);
        drawErrorIcon(g);
        drawOperators(g);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
            float partialTick) {
        // no-op — YZUI 面板在 extractRenderState 中绘制
    }

    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        drawSlotBackgrounds(guiGraphics, mouseX, mouseY);
        super.extractSlots(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // no-op — 标题/背包标签由 YZUI 自绘
    }

    @Override
    protected void extractErrorIcon(@NonNull GuiGraphicsExtractor g, int x, int y) {
        // no-op — 错误图标由 extractRenderState 中的 drawErrorIcon 绘制
    }

    // ========== 锻造台特有渲染 ==========

    /** 装甲架预览：结果槽装备实时渲染在右侧 40×60 区域。 */
    private void drawArmorStandPreview(GuiGraphicsExtractor g) {
        g.entity(this.armorStandPreview, 25.0F, ARMOR_STAND_TRANSLATION, ARMOR_STAND_ANGLE, null,
                this.leftPos + ARMOR_STAND_LEFT, this.topPos + ARMOR_STAND_TOP,
                this.leftPos + ARMOR_STAND_RIGHT, this.topPos + ARMOR_STAND_BOTTOM);
    }

    /** 配方不可用错误图标（条件由菜单 DataSlot 驱动，原版 sprite）。 */
    private void drawErrorIcon(GuiGraphicsExtractor g) {
        if (this.menu.hasRecipeError()) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE,
                    this.leftPos + ERROR_ICON_X, this.topPos + ERROR_ICON_Y,
                    ERROR_ICON_WIDTH, ERROR_ICON_HEIGHT);
        }
    }

    // ========== 锻造台交互（复制原版逻辑，保证行为一致） ==========

    @Override
    public void containerTick() {
        super.containerTick();
        Optional<SmithingTemplateItem> template = this.getTemplateItem();
        this.templateIcon.tick(EMPTY_SLOT_SMITHING_TEMPLATES);
        this.baseIcon.tick(template.map(SmithingTemplateItem::getBaseSlotEmptyIcons).orElse(List.of()));
        this.additionalIcon.tick(template.map(SmithingTemplateItem::getAdditionalSlotEmptyIcons).orElse(List.of()));
    }

    private Optional<SmithingTemplateItem> getTemplateItem() {
        ItemStack stack = this.menu.getSlot(0).getItem();
        if (!stack.isEmpty() && stack.getItem() instanceof SmithingTemplateItem item) {
            return Optional.of(item);
        }
        return Optional.empty();
    }

    @Override
    public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack stack) {
        if (slotIndex == 3) {
            this.updateArmorStandPreview(stack);
        }
    }

    /**
     * 更新装甲架预览（复制原版逻辑）：
     * 结果槽物品按装备槽位分层——可渲染装甲放入对应部位；头盔类非装甲物品走
     * {@code HEAD} 物品模型；其余非装备物品显示在左手。
     */
    private void updateArmorStandPreview(ItemStack stack) {
        this.armorStandPreview.leftHandItemStack = ItemStack.EMPTY;
        this.armorStandPreview.leftHandItemState.clear();
        this.armorStandPreview.headEquipment = ItemStack.EMPTY;
        this.armorStandPreview.headItem.clear();
        this.armorStandPreview.chestEquipment = ItemStack.EMPTY;
        this.armorStandPreview.legsEquipment = ItemStack.EMPTY;
        this.armorStandPreview.feetEquipment = ItemStack.EMPTY;

        if (stack.isEmpty()) {
            return;
        }

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        EquipmentSlot equipmentSlot = equippable != null ? equippable.slot() : null;
        ItemModelResolver resolver = this.minecraft != null ? this.minecraft.getItemModelResolver() : null;
        if (resolver == null) {
            return;
        }

        if (equipmentSlot == null) {
            // 非装备物品 → 左手持有
            this.armorStandPreview.leftHandItemStack = stack.copy();
            resolver.updateForTopItem(this.armorStandPreview.leftHandItemState, stack,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND, null, null, 0);
            return;
        }

        switch (equipmentSlot) {
            case HEAD -> {
                if (HumanoidArmorLayer.shouldRender(stack, EquipmentSlot.HEAD)) {
                    this.armorStandPreview.headEquipment = stack.copy();
                } else {
                    resolver.updateForTopItem(this.armorStandPreview.headItem, stack,
                            ItemDisplayContext.HEAD, null, null, 0);
                }
            }
            case CHEST -> this.armorStandPreview.chestEquipment = stack.copy();
            case LEGS -> this.armorStandPreview.legsEquipment = stack.copy();
            case FEET -> this.armorStandPreview.feetEquipment = stack.copy();
            default -> {
                this.armorStandPreview.leftHandItemStack = stack.copy();
                resolver.updateForTopItem(this.armorStandPreview.leftHandItemState, stack,
                        ItemDisplayContext.THIRD_PERSON_LEFT_HAND, null, null, 0);
            }
        }
    }

    // ========== 鼠标事件（仅关闭按钮，其余交互委托原版） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        if (ev.button() == 0 && isOverCloseButton((int) ev.x(), (int) ev.y())) {
            DebugLogger.info("YzuSmithingScreen", "点击关闭按钮，关闭锻造台: %s", this.title.getString());
            this.onClose();
            return true;
        }
        return super.mouseClicked(ev, real);
    }

    // ========== YZUI 面板绘制 ==========

    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillR(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, PANEL_RADIUS, PANEL_BG);
    }

    /** 标题区：锻造台图标（12×12 底衬 + 缩放物品）+ 标题文字 + 强调条。 */
    private void drawTitle(GuiGraphicsExtractor g) {
        int ix = this.leftPos + 8;
        int iy = this.topPos + 5;

        fillR(g, ix, iy, ICON_SIZE, ICON_SIZE, 3, SLOT_COLOR);
        if (!ICON.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate(ix, iy);
            g.pose().scale(ICON_SCALE, ICON_SCALE);
            g.item(ICON, 0, 0, 0);
            g.pose().popMatrix();
        }

        int tx = ix + ICON_SIZE + TITLE_ICON_GAP;
        int ty = this.topPos + 5;
        g.text(this.font, this.title, tx, ty, TITLE_COLOR, false);

        int titleWidth = Math.min(this.font.width(this.title), this.imageWidth - 8 - ICON_SIZE - TITLE_ICON_GAP - 8);
        fillR(g, tx, ty + 10, titleWidth, 2, 1, ACCENT_BAR_COLOR);
    }

    private void drawInventoryLabel(GuiGraphicsExtractor g) {
        g.text(this.font, this.playerInventoryTitle,
                this.leftPos + 8, this.topPos + this.imageHeight - 94, LABEL_COLOR, false);
    }

    private void drawCloseButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int cx = this.leftPos + this.imageWidth - CLOSE_SIZE - CLOSE_MARGIN;
        int cy = this.topPos + CLOSE_TOP;
        boolean hovered = isOverCloseButton(mouseX, mouseY);
        fillR(g, cx, cy, CLOSE_SIZE, CLOSE_SIZE, CLOSE_RADIUS, hovered ? CLOSE_BG_HOVER : CLOSE_BG);
        int tx = cx + (CLOSE_SIZE - this.font.width(CLOSE_GLYPH)) / 2;
        int ty = cy + (CLOSE_SIZE - this.font.lineHeight) / 2;
        g.text(this.font, CLOSE_GLYPH, tx, ty, hovered ? CLOSE_ICON_HOVER : CLOSE_ICON, false);
    }

    private void drawSlotBackgrounds(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            boolean hovered = mouseX >= this.leftPos + slot.x && mouseX < this.leftPos + slot.x + SLOT_SIZE
                    && mouseY >= this.topPos + slot.y && mouseY < this.topPos + slot.y + SLOT_SIZE;
            fillR(g, slot.x, slot.y, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS,
                    hovered ? SLOT_HOVER_COLOR : SLOT_COLOR);
        }
    }

    private boolean isOverCloseButton(int mx, int my) {
        int cx = this.leftPos + this.imageWidth - CLOSE_SIZE - CLOSE_MARGIN;
        int cy = this.topPos + CLOSE_TOP;
        return mx >= cx && mx < cx + CLOSE_SIZE && my >= cy && my < cy + CLOSE_SIZE;
    }

    // ========== 工具方法 ==========

    private static void fillR(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 3) {
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, c);
        for (int j = 0; j < r; j++) {
            int n = 0;
            while (n < r && n * n + j * j < r * r) {
                n++;
            }
            int x0 = x + r - n, x1 = x + w - r + n;
            g.fill(x0, y + r - j - 1, x1, y + r - j, c);
            g.fill(x0, y + h - r + j, x1, y + h - r + j + 1, c);
        }
    }

    /**
     * 补画锻造台装饰符号（自定义贴图 blitSprite）：
     * <ul>
     *   <li>{@code →}（anvil_arrow.png）在附加槽与结果槽之间 @ (68,49,22,15)。</li>
     * </ul>
     */
    private void drawOperators(GuiGraphicsExtractor g) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_SPRITE,
                this.leftPos + ARROW_X, this.topPos + ARROW_Y, ARROW_W, ARROW_H);
    }
}
