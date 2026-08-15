package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 酿造台屏幕（替换原版 {@code BrewingStandScreen}）。
 * <p>
 * 26.2 中原版酿造台屏继承 {@link AbstractContainerScreen}，本类沿用同一基类，
 * 将背景纹理替换为 YZUI 白底圆角面板，并保留全部原版交互与动态指示：
 * <ul>
 *   <li><b>燃料条</b>：左下角 18×4 水平条，橙黄色按 {@code menu.getFuel()} 比例填充
 *       （0..20，18px 满格）；</li>
 *   <li><b>酿造进度</b>：材料槽下方 9×28 竖直条，黄绿色从底部向上生长
 *       （{@code progress = 28 * (1 - ticks/400)}）；</li>
 *   <li><b>气泡动画</b>：进度条左侧 12×29 区域，深灰色"气泡水位"按
 *       {@code BUBBLELENGTHS[ticks/2 % 7]} 高度脉动（还原原版气泡节奏）；</li>
 *   <li><b>槽位交互</b>：点击、Shift 快捷移动、双击收集、拖拽分发全继承原版；
 *       药水槽/材料槽/燃料槽的服务端校验（PotionSlot/IngredientsSlot/FuelSlot）
 *       原样生效。</li>
 * </ul>
 * <p>
 * <h3>槽位布局（沿用原版，相对面板左上角）</h3>
 * <table>
 *   <tr><th>槽位</th><th>坐标</th><th>说明</th></tr>
 *   <tr><td>药水 1/2/3</td><td>(56,51) (79,58) (102,51)</td><td>玻璃瓶槽</td></tr>
 *   <tr><td>材料</td><td>(79, 17)</td><td>酿造材料（地狱疣等）</td></tr>
 *   <tr><td>燃料</td><td>(17, 17)</td><td>烈焰粉</td></tr>
 *   <tr><td>玩家背包</td><td>起点 (8, 84)</td><td>36 格</td></tr>
 * </table>
 * 面板尺寸 176×166；燃料条 (60,44,18,4)、进度条 (97,16,9,28)、气泡区 (63,14,12,29)。
 * <p>
 * 主题色：<b>酿造台暗青</b> —— 标题 {@code 0xFF3F6E6E}、强调条 {@code 0xB06FC0B0}、
 * 槽位 {@code 0x50A0D8D0 / 悬停 0x70A0D8D0}（青绿系，与箱子类白色系槽位区分，
 * 但整体仍在 YZUI 统一设计语言内）。动态指示器为纯色圆角矩形，零新增纹理。
 * <p>
 * 开关机制：{@code YzuContainerScreenSwitchMixin} 在 {@code Gui.setScreen}
 * 拦截原版 {@code BrewingStandScreen}，仅当 {@code ClientExternalSettings.isYzuiEnabled()}
 * 为 {@code true} 时替换为本类。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuBrewingStandScreen extends AbstractContainerScreen<BrewingStandMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuBrewingStandScreen");

    // ========== YZUI 统一设计常量（与 YzuContainerScreen 一致） ==========

    private static final int PANEL_BG = 0x80FFFFFF;
    private static final int PANEL_RADIUS = 6;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3;

    private static final int LABEL_COLOR = 0xCC404040;

    // ========== 装饰符号（↓，自定义贴图） ==========

    /** ↓ 装饰贴图（brewing_down.png 22×17，透明背景，手绘；位于 textures/gui/sprites，atlas id 无前缀） */
    private static final Identifier ARROW_DOWN_SPRITE = Identifier.fromNamespaceAndPath("youzaiworldcore", "brewing_down");
    /** "↓" 绘制位置（相对面板）：材料槽(79,17) 下方到药水槽之间，水平居中 */
    private static final int ARROW_DOWN_X = 69, ARROW_DOWN_Y = 33, ARROW_DOWN_W = 22, ARROW_DOWN_H = 17;

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

    // ========== 酿造台主题（暗青，与箱子类白色系区分） ==========

    private static final int TITLE_COLOR = 0xFF3F6E6E;
    private static final int SLOT_COLOR = 0x50A0D8D0;
    private static final int SLOT_HOVER_COLOR = 0x70A0D8D0;
    private static final int ACCENT_BAR_COLOR = 0xB06FC0B0;
    private static final ItemStack ICON = new ItemStack(Items.BREWING_STAND);

    // ========== 动态指示器（纯色 YZUI 风格，坐标沿用原版） ==========

    /** 燃料条：位置 (60,44)，18×4 水平条；燃料 0..20 → 18px 满格 */
    private static final int FUEL_X = 60;
    private static final int FUEL_Y = 44;
    private static final int FUEL_W = 18;
    private static final int FUEL_H = 4;
    private static final int FUEL_BG = 0x30000000;
    private static final int FUEL_FILL = 0xCCFF8C1A;

    /** 酿造进度条：位置 (97,16)，9×28 竖直条，从底部向上生长 */
    private static final int PROGRESS_X = 97;
    private static final int PROGRESS_Y = 16;
    private static final int PROGRESS_W = 9;
    private static final int PROGRESS_H = 28;
    private static final int PROGRESS_BG = 0x30000000;
    private static final int PROGRESS_FILL = 0xC0C8E068;
    /** 酿造总时长（tick），进度 = 1 - ticks/400 */
    private static final float BREW_TIME = 400.0F;

    /** 气泡区：位置 (63,14)，12×29 竖直脉动 */
    private static final int BUBBLE_X = 63;
    private static final int BUBBLE_Y = 14;
    private static final int BUBBLE_W = 12;
    private static final int BUBBLE_H = 29;
    private static final int BUBBLE_BG = 0x28000000;
    private static final int BUBBLE_FILL = 0x90000000;
    /** 气泡高度脉动序列（原版 BUBBLELENGTHS，由 ticks/2 % 7 索引） */
    private static final int[] BUBBLELENGTHS = { 29, 24, 20, 16, 11, 6, 0 };

    // ========== 构造 ==========

    public YzuBrewingStandScreen(BrewingStandMenu menu, Inventory playerInventory, Component title) {
        // 面板尺寸沿用原版：176 × 166
        super(menu, playerInventory, title, 176, 166);
        DebugLogger.info("YzuBrewingStandScreen", "创建 YZUI 酿造台屏幕: title=%s menuType=%s",
                title.getString(), menu.getType());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuBrewingStandScreen.init() — leftPos={} topPos={} image={}x{} title={}",
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.title.getString());
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawMainPanel(g);
        drawTitle(g);
        drawInventoryLabel(g);
        drawCloseButton(g, mouseX, mouseY);
        drawBrewIndicators(g);
        drawOperator(g);

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

    // ========== 鼠标事件（仅关闭按钮，其余交互委托原版） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        if (ev.button() == 0 && isOverCloseButton((int) ev.x(), (int) ev.y())) {
            DebugLogger.info("YzuBrewingStandScreen", "点击关闭按钮，关闭酿造台: %s", this.title.getString());
            this.onClose();
            return true;
        }
        return super.mouseClicked(ev, real);
    }

    // ========== 酿造动态指示器 ==========

    /**
     * 燃料条 / 酿造进度 / 气泡动画（坐标与节奏沿用原版，绘制改为 YZUI 纯色圆角矩形）。
     * 数据来源：{@code menu.getFuel()}（0..20）、{@code menu.getBrewingTicks()}（400 满）。
     * 本方法在 extractRenderState 中调用（无 translate），坐标必须为屏幕绝对坐标。
     */
    private void drawBrewIndicators(GuiGraphicsExtractor g) {
        int ox = this.leftPos;
        int oy = this.topPos;

        // 1. 燃料条（底槽常显，填充按比例）
        int fuelWidth = Mth.clamp((18 * this.menu.getFuel() + 20 - 1) / 20, 0, 18);
        fillR(g, ox + FUEL_X, oy + FUEL_Y, FUEL_W, FUEL_H, 2, FUEL_BG);
        if (fuelWidth > 0) {
            fillR(g, ox + FUEL_X, oy + FUEL_Y, fuelWidth, FUEL_H, 2, FUEL_FILL);
        }

        // 2. 酿造进度 + 气泡（仅酿造中显示）
        int ticks = this.menu.getBrewingTicks();
        if (ticks <= 0) {
            return;
        }
        int progress = (int) (28.0F * (1.0F - ticks / BREW_TIME));
        if (progress > 0) {
            fillR(g, ox + PROGRESS_X, oy + PROGRESS_Y, PROGRESS_W, PROGRESS_H, 2, PROGRESS_BG);
            // 从底部向上生长：y 起点 = 条底 - progress
            fillR(g, ox + PROGRESS_X, oy + PROGRESS_Y + PROGRESS_H - progress, PROGRESS_W, progress, 2,
                    PROGRESS_FILL);
        }
        int bubble = BUBBLELENGTHS[ticks / 2 % 7];
        if (bubble > 0) {
            fillR(g, ox + BUBBLE_X, oy + BUBBLE_Y, BUBBLE_W, BUBBLE_H, 3, BUBBLE_BG);
            // 气泡水位从底部向上
            fillR(g, ox + BUBBLE_X, oy + BUBBLE_Y + BUBBLE_H - bubble, BUBBLE_W, bubble, 3, BUBBLE_FILL);
        }
    }

    // ========== YZUI 面板绘制 ==========

    private void drawMainPanel(GuiGraphicsExtractor g) {
        fillR(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, PANEL_RADIUS, PANEL_BG);
    }

    /** 标题区：酿造台图标（12×12 底衬 + 缩放物品）+ 标题文字 + 强调条。 */
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

    /** 槽位背景：每个活动槽绘制主题色圆角矩形，悬浮提亮。 */
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

    /**
     * 补画酿造台装饰符号（自定义贴图 blitSprite）：
     * <ul>
     *   <li>{@code ↓}（brewing_down.png 22×17）在材料槽与药水槽之间，表示材料流向药水。</li>
     * </ul>
     */
    private void drawOperator(GuiGraphicsExtractor g) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW_DOWN_SPRITE,
                this.leftPos + ARROW_DOWN_X, this.topPos + ARROW_DOWN_Y,
                ARROW_DOWN_W, ARROW_DOWN_H);
    }
}
