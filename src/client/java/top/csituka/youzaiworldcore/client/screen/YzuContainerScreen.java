package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * YZUI 箱类容器：普通箱子、大箱子、末影箱、木桶及铜箱子。
 * 使用官网薄荷色 MD3 卡片、统一槽位状态与容器物品图标。
 * 槽位坐标、点击分发、快捷移动和容器类型判定沿用既有实现。
 */
@SuppressWarnings({ "null", "unused" })
public class YzuContainerScreen extends AbstractContainerScreen<ChestMenu> {

    private static final Logger LOGGER = LoggerFactory.getLogger("YzuContainerScreen");

    // ========== YZUI 统一设计常量 ==========

    /** 面板背景随主题及效果预设切换。 */
    private static int panelBg() { return YzuiTheme.surface(); }
    private static final int PANEL_RADIUS = 6;

    private static final int SLOT_SIZE = 16;
    private static final int SLOT_RADIUS = 3; // r≤3 走矩形快速路径，等价实心矩形

    /** 玩家背包标题使用主题正文色。 */
    private static int labelColor() { return YzuiTheme.text(); }

    // ========== 关闭按钮 ==========

    private static final int CLOSE_SIZE = 14;
    private static final int CLOSE_RADIUS = 4;
    /** 距面板右缘的间距 */
    private static final int CLOSE_MARGIN = 6;
    /** 距面板上缘的间距（底部 ≤16，与容器格 y=18 保持 2px 间隙） */
    private static final int CLOSE_TOP = 2;
    private static int closeBg() { return YzuiTheme.surface(); }
    private static int closeBgHover() { return YzuiTheme.surfaceHigh(); }
    private static int closeIcon() { return YzuiTheme.text(); }
    private static int closeIconHover() { return YzuiTheme.text(); }
    private static final String CLOSE_GLYPH = "\u00d7"; // ×

    /** 标题图标与文字间距 */
    private static final int TITLE_ICON_GAP = 4;
    /** 标题区图标缩放（16px 物品模型 → 12px 显示，为顶部留白腾空间） */
    private static final float ICON_SCALE = 0.75f;
    private static final int ICON_SIZE = 12;

    private final int containerRows;
    private final Kind kind;

    // ========== 构造 ==========

    public YzuContainerScreen(ChestMenu menu, Inventory playerInventory, Component title) {
        // 沿用原版 ContainerScreen 的面板尺寸：176 × (114 + rows*18)
        super(menu, playerInventory, title, 176, 114 + menu.getRowCount() * 18);
        this.containerRows = menu.getRowCount();
        this.kind = Kind.resolve(menu, title);
        DebugLogger.info("YzuContainerScreen", "创建 YZUI 容器屏幕: kind=%s rows=%d title=%s menuType=%s",
                kind, containerRows, title.getString(), menu.getType());
    }

    // ========== 初始化 ==========

    @Override
    protected void init() {
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        super.init();
        LOGGER.debug("YzuContainerScreen.init() — kind={} rows={} leftPos={} topPos={} image={}x{} title={}",
                kind, containerRows, this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
                this.title.getString());
    }

    // ========== 渲染管线 ==========

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 面板、标题、强调条、背包标签、关闭按钮均在 super 之前绘制（底层）
        drawMainPanel(g);
        drawTitle(g);
        drawInventoryLabel(g);
        drawCloseButton(g, mouseX, mouseY);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY,
            float partialTick) {
        // 背景由共用屏幕入口在内容变换之前绘制，避免重复模糊与叠加遮罩。
    }

    @Override
    protected void extractSlots(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        drawSlotBackgrounds(guiGraphics, mouseX, mouseY);
        super.extractSlots(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // no-op — 标题由 drawTitle 绘制（替换原版深灰文字）
    }

    // ========== 鼠标事件（仅关闭按钮，其余交互全部委托原版） ==========

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean real) {
        if (ev.button() == 0 && isOverCloseButton((int) ev.x(), (int) ev.y())) {
            DebugLogger.info("YzuContainerScreen", "点击关闭按钮，关闭容器: %s", this.title.getString());
            this.onClose();
            return true;
        }
        return super.mouseClicked(ev, real);
    }

    // ========== YZUI 面板绘制 ==========

    /** 面板背景：半透明白圆角矩形（尺寸与槽位布局严格一致）。 */
    private void drawMainPanel(GuiGraphicsExtractor g) {
        YzuiTheme.card(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    /**
     * 标题区：容器图标（带槽位风格底衬）+ 标题文字 + 类型强调条。
     * <p>
     * ⚠️ 26.2 {@code GuiGraphicsExtractor.text()} 的 y 参数是<b>文字顶部</b>
     * （非基线，与原版 titleLabelY=6 的标题渲染位置一致），字形占 y..y+9。
     * 头部可用高度 18px（容器格起点 y=18），顶部留白 5px：
     * <ul>
     * <li>图标底衬 12×12 @ (8,5) → 5..17；物品模型 16×16 经 pose 矩阵缩放
     *     0.75 渲染为 12×12，与底衬精确对齐（translate+scale，参照
     *     MailViewport/ItemDisplayEntry 的既有用法）；</li>
     * <li>标题文字顶部 ty=5 → 字形 5..14，与图标行顶部对齐；</li>
     * <li>强调条 y=15..17，紧跟文字下方、不压容器格。</li>
     * </ul>
     */
    private void drawTitle(GuiGraphicsExtractor g) {
        int ix = this.leftPos + 8;
        int iy = this.topPos + 5;

        // 图标底衬 12×12（物品模型缩放 0.75 → 12×12 居中覆盖）
        fillR(g, ix, iy, ICON_SIZE, ICON_SIZE, 3, YzuiTheme.slot());
        ItemStack icon = kind.icon;
        if (!icon.isEmpty()) {
            g.pose().pushMatrix();
            g.pose().translate(ix, iy);
            g.pose().scale(ICON_SCALE, ICON_SCALE);
            g.item(icon, 0, 0, 0);
            g.pose().popMatrix();
        }

        int tx = ix + ICON_SIZE + TITLE_ICON_GAP;
        int ty = this.topPos + 5; // 文字顶部（字形 5..14），与图标行顶部对齐
        g.text(this.font, this.title, tx, ty, YzuiTheme.primary(), false);

        // 强调条：位于标题下方（字形底 14 + 1 → 15..17），不压容器格；宽度取标题实际宽度
        int titleWidth = Math.min(this.font.width(this.title), this.imageWidth - 8 - ICON_SIZE - TITLE_ICON_GAP - 8);
        fillR(g, tx, ty + 10, titleWidth, 2, 1, YzuiTheme.primary());
    }

    /** 玩家背包区域标签（沿用原版标签坐标 imageHeight-94，仅改 YZUI 配色，无阴影）。 */
    private void drawInventoryLabel(GuiGraphicsExtractor g) {
        g.text(this.font, this.playerInventoryTitle,
                this.leftPos + 8, this.topPos + this.imageHeight - 94, labelColor(), false);
    }

    /** 关闭按钮：圆角矩形 + × 图标，悬停提亮。 */
    private void drawCloseButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int cx = this.leftPos + this.imageWidth - CLOSE_SIZE - CLOSE_MARGIN;
        int cy = this.topPos + CLOSE_TOP;
        boolean hovered = isOverCloseButton(mouseX, mouseY);
        fillR(g, cx, cy, CLOSE_SIZE, CLOSE_SIZE, CLOSE_RADIUS, hovered ? closeBgHover() : closeBg());
        int tx = cx + (CLOSE_SIZE - this.font.width(CLOSE_GLYPH)) / 2;
        int ty = cy + (CLOSE_SIZE - this.font.lineHeight) / 2;
        g.text(this.font, CLOSE_GLYPH, tx, ty, hovered ? closeIconHover() : closeIcon(), false);
    }

    /** 槽位背景：每个活动槽绘制类型色圆角矩形，悬浮提亮（与 YzuInventoryScreen 同款）。 */
    private void drawSlotBackgrounds(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Slot slot : this.menu.slots) {
            if (!slot.isActive())
                continue;
            boolean hovered = mouseX >= this.leftPos + slot.x && mouseX < this.leftPos + slot.x + SLOT_SIZE
                    && mouseY >= this.topPos + slot.y && mouseY < this.topPos + slot.y + SLOT_SIZE;
            fillR(g, slot.x, slot.y, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS,
                    hovered ? YzuiTheme.slotHover() : YzuiTheme.slot());
        }
    }

    /** 关闭按钮命中检测（鼠标绝对坐标）。 */
    private boolean isOverCloseButton(int mx, int my) {
        int cx = this.leftPos + this.imageWidth - CLOSE_SIZE - CLOSE_MARGIN;
        int cy = this.topPos + CLOSE_TOP;
        return mx >= cx && mx < cx + CLOSE_SIZE && my >= cy && my < cy + CLOSE_SIZE;
    }

    // ========== 工具方法 ==========

    /**
     * 圆角矩形填充（优化版，与 YzuCreativeInventoryScreen 一致）。
     * <p>
     * 每次 {@code g.fill} 都会往 GuiRenderState 塞一个 ColoredRectangleRenderState，
     * 圆角必须按整行扫描线输出，不能逐像素填：
     * <ul>
     * <li>{@code r <= 3}：圆角判定对所有角像素恒成立 → 只发 1 个 fill（实心矩形）；</li>
     * <li>其余：中间整块 1 个 + 上下各 r 行、每行 1 个，共 1 + 2r 个。</li>
     * </ul>
     */
    private static void fillR(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0)
            return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 3) {
            // 圆角不裁掉任何像素，等价于实心矩形
            g.fill(x, y, x + w, y + h, c);
            return;
        }
        // 中间整块（含左右两条直边）
        g.fill(x, y + r, x + w, y + h - r, c);
        // 上下圆角区：每行一条扫描线
        for (int j = 0; j < r; j++) {
            int n = 0;
            while (n < r && n * n + j * j < r * r)
                n++;
            int x0 = x + r - n, x1 = x + w - r + n;
            g.fill(x0, y + r - j - 1, x1, y + r - j, c);         // 顶部第 j 行
            g.fill(x0, y + h - r + j, x1, y + h - r + j + 1, c); // 底部第 j 行
        }
    }

    // ========== 容器类型 ==========

    /**
     * YZUI 容器类型。各容器共享统一的 YZUI 设计语言，仅通过
     * 容器图标与类型区分，所有颜色即时读取当前 MD3 主题。
     */
    public enum Kind {
        /** 普通箱子（含陷阱箱）：3×9，中性灰强调 */
        CHEST("container.chest", Items.CHEST),
        /** 大箱子（双格）：6×9，暗金强调 */
        DOUBLE_CHEST("container.chestDouble", Items.CHEST),
        /** 末影箱：3×9，暗紫强调 */
        ENDER_CHEST("container.enderchest", Items.ENDER_CHEST),
        /** 木桶：3×9，深木强调 */
        BARREL("container.barrel", Items.BARREL),
        /** 铜箱子（含锈蚀/涂蜡变体，26.2 新增）：3×9，铜橙强调 */
        COPPER_CHEST("block.minecraft.copper_chest", copperChestIcon());

        /** 原版翻译键（仅用于日志/定位） */
        private final String langKey;
        /** 标题区容器图标 */
        private final ItemStack icon;

        Kind(String langKey, Item icon) {
            this.langKey = langKey;
            this.icon = new ItemStack(icon);
        }

        public String getLangKey() {
            return langKey;
        }

        /** 铜箱子图标：取 WeatheringCopperCollection 中未氧化（UNAFFECTED）形态的物品。 */
        private static Item copperChestIcon() {
            return Items.COPPER_CHEST.asList().getFirst();
        }

        /**
         * 依据菜单与标题解析容器类型。
         * <p>
         * <b>不能</b>使用 {@code menu.getContainer()} 区分方块：客户端创建菜单时
         * （{@code MenuType.create}）走无容器工厂，容器恒为客户端新建的
         * {@code SimpleContainer}，方块实体信息不可达。类型识别改为服务端下发的
         * <b>标题翻译键</b>：
         * <ol>
         * <li>行数 ≥ 6（GENERIC_9x6）→ 大箱子（双格）；</li>
         * <li>{@code container.enderchest} → 末影箱；</li>
         * <li>{@code container.barrel} → 木桶；</li>
         * <li>{@code block.minecraft.*copper_chest} → 铜箱子（服务端
         *     {@code ChestBlockEntityCopperTitleMixin} 已将标题修正为方块名称）；</li>
         * <li>其余（{@code container.chest} / 自定义命名）→ 普通箱子。</li>
         * </ol>
         */
        static Kind resolve(ChestMenu menu, Component title) {
            if (menu.getRowCount() >= 6)
                return DOUBLE_CHEST;
            if (title.getContents() instanceof TranslatableContents tc) {
                String key = tc.getKey();
                if ("container.enderchest".equals(key))
                    return ENDER_CHEST;
                if ("container.barrel".equals(key))
                    return BARREL;
                if (key.startsWith("block.minecraft.") && key.endsWith("copper_chest"))
                    return COPPER_CHEST;
            }
            return CHEST;
        }

        @Override
        public String toString() {
            return name();
        }
    }
}
