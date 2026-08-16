package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundRenameItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 铁砧屏幕（替换原版 {@code AnvilScreen}）。
 * <p>
 * 26.2 中原版铁砧屏继承 {@link ItemCombinerScreen}（含菜单监听器生命周期），
 * 本类沿用同一基类，仅将背景纹理替换为 YZUI 白底圆角面板，并保留全部原版交互：
 * <ul>
 *   <li><b>物品重命名</b>：使用 {@link MultiLineEditBox} 支持<b>多行文本命名</b>
 *       （{@code \\n} 分行，物品 {@code item_name} 组件支持多行显示），
 *       输入经 {@link AnvilMenu#setItemName} + {@code ServerboundRenameItemPacket} 同步；</li>
 *   <li><b>维修/附魔</b>：槽位点击、Shift 快捷移动、双击收集、拖拽分发全继承原版；</li>
 *   <li><b>经验价格显示</b>：右下角经验价格文本（绿/红）与"过于昂贵"提示保留原版语义；</li>
 *   <li><b>错误图标</b>：输入存在但结果为空时，复用原版 {@code container/anvil/error} sprite；</li>
 *   <li><b>经验条动画</b>：{@code containerTick} 中同步
 *       {@code experienceDisplayStartTick}（与原版一致）。</li>
 *   <li><b>装饰符号</b>：输入槽之间补画 {@code +}，输入2 与结果槽之间补画
 *       {@code →}（与原版 anvil.png 纹理一致，YZUI 字符风格）。</li>
 * </ul>
 * <p>
 * <h3>槽位布局（沿用原版，相对面板左上角）</h3>
 * <table>
 *   <tr><th>槽位/元素</th><th>坐标</th><th>说明</th></tr>
 *   <tr><td>输入 1</td><td>(27, 47)</td><td>待维修/附魔物品</td></tr>
 *   <tr><td>+</td><td>(59, 47..58)</td><td>两输入之间符号</td></tr>
 *   <tr><td>输入 2</td><td>(76, 47)</td><td>维修材料/附魔书</td></tr>
 *   <tr><td>→</td><td>(109, 47..58)</td><td>输入2 与结果之间符号</td></tr>
 *   <tr><td>结果</td><td>(134, 47)</td><td>合成结果</td></tr>
 *   <tr><td>玩家背包</td><td>起点 (8, 84)</td><td>36 格</td></tr>
 * </table>
 * 面板尺寸 176×166；重命名输入框（{@link MultiLineEditBox}）组件位于
 * <b>(8, 18, 161, 28)</b>（占用原版 anvil.png 左上角锤子图案的空间，左缘对齐面板边距 8，
 * 右缘保持原 sprite 右缘 169），背景框 = 组件区域；文本经 {@code innerPadding(4)}
 * 在框内左右各留 4px 间距（12..165）。支持 2 行显示（{@code setLineLimit(2)}）。
 * <p>
 * 主题色：<b>铁砧钢灰</b> —— 标题 {@code 0xFF6A6A6A}、强调条 {@code 0xB09A9A9A}、
 * 槽位 {@code 0x40FFFFFF / 悬停 0x60FFFFFF}（统一白色系派生，与
 * {@link YzuContainerScreen}/{@link YzuShulkerBoxScreen} 同设计语言）；
 * {@code +/→} 装饰符深灰 {@code 0xFF606060}。
 * <p>
 * 开关机制：{@code YzuContainerScreenSwitchMixin} 在 {@code Gui.setScreen}
 * 拦截原版 {@code AnvilScreen}，仅当 {@code ClientExternalSettings.isYzuiEnabled()}
 * 为 {@code true} 时替换为本类。零新增纹理（纯色矩形 + 原版物品图标/错误 sprite）。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuAnvilScreen extends ItemCombinerScreen<AnvilMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuAnvilScreen");

    // ========== YZUI 统一设计常量（与 YzuContainerScreen 一致） ==========

    /** 面板背景：半透明白 */
    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_RADIUS = 6;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3; // r≤3 走矩形快速路径

    /** 玩家背包标题（深灰，白底可读，无阴影） */
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

    // ========== 铁砧主题（钢灰） ==========

    private static final int TITLE_COLOR = 0xFF6A6A6A;
    private static final int SLOT_COLOR = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR = 0x60FFFFFF;
    private static final int ACCENT_BAR_COLOR = 0xB09A9A9A;
    private static final ItemStack ICON = new ItemStack(Items.ANVIL);

    // ========== 装饰符号（+/→，自定义贴图 blitSprite） ==========

    /** 铁砧 + 装饰贴图（13×13，透明背景，手绘；位于 textures/gui/sprites，atlas id 无前缀） */
    private static final Identifier PLUS_SPRITE = Identifier.fromNamespaceAndPath("youzaiworldcore", "anvil_plus");
    /** 铁砧 → 装饰贴图（22×15，透明背景，手绘；锻造台/工作台复用同一张贴图） */
    private static final Identifier ARROW_SPRITE = Identifier.fromNamespaceAndPath("youzaiworldcore", "anvil_arrow");
    /** "+" 绘制位置（相对面板，与槽位行对齐） */
    private static final int PLUS_X = 53, PLUS_Y = 49, PLUS_W = 13, PLUS_H = 13;
    /** "→" 绘制位置（相对面板） */
    private static final int ARROW_X = 102, ARROW_Y = 48, ARROW_W = 22, ARROW_H = 15;

    // ========== 原版资源（复用，零新增纹理） ==========

    private static final Identifier ERROR_SPRITE = Identifier.withDefaultNamespace("container/anvil/error");

    // ========== 铁砧特有 ==========

    private static final Component TOO_EXPENSIVE_TEXT = Component.translatable("container.repair.expensive");
    private static final int COST_COLOR_OK = 0xFF80FF20;
    private static final int COST_COLOR_BAD = 0xFFFF6060;
    private static final int COST_BG = 0x4F000000;
    private static final int COST_Y = 69;
    private static final int COST_BG_TOP = 67;
    private static final int COST_BG_BOTTOM = 79;

    /**
     * 重命名输入框（{@link MultiLineEditBox}，支持 {@code \\n} 多行命名；
     * YZUI 样式由项目既有 {@code MultiLineEditBoxYzuiMixin} 接管渲染）。
     */
    private MultiLineEditBox name;
    private final Player player;

    // ========== 构造 ==========

    public YzuAnvilScreen(AnvilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title,
                Identifier.withDefaultNamespace("textures/gui/container/anvil.png"));
        this.player = playerInventory.player;
        this.titleLabelX = 60;
        DebugLogger.info("YzuAnvilScreen", "创建 YZUI 铁砧屏幕: title=%s menuType=%s",
                title.getString(), menu.getType());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuAnvilScreen.init() — leftPos={} topPos={} image={}x{} title={}",
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.title.getString());
    }

    /**
     * 创建重命名输入框（{@link MultiLineEditBox}，2 行高）。
     * <p>
     * <b>占用原版锤子图案空间：</b>原版 {@code anvil.png} 左上角绘制大锤子图案
     * （{@code x≈8..47, y≈7..46}），其右侧才是文本域 sprite
     * {@code (59, 20, 110, 16)}——锤子占据了 51px 宽的左侧空间。
     * YZUI 面板是纯色圆角矩形（无锤子），因此这里将输入框<b>向左扩展占用</b>锤子区域：
     * 视觉背景框 <b>(8, 18, 161, 28)</b>——左缘对齐 YZUI 面板内边距 <b>8</b>
     * （与标题图标 / 背包标签左缘一致），右缘保持原 sprite 右缘 <b>169</b>。
     * </p>
     * <p>
     * <b>内边距：</b>{@code AbstractTextAreaWidget.innerPadding() = 4}，
     * 文本/占位符/光标渲染起点 = {@code getX() + 4}。组件 x 直接设为
     * {@code leftPos + 8}（背景框 = 组件区域，Mixin 无需补偿），于是：
     * <ul>
     *   <li>文本起点 = {@code 8 + 4 = 12}，距背景框左缘 4px；</li>
     *   <li>文本宽度 = {@code 161 - totalInnerPadding(8) = 153}，右缘 = {@code 12 + 153 = 165}，
     *       距背景框右缘 4px——文字与边框左右各留 4px 间距（对称），不再贴边。</li>
     * </ul>
     * 高度 28 支持 2 行（{@code setLineLimit(2)}），允许在物品名中
     * 输入 {@code \\n} 实现多行显示（{@code item_name} 组件支持多行）。
     * </p>
     */
    @Override
    protected void subInit() {
        Slot firstSlot = this.menu.getSlot(0);
        String initialValue = firstSlot.hasItem()
                ? firstSlot.getItem().getHoverName().getString() : "";

        // 26.2 MultiLineEditBox.Builder：仅配置 x/y/颜色/背景，尺寸与监听在
        // build(font, width, height, label) 之后通过实例方法设置（Builder 无 setWidth/setHeight）。
        // x = leftPos+8、width = 161：背景框 = 组件区域 (8..169)，
        // 文本经 innerPadding(4) 自动留出左右 4px 间距（12..165）。
        this.name = new MultiLineEditBox.Builder()
                .setX(this.leftPos + 8)
                .setY(this.topPos + 18)
                .setPlaceholder(Component.translatable("container.repair"))
                .setTextColor(0xFF404040)
                .setTextShadow(false)
                .setCursorColor(0xFF000000)
                .setShowBackground(false)
                .setShowDecorations(true)
                .build(this.font, 161, 28, Component.empty());
        this.name.setLineLimit(2);
        this.name.setCharacterLimit(AnvilMenu.MAX_NAME_LENGTH);
        this.name.setValueListener(this::onNameChanged);
        this.name.setValue(initialValue);
        this.addRenderableWidget(this.name);
        DebugLogger.info("YzuAnvilScreen", "重命名输入框初始化完成 @ (%d,%d) 161x28 multiLine=true"
                        + "（视觉背景框 8..169，文本 12..165 与边框各留 4px 间距）",
                this.leftPos + 8, this.topPos + 18);
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawMainPanel(g);
        drawTitle(g);
        drawInventoryLabel(g);
        drawCloseButton(g, mouseX, mouseY);
        // 顺序敏感：先画装饰符（+/→），再画错误图标（X）。
        // 两者区域重叠（X: 99..127×45..66，→: 102..124×48..63），
        // 延迟渲染按提交顺序合成，后画者在上；X 必须盖住箭头（与原版语义一致）。
        drawOperators(g);
        drawErrorIcon(g);

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
    protected void extractLabels(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int cost = this.menu.getCost();
        if (cost <= 0) {
            return;
        }

        Component text;
        int color;
        if (cost >= 40 && !this.player.hasInfiniteMaterials()) {
            text = TOO_EXPENSIVE_TEXT;
            color = COST_COLOR_BAD;
        } else {
            Slot resultSlot = this.menu.getSlot(2);
            if (!resultSlot.hasItem()) {
                return;
            }
            text = Component.translatable("container.repair.cost", cost);
            color = COST_COLOR_OK;
            if (!resultSlot.mayPickup(this.player)) {
                color = COST_COLOR_BAD;
            }
        }
        if (text == null) {
            return;
        }

        int x = this.imageWidth - 8 - this.font.width(text) - 2;
        fillR(g, x - 2, COST_BG_TOP, (this.imageWidth - 8) - (x - 2),
                COST_BG_BOTTOM - COST_BG_TOP, 1, COST_BG);
        g.text(this.font, text, x, COST_Y, color, false);
    }

    private void drawErrorIcon(GuiGraphicsExtractor g) {
        boolean hasInput = this.menu.getSlot(0).hasItem() || this.menu.getSlot(1).hasItem();
        if (hasInput && !this.menu.getSlot(this.menu.getResultSlot()).hasItem()) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ERROR_SPRITE,
                    this.leftPos + 99, this.topPos + 45, 28, 21);
        }
    }

    /**
     * 补画铁砧两个装饰符号（自定义贴图 blitSprite）：
     * <ul>
     *   <li>{@code +}（anvil_plus.png）在两个输入槽之间 @ (53,49,13,13)；</li>
     *   <li>{@code →}（anvil_arrow.png）在输入2 与结果槽之间 @ (102,48,22,15)。</li>
     * </ul>
     */
    private void drawOperators(GuiGraphicsExtractor g) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PLUS_SPRITE,
                this.leftPos + PLUS_X, this.topPos + PLUS_Y, PLUS_W, PLUS_H);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_SPRITE,
                this.leftPos + ARROW_X, this.topPos + ARROW_Y, ARROW_W, ARROW_H);
    }

    @Override
    protected void extractErrorIcon(@NonNull GuiGraphicsExtractor g, int x, int y) {
        // no-op — 错误图标由 extractErrorIcon 方法绘制
    }

    // ========== 铁砧交互（复制原版逻辑） ==========

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.experienceDisplayStartTick = this.minecraft.player.tickCount;
        }
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.name);
    }

    @Override
    public void resize(int width, int height) {
        String value = this.name != null ? this.name.getValue() : "";
        this.init(width, height);
        if (this.name != null) {
            this.name.setValue(value);
        }
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent keyEvent) {
        if (keyEvent.isEscape()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.closeContainer();
            }
            return true;
        }
        // MultiLineEditBox 无 canConsumeInput()（EditBox 特有）；文本编辑键由其
        // keyPressed 消费，未消费的交给原版 AbstractContainerScreen 处理快捷键。
        return this.name.keyPressed(keyEvent) || super.keyPressed(keyEvent);
    }

    @Override
    public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack stack) {
        if (slotIndex == 0 && this.name != null) {
            this.name.setValue(stack.isEmpty() ? "" : stack.getHoverName().getString());
        }
    }

    private void onNameChanged(String newName) {
        Slot firstSlot = this.menu.getSlot(0);
        if (!firstSlot.hasItem()) {
            return;
        }
        String name = newName;
        if (!firstSlot.getItem().has(DataComponents.CUSTOM_NAME)
                && newName.equals(firstSlot.getItem().getHoverName().getString())) {
            name = "";
        }
        if (this.menu.setItemName(name) && this.minecraft != null
                && this.minecraft.player != null && this.minecraft.player.connection != null) {
            this.minecraft.player.connection.send(new ServerboundRenameItemPacket(name));
        }
    }

    // ========== 鼠标事件（仅关闭按钮） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        if (ev.button() == 0 && isOverCloseButton((int) ev.x(), (int) ev.y())) {
            DebugLogger.info("YzuAnvilScreen", "点击关闭按钮，关闭铁砧: %s", this.title.getString());
            this.onClose();
            return true;
        }
        return super.mouseClicked(ev, real);
    }

    // ========== YZUI 面板绘制 ==========

    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillR(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, PANEL_RADIUS, PANEL_BG);
    }

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
}