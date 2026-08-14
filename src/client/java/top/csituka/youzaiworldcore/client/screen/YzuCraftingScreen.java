package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 工作台屏幕（替换原版 {@code CraftingScreen}）。
 * <p>
 * 26.2 中原版工作台屏继承 {@link AbstractRecipeBookScreen}（3×3 合成 + 配方书），
 * 本类沿用同一基类，将背景纹理替换为 YZUI 白底圆角面板，并保留全部原版交互：
 * <ul>
 *   <li><b>3×3 合成</b>：合成格点击、Shift 快捷移动、双击收集、拖拽分发全继承原版；</li>
 *   <li><b>配方书</b>：{@link CraftingRecipeBookComponent} 完整保留，按钮位于
 *       面板左侧 (leftPos+5, height/2-49)（原版坐标），配方书面板/按钮/标签页
 *       的 YZUI 化由项目既有 RecipeBook 系列 Mixin 处理；</li>
 *   <li><b>合成箭头</b>：合成格与输出槽之间绘制原版交易箭头 sprite
 *       （与 {@link YzuInventoryScreen} 同款做法）；</li>
 *   <li><b>结果槽高亮</b>：输出槽底色提亮（与 {@link YzuInventoryScreen} 一致）。</li>
 * </ul>
 * <p>
 * <h3>槽位布局（沿用原版，相对面板左上角）</h3>
 * <table>
 *   <tr><th>槽位</th><th>坐标</th><th>说明</th></tr>
 *   <tr><td>结果</td><td>(124, 35)</td><td>合成结果（高亮）</td></tr>
 *   <tr><td>3×3 合成格</td><td>起点 (30, 17)</td><td>9 格，间距 18</td></tr>
 *   <tr><td>玩家背包</td><td>起点 (8, 84)</td><td>36 格</td></tr>
 * </table>
 * 面板尺寸 176×166；合成箭头 (93, 35, 20, 16)。
 * <p>
 * 主题色：<b>工作台浅木棕</b> —— 标题 {@code 0xFF8B6F47}、强调条 {@code 0xB0C8A05C}、
 * 槽位 {@code 0x40FFFFFF / 悬停 0x60FFFFFF}（统一白色系派生，与
 * {@link YzuContainerScreen}/{@link YzuShulkerBoxScreen} 同设计语言）。
 * <p>
 * 开关机制：{@code YzuContainerScreenSwitchMixin} 在 {@code Gui.setScreen}
 * 拦截原版 {@code CraftingScreen}，仅当 {@code ClientExternalSettings.isYzuiEnabled()}
 * 为 {@code true} 时替换为本类。零新增纹理（纯色矩形 + 原版物品图标/sprite）。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuCraftingScreen extends AbstractRecipeBookScreen<CraftingMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuCraftingScreen");

    // ========== YZUI 统一设计常量（与 YzuContainerScreen 一致） ==========

    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_RADIUS = 6;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3;

    /** 合成结果槽底色（比普通槽位更亮，突出输出位） */
    private static final int CRAFT_RESULT_BG = 0x60FFFFFF;

    private static final int LABEL_COLOR = 0xCC404040;

    // ========== 装饰符号（→） ==========

    private static final String ARROW_GLYPH = "\u2192"; // →
    private static final int OPERATOR_COLOR = 0xFF604018;
    private static final int OPERATOR_BG = 0x30000000;
    private static final int OPERATOR_BG_W = 22;
    private static final int OPERATOR_BG_H = 14;
    /** "→" 中心坐标（3×3 合成格(82) 与 结果槽(124) 之间） */
    private static final int ARROW_CENTER_X = 103;
    private static final int ARROW_CENTER_Y = 43;
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

    // ========== 工作台主题（浅木棕） ==========

    private static final int TITLE_COLOR = 0xFF8B6F47;
    private static final int SLOT_COLOR = 0x40FFFFFF;
    private static final int SLOT_HOVER_COLOR = 0x60FFFFFF;
    private static final int ACCENT_BAR_COLOR = 0xB0C8A05C;
    private static final ItemStack ICON = new ItemStack(Items.CRAFTING_TABLE);

    /** 合成箭头位置（已移除，使用 drawOperatorGlyph 字符代替） */

    // ========== 构造 ==========

    public YzuCraftingScreen(CraftingMenu menu, Inventory playerInventory, Component title) {
        super(menu, new CraftingRecipeBookComponent(menu), playerInventory, title);
        DebugLogger.info("YzuCraftingScreen", "创建 YZUI 工作台屏幕: title=%s menuType=%s",
                title.getString(), menu.getType());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        this.titleLabelX = 29;
        LOGGER.debug("YzuCraftingScreen.init() — leftPos={} topPos={} image={}x{} title={}",
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.title.getString());
    }

    @Override
    protected @NonNull ScreenPosition getRecipeBookButtonPosition() {
        return new ScreenPosition(this.leftPos + 5, this.height / 2 - 49);
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawMainPanel(g);
        drawTitle(g);
        drawInventoryLabel(g);
        drawCloseButton(g, mouseX, mouseY);
        drawCraftOperator(g);

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
        // 注意：此处必须调 super（AbstractRecipeBookScreen.extractSlots），
        // 它会在配方书打开时处理槽位右移偏移。
        super.extractSlots(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // no-op — 标题/背包标签由 YZUI 自绘
    }

    // ========== 鼠标事件（仅关闭按钮，其余交互委托原版） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        if (ev.button() == 0 && isOverCloseButton((int) ev.x(), (int) ev.y())) {
            DebugLogger.info("YzuCraftingScreen", "点击关闭按钮，关闭工作台: %s", this.title.getString());
            this.onClose();
            return true;
        }
        return super.mouseClicked(ev, real);
    }

    // ========== YZUI 面板绘制 ==========

    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillR(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, PANEL_RADIUS, PANEL_BG);
    }

    /** 标题区：工作台图标（12×12 底衬 + 缩放物品）+ 标题文字 + 强调条。 */
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

    /** 玩家背包区域标签（沿用原版标签坐标 imageHeight-94，无阴影）。 */
    private void drawInventoryLabel(GuiGraphicsExtractor g) {
        g.text(this.font, this.playerInventoryTitle,
                this.leftPos + 8, this.topPos + this.imageHeight - 94, LABEL_COLOR, false);
    }

    /** 关闭按钮：圆角矩形 + × 图标，悬停提亮。 */
    private void drawCloseButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int cx = this.leftPos + this.imageWidth - CLOSE_SIZE - CLOSE_MARGIN;
        int cy = this.topPos + CLOSE_TOP;
        boolean hovered = isOverCloseButton(mouseX, mouseY);
        fillR(g, cx, cy, CLOSE_SIZE, CLOSE_SIZE, CLOSE_RADIUS, hovered ? CLOSE_BG_HOVER : CLOSE_BG);
        int tx = cx + (CLOSE_SIZE - this.font.width(CLOSE_GLYPH)) / 2;
        int ty = cy + (CLOSE_SIZE - this.font.lineHeight) / 2;
        g.text(this.font, CLOSE_GLYPH, tx, ty, hovered ? CLOSE_ICON_HOVER : CLOSE_ICON, false);
    }

    /** 合成箭头：3×3 合成格与结果槽之间的"→"（YZUI 字符风格；原版 trade_arrow
     * sprite 在白底面板上几乎不可见，故改用文字字符 + 圆角小框，与浅木棕主题呼应）。 */
    private void drawCraftOperator(GuiGraphicsExtractor g) {
        int bgLeft = this.leftPos + ARROW_CENTER_X - OPERATOR_BG_W / 2;
        int bgTop = this.topPos + ARROW_CENTER_Y - OPERATOR_BG_H / 2;
        fillR(g, bgLeft, bgTop, OPERATOR_BG_W, OPERATOR_BG_H, 3, OPERATOR_BG);
        int textY = bgTop + (OPERATOR_BG_H - this.font.lineHeight) / 2;
        g.text(this.font, ARROW_GLYPH,
                this.leftPos + ARROW_CENTER_X - this.font.width(ARROW_GLYPH) / 2, textY,
                OPERATOR_COLOR, false);
    }

    /** 槽位背景：每个活动槽绘制主题色圆角矩形，悬浮提亮；结果槽额外提亮。 */
    private void drawSlotBackgrounds(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            boolean hovered = mouseX >= this.leftPos + slot.x && mouseX < this.leftPos + slot.x + SLOT_SIZE
                    && mouseY >= this.topPos + slot.y && mouseY < this.topPos + slot.y + SLOT_SIZE;
            int color = (i == 0) ? CRAFT_RESULT_BG : (hovered ? SLOT_HOVER_COLOR : SLOT_COLOR);
            fillR(g, slot.x, slot.y, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS, color);
        }
    }

    /** 关闭按钮命中检测（鼠标绝对坐标）。 */
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
